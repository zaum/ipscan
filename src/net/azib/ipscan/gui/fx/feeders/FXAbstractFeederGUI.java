package net.azib.ipscan.gui.fx.feeders;

import javafx.scene.layout.VBox;
import net.azib.ipscan.feeders.Feeder;
import net.azib.ipscan.feeders.FeederCreator;

/**
 * Base class for JavaFX feeder GUI components.
 */
public abstract class FXAbstractFeederGUI extends VBox implements FeederCreator {

    protected Feeder feeder;

    public FXAbstractFeederGUI() {
        setVisible(false);
        getStyleClass().add("feeder-area");
    }

    public abstract void initialize();

    public String getFeederId() {
        return feeder.getId();
    }

    public String getFeederName() {
        return feeder.getName();
    }

    public String getInfo() {
        return getFeederName() + ": " + createFeeder().getInfo();
    }
}
