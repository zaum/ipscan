package net.azib.ipscan.gui.fx.feeders;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import net.azib.ipscan.feeders.Feeder;
import net.azib.ipscan.feeders.FileFeeder;

import static net.azib.ipscan.config.Labels.getLabel;

/**
 * JavaFX GUI for FileFeeder.
 * Replaces the SWT-based FileFeederGUI.java.
 */
public class FXFileFeederGUI extends FXAbstractFeederGUI {

    private TextField fileNameField;

    public FXFileFeederGUI() {
        feeder = new FileFeeder();
        initialize();
    }

    @Override
    public void initialize() {
        var row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        var label = new Label(getLabel("feeder.file.name") + ":");
        label.getStyleClass().add("controls-label");

        fileNameField = new TextField();
        fileNameField.getStyleClass().add("controls-field");
        fileNameField.setPrefColumnCount(25);

        var browseButton = new Button(getLabel("feeder.file.browse"));
        browseButton.getStyleClass().add("dialog-button");
        browseButton.setOnAction(e -> {
            var chooser = new FileChooser();
            chooser.setTitle(getLabel("feeder.file.browse"));
            var file = chooser.showOpenDialog(null);
            if (file != null) {
                fileNameField.setText(file.getAbsolutePath());
            }
        });

        row.getChildren().addAll(label, fileNameField, browseButton);
        getChildren().add(row);
    }

    @Override
    public Feeder createFeeder() {
        feeder = new FileFeeder(fileNameField.getText());
        return feeder;
    }

    @Override
    public String[] serialize() {
        return new String[] {fileNameField.getText()};
    }

    @Override
    public void unserialize(String[] parts) {
        fileNameField.setText(parts[0]);
    }

    @Override
    public String[] serializePartsLabels() {
        return new String[] {"feeder.file.name"};
    }
}
