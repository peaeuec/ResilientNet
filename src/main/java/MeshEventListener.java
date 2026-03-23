public interface MeshEventListener {
    void onMessageReceived(Message msg);
    void onPeerDiscovered(String peerId);
    void onPeerLost(String peerId);
    void onSystemMessage(String text);
}