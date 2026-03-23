import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class MeshApp extends Application implements MeshEventListener {
    private TextArea chatArea;
    private TextField inputField;
    private ListView<String> peerList;
    private MeshNode node;

    @Override
    public void start(Stage primaryStage) {
        // --- UI Setup ---
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");
        
        inputField = new TextField();
        inputField.setPromptText("Type a message...");
        Button sendBtn = new Button("Send");
        sendBtn.setStyle("-fx-background-color: #0078D7; -fx-text-fill: white; -fx-font-weight: bold;");
        
        peerList = new ListView<>();
        peerList.setPrefWidth(160);
        Label peerLabel = new Label("Active Peers:");
        peerLabel.setStyle("-fx-font-weight: bold; -fx-padding: 0 0 5 0;");

        VBox rightPane = new VBox(peerLabel, peerList);
        rightPane.setPadding(new Insets(10));
        rightPane.setStyle("-fx-background-color: #f0f0f0;");

        HBox bottomBox = new HBox(10, inputField, sendBtn);
        bottomBox.setPadding(new Insets(10));
        HBox.setHgrow(inputField, Priority.ALWAYS);
        
        BorderPane root = new BorderPane();
        root.setCenter(chatArea);
        root.setRight(rightPane);
        root.setBottom(bottomBox);

        // --- Network Node Initialization ---
        try {
            // Initializes the node. 'false' means it's a standard user, not admin.
            // 'this' passes the MeshEventListener to the node.
            node = new MeshNode(false, this); 
            node.startListening();
            primaryStage.setTitle("ResilientNet Chat - " + node.getNodeId());
        } catch (Exception e) {
            chatArea.appendText("Failed to start networking: " + e.getMessage() + "\n");
        }

        // --- Event Handlers ---
        sendBtn.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage()); // Send on Enter key

        primaryStage.setOnCloseRequest(e -> {
            if (node != null) node.shutdown();
            Platform.exit();
            System.exit(0);
        });

        primaryStage.setScene(new Scene(root, 750, 500));
        primaryStage.show();
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && node != null) {
            node.broadcastMessage(text);
            chatArea.appendText("You: " + text + "\n");
            inputField.clear();
        }
    }

    // --- MeshEventListener Implementations (Safely update UI from network thread) ---
    
    @Override
    public void onMessageReceived(Message msg) {
        Platform.runLater(() -> chatArea.appendText(msg.toString() + "\n"));
    }

    @Override
    public void onPeerDiscovered(String peerId) {
        Platform.runLater(() -> {
            if (!peerList.getItems().contains(peerId)) {
                peerList.getItems().add(peerId);
                chatArea.appendText("🔵 [SYSTEM] Peer joined: " + peerId + "\n");
            }
        });
    }

    @Override
    public void onPeerLost(String peerId) {
        Platform.runLater(() -> {
            peerList.getItems().remove(peerId);
            chatArea.appendText("🔴 [SYSTEM] Peer left: " + peerId + "\n");
        });
    }

    @Override
    public void onSystemMessage(String text) {
        Platform.runLater(() -> chatArea.appendText("⚙️ [SYSTEM] " + text + "\n"));
    }

    public static void main(String[] args) {
        launch(args);
    }
}