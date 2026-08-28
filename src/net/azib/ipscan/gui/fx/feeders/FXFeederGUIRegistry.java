package net.azib.ipscan.gui.fx.feeders;

import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import net.azib.ipscan.config.GUIConfig;
import net.azib.ipscan.feeders.Feeder;
import net.azib.ipscan.feeders.FeederCreator;
import net.azib.ipscan.feeders.FeederException;
import net.azib.ipscan.feeders.FeederRegistry;

import java.util.Iterator;
import java.util.List;

public class FXFeederGUIRegistry implements FeederRegistry {

    private final List<FXAbstractFeederGUI> feederGUIList;
    private final ComboBox<String> feederCombo;
    private final GUIConfig guiConfig;
    private final VBox container;

    private FXAbstractFeederGUI currentFeederGUI;
    Feeder lastFeeder;
    private boolean selecting;

    public FXFeederGUIRegistry(
        List<FXAbstractFeederGUI> allFeeders,
        ComboBox<String> feederCombo,
        VBox container,
        GUIConfig guiConfig
    ) {
        this.feederGUIList = allFeeders;
        this.feederCombo = feederCombo;
        this.container = container;
        this.guiConfig = guiConfig;

        for (var f : allFeeders) {
            feederCombo.getItems().add(f.getFeederName());
        }

        this.currentFeederGUI = allFeeders.get(0);
        this.lastFeeder = currentFeederGUI.createFeeder();

        feederCombo.getSelectionModel().selectedIndexProperty().addListener((obs, old, idx) -> {
            if (!selecting && idx.intValue() >= 0 && idx.intValue() < feederGUIList.size()) {
                select(idx.intValue());
            }
        });

        select(0);
    }

    public FXAbstractFeederGUI current() {
        return currentFeederGUI;
    }

    public void select(int newActiveFeeder) {
        if (selecting) return;
        selecting = true;
        try {
            var oldIndex = feederGUIList.indexOf(currentFeederGUI);
            if (oldIndex != newActiveFeeder) {
                saveCurrentFeederData();
            }

            container.getChildren().remove(currentFeederGUI);
            currentFeederGUI.setVisible(false);

            currentFeederGUI = feederGUIList.get(newActiveFeeder);
            guiConfig.activeFeeder = newActiveFeeder;

            var saved = guiConfig.getFeederData(currentFeederGUI.getFeederId());
            if (saved != null && !saved.isEmpty()) {
                currentFeederGUI.unserialize(saved.split("@@@"));
            }

            currentFeederGUI.setVisible(true);
            container.getChildren().add(currentFeederGUI);

            feederCombo.getSelectionModel().select(newActiveFeeder);
        } finally {
            selecting = false;
        }
    }

    public void select(String feederId) {
        for (var i = 0; i < feederGUIList.size(); i++) {
            var gui = feederGUIList.get(i);
            if (gui.getFeederId().equals(feederId) || gui.getFeederName().equals(feederId)) {
                select(i);
                return;
            }
        }
        throw new FeederException("Feeder not found: " + feederId);
    }

    public Feeder createFeeder() {
        lastFeeder = current().createFeeder();
        return lastFeeder;
    }

    public void saveCurrentFeederData() {
        var parts = current().serialize();
        if (parts != null && parts.length > 0) {
            guiConfig.setFeederData(current().getFeederId(), String.join("@@@", parts));
        }
        guiConfig.store();
    }

    public ComboBox<String> getFeederCombo() {
        return feederCombo;
    }

    public VBox getContainer() {
        return container;
    }

    @Override
    public Iterator<FeederCreator> iterator() {
        return (Iterator<FeederCreator>) (Iterator<?>) feederGUIList.iterator();
    }
}
