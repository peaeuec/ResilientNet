import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.stage.Stage;

public class MeshApp extends Application implements MeshEventListener {

    // ─── Backend admin password (fixed) ───────────────────────────────────────
    private static final String ADMIN_PASSWORD = "mesh@admin123";

    // ─── Core UI ──────────────────────────────────────────────────────────────
    private TextArea  chatArea;
    private TextField inputField;
    private ListView<String> peerList;
    private MeshNode  node;
    private Stage     primaryStage;

    // ─── Status Bar ───────────────────────────────────────────────────────────
    private Label       batteryLabel;
    private Label       signalLabel;
    private Label       nodeIdLabel;
    private Label       roleLabel;
    private Circle      signalDot;
    private ProgressBar batteryBar;

    // ─── Controls ─────────────────────────────────────────────────────────────
    private Button    sendBtn;
    private Button    toggleSignalBtn;
    private TextField batteryField;
    private TextField targetField;
    private CheckBox  targetedCheckbox;
    private Label     bufferStatusLabel;

    // ══════════════════════════════════════════════════════════════════════════
    // APPLICATION START — show login screen first
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        showLoginScreen();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOGIN SCREEN
    // ══════════════════════════════════════════════════════════════════════════
    private void showLoginScreen() {
        primaryStage.setTitle("ResilientNet — Login");

        // Background
        StackPane bg = new StackPane();
        bg.setStyle("-fx-background-color: #04080F;");
        Pane gridPane = buildHexGrid();
        bg.getChildren().add(gridPane);

        // Card
        VBox card = new VBox(0);
        card.setMaxWidth(400);
        card.setAlignment(Pos.CENTER);
        card.setStyle(
            "-fx-background-color: #090F1C;" +
            "-fx-border-color: #00FF8833;" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 4;" +
            "-fx-border-radius: 4;" +
            "-fx-effect: dropshadow(gaussian, #00FF8840, 40, 0.5, 0, 0);"
        );

        // Header
        VBox titleGroup = new VBox(4);
        titleGroup.setAlignment(Pos.CENTER);
        titleGroup.setPadding(new Insets(28, 32, 20, 32));
        titleGroup.setStyle("-fx-background-color: #050A14; -fx-background-radius: 4 4 0 0;");

        Label logo = new Label("\u2B21  RESILIENTNET");
        logo.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #00FF88;");
        DropShadow glow = new DropShadow(12, Color.web("#00FF88"));
        logo.setEffect(glow);

        Label tagline = new Label("MESH COMMUNICATION NODE");
        tagline.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10px; -fx-text-fill: #336644;");

        titleGroup.getChildren().addAll(logo, tagline);

        // Divider
        Rectangle divider = new Rectangle(400, 1);
        divider.setFill(Color.web("#00FF8822"));

        // Body
        VBox body = new VBox(16);
        body.setPadding(new Insets(28, 32, 32, 32));

        Label roleSelLabel = new Label("SELECT ROLE");
        roleSelLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10px; -fx-text-fill: #446655;");

        ToggleGroup roleGroup = new ToggleGroup();
        ToggleButton userBtn  = makeRoleToggle("\u25B8  JOIN AS USER",   roleGroup, true);
        ToggleButton adminBtn = makeRoleToggle("\u2B21  LOGIN AS ADMIN", roleGroup, false);

        HBox roleRow = new HBox(8, userBtn, adminBtn);
        roleRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(userBtn,  Priority.ALWAYS);
        HBox.setHgrow(adminBtn, Priority.ALWAYS);

        // Password section
        Label passLabel = new Label("ADMIN PASSWORD");
        passLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10px; -fx-text-fill: #446655;");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Enter admin password...");
        passField.setStyle(
            "-fx-background-color: #060C18;" +
            "-fx-text-fill: #00FF88;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 13px;" +
            "-fx-border-color: #00FF8844;" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-background-radius: 2;" +
            "-fx-padding: 10 12 10 12;" +
            "-fx-prompt-text-fill: #1A3322;"
        );

        Label errorLabel = new Label("");
        errorLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-text-fill: #FF4444;");
        errorLabel.setMinHeight(16);

        VBox passSection = new VBox(8, passLabel, passField, errorLabel);
        passSection.setVisible(false);
        passSection.setManaged(false);

        adminBtn.selectedProperty().addListener((obs, o, selected) -> {
            passSection.setVisible(selected);
            passSection.setManaged(selected);
            errorLabel.setText("");
            if (selected) Platform.runLater(passField::requestFocus);
        });

        // Enter button
        Button enterBtn = new Button("\u25B6   CONNECT TO MESH");
        enterBtn.setMaxWidth(Double.MAX_VALUE);
        String enterStyleNormal =
            "-fx-background-color: #002211;" +
            "-fx-text-fill: #00FF88;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-border-color: #00FF8866;" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 2;" +
            "-fx-border-radius: 2;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 12 0 12 0;";
        String enterStyleHover =
            "-fx-background-color: #003318;" +
            "-fx-text-fill: #00FF88;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;" +
            "-fx-border-color: #00FF88CC;" +
            "-fx-border-width: 1;" +
            "-fx-background-radius: 2;" +
            "-fx-border-radius: 2;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 12 0 12 0;" +
            "-fx-effect: dropshadow(gaussian, #00FF8866, 12, 0.5, 0, 0);";
        enterBtn.setStyle(enterStyleNormal);
        enterBtn.setOnMouseEntered(e -> enterBtn.setStyle(enterStyleHover));
        enterBtn.setOnMouseExited(e  -> enterBtn.setStyle(enterStyleNormal));

        Label footerNote = new Label("v1.0  \u00B7  UDP mesh  \u00B7  port 9876");
        footerNote.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10px; -fx-text-fill: #1A2F22;");

        body.getChildren().addAll(roleSelLabel, roleRow, passSection, enterBtn, footerNote);
        card.getChildren().addAll(titleGroup, divider, body);
        StackPane.setAlignment(card, Pos.CENTER);
        bg.getChildren().add(card);

        // Login action
        Runnable doLogin = () -> {
            boolean wantsAdmin = adminBtn.isSelected();
            if (wantsAdmin) {
                if (!ADMIN_PASSWORD.equals(passField.getText())) {
                    errorLabel.setText("\u2717  Incorrect password. Access denied.");
                    passField.clear();
                    shakeNode(card);
                    return;
                }
            }
            launchMainApp(wantsAdmin);
        };
        enterBtn.setOnAction(e -> doLogin.run());
        passField.setOnAction(e -> doLogin.run());

        // Fade in card
        card.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(600), card);
        ft.setFromValue(0); ft.setToValue(1);

        Scene loginScene = new Scene(bg, 700, 480);
        primaryStage.setScene(loginScene);
        primaryStage.setResizable(false);
        primaryStage.show();
        ft.play();
    }

    private ToggleButton makeRoleToggle(String text, ToggleGroup group, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setSelected(selected);
        btn.setMaxWidth(Double.MAX_VALUE);
        String unsel =
            "-fx-font-family: 'Courier New'; -fx-font-size: 12px;" +
            "-fx-background-radius: 2; -fx-border-radius: 2; -fx-cursor: hand; -fx-padding: 9 0 9 0;" +
            "-fx-background-color: #060C18; -fx-text-fill: #446655; -fx-border-color: #1A2F1A; -fx-border-width: 1;";
        String sel =
            "-fx-font-family: 'Courier New'; -fx-font-size: 12px; -fx-font-weight: bold;" +
            "-fx-background-radius: 2; -fx-border-radius: 2; -fx-cursor: hand; -fx-padding: 9 0 9 0;" +
            "-fx-background-color: #002211; -fx-text-fill: #00FF88; -fx-border-color: #00FF8866; -fx-border-width: 1;";
        btn.setStyle(selected ? sel : unsel);
        btn.selectedProperty().addListener((obs, o, n) -> btn.setStyle(n ? sel : unsel));
        return btn;
    }

    private void shakeNode(javafx.scene.Node n) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(60), n);
        tt.setFromX(0); tt.setByX(10);
        tt.setCycleCount(6); tt.setAutoReverse(true);
        tt.setOnFinished(e -> n.setTranslateX(0));
        tt.play();
    }

    private Pane buildHexGrid() {
        Pane pane = new Pane();
        pane.setPrefSize(700, 480);
        pane.setOpacity(0.18);
        double r = 28;
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 13; col++) {
                double cx = col * r * 1.75 + (row % 2 == 0 ? 0 : r * 0.875);
                double cy = row * r * 1.5;
                double[] xs = new double[6], ys = new double[6];
                for (int i = 0; i < 6; i++) {
                    xs[i] = cx + (r - 2) * Math.cos(Math.toRadians(60 * i - 30));
                    ys[i] = cy + (r - 2) * Math.sin(Math.toRadians(60 * i - 30));
                }
                for (int i = 0; i < 6; i++) {
                    Line l = new Line(xs[i], ys[i], xs[(i+1)%6], ys[(i+1)%6]);
                    l.setStroke(Color.web("#00FF88"));
                    l.setStrokeWidth(0.6);
                    pane.getChildren().add(l);
                }
            }
        }
        FadeTransition pulse = new FadeTransition(Duration.millis(2800), pane);
        pulse.setFromValue(0.10); pulse.setToValue(0.22);
        pulse.setCycleCount(Animation.INDEFINITE); pulse.setAutoReverse(true);
        pulse.play();
        return pane;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRANSITION: Login → Main App
    // ══════════════════════════════════════════════════════════════════════════
    private void launchMainApp(boolean adminMode) {
        Scene mainScene = buildMainScene(adminMode);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300),
                primaryStage.getScene().getRoot());
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            primaryStage.setScene(mainScene);
            primaryStage.setResizable(true);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(400), mainScene.getRoot());
            fadeIn.setFromValue(0); fadeIn.setToValue(1);
            fadeIn.play();

            try {
                node = new MeshNode(adminMode, this);
                node.startListening();
                String id = node.getNodeId();
                primaryStage.setTitle("ResilientNet  \u25B8  " + id + (adminMode ? "  [ADMIN]" : ""));
                nodeIdLabel.setText("ID: " + id);
                roleLabel.setText("\u25CF " + node.getRole());
                if (adminMode) {
                    roleLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 12px; -fx-text-fill: #FFB800; -fx-font-weight: bold;");
                    targetedCheckbox.setDisable(false);
                } else {
                    targetedCheckbox.setDisable(true);
                }
            } catch (Exception ex) {
                chatArea.appendText("[ERROR] Failed to start networking: " + ex.getMessage() + "\n");
            }
        });
        fadeOut.play();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN CHAT SCENE
    // ══════════════════════════════════════════════════════════════════════════
    private Scene buildMainScene(boolean adminMode) {

        // TOP STATUS BAR
        Label appTitle = new Label("\u2B21  RESILIENTNET");
        appTitle.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #00FF88;");

        nodeIdLabel = new Label("ID: ...");
        nodeIdLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 12px; -fx-text-fill: #AAFFCC;");

        roleLabel = new Label("\u25CF USER");
        roleLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 12px; -fx-text-fill: #88CCFF; -fx-font-weight: bold;");

        signalDot = new Circle(6, Color.web("#00FF88"));
        signalLabel = new Label("SIGNAL: ON");
        signalLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-text-fill: #00FF88;");
        HBox signalBox = new HBox(6, signalDot, signalLabel);
        signalBox.setAlignment(Pos.CENTER_LEFT);

        batteryBar = new ProgressBar(1.0);
        batteryBar.setPrefWidth(80);
        batteryBar.setStyle("-fx-accent: #00FF88;");
        batteryLabel = new Label("BAT: 100%");
        batteryLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-text-fill: #FFDD55;");
        HBox batteryBox = new HBox(6, batteryBar, batteryLabel);
        batteryBox.setAlignment(Pos.CENTER_LEFT);

        bufferStatusLabel = new Label("");
        bufferStatusLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-text-fill: #FF8833;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(16, appTitle, nodeIdLabel, roleLabel, spacer, bufferStatusLabel, signalBox, batteryBox);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10, 16, 10, 16));
        topBar.setStyle(
            "-fx-background-color: #0A0F1E;" +
            "-fx-border-color: #00FF8844;" +
            "-fx-border-width: 0 0 1 0;"
        );

        // LEFT PEER PANEL
        Label peerHeader = new Label("\u25C8 ACTIVE NODES");
        peerHeader.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #00FF88;");

        peerList = new ListView<>();
        peerList.setStyle(
            "-fx-background-color: #080D18;" +
            "-fx-border-color: #1A2F1A;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 12px;" +
            "-fx-control-inner-background: #080D18;"
        );
        peerList.setPrefWidth(170);
        VBox.setVgrow(peerList, Priority.ALWAYS);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #1A3322;");

        Button statusBtn = new Button("\u229E  NODE STATUS");
        statusBtn.setMaxWidth(Double.MAX_VALUE);
        statusBtn.setStyle(
            "-fx-background-color: #0F1E10;" +
            "-fx-text-fill: #88FFAA;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 11px;" +
            "-fx-border-color: #2A5A2A;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;"
        );
        statusBtn.setOnAction(e -> handleStatusCommand());

        VBox leftPane = new VBox(8, peerHeader, peerList, sep, statusBtn);
        leftPane.setPadding(new Insets(12));
        leftPane.setStyle("-fx-background-color: #090E1A; -fx-border-color: #1A2F1A; -fx-border-width: 0 1 0 0;");
        leftPane.setPrefWidth(185);

        // CENTER CHAT
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle(
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 13px;" +
            "-fx-text-fill: #C8FFD8;" +
            "-fx-background-color: #060B14;" +
            "-fx-control-inner-background: #060B14;" +
            "-fx-border-color: transparent;"
        );
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        // BOTTOM CONTROLS
        targetedCheckbox = new CheckBox("\u2316 TARGETED");
        targetedCheckbox.setStyle("-fx-text-fill: #88CCFF; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        targetedCheckbox.setTooltip(new Tooltip("Admin only: send to specific Node IDs"));
        targetedCheckbox.setDisable(!adminMode);

        targetField = new TextField();
        targetField.setPromptText("node-id1, node-id2 ...");
        targetField.setStyle(
            "-fx-background-color: #0A1520;" +
            "-fx-text-fill: #88CCFF;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 11px;" +
            "-fx-border-color: #1A3055;" +
            "-fx-border-width: 1;" +
            "-fx-prompt-text-fill: #334466;"
        );
        targetField.setDisable(true);
        HBox.setHgrow(targetField, Priority.ALWAYS);
        targetedCheckbox.selectedProperty().addListener((obs, o, n) -> targetField.setDisable(!n));

        HBox targetRow = new HBox(10, targetedCheckbox, targetField);
        targetRow.setAlignment(Pos.CENTER_LEFT);
        targetRow.setPadding(new Insets(4, 10, 0, 10));

        inputField = new TextField();
        inputField.setPromptText("Broadcast a message to all nodes...");
        inputField.setStyle(
            "-fx-background-color: #0A1520;" +
            "-fx-text-fill: #C8FFD8;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 13px;" +
            "-fx-border-color: #00FF8833;" +
            "-fx-border-width: 1;" +
            "-fx-prompt-text-fill: #2A4A3A;"
        );
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendBtn = new Button("\u25B6  SEND");
        sendBtn.setStyle(
            "-fx-background-color: #004422;" +
            "-fx-text-fill: #00FF88;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-border-color: #00FF8855;" +
            "-fx-border-width: 1;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8 18 8 18;"
        );

        HBox inputRow = new HBox(8, inputField, sendBtn);
        inputRow.setPadding(new Insets(6, 10, 6, 10));
        inputRow.setAlignment(Pos.CENTER);

        toggleSignalBtn = new Button("\u21CB  TOGGLE SIGNAL");
        toggleSignalBtn.setStyle(makeCtrlBtnStyle("#003344", "#0088CC"));
        toggleSignalBtn.setTooltip(new Tooltip("Simulate signal loss / recovery"));

        Label batSetLabel = new Label("\u26A1 BAT %:");
        batSetLabel.setStyle("-fx-text-fill: #FFDD55; -fx-font-family: 'Courier New'; -fx-font-size: 11px;");
        batteryField = new TextField("100");
        batteryField.setPrefWidth(55);
        batteryField.setStyle(
            "-fx-background-color: #1A1500;" +
            "-fx-text-fill: #FFDD55;" +
            "-fx-font-family: 'Courier New';" +
            "-fx-font-size: 11px;" +
            "-fx-border-color: #554400;" +
            "-fx-border-width: 1;"
        );
        Button setBatBtn = new Button("SET");
        setBatBtn.setStyle(makeCtrlBtnStyle("#1A1500", "#FFDD55"));

        Region ctrlSpacer = new Region();
        HBox.setHgrow(ctrlSpacer, Priority.ALWAYS);

        HBox controlRow = new HBox(10, toggleSignalBtn, ctrlSpacer, batSetLabel, batteryField, setBatBtn);
        controlRow.setPadding(new Insets(0, 10, 8, 10));
        controlRow.setAlignment(Pos.CENTER_LEFT);

        VBox bottomPane = new VBox(targetRow, inputRow, controlRow);
        bottomPane.setStyle("-fx-background-color: #080D18; -fx-border-color: #1A2F1A; -fx-border-width: 1 0 0 0;");

        VBox centerPane = new VBox(chatArea);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(leftPane);
        root.setCenter(centerPane);
        root.setBottom(bottomPane);
        root.setStyle("-fx-background-color: #060B14;");

        sendBtn.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());
        toggleSignalBtn.setOnAction(e -> { if (node != null) node.toggleSignal(); });
        setBatBtn.setOnAction(e -> {
            try {
                int val = Integer.parseInt(batteryField.getText().trim());
                if (val < 0 || val > 100) throw new NumberFormatException();
                if (node != null) node.setBatteryLevel(val);
                updateBatteryUI(val);
            } catch (NumberFormatException ex) {
                appendSystem("\u26A0 Invalid battery value. Enter 0-100.");
            }
        });

        primaryStage.setOnCloseRequest(e -> {
            if (node != null) node.shutdown();
            Platform.exit();
            System.exit(0);
        });

        Scene scene = new Scene(root, 900, 580);
        scene.getStylesheets().add("data:text/css," +
            ".scroll-bar{-fx-background-color:#0A0F1E;}" +
            ".scroll-bar .thumb{-fx-background-color:#1A3322;}" +
            ".list-cell{-fx-background-color:#080D18;-fx-text-fill:#AAFFCC;" +
                "-fx-font-family:'Courier New';-fx-font-size:12px;}" +
            ".list-cell:filled:hover{-fx-background-color:#0F1E10;}" +
            ".list-cell:filled:selected{-fx-background-color:#0F2A15;-fx-text-fill:#00FF88;}");
        return scene;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SEND LOGIC
    // ══════════════════════════════════════════════════════════════════════════
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || node == null) return;

        if (targetedCheckbox.isSelected() && !targetedCheckbox.isDisabled()) {
            String rawTargets = targetField.getText().trim();
            if (rawTargets.isEmpty()) { appendSystem("\u26A0 Enter target node IDs (comma-separated)."); return; }
            java.util.Set<String> targetSet = new java.util.HashSet<>(
                    java.util.Arrays.asList(rawTargets.split("[,\\s]+")));
            String result = node.sendTargeted(text, targetSet);
            chatArea.appendText("You \u2192 [" + rawTargets + "]: " + text + "\n");
            if (result != null) appendSystem(result);
        } else {
            String result = node.broadcastMessage(text);
            chatArea.appendText("You: " + text + "\n");
            if (result != null) appendSystem(result);
        }
        updateBatteryUI(node.getBatteryLevel());
        inputField.clear();
    }

    private void handleStatusCommand() {
        if (node == null) return;
        appendSystem(node.getStatusReport());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private void appendSystem(String text) {
        chatArea.appendText("\u2699  " + text + "\n");
    }

    private void updateBatteryUI(int level) {
        batteryLabel.setText("BAT: " + level + "%");
        batteryBar.setProgress(level / 100.0);
        batteryField.setText(String.valueOf(level));
        if (level <= 15) {
            batteryBar.setStyle("-fx-accent: #FF3333;");
            batteryLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-text-fill: #FF3333;");
        } else if (level <= 30) {
            batteryBar.setStyle("-fx-accent: #FF8833;");
            batteryLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-text-fill: #FF8833;");
        } else {
            batteryBar.setStyle("-fx-accent: #00FF88;");
            batteryLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-text-fill: #FFDD55;");
        }
    }

    private String makeCtrlBtnStyle(String bg, String fg) {
        return "-fx-background-color: " + bg + ";" +
               "-fx-text-fill: " + fg + ";" +
               "-fx-font-family: 'Courier New';" +
               "-fx-font-size: 11px;" +
               "-fx-border-color: " + fg + "44;" +
               "-fx-border-width: 1;" +
               "-fx-cursor: hand;" +
               "-fx-padding: 5 12 5 12;";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MeshEventListener
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void onMessageReceived(Message msg) {
        Platform.runLater(() -> {
            String prefix = msg.urgent ? "\uD83C\uDD98 [URGENT] " : "";
            chatArea.appendText(prefix + msg.toString() + "\n");
        });
    }

    @Override
    public void onPeerDiscovered(String peerId) {
        Platform.runLater(() -> {
            if (!peerList.getItems().contains(peerId)) {
                peerList.getItems().add(peerId);
                chatArea.appendText("\uD83D\uDD35 [JOINED] " + peerId + "\n");
            }
        });
    }

    @Override
    public void onPeerLost(String peerId) {
        Platform.runLater(() -> {
            peerList.getItems().remove(peerId);
            chatArea.appendText("\uD83D\uDD34 [DISCONNECTED] " + peerId + "\n");
        });
    }

    @Override
    public void onPeerReturned(String peerId) {
        Platform.runLater(() -> chatArea.appendText("\uD83D\uDFE2 [RE-APPEARED] " + peerId + " is back online!\n"));
    }

    @Override
    public void onSystemMessage(String text) {
        Platform.runLater(() -> chatArea.appendText("\u2699  " + text + "\n"));
    }

    @Override
    public void onSignalToggled(boolean signalOn) {
        Platform.runLater(() -> {
            if (signalOn) {
                signalDot.setFill(Color.web("#00FF88"));
                signalLabel.setText("SIGNAL: ON");
                signalLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-text-fill: #00FF88;");
                toggleSignalBtn.setStyle(makeCtrlBtnStyle("#003344", "#0088CC"));
                chatArea.appendText("\u2699  [SIGNAL RECOVERED] Flushing buffered messages...\n");
            } else {
                signalDot.setFill(Color.web("#FF3333"));
                signalLabel.setText("SIGNAL: OFF");
                signalLabel.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-text-fill: #FF3333;");
                toggleSignalBtn.setStyle(makeCtrlBtnStyle("#330011", "#FF3333"));
                chatArea.appendText("\u2699  [SIGNAL LOST] Messages will be buffered.\n");
            }
        });
    }

    @Override
    public void onBufferUpdate(int bufferedCount) {
        Platform.runLater(() -> bufferStatusLabel.setText(
                bufferedCount > 0 ? "\u23F3 BUFFERED: " + bufferedCount : ""));
    }

    @Override
    public void onDeliveryAck(String fromId) {
        Platform.runLater(() -> chatArea.appendText("\u2713  [ACK] Delivered \u2192 " + fromId + "\n"));
    }

    @Override
    public void onBatteryUpdate(int level) {
        Platform.runLater(() -> updateBatteryUI(level));
    }

    public static void main(String[] args) { launch(args); }
}