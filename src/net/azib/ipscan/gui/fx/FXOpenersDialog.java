package net.azib.ipscan.gui.fx;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.azib.ipscan.config.Config;
import net.azib.ipscan.config.Labels;
import net.azib.ipscan.config.OpenersConfig;
import net.azib.ipscan.fetchers.FetcherRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Edit Openers" dialog - a JavaFX port of the SWT EditOpenersDialog.
 * Allows viewing/adding/removing openers with their execution strings.
 */
public class FXOpenersDialog {

    private final Stage owner;
    private final OpenersConfig openersConfig;
    private final ListView<String> listView = new ListView<>();
    private final TextField nameText = new TextField();
    private final TextField stringText = new TextField();
    private final TextField workingDirText = new TextField();
    private final CheckBox inTerminalCheck = new CheckBox(Labels.getLabel("text.openers.inTerminal"));
    private int currentSelectionIndex = -1;
    /** working copy of the user openers; the shared OpenersConfig is only touched when OK is pressed */
    private final Map<String, OpenersConfig.Opener> working = new LinkedHashMap<>();

    public FXOpenersDialog(Stage owner) {
        this.owner = owner;
        this.openersConfig = Config.getConfig().forOpeners();
    }

    public void show() {
        var dialog = new Dialog<Void>();
        dialog.initOwner(owner);
        dialog.setTitle(Labels.getLabel("title.openers.edit"));
        dialog.setResizable(true);

        var messageLabel = new Label(Labels.getLabel("text.openers.edit"));
        messageLabel.setWrapText(true);

        listView.setPrefSize(200, 220);
        for (var name : openersConfig) {
            if (!name.startsWith("opener.")) {
                listView.getItems().add(name);
                working.put(name, openersConfig.getOpener(name));
            }
        }
        listView.getSelectionModel().selectedIndexProperty().addListener((obs, old, idx) -> {
            if (idx.intValue() >= 0) loadFieldsForSelection();
        });

        var upBtn = new Button(Labels.getLabel("button.up"));
        var downBtn = new Button(Labels.getLabel("button.down"));
        var addBtn = new Button(Labels.getLabel("button.add"));
        var deleteBtn = new Button(Labels.getLabel("button.delete"));

        upBtn.setOnAction(e -> move(-1));
        downBtn.setOnAction(e -> move(1));
        addBtn.setOnAction(e -> addOpener());
        deleteBtn.setOnAction(e -> deleteOpener());
        deleteBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

        var sideBar = new VBox(8, upBtn, downBtn, addBtn, deleteBtn);
        sideBar.setAlignment(Pos.TOP_CENTER);

        var listPane = new HBox(8, listView, sideBar);
        HBox.setHgrow(listView, Priority.ALWAYS);

        var hintBtn = new Button(Labels.getLabel("text.openers.hint"));
        hintBtn.setOnAction(e -> showHint());

        var editFields = new VBox(6,
            new Label(Labels.getLabel("text.openers.name")), nameText,
            inTerminalCheck,
            new Label(Labels.getLabel("text.openers.string")), stringText,
            new Label(Labels.getLabel("text.openers.directory")), workingDirText,
            hintBtn);

        var content = new VBox(10, messageLabel, listPane, editFields);
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyCss(dialog.getDialogPane());

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                saveCurrentFields(); // persist the currently edited fields into the working copy
                var ordered = new ArrayList<String>();
                for (var name : openersConfig) {
                    if (name.startsWith("opener.")) ordered.add(name);
                }
                for (var name : listView.getItems()) {
                    ordered.add(name);
                    openersConfig.add(name, working.get(name));
                }
                openersConfig.update(ordered.toArray(new String[0]));
                openersConfig.store();
            }
            return null;
        });

        if (!listView.getItems().isEmpty()) {
            listView.getSelectionModel().select(0);
        }
        dialog.showAndWait();
    }

    private void move(int delta) {
        var idx = listView.getSelectionModel().getSelectedIndex();
        var newIdx = idx + delta;
        if (idx < 0 || newIdx < 0 || newIdx >= listView.getItems().size()) return;
        var item = listView.getItems().remove(idx);
        listView.getItems().add(newIdx, item);
        listView.getSelectionModel().select(newIdx);
    }

    private void addOpener() {
        saveCurrentFields();
        var newName = Labels.getLabel("text.openers.new");
        var idx = listView.getSelectionModel().getSelectedIndex();
        if (idx < 0) idx = listView.getItems().size();
        listView.getItems().add(idx, newName);
        listView.getSelectionModel().select(idx);
        currentSelectionIndex = idx;
        working.put(newName, new OpenersConfig.Opener("${fetcher.ip}", false, null));
        nameText.setText(newName);
        stringText.setText("${fetcher.ip}");
        workingDirText.setText("");
        inTerminalCheck.setSelected(false);
        nameText.requestFocus();
    }

    private void deleteOpener() {
        var idx = listView.getSelectionModel().getSelectedIndex();
        if (idx < 0) return;
        listView.getItems().remove(idx);
        currentSelectionIndex = -1;
        if (idx >= listView.getItems().size()) idx = listView.getItems().size() - 1;
        if (idx >= 0) listView.getSelectionModel().select(idx);
    }

    private void loadFieldsForSelection() {
        saveCurrentFields();
        currentSelectionIndex = listView.getSelectionModel().getSelectedIndex();
        if (currentSelectionIndex < 0) return;
        var name = listView.getItems().get(currentSelectionIndex);
        var opener = working.get(name);
        nameText.setText(name);
        if (opener != null) {
            stringText.setText(opener.execString);
            workingDirText.setText(opener.workingDir != null ? opener.workingDir.toString() : "");
            inTerminalCheck.setSelected(opener.inTerminal);
        }
    }

    private void saveCurrentFields() {
        if (currentSelectionIndex < 0 || currentSelectionIndex >= listView.getItems().size()) return;
        var oldName = listView.getItems().get(currentSelectionIndex);
        var workingDir = workingDirText.getText().length() > 0 ? new File(workingDirText.getText()) : null;
        working.remove(oldName);
        working.put(nameText.getText(), new OpenersConfig.Opener(stringText.getText(), inTerminalCheck.isSelected(), workingDir));
        listView.getItems().set(currentSelectionIndex, nameText.getText());
    }

    private void showHint() {
        var message = new StringBuilder(Labels.getLabel("text.openers.hintText"));
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(Labels.getLabel("title.openers.edit"));
        alert.setHeaderText(null);
        alert.setContentText(message.toString());
        applyCss(alert.getDialogPane());
        alert.show();
    }

    private void applyCss(DialogPane pane) {
        var cssUrl = getClass().getResource("/css/cyberpunk.css");
        if (cssUrl != null) {
            pane.getStylesheets().add(cssUrl.toExternalForm());
        }
        pane.getStyleClass().add("main-stage");
    }
}
