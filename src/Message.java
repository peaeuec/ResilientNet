import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Message implements Serializable {
    public enum Scope { BROADCAST, TARGETED }

    public String id;
    public String senderId;
    public String senderRole;
    public String content;
    public boolean urgent;
    public long timestamp;
    public Scope scope;
    public Set<String> targets;

    public Message(String content, String senderId, String senderRole, Scope scope, Set<String> targets) {
        this.id = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.content = content;
        this.scope = scope;
        this.targets = targets != null ? targets : new HashSet<>();
        this.timestamp = System.currentTimeMillis();
        
        String up = content.toUpperCase();
        this.urgent = up.contains("HELP") || up.contains("SOS");
    }

    @Override
    public String toString() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z")
                                .withZone(ZoneId.of("Asia/Kolkata"))
                                .withLocale(Locale.ENGLISH);
        
        String formattedDate = dtf.format(Instant.ofEpochMilli(this.timestamp));
        return String.format("[%s] %s (%s): %s", formattedDate, senderId, senderRole, content);
    }
}