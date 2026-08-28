package net.azib.ipscan.gui.fx;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import net.azib.ipscan.config.*;
import net.azib.ipscan.core.net.PingerRegistry;

public class FXPreferencesDialog {

    private final Config globalConfig;
    private final ScannerConfig scannerConfig;
    private final GUIConfig guiConfig;
    private final PingerRegistry pingerRegistry;

    private static String lbl(String key) {
        return Labels.getLabel(key).replace("&", "");
    }

    public FXPreferencesDialog(Config globalConfig, ScannerConfig scannerConfig, GUIConfig guiConfig, PingerRegistry pingerRegistry) {
        this.globalConfig = globalConfig;
        this.scannerConfig = scannerConfig;
        this.guiConfig = guiConfig;
        this.pingerRegistry = pingerRegistry;
    }

    public void open(Stage owner) {
        var dialog = new Dialog<Void>();
        dialog.initOwner(owner);
        dialog.setTitle(lbl("title.preferences"));
        dialog.setResizable(true);

        var tabPane = new TabPane();
        tabPane.getTabs().addAll(createScanningTab(), createPortsTab(), createDisplayTab());

        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        var scene = dialog.getDialogPane().getScene();
        var cssUrl = getClass().getResource("/css/cyberpunk.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        dialog.getDialogPane().getStyleClass().add("main-stage");

        var stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.setMinWidth(480);

        loadPreferences();

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                savePreferences();
                globalConfig.store();
            }
            return null;
        });

        dialog.showAndWait();
    }

    private TextField maxThreadsText, threadDelayText, pingingCountText, pingingTimeoutText;
    private TextField portTimeoutText, minPortTimeoutText;
    private TextArea portsText;
    private CheckBox deadHostsCheckbox, skipBroadcastsCheckbox;
    private CheckBox adaptTimeoutCheckbox, addRequestedPortsCheckbox;
    private CheckBox showInfoCheckbox, askConfirmationCheckbox, autoStartScanCheckbox;
    private CheckBox versionCheckCheckbox, allowReports;
    private ComboBox<String> pingersCombo, languageCombo;
    private TextField notAvailableText, notScannedText;
    private ToggleGroup displayMethodGroup;
    private RadioButton allRadio, aliveRadio, portsRadio;

    private Tab createScanningTab() {
        var tab = new Tab(lbl("title.preferences.scanning"));
        tab.setClosable(false);

        var content = new VBox(12);
        content.setPadding(new Insets(12));

        var threadsGroup = createGroup(lbl("preferences.threads"));
        var threadsGrid = new GridPane();
        threadsGrid.setHgap(8);
        threadsGrid.setVgap(6);

        threadsGrid.add(new Label(lbl("preferences.threads.delay") + ":"), 0, 0);
        threadDelayText = new TextField();
        threadsGrid.add(threadDelayText, 1, 0);

        threadsGrid.add(new Label(lbl("preferences.threads.maxThreads") + ":"), 0, 1);
        maxThreadsText = new TextField();
        threadsGrid.add(maxThreadsText, 1, 1);

        threadsGroup.getChildren().add(threadsGrid);

        var pingGroup = createGroup(lbl("preferences.pinging"));
        var pingGrid = new GridPane();
        pingGrid.setHgap(8);
        pingGrid.setVgap(6);

        pingGrid.add(new Label(lbl("preferences.pinging.type") + ":"), 0, 0);
        pingersCombo = new ComboBox<>();
        for (var pingerId : pingerRegistry.getRegisteredNames()) {
            pingersCombo.getItems().add(lbl(pingerId));
        }
        pingGrid.add(pingersCombo, 1, 0);

        pingGrid.add(new Label(lbl("preferences.pinging.count") + ":"), 0, 1);
        pingingCountText = new TextField();
        pingGrid.add(pingingCountText, 1, 1);

        pingGrid.add(new Label(lbl("preferences.pinging.timeout") + ":"), 0, 2);
        pingingTimeoutText = new TextField();
        pingGrid.add(pingingTimeoutText, 1, 2);

        deadHostsCheckbox = new CheckBox(lbl("preferences.pinging.deadHosts"));
        pingGrid.add(deadHostsCheckbox, 0, 3, 2, 1);

        pingGroup.getChildren().add(pingGrid);

        var skipGroup = createGroup(lbl("preferences.skipping"));
        skipBroadcastsCheckbox = new CheckBox(lbl("preferences.skipping.broadcast"));
        skipGroup.getChildren().add(skipBroadcastsCheckbox);

        content.getChildren().addAll(threadsGroup, pingGroup, skipGroup);
        tab.setContent(new ScrollPane(content));
        return tab;
    }

    private Tab createPortsTab() {
        var tab = new Tab(lbl("title.preferences.ports"));
        tab.setClosable(false);

        var content = new VBox(12);
        content.setPadding(new Insets(12));

        var timingGroup = createGroup(lbl("preferences.ports.timing"));
        var timingGrid = new GridPane();
        timingGrid.setHgap(8);
        timingGrid.setVgap(6);

        timingGrid.add(new Label(lbl("preferences.ports.timing.timeout") + ":"), 0, 0);
        portTimeoutText = new TextField();
        timingGrid.add(portTimeoutText, 1, 0);

        adaptTimeoutCheckbox = new CheckBox(lbl("preferences.ports.timing.adaptTimeout"));
        timingGrid.add(adaptTimeoutCheckbox, 0, 1, 2, 1);

        timingGrid.add(new Label(lbl("preferences.ports.timing.minTimeout") + ":"), 0, 2);
        minPortTimeoutText = new TextField();
        timingGrid.add(minPortTimeoutText, 1, 2);
        minPortTimeoutText.disableProperty().bind(adaptTimeoutCheckbox.selectedProperty().not());

        timingGroup.getChildren().add(timingGrid);

        var portsGroup = createGroup(lbl("preferences.ports.ports"));
        portsText = new TextArea();
        portsText.setPrefRowCount(8);
        addRequestedPortsCheckbox = new CheckBox(lbl("preferences.ports.addRequested"));
        portsGroup.getChildren().addAll(portsText, addRequestedPortsCheckbox);

        content.getChildren().addAll(timingGroup, portsGroup);
        tab.setContent(new ScrollPane(content));
        return tab;
    }

    private Tab createDisplayTab() {
        var tab = new Tab(lbl("title.preferences.display"));
        tab.setClosable(false);

        var content = new VBox(12);
        content.setPadding(new Insets(12));

        var listGroup = createGroup(lbl("preferences.display.list"));
        displayMethodGroup = new ToggleGroup();
        allRadio = new RadioButton(lbl("preferences.display.list." + GUIConfig.DisplayMethod.ALL));
        allRadio.setToggleGroup(displayMethodGroup);
        aliveRadio = new RadioButton(lbl("preferences.display.list." + GUIConfig.DisplayMethod.ALIVE));
        aliveRadio.setToggleGroup(displayMethodGroup);
        portsRadio = new RadioButton(lbl("preferences.display.list." + GUIConfig.DisplayMethod.PORTS));
        portsRadio.setToggleGroup(displayMethodGroup);
        listGroup.getChildren().addAll(allRadio, aliveRadio, portsRadio);

        var confirmGroup = createGroup(lbl("preferences.display.confirmation"));
        askConfirmationCheckbox = new CheckBox(lbl("preferences.display.confirmation.newScan"));
        showInfoCheckbox = new CheckBox(lbl("preferences.display.confirmation.showInfo"));
        confirmGroup.getChildren().addAll(showInfoCheckbox, askConfirmationCheckbox);

        var startupGroup = createGroup(lbl("preferences.startup"));
        autoStartScanCheckbox = new CheckBox(lbl("preferences.startup.autoStartScan"));
        startupGroup.getChildren().add(autoStartScanCheckbox);

        var langGroup = createGroup(lbl("preferences.language"));
        languageCombo = new ComboBox<>();
        for (var language : Labels.LANGUAGES) {
            languageCombo.getItems().add(lbl("language." + language));
        }
        langGroup.getChildren().add(languageCombo);

        versionCheckCheckbox = new CheckBox(lbl("preferences.versionCheck"));
        allowReports = new CheckBox(lbl("preferences.allowReports"));

        var labelsGroup = createGroup(lbl("preferences.display.labels"));
        var labelsGrid = new GridPane();
        labelsGrid.setHgap(8);
        labelsGrid.setVgap(6);
        labelsGrid.add(new Label(lbl("preferences.display.labels.notAvailable") + ":"), 0, 0);
        notAvailableText = new TextField();
        labelsGrid.add(notAvailableText, 1, 0);
        labelsGrid.add(new Label(lbl("preferences.display.labels.notScanned") + ":"), 0, 1);
        notScannedText = new TextField();
        labelsGrid.add(notScannedText, 1, 1);
        labelsGroup.getChildren().add(labelsGrid);

        content.getChildren().addAll(listGroup, labelsGroup, confirmGroup, startupGroup, langGroup, versionCheckCheckbox, allowReports);
        tab.setContent(new ScrollPane(content));
        return tab;
    }

    private VBox createGroup(String title) {
        var group = new VBox(6);
        group.setStyle("-fx-border-color: rgba(47,230,217,0.2); -fx-border-radius: 4; -fx-padding: 8; -fx-border-width: 1;");
        var label = new Label(title);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #2FE6D9;");
        group.getChildren().add(label);
        return group;
    }

    private void loadPreferences() {
        maxThreadsText.setText(String.valueOf(scannerConfig.maxThreads));
        threadDelayText.setText(String.valueOf(scannerConfig.threadDelay));
        pingingCountText.setText(String.valueOf(scannerConfig.pingCount));
        pingingTimeoutText.setText(String.valueOf(scannerConfig.pingTimeout));
        deadHostsCheckbox.setSelected(scannerConfig.scanDeadHosts);
        skipBroadcastsCheckbox.setSelected(scannerConfig.skipBroadcastAddresses);
        portTimeoutText.setText(String.valueOf(scannerConfig.portTimeout));
        adaptTimeoutCheckbox.setSelected(scannerConfig.adaptPortTimeout);
        minPortTimeoutText.setText(String.valueOf(scannerConfig.minPortTimeout));
        portsText.setText(scannerConfig.portString);
        addRequestedPortsCheckbox.setSelected(scannerConfig.useRequestedPorts);
        showInfoCheckbox.setSelected(guiConfig.showScanStats);
        askConfirmationCheckbox.setSelected(guiConfig.askScanConfirmation);
        autoStartScanCheckbox.setSelected(guiConfig.autoStartScan);
        versionCheckCheckbox.setSelected(guiConfig.versionCheckEnabled);
        allowReports.setSelected(globalConfig.allowReports);
        notAvailableText.setText(scannerConfig.notAvailableText);
        notScannedText.setText(scannerConfig.notScannedText);

        switch (guiConfig.displayMethod) {
            case ALL -> allRadio.setSelected(true);
            case ALIVE -> aliveRadio.setSelected(true);
            case PORTS -> portsRadio.setSelected(true);
        }

        var pingerNames = pingersCombo.getItems();
        var selectedPingerLabel = lbl(scannerConfig.selectedPinger);
        for (var i = 0; i < pingerNames.size(); i++) {
            if (pingerNames.get(i).equals(selectedPingerLabel)) {
                pingersCombo.getSelectionModel().select(i);
                break;
            }
        }

        for (var i = 0; i < Labels.LANGUAGES.length; i++) {
            if (globalConfig.language.equals(Labels.LANGUAGES[i])) {
                languageCombo.getSelectionModel().select(i);
                break;
            }
        }
    }

    private void savePreferences() {
        try {
            scannerConfig.maxThreads = Integer.parseInt(maxThreadsText.getText());
        } catch (NumberFormatException ignored) {}
        try {
            scannerConfig.threadDelay = Integer.parseInt(threadDelayText.getText());
        } catch (NumberFormatException ignored) {}
        try {
            scannerConfig.pingCount = Integer.parseInt(pingingCountText.getText());
        } catch (NumberFormatException ignored) {}
        try {
            scannerConfig.pingTimeout = Integer.parseInt(pingingTimeoutText.getText());
        } catch (NumberFormatException ignored) {}
        try {
            scannerConfig.portTimeout = Integer.parseInt(portTimeoutText.getText());
        } catch (NumberFormatException ignored) {}
        try {
            scannerConfig.minPortTimeout = Integer.parseInt(minPortTimeoutText.getText());
        } catch (NumberFormatException ignored) {}

        scannerConfig.scanDeadHosts = deadHostsCheckbox.isSelected();
        scannerConfig.skipBroadcastAddresses = skipBroadcastsCheckbox.isSelected();
        scannerConfig.adaptPortTimeout = adaptTimeoutCheckbox.isSelected();
        scannerConfig.portString = portsText.getText();
        scannerConfig.useRequestedPorts = addRequestedPortsCheckbox.isSelected();
        guiConfig.showScanStats = showInfoCheckbox.isSelected();
        guiConfig.askScanConfirmation = askConfirmationCheckbox.isSelected();
        guiConfig.autoStartScan = autoStartScanCheckbox.isSelected();
        guiConfig.versionCheckEnabled = versionCheckCheckbox.isSelected();
        globalConfig.allowReports = allowReports.isSelected();
        scannerConfig.notAvailableText = notAvailableText.getText();
        scannerConfig.notScannedText = notScannedText.getText();

        if (allRadio.isSelected()) guiConfig.displayMethod = GUIConfig.DisplayMethod.ALL;
        else if (aliveRadio.isSelected()) guiConfig.displayMethod = GUIConfig.DisplayMethod.ALIVE;
        else guiConfig.displayMethod = GUIConfig.DisplayMethod.PORTS;

        var pingerNames = pingersCombo.getItems();
        var pingerIdx = pingersCombo.getSelectionModel().getSelectedIndex();
        if (pingerIdx >= 0 && pingerIdx < pingerNames.size()) {
            for (var pingerId : pingerRegistry.getRegisteredNames()) {
                if (lbl(pingerId).equals(pingerNames.get(pingerIdx))) {
                    scannerConfig.selectedPinger = pingerId;
                    break;
                }
            }
        }

        var sel = languageCombo.getSelectionModel().getSelectedIndex();
        if (sel >= 0 && sel < Labels.LANGUAGES.length) {
            var newLang = Labels.LANGUAGES[sel];
            if (!newLang.equals(globalConfig.language)) {
                globalConfig.language = newLang;
                var alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setContentText(lbl("preferences.language.needsRestart"));
                alert.show();
            }
        }
    }
}
