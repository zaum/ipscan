package net.azib.ipscan.gui.fx;

import javafx.application.Platform;
import net.azib.ipscan.core.state.StateMachine;
import net.azib.ipscan.core.state.StateMachine.Transition;

/**
 * JavaFX-based StateMachine that notifies listeners on the JavaFX Application thread.
 */
public class FXStateMachine extends StateMachine {

    @Override
    protected void notifyAboutTransition(Transition transition) {
        if (Platform.isFxApplicationThread()) {
            super.notifyAboutTransition(transition);
        } else {
            Platform.runLater(() -> super.notifyAboutTransition(transition));
        }
    }
}
