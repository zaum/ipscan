package net.azib.ipscan.gui.fx.feeders;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import net.azib.ipscan.feeders.Feeder;
import net.azib.ipscan.feeders.RandomFeeder;

import static net.azib.ipscan.config.Labels.getLabel;

/**
 * JavaFX GUI for RandomFeeder.
 * Replaces the SWT-based RandomFeederGUI.java.
 */
public class FXRandomFeederGUI extends FXAbstractFeederGUI {

    private TextField ipPrototypeField;
    private ComboBox<String> maskCombo;
    private Spinner<Integer> countSpinner;

    public FXRandomFeederGUI() {
        feeder = new RandomFeeder();
        initialize();
    }

    @Override
    public void initialize() {
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        // Row 0: IP prototype
        var ipLabel = new Label(getLabel("feeder.random.prototype") + ":");
        ipLabel.getStyleClass().add("controls-label");

        ipPrototypeField = new TextField();
        ipPrototypeField.getStyleClass().add("controls-field");
        ipPrototypeField.setPrefColumnCount(15);
        ipPrototypeField.setText("192.168.0.1");

        maskCombo = new ComboBox<>();
        maskCombo.getStyleClass().add("controls-combo");
        maskCombo.getItems().addAll(
            "255...128", "255...0", "255..0.0", "255.0.0.0", "0.0.0.0", "255..0.255", "255.0.0.255"
        );
        maskCombo.setValue("255.0.0.0");

        grid.add(ipLabel, 0, 0);
        grid.add(ipPrototypeField, 1, 0);
        grid.add(maskCombo, 2, 0);

        // Row 1: Count
        var countLabel = new Label(getLabel("feeder.random.count"));
        countLabel.getStyleClass().add("controls-label");

        countSpinner = new Spinner<>(1, 100000000, 100);
        countSpinner.setEditable(true);
        countSpinner.getStyleClass().add("controls-field");

        grid.add(countLabel, 0, 1);
        grid.add(countSpinner, 1, 1);

        getChildren().add(grid);
    }

    @Override
    public Feeder createFeeder() {
        feeder = new RandomFeeder(
            ipPrototypeField.getText().trim(),
            maskCombo.getValue(),
            countSpinner.getValue()
        );
        return feeder;
    }

    @Override
    public String[] serialize() {
        return new String[] {
            ipPrototypeField.getText().trim(),
            maskCombo.getValue(),
            String.valueOf(countSpinner.getValue())
        };
    }

    @Override
    public void unserialize(String[] parts) {
        ipPrototypeField.setText(parts[0]);
        maskCombo.setValue(parts.length > 1 ? parts[1] : "255.0.0.0");
        countSpinner.getValueFactory().setValue(parts.length > 2 ? Integer.parseInt(parts[2]) : 100);
    }

    @Override
    public String[] serializePartsLabels() {
        return new String[] {"feeder.random.prototype", "feeder.random.mask", "feeder.random.count"};
    }
}
