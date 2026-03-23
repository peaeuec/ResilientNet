import java.net.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;

public class MeshNode {
    private static final int PORT = 9876;
    private DatagramSocket socket;
    private MeshEventListener listener;

    private boolean running = true;
    private boolean isAdmin;
    private String nodeId; 
    private String role;

    private Map<InetSocketAddress, Long> neighbors = new ConcurrentHashMap<>();
    private Map<String, InetSocketAddress> knownNodes = new ConcurrentHashMap<>();
    private Map<String, Message> localStore = new ConcurrentHashMap<>();
    
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public MeshNode(boolean adminMode, MeshEventListener listener) throws Exception {
        this.isAdmin = adminMode;
        this.role = adminMode ? "ADMIN" : "USER";
        this.listener = listener;
        this.nodeId = loadOrGenerateId(); 

        socket = new DatagramSocket(PORT);
        socket.setBroadcast(true);

        listener.onSystemMessage("MESH NODE ONLINE (" + role + ") | ID: " + nodeId);

        startDiscoveryHeartbeat();
        startTimeoutChecker();
    }

    public String getNodeId() { return nodeId; }

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
            try { sendBroadcastHello(); } catch (Exception ignored) {}
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
                        listener.onPeerLost(timedOutId);
                        knownNodes.remove(timedOutId);
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
                    
                    PacketType type = (PacketType) ois.readObject();
                    String senderId = (String) ois.readObject();

                    if (!knownNodes.containsKey(senderId)) {
                        listener.onPeerDiscovered(senderId);
                    }

                    neighbors.put(sender, System.currentTimeMillis());
                    knownNodes.put(senderId, sender);

                    if (type == PacketType.DATA) {
                        Message msg = (Message) ois.readObject();
                        handleIncomingMessage(msg);
                    } 
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void handleIncomingMessage(Message msg) {
        if (localStore.containsKey(msg.id)) return;
        localStore.put(msg.id, msg);

        if (msg.scope == Message.Scope.BROADCAST || 
           (msg.scope == Message.Scope.TARGETED && (msg.targets.contains(nodeId) || isAdmin))) {
            listener.onMessageReceived(msg);
        }

        if (msg.ttl > 0 && msg.scope != Message.Scope.LOCAL) {
            msg.ttl--;
            for (InetSocketAddress n : neighbors.keySet()) {
                try { sendObject(PacketType.DATA, msg, n); } catch (Exception ignored) {}
            }
        }
    }

    public void broadcastMessage(String content) {
        Message msg = new Message(content, nodeId, role, Message.Scope.BROADCAST, null);
        localStore.put(msg.id, msg);
        for (InetSocketAddress n : neighbors.keySet()) {
            try { sendObject(PacketType.DATA, msg, n); } catch (Exception ignored) {}
        }
    }

    private void sendBroadcastHello() throws Exception {
        InetSocketAddress b1 = new InetSocketAddress("255.255.255.255", PORT);
        sendObject(PacketType.HELLO, null, b1);
    }

    private void sendObject(PacketType type, Object payload, InetSocketAddress target) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
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