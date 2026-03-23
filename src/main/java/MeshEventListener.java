public interface MeshEventListener {
    void onMessageReceived(Message msg);
    void onPeerDiscovered(String peerId);
    void onPeerLost(String peerId);
    void onPeerReturned(String peerId);       // Re-appearance detection
    void onSystemMessage(String text);
    void onSignalToggled(boolean signalOn);   // Signal loss/recovery UI update
    void onBufferUpdate(int bufferedCount);   // Buffer queue size
    void onDeliveryAck(String fromId);        // ACK receipt from peer
    void onBatteryUpdate(int level);          // Battery level changes
}