import java.net.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class MeshNode {
    private static final int PORT = 9876;
    private DatagramSocket socket;
    private MeshEventListener listener;

    // ===== Node State =====
    private int batteryLevel = 100;
    private boolean running = true;
    private boolean isNeighborInRange = true;
    private boolean isAdmin;
    private String nodeId;
    private String role;

    // ===== Mesh State =====
    private Map<InetSocketAddress, Long> neighbors    = new ConcurrentHashMap<>();
    private Map<String, InetSocketAddress> knownNodes = new ConcurrentHashMap<>();
    private Set<String> disconnectedNodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Map<String, String> nodePriorities = new ConcurrentHashMap<>(); // nodeId -> priority label

    private Map<String, Message> localStore = new ConcurrentHashMap<>();
    private List<Message> buffer = Collections.synchronizedList(new ArrayList<>());

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public MeshNode(boolean adminMode, MeshEventListener listener) throws Exception {
        this.isAdmin  = adminMode;
        this.role     = adminMode ? "ADMIN" : "USER";
        this.listener = listener;
        this.nodeId   = loadOrGenerateId();

        socket = new DatagramSocket(PORT);
        socket.setBroadcast(true);

        listener.onSystemMessage("MESH NODE ONLINE (" + role + ") | ID: " + nodeId + " | BAT: " + batteryLevel + "%");

        startDiscoveryHeartbeat();
        startTimeoutChecker();
    }

    // ─────────────────────────────────────────
    // Public Accessors
    // ─────────────────────────────────────────
    public String  getNodeId()    { return nodeId; }
    public String  getRole()      { return role; }
    public boolean isAdmin()      { return isAdmin; }
    public int     getBatteryLevel() { return batteryLevel; }

    /** Returns a snapshot of known node IDs (for priority dialog population) */
    public Set<String> getKnownNodeIds() {
        return Collections.unmodifiableSet(knownNodes.keySet());
    }

    public void setBatteryLevel(int level) {
        this.batteryLevel = Math.max(0, Math.min(100, level));
        listener.onBatteryUpdate(this.batteryLevel);
        listener.onSystemMessage("Battery set to " + this.batteryLevel + "%");
    }

    /** Admin sets a display-priority label for a node (e.g. "HIGH", "CRITICAL") */
    public void setNodePriority(String nodeId, String priority) {
        nodePriorities.put(nodeId, priority);
        fireDashboardUpdate();
    }

    // ─────────────────────────────────────────
    // ID Persistence
    // ─────────────────────────────────────────
    private String loadOrGenerateId() {
        File file = new File("node_id_" + PORT + ".txt");
        try {
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String id = reader.readLine();
                reader.close();
                return id;
            } else {
                String newId = UUID.randomUUID().toString().substring(0, 6);
                FileWriter writer = new FileWriter(file);
                writer.write(newId);
                writer.close();
                return newId;
            }
        } catch (IOException e) {
            return "NODE_" + (int)(Math.random() * 1000);
        }
    }

    // ─────────────────────────────────────────
    // Scheduled Tasks
    // ─────────────────────────────────────────
    private void startDiscoveryHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try { if (isNeighborInRange) sendBroadcastHello(); }
            catch (Exception ignored) {}
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void startTimeoutChecker() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            neighbors.entrySet().removeIf(entry -> {
                boolean timedOut = (now - entry.getValue()) > 15000;
                if (timedOut) {
                    String timedOutId = null;
                    for (Map.Entry<String, InetSocketAddress> known : knownNodes.entrySet()) {
                        if (known.getValue().equals(entry.getKey())) {
                            timedOutId = known.getKey();
                            break;
                        }
                    }
                    if (timedOutId != null) {
                        disconnectedNodes.add(timedOutId);
                        listener.onPeerLost(timedOutId);
                        if (isAdmin)
                            listener.onSystemMessage("[ADMIN ALERT] Node Disconnected: " + timedOutId);
                        fireDashboardUpdate();
                    }
                }
                return timedOut;
            });
        }, 5, 5, TimeUnit.SECONDS);
    }

    // ─────────────────────────────────────────
    // Listening
    // ─────────────────────────────────────────
    public void startListening() {
        new Thread(() -> {
            byte[] buf = new byte[8192];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    InetAddress myAddr = InetAddress.getLocalHost();
                    if (packet.getAddress().getHostAddress().equals(myAddr.getHostAddress())
                            && packet.getPort() == PORT) continue;

                    InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    ObjectInputStream ois = new ObjectInputStream(
                            new ByteArrayInputStream(packet.getData()));

                    PacketType type    = (PacketType) ois.readObject();
                    String     senderId = (String) ois.readObject();

                    boolean wasOffline = disconnectedNodes.remove(senderId);
                    boolean isNew      = !knownNodes.containsKey(senderId);

                    if (wasOffline) {
                        listener.onPeerReturned(senderId);
                        if (!knownNodes.containsKey(senderId)) listener.onPeerDiscovered(senderId);
                    } else if (isNew) {
                        listener.onPeerDiscovered(senderId);
                    }

                    neighbors.put(sender, System.currentTimeMillis());
                    knownNodes.put(senderId, sender);

                    if (type == PacketType.DATA) {
                        Message msg = (Message) ois.readObject();
                        sendObject(PacketType.ACK, null, sender);
                        handleIncomingMessage(msg);
                    } else if (type == PacketType.ACK) {
                        listener.onDeliveryAck(senderId);
                    }

                    fireDashboardUpdate();

                } catch (Exception ignored) {}
            }
        }).start();
    }

    // ─────────────────────────────────────────
    // Message Handling
    // ─────────────────────────────────────────
    private void handleIncomingMessage(Message msg) {
        if (localStore.containsKey(msg.id)) return;
        localStore.put(msg.id, msg);

        boolean forMe = msg.scope == Message.Scope.BROADCAST ||
                (msg.scope == Message.Scope.TARGETED &&
                 (msg.targets.contains(nodeId) || isAdmin));

        if (forMe) {
            if (isAdmin && msg.urgent) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z")
                        .withZone(ZoneId.of("Asia/Kolkata"));
                String ts = dtf.format(Instant.ofEpochMilli(msg.timestamp));
                listener.onSystemMessage("[" + ts + "] [SOS ALERT] From " + msg.senderId + ": " + msg.content);
            }
            listener.onMessageReceived(msg);
        }

        // Relay with TTL
        if (msg.ttl > 0 && msg.scope != Message.Scope.LOCAL) {
            msg.ttl--;
            for (InetSocketAddress n : neighbors.keySet()) {
                try { sendObject(PacketType.DATA, msg, n); } catch (Exception ignored) {}
            }
        }
    }

    // ─────────────────────────────────────────
    // Public Send API
    // ─────────────────────────────────────────
    public String broadcastMessage(String content, Message.Priority priority) {
        Message msg = new Message(content, nodeId, role, Message.Scope.BROADCAST, null, priority);
        return sendOrBuffer(msg);
    }

    public String broadcastMessage(String content) {
        return broadcastMessage(content, Message.Priority.NORMAL);
    }

    public String sendTargeted(String content, Set<String> targets, Message.Priority priority) {
        if (!isAdmin) return "[DENIED] Targeted send requires Admin role.";
        Message msg = new Message(content, nodeId, role, Message.Scope.TARGETED, targets, priority);
        return sendOrBuffer(msg);
    }

    public String sendTargeted(String content, Set<String> targets) {
        return sendTargeted(content, targets, Message.Priority.NORMAL);
    }

    private String sendOrBuffer(Message msg) {
        // CRITICAL always bypasses low-battery block
        if (batteryLevel < 15 && msg.priority != Message.Priority.CRITICAL) {
            return "[BLOCKED] Battery too low (" + batteryLevel + "%). Only CRITICAL messages allowed.";
        }
        localStore.put(msg.id, msg);

        if (!isNeighborInRange || neighbors.isEmpty()) {
            buffer.add(msg);
            listener.onBufferUpdate(buffer.size());
            return "[BUFFERED] No active neighbors. Message queued (" + buffer.size() + " in queue).";
        }

        for (InetSocketAddress n : neighbors.keySet()) {
            try { sendObject(PacketType.DATA, msg, n); } catch (Exception ignored) {}
        }
        batteryLevel = Math.max(0, batteryLevel - 1);
        listener.onBatteryUpdate(batteryLevel);
        return null;
    }

    // ─────────────────────────────────────────
    // Signal Toggle
    // ─────────────────────────────────────────
    public void toggleSignal() {
        isNeighborInRange = !isNeighborInRange;
        listener.onSignalToggled(isNeighborInRange);
        if (isNeighborInRange) flushBuffer();
    }

    private void flushBuffer() {
        if (buffer.isEmpty()) return;
        listener.onSystemMessage("Flushing " + buffer.size() + " buffered message(s)...");
        synchronized (buffer) {
            // Sort: CRITICAL first, then HIGH, NORMAL, LOW
            buffer.sort((a, b) -> b.priority.ordinal() - a.priority.ordinal());
            for (Message msg : buffer) {
                for (InetSocketAddress n : neighbors.keySet()) {
                    try { sendObject(PacketType.DATA, msg, n); } catch (Exception ignored) {}
                }
                batteryLevel = Math.max(0, batteryLevel - 1);
            }
            buffer.clear();
        }
        listener.onBufferUpdate(0);
        listener.onBatteryUpdate(batteryLevel);
        listener.onSystemMessage("Buffer flushed. All messages delivered.");
    }

    // ─────────────────────────────────────────
    // Dashboard Data
    // ─────────────────────────────────────────
    private void fireDashboardUpdate() {
        if (!isAdmin) return;
        Map<String, String> statuses   = new LinkedHashMap<>();
        Map<String, String> priorities = new LinkedHashMap<>();
        Map<String, String> addresses  = new LinkedHashMap<>();
        knownNodes.forEach((id, addr) -> {
            statuses.put(id, neighbors.containsKey(addr) ? "ONLINE" : "OFFLINE");
            priorities.put(id, nodePriorities.getOrDefault(id, "NORMAL"));
            addresses.put(id, addr.getAddress().getHostAddress());
        });
        listener.onDashboardUpdate(statuses, priorities, addresses);
    }

    public String getStatusReport() {
        if (!isAdmin) return "[DENIED] Admin only.";
        if (knownNodes.isEmpty()) return "[STATUS] No known nodes yet.";
        StringBuilder sb = new StringBuilder();
        sb.append("NODE REGISTRY\n");
        sb.append(String.format("%-10s  %-16s  %-8s  %s\n", "ID", "ADDRESS", "STATUS", "PRIORITY"));
        sb.append("─".repeat(52) + "\n");
        knownNodes.forEach((id, addr) -> {
            String status   = neighbors.containsKey(addr) ? "ONLINE" : "OFFLINE";
            String priority = nodePriorities.getOrDefault(id, "NORMAL");
            sb.append(String.format("%-10s  %-16s  %-8s  %s\n",
                    id, addr.getAddress().getHostAddress(), status, priority));
        });
        return sb.toString();
    }

    // ─────────────────────────────────────────
    // Network Helpers
    // ─────────────────────────────────────────
    private void sendBroadcastHello() throws Exception {
        sendObject(PacketType.HELLO, null, new InetSocketAddress("255.255.255.255", PORT));
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            String subnet  = localIp.substring(0, localIp.lastIndexOf("."));
            sendObject(PacketType.HELLO, null, new InetSocketAddress(subnet + ".255", PORT));
        } catch (Exception ignored) {}
    }

    private void sendObject(PacketType type, Object payload, InetSocketAddress target) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream    oos = new ObjectOutputStream(bos);
        oos.writeObject(type);
        oos.writeObject(nodeId);
        if (payload != null) oos.writeObject(payload);
        byte[] data = bos.toByteArray();
        socket.send(new DatagramPacket(data, data.length, target.getAddress(), target.getPort()));
    }

    public void shutdown() {
        running = false;
        scheduler.shutdown();
        if (socket != null && !socket.isClosed()) socket.close();
    }
}