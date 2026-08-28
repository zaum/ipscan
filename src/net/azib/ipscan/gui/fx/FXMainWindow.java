package net.azib.ipscan.gui.fx;

import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.azib.ipscan.config.*;
import net.azib.ipscan.core.*;
import net.azib.ipscan.core.ScanningResult.ResultType;
import net.azib.ipscan.core.state.ScanningState;
import net.azib.ipscan.core.state.StateMachine;
import net.azib.ipscan.core.state.StateMachine.Transition;
import net.azib.ipscan.core.state.StateTransitionListener;
import net.azib.ipscan.feeders.Feeder;
import net.azib.ipscan.feeders.RescanFeeder;
import net.azib.ipscan.gui.fx.feeders.FXFeederGUIRegistry;

import java.net.InetAddress;
import java.util.ArrayList;

public class FXMainWindow implements ScanningProgressCallback {
    private final Stage stage;
    private final GUIConfig guiConfig;
    private final BorderPane root;

    private final FXFeederGUIRegistry feederRegistry;
    private final FXResultTable resultTable;
    private final FXStatusBar statusBar;
    private final FXMainMenu mainMenu;
    private final StateMachine stateMachine;

    private Button scanButton;
    private Region activityBar;
    private TranslateTransition scanlineAnim;
    private HBox controlsBar;
    private javafx.scene.control.ComboBox<String> feederCombo;
    private VBox feederPanel;

    private ScannerDispatcherThreadFactory scannerThreadFactory;
    private ScannerDispatcherThread scannerThread;
    volatile Feeder pendingRescanFeeder;

    public FXMainWindow(
        Stage stage, GUIConfig guiConfig,
        FXFeederGUIRegistry feederRegistry,
        FXResultTable resultTable,
        FXStatusBar statusBar,
        FXMainMenu mainMenu,
        StateMachine stateMachine,
        ScannerConfig scannerConfig, Scanner scanner, ScanningResultList scanningResults
    ) {
        this.stage = stage;
        this.guiConfig = guiConfig;
        this.feederRegistry = feederRegistry;
        this.resultTable = resultTable;
        this.statusBar = statusBar;
        this.mainMenu = mainMenu;
        this.stateMachine = stateMachine;

        this.scannerThreadFactory = new ScannerDispatcherThreadFactory(
            scanningResults, scanner, stateMachine, scannerConfig
        );

        this.root = new BorderPane();
    }

    public void init(Stage stage) {
        root.getStyleClass().add("main-stage");

        var menuBar = mainMenu.createMenuBar();
        root.setTop(menuBar);

        var centerBox = new VBox();
        centerBox.setFillWidth(true);

        controlsBar = createControlsBar();
        centerBox.getChildren().add(controlsBar);

        activityBar = createActivityBar();
        centerBox.getChildren().add(activityBar);

        var tableView = resultTable.getNode();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        centerBox.getChildren().add(tableView);

        root.setCenter(centerBox);
        root.setBottom(statusBar.getNode());

        stateMachine.addTransitionListener(new ScanStarter());
        stateMachine.addTransitionListener(new EnablerDisabler());
        stateMachine.addTransitionListener(new ScanlineAnimator());

        stage.setTitle(Version.NAME);
    }

    private HBox createTitleBar() {
        var bar = new HBox();
        bar.getStyleClass().add("title-bar");

        var glyph = new StackPane();
        glyph.getStyleClass().add("title-glyph");
        var dot = new javafx.scene.shape.Circle(4);
        dot.getStyleClass().add("title-glyph-dot");
        glyph.getChildren().add(dot);

        var label = new Label(Version.NAME);
        label.getStyleClass().add("title-label");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var winctl = new HBox();
        winctl.getStyleClass().add("title-winctl");
        var colors = new javafx.scene.shape.Circle[]{
            new javafx.scene.shape.Circle(4.5), new javafx.scene.shape.Circle(4.5), new javafx.scene.shape.Circle(4.5)
        };
        colors[0].getStyleClass().add("dot-amber");
        colors[1].getStyleClass().add("dot-cyan");
        colors[2].getStyleClass().add("dot-magenta");
        winctl.getChildren().addAll(colors);

        bar.getChildren().addAll(glyph, label, spacer, winctl);
        return bar;
    }

    private HBox createControlsBar() {
        var bar = new HBox(10);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bar.getStyleClass().add("controls-bar");

        scanButton = new Button();
        scanButton.getStyleClass().add("scan-button");
        scanButton.setOnAction(e -> handleScanClick());
        updateButtonForState(ScanningState.IDLE);

        feederCombo = feederRegistry.getFeederCombo();
        feederCombo.getStyleClass().add("controls-combo");

        feederPanel = feederRegistry.getContainer();

        bar.getChildren().addAll(scanButton, feederCombo, feederPanel);

        return bar;
    }

    private void handleScanClick() {
        if (stateMachine.inState(ScanningState.IDLE)) {
            if (!preScanChecks()) return;
            resultTable.removeAll();
            stateMachine.transitionToNext();
        } else if (stateMachine.inState(ScanningState.SCANNING)) {
            stateMachine.stop();
        } else {
            stateMachine.transitionToNext();
        }
    }

    private boolean preScanChecks() {
        if (guiConfig.askScanConfirmation && resultTable.getScanningResults().getItemCount() > 0) {
            var box = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
            box.initOwner(stage);
            box.setTitle(Labels.getLabel("text.scan.new"));
            box.setHeaderText(null);
            box.setContentText(Labels.getLabel("text.scan.confirmation"));
            return box.showAndWait().filter(r -> r == javafx.scene.control.ButtonType.OK).isPresent();
        }
        return true;
    }

    private void updateButtonForState(ScanningState state) {
        Platform.runLater(() -> {
            scanButton.setStyle("");
            scanButton.setDisable(false);
            if (state == ScanningState.IDLE) {
                scanButton.setText("\u25B6 " + Labels.getLabel("button.start"));
            } else if (state == ScanningState.SCANNING) {
                scanButton.setText("\u25A0 " + Labels.getLabel("button.stop"));
                scanButton.setStyle("-fx-text-fill: #FF4757; -fx-border-color: #FF4757; -fx-effect: dropshadow(gaussian, #FF4757, 10, 0.35, 0, 0); -fx-background-color: linear-gradient(from 0% 0% to 0% 100%, rgba(255,71,87,0.16), rgba(255,71,87,0.04));");
            } else if (state == ScanningState.STOPPING || state == ScanningState.KILLING) {
                scanButton.setText("\u25A0 " + Labels.getLabel("button.stop"));
                scanButton.setDisable(true);
            }
        });
    }

    private Region createActivityBar() {
        var bar = new Region();
        bar.getStyleClass().add("activity-bar");
        bar.setVisible(false);
        bar.setPrefHeight(2);

        scanlineAnim = new TranslateTransition(Duration.seconds(1.5), bar);
        scanlineAnim.setFromY(0);
        scanlineAnim.setToY(600);
        scanlineAnim.setCycleCount(TranslateTransition.INDEFINITE);
        scanlineAnim.setAutoReverse(false);
        scanlineAnim.setInterpolator(javafx.animation.Interpolator.LINEAR);
        return bar;
    }

    public BorderPane getRoot() {
        return root;
    }

    public void afterShow() {
        feederRegistry.select(guiConfig.activeFeeder);
        resultTable.rebuildColumns();
    }

    public void dispose() {
        guiConfig.mainWindowSize = new int[]{(int) stage.getWidth(), (int) stage.getHeight()};
        guiConfig.mainWindowPosition = new int[]{(int) stage.getX(), (int) stage.getY()};
        guiConfig.isMainWindowMaximized = stage.isMaximized();
        feederRegistry.saveCurrentFeederData();
    }

    public void startScan() {
        if (stateMachine.inState(ScanningState.IDLE)) {
            if (!preScanChecks()) return;
            resultTable.removeAll();
            stateMachine.transitionToNext();
        }
    }

    Runnable createStartScanTrigger() {
        return this::startScan;
    }

    Runnable createRescanTrigger() {
        return () -> {
            var table = resultTable.getTableView();
            var sel = table.getSelectionModel().getSelectedItems();
            if (sel.isEmpty()) return;
            var ips = new ArrayList<String>();
            for (var r : sel) {
                ips.add(r.getAddress().getHostAddress());
            }
            var originalFeeder = feederRegistry.current().createFeeder();
            pendingRescanFeeder = new RescanFeeder(originalFeeder, ips.toArray(new String[0]));
            stateMachine.rescan();
        };
    }

    @Override
    public void updateProgress(InetAddress currentAddress, int runningThreads, int percentageComplete) {
        Platform.runLater(() -> {
            if (currentAddress != null) {
                statusBar.setStatusText(Labels.getLabel("state.scanning") + " " + currentAddress.getHostAddress());
            }
            statusBar.setRunningThreads(runningThreads);
            statusBar.setProgress(percentageComplete);
        });
    }

    class ScanStarter implements StateTransitionListener {
        @Override
        public void transitionTo(ScanningState state, Transition transition) {
            updateButtonForState(state);

            if (state == ScanningState.STARTING && transition != Transition.CONTINUE) {
                Platform.runLater(() -> resultTable.removeAll());

                try {
                    var feeder = feederRegistry.createFeeder();
                    scannerThread = scannerThreadFactory.createScannerThread(
                        feeder,
                        FXMainWindow.this,
                        createResultsCallback()
                    );
                    stateMachine.startScanning();
                } catch (RuntimeException e) {
                    stateMachine.reset();
                    throw e;
                }
            } else if (state == ScanningState.RESTARTING) {
                var feeder = pendingRescanFeeder;
                pendingRescanFeeder = null;
                if (feeder == null) return;
                try {
                    scannerThread = scannerThreadFactory.createScannerThread(
                        feeder,
                        FXMainWindow.this,
                        createResultsCallback()
                    );
                    stateMachine.startScanning();
                } catch (RuntimeException e) {
                    stateMachine.reset();
                    throw e;
                }
            } else if (state == ScanningState.SCANNING) {
                if (scannerThread != null) {
                    scannerThread.start();
                }
            }
        }

        private ScanningResultCallback createResultsCallback() {
            return new ScanningResultCallback() {
                @Override
                public void prepareForResults(ScanningResult result) {
                    if (guiConfig.displayMethod == GUIConfig.DisplayMethod.ALL ||
                        guiConfig.displayMethod == null ||
                        stateMachine.inState(ScanningState.RESTARTING)) {
                        resultTable.addOrUpdateResultRow(result);
                    }
                }

                @Override
                public void consumeResults(ScanningResult result) {
                    if (stateMachine.inState(ScanningState.RESTARTING)) {
                        // rescan must follow the same strategy of displaying all hosts,
                        // because the results are already in the list
                        resultTable.addOrUpdateResultRow(result);
                    } else if (guiConfig.displayMethod == GUIConfig.DisplayMethod.ALIVE) {
                        if (result.getType().ordinal() >= ResultType.ALIVE.ordinal()) {
                            resultTable.addOrUpdateResultRow(result);
                        }
                    } else if (guiConfig.displayMethod == GUIConfig.DisplayMethod.PORTS) {
                        if (result.getType() == ResultType.WITH_PORTS) {
                            resultTable.addOrUpdateResultRow(result);
                        }
                    } else {
                        resultTable.addOrUpdateResultRow(result);
                    }
                }
            };
        }
    }

    class EnablerDisabler implements StateTransitionListener {
        @Override
        public void transitionTo(ScanningState state, Transition transition) {
            if (transition != Transition.START && transition != Transition.COMPLETE && transition != Transition.STOP) return;
            var enabled = state == ScanningState.IDLE;
            Platform.runLater(() -> {
                // keep the Scan/Stop button enabled so that "Stop" remains clickable during scanning
                feederCombo.setDisable(!enabled);
                feederPanel.setDisable(!enabled);
            });
        }
    }

    class ScanlineAnimator implements StateTransitionListener {
        @Override
        public void transitionTo(ScanningState state, Transition transition) {
            if (transition == Transition.START && state == ScanningState.SCANNING) {
                Platform.runLater(() -> {
                    activityBar.setVisible(true);
                    scanlineAnim.setToY(root.getHeight());
                    scanlineAnim.playFromStart();
                });
            } else if (transition == Transition.COMPLETE && state == ScanningState.IDLE) {
                Platform.runLater(() -> {
                    scanlineAnim.stop();
                    activityBar.setVisible(false);
                });
            }
        }
    }
}
