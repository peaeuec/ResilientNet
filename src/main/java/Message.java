import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Message implements Serializable {
    public enum Scope {
        LOCAL,      // Only for the immediate neighbor
        BROADCAST,  // For everyone in the mesh
        TARGETED    // For specific Node IDs
    }

    public String id;            
    public String senderId;      
    public String senderRole;    
    public String content;       
    public boolean urgent;       
    public long timestamp;       
    public Scope scope;          
    public Set<String> targets;  
    
    // This is the missing field causing your error!
    public int ttl = 3; 

    public Message(String content, String senderId, String senderRole, Scope scope, Set<String> targets) {
        this.id = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.content = content;
        this.scope = scope;
        this.targets = (targets != null) ? targets : new HashSet<>();
        this.timestamp = System.currentTimeMillis();

        String upper = content.toUpperCase();
        this.urgent = upper.contains("HELP") || upper.contains("SOS");
    }

    @Override
    public String toString() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss")
                                .withZone(ZoneId.systemDefault());
        String formattedTime = dtf.format(Instant.ofEpochMilli(this.timestamp));
        return String.format("[%s] %s: %s", formattedTime, senderId, content);
    }
}