import java.net.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class MeshNode {
    private static final int PORT = 9876;
    private DatagramSocket socket;

    // ===== Node State =====
    private int batteryLevel = 100;
    private boolean running = true;
    private boolean isNeighborInRange = true; 
    private boolean isAdmin;

    private String nodeId; 
    private String role;

    // ===== Mesh State =====
    private Map<InetSocketAddress, Long> neighbors = new ConcurrentHashMap<>();
    private Map<String, InetSocketAddress> knownNodes = new ConcurrentHashMap<>();
    private Set<String> disconnectedNodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    private Map<String, Message> localStore = new ConcurrentHashMap<>();
    private List<Message> buffer = Collections.synchronizedList(new ArrayList<>());

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public MeshNode(boolean adminMode) throws Exception {
        this.isAdmin = adminMode;
        this.role = adminMode ? "ADMIN" : "USER";
        this.nodeId = loadOrGenerateId(); 

        socket = new DatagramSocket(PORT);
        socket.setBroadcast(true);

        System.out.println("\n========================================");
        System.out.println("MESH NODE ONLINE (" + role + ")");
        System.out.println("Node ID  : " + nodeId);
        System.out.println("Battery  : " + batteryLevel + "%");
        System.out.println("========================================\n");

        startDiscoveryHeartbeat();
        startTimeoutChecker();
    }

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

    private void startDiscoveryHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (isNeighborInRange) sendBroadcastHello();
            } catch (Exception ignored) {}
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
                    
                    if (timedOutId != null) disconnectedNodes.add(timedOutId);

                    if (isAdmin) {
                        System.out.println("\n[ADMIN ALERT] Node Disconnected: " + 
                            (timedOutId != null ? timedOutId : entry.getKey().getAddress().getHostAddress()));
                        System.out.print("You (" + batteryLevel + "%): ");
                    }
                }
                return timedOut;
            });
        }, 5, 5, TimeUnit.SECONDS);
    }

    public void startListening() {
        new Thread(() -> {
            byte[] buf = new byte[8192];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    InetAddress myAddr = InetAddress.getLocalHost();
                    if (packet.getAddress().getHostAddress().equals(myAddr.getHostAddress()) && packet.getPort() == PORT) {
                        continue;
                    }

                    InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(packet.getData()));
                    String type = (String) ois.readObject();
                    String senderId = (String) ois.readObject();

                    // Logic fix: Only show ONE notification
                    boolean wasOffline = disconnectedNodes.remove(senderId);
                    boolean isNew = !knownNodes.containsKey(senderId);

                    if (wasOffline) {
                        System.out.println("\n[RE-APPEARANCE] Node " + senderId + " is back online!");
                        System.out.print("You (" + batteryLevel + "%): ");
                    } else if (isNew) {
                        System.out.println("\n[PEER DISCOVERED] " + senderId + " (" + sender.getAddress().getHostAddress() + ")");
                        System.out.print("You (" + batteryLevel + "%): ");
                    }

                    neighbors.put(sender, System.currentTimeMillis());
                    knownNodes.put(senderId, sender);

                    if (type.equals("DATA")) {
                        Message msg = (Message) ois.readObject();
                        sendObject("ACK", null, sender); 
                        handleIncomingMessage(msg);
                    } 
                    else if (type.equals("ACK")) {
                        System.out.println("\n[DELIVERY RECEIPT] Message received by " + senderId);
                        System.out.print("You (" + batteryLevel + "%): ");
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void handleIncomingMessage(Message msg) {
        if (localStore.containsKey(msg.id)) return;
        
        if (msg.scope == Message.Scope.TARGETED) {
            if (!msg.targets.contains(nodeId) && !isAdmin) return;
        }

        localStore.put(msg.id, msg);

        if (isAdmin && msg.urgent) {
            // Added IST timestamp to Admin Alert
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z").withZone(ZoneId.of("Asia/Kolkata"));
            String formattedTime = dtf.format(Instant.ofEpochMilli(msg.timestamp));
            System.out.println("\n[" + formattedTime + "] [ADMIN ALERT - SOS] From " + msg.senderId + ": " + msg.content);
        } else {
            System.out.println("\n" + msg.toString());
        }
        System.out.print("You (" + batteryLevel + "%): ");
    }

    public void startSending() throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.println("Commands:");
        System.out.println(" /sendall <msg>           - Broadcast to all");
        System.out.println(" /sendto <id1,id2> <msg>  - Target specific IDs (Admin only)");
        System.out.println(" /peers                   - Show active neighbors");
        System.out.println(" /status                  - Show full node registry (Admin only)");
        System.out.println(" /toggle                  - Simulate Signal Loss/Gain");
        System.out.println(" /setbat <x>              - Change battery level");
        System.out.println("Direct Chat: Just type your message to contact ADMIN directly.\n");

        while (running) {
            System.out.print("You (" + batteryLevel + "%): ");
            if (!sc.hasNextLine()) break;
            String input = sc.nextLine().trim();
            if (input.isEmpty()) continue;

            if (input.startsWith("/")) {
                handleCommand(input);
            } else {
                if (isAdmin) {
                    System.out.println("[INFO] You are Admin. Use /sendall to broadcast to everyone.");
                } else {
                    System.out.println("[HQ] Direct message sent to Admin.");
                    Message toAdmin = new Message(input, nodeId, role, Message.Scope.TARGETED, new HashSet<>());
                    sendOrBuffer(toAdmin);
                }
            }
        }
    }

    private void handleCommand(String input) {
        if (input.equalsIgnoreCase("/peers")) {
            neighbors.forEach((addr, time) -> System.out.println(" - " + addr.getAddress().getHostAddress() + " (" + (System.currentTimeMillis()-time)/1000 + "s ago)"));
        } 
        else if (input.equalsIgnoreCase("/status")) {
            if (!isAdmin) { System.out.println("[DENIED] Admin only."); return; }
            System.out.println("\n--- MESH NODE STATUS LIST ---");
            System.out.printf("%-10s | %-15s | %-10s\n", "Node ID", "Address", "Status");
            knownNodes.forEach((id, addr) -> {
                String status = neighbors.containsKey(addr) ? "ONLINE" : "OFFLINE";
                System.out.printf("%-10s | %-15s | %-10s\n", id, addr.getAddress().getHostAddress(), status);
            });
        }
        else if (input.equalsIgnoreCase("/toggle")) {
            isNeighborInRange = !isNeighborInRange;
            System.out.println(isNeighborInRange ? "[SIGNAL RECOVERED]" : "[SIGNAL LOST]");
            if (isNeighborInRange) flushBuffer();
        } else if (input.startsWith("/setbat")) {
            try { batteryLevel = Integer.parseInt(input.split(" ")[1]); } catch (Exception e) { System.out.println("Usage: /setbat <x>"); }
        } else if (input.startsWith("/sendall")) {
            String content = input.replace("/sendall", "").trim();
            if (!content.isEmpty()) sendOrBuffer(new Message(content, nodeId, role, Message.Scope.BROADCAST, null));
        } else if (input.startsWith("/sendto") && isAdmin) {
            try {
                String[] parts = input.split(" ", 3);
                Set<String> targets = new HashSet<>(Arrays.asList(parts[1].split(",")));
                sendOrBuffer(new Message(parts[2], nodeId, role, Message.Scope.TARGETED, targets));
            } catch (Exception e) { System.out.println("Usage: /sendto id1,id2 msg"); }
        }
    }

    private void sendOrBuffer(Message msg) {
        if (batteryLevel < 15 && !msg.urgent) {
            System.out.println("[BLOCKED] Low battery.");
            return;
        }
        localStore.put(msg.id, msg);
        if (!isNeighborInRange || neighbors.isEmpty()) {
            buffer.add(msg);
            System.out.println("[STORED] No neighbors found. Buffered.");
            return;
        }
        for (InetSocketAddress n : neighbors.keySet()) {
            try { sendObject("DATA", msg, n); } catch (Exception ignored) {}
        }
        batteryLevel--;
    }

    private void flushBuffer() {
        if (buffer.isEmpty()) return;
        System.out.println("Syncing...");
        synchronized (buffer) {
            for (Message msg : buffer) {
                for (InetSocketAddress n : neighbors.keySet()) {
                    try { sendObject("DATA", msg, n); } catch (Exception ignored) {}
                }
                batteryLevel--;
            }
            buffer.clear();
        }
    }

    private void sendBroadcastHello() throws Exception {
        InetSocketAddress b1 = new InetSocketAddress("255.255.255.255", PORT);
        sendObject("HELLO", null, b1);
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            String subnet = localIp.substring(0, localIp.lastIndexOf("."));
            sendObject("HELLO", null, new InetSocketAddress(subnet + ".255", PORT));
        } catch (Exception ignored) {}
    }

    private void sendObject(String type, Object payload, InetSocketAddress target) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(type);
        oos.writeObject(nodeId); 
        if (payload != null) oos.writeObject(payload);
        byte[] data = bos.toByteArray();
        socket.send(new DatagramPacket(data, data.length, target.getAddress(), target.getPort()));
    }

    public static void main(String[] args) throws Exception {
        boolean adminMode = args.length > 0 && args[0].equalsIgnoreCase("admin");
        MeshNode node = new MeshNode(adminMode);
        node.startListening();
        node.startSending();
    }
}