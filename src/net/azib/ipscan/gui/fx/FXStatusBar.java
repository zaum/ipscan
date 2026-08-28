package net.azib.ipscan.gui.fx;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import net.azib.ipscan.config.GUIConfig;
import net.azib.ipscan.config.Labels;
import net.azib.ipscan.config.ScannerConfig;
import net.azib.ipscan.core.ScanningResult;
import net.azib.ipscan.core.ScanningResult.ResultType;
import net.azib.ipscan.core.state.ScanningState;
import net.azib.ipscan.core.state.StateMachine;

/**
 * JavaFX Status bar for the main window.
 * Replaces the SWT-based StatusBar.java.
 */
public class FXStatusBar {

    private final HBox node;
    private final Label statusLabel;
    private final Label displayLabel;
    private final Label threadsLabel;
    private final ProgressBar progressBar;
    private final GUIConfig guiConfig;
    private final ScannerConfig scannerConfig;
    private final FXResultTable resultTable;
    private final StateMachine stateMachine;

    public FXStatusBar(GUIConfig guiConfig, ScannerConfig scannerConfig, StateMachine stateMachine, FXResultTable resultTable) {
        this.guiConfig = guiConfig;
        this.scannerConfig = scannerConfig;
        this.stateMachine = stateMachine;
        this.resultTable = resultTable;

        node = new HBox(16);
        node.getStyleClass().add("status-bar");

        // Status text
        statusLabel = new Label(Labels.getLabel("state.ready"));
        statusLabel.getStyleClass().add("status-label");
        node.getChildren().add(statusLabel);

        // Display method
        displayLabel = new Label(Labels.getLabel("text.display." + guiConfig.displayMethod));
        displayLabel.getStyleClass().add("status-label");
        // fixed width, so cycling between "All"/"Alive only"/"Open ports" texts does not
        // push the UI elements after this label left/right
        displayLabel.setMinWidth(180);
        displayLabel.setPrefWidth(180);
        displayLabel.setMaxWidth(180);
        displayLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        displayLabel.setOnMouseClicked(e -> cycleDisplayMethod());
        node.getChildren().add(displayLabel);

        // Threads
        threadsLabel = new Label(Labels.getLabel("text.threads") + "0");
        threadsLabel.getStyleClass().add("status-label");
        node.getChildren().add(threadsLabel);

        // Progress bar
        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("status-progress");
        progressBar.setPrefWidth(120);
        node.getChildren().add(progressBar);

        // Spacer
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        node.getChildren().add(spacer);

        // update the status text when the selection changes (matches SWT TableSelection listener)
        resultTable.getTableView().getSelectionModel().getSelectedIndices().addListener(
            (javafx.collections.ListChangeListener<Integer>) change -> {
                if (stateMachine.inState(ScanningState.IDLE)) {
                    var selectionCount = resultTable.getTableView().getSelectionModel().getSelectedIndices().size();
                    if (selectionCount > 1) {
                        statusLabel.setText(selectionCount + Labels.getLabel("text.hostsSelected"));
                    } else {
                        statusLabel.setText(Labels.getLabel("state.ready"));
                    }
                }
            });
    }

    public Node getNode() {
        return node;
    }

    public void setStatusText(String text) {
        if (text == null) {
            text = Labels.getLabel("state.ready");
        }
        statusLabel.setText(text);
    }

    public void setRunningThreads(int runningThreads) {
        var text = Labels.getLabel("text.threads") + runningThreads;
        if (runningThreads == scannerConfig.maxThreads) {
            text += Labels.getLabel("text.threads.max");
        }
        threadsLabel.setText(text);
    }

    public void setProgress(int percentage) {
        progressBar.setProgress(percentage / 100.0);
    }

    public void updateConfigText() {
        displayLabel.setText(Labels.getLabel("text.display." + guiConfig.displayMethod));
    }

    private void cycleDisplayMethod() {
        var methods = GUIConfig.DisplayMethod.values();
        var next = (guiConfig.displayMethod.ordinal() + 1) % methods.length;
        guiConfig.displayMethod = methods[next];
        onDisplayMethodChanged();
    }

    /**
     * Called whenever the display method changes (by cycling the status bar label or
     * from the Preferences dialog), so that every affected piece of UI is refreshed:
     * the status bar text and the results table filtering.
     */
    public void onDisplayMethodChanged() {
        updateConfigText();
        // always refresh the displayed list to match the new display method
        resultTable.filterByDisplayMethod();
    }
}
