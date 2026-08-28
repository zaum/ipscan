package net.azib.ipscan.config;

import javafx.stage.Stage;
import net.azib.ipscan.di.Injector;
import net.azib.ipscan.gui.fx.*;
import net.azib.ipscan.gui.fx.feeders.*;

/**
 * Registers JavaFX components in the DI container.
 */
public class FXComponentRegistry {

    private Stage primaryStage;

    public FXComponentRegistry(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void register(Injector i) {
        i.register(Stage.class, primaryStage);
    }
}
