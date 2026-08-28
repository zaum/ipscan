package net.azib.ipscan.gui.fx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.azib.ipscan.config.*;
import net.azib.ipscan.core.ScanningResultList;
import net.azib.ipscan.core.state.StateMachine;
import net.azib.ipscan.di.Injector;
import net.azib.ipscan.core.net.PingerRegistry;
import net.azib.ipscan.exporters.ExporterRegistry;
import net.azib.ipscan.fetchers.FetcherRegistry;
import net.azib.ipscan.gui.fx.feeders.*;

import java.util.List;
import java.util.logging.Logger;

public class FXGUI extends Application {
    static final Logger LOG = LoggerFactory.getLogger();

    private static Injector staticInjector;
    private static boolean staticShowStartupInfo;

    private Stage primaryStage;
    private FXMainWindow mainWindow;

    public static void init(Injector inj, boolean startupInfo) {
        staticInjector = inj;
        staticShowStartupInfo = startupInfo;
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle(Version.NAME);

        new FXComponentRegistry(stage).register(staticInjector);

        var config = Config.getConfig();
        var guiConfig = config.forGUI();
        var scannerConfig = config.forScanner();
        staticInjector.register(GUIConfig.class, guiConfig);
        staticInjector.register(ScannerConfig.class, scannerConfig);
        var fetcherRegistry = staticInjector.require(FetcherRegistry.class);
        var stateMachine = new FXStateMachine();
        staticInjector.register(StateMachine.class, stateMachine);
        var scanningResults = new ScanningResultList(fetcherRegistry, stateMachine);
        staticInjector.register(ScanningResultList.class, scanningResults);

        var feederCombo = new ComboBox<String>();
        var feederContainer = new VBox();
        feederContainer.setFillWidth(true);

        var feeders = List.of(
            new FXRangeFeederGUI(),
            new FXRandomFeederGUI(),
            new FXFileFeederGUI()
        );
        var feederGUIRegistry = new FXFeederGUIRegistry(feeders, feederCombo, feederContainer, guiConfig);
        staticInjector.register(net.azib.ipscan.feeders.FeederRegistry.class, feederGUIRegistry);

        var scanner = new net.azib.ipscan.core.Scanner(fetcherRegistry);

        var resultTable = new FXResultTable(guiConfig, fetcherRegistry, scanningResults, stateMachine, config.forDefaultOpeners(), new CommentsConfig(config));
        var statusBar = new FXStatusBar(guiConfig, scannerConfig, stateMachine, resultTable);
        var exporterRegistry = staticInjector.require(ExporterRegistry.class);
        var pingerRegistry = staticInjector.require(PingerRegistry.class);
        var mainMenu = new FXMainMenu(stateMachine, resultTable, feederGUIRegistry, () -> primaryStage, exporterRegistry, fetcherRegistry, scanningResults, pingerRegistry);

        mainWindow = new FXMainWindow(stage, guiConfig, feederGUIRegistry, resultTable, statusBar, mainMenu, stateMachine, scannerConfig, scanner, scanningResults);
        mainMenu.setTriggerRescan(mainWindow.createRescanTrigger());
        mainMenu.setTriggerStartScan(mainWindow.createStartScanTrigger());
        mainMenu.setDisplayMethodChanged(statusBar::onDisplayMethodChanged);
        resultTable.setTriggerRescan(mainWindow.createRescanTrigger());

        var windowSize = guiConfig.mainWindowSize;

        mainWindow.init(stage);

        loadFonts();

        var scene = new Scene(mainWindow.getRoot(), windowSize[0], windowSize[1]);
        var cssUrl = getClass().getResource("/css/cyberpunk.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        scene.getRoot().getStyleClass().add("main-stage");

        stateMachine.init();

        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(400);

        if (guiConfig.isMainWindowMaximized) {
            stage.setMaximized(true);
        }
        stage.setX(guiConfig.mainWindowPosition[0]);
        stage.setY(guiConfig.mainWindowPosition[1]);

        stage.setOnCloseRequest(e -> {
            guiConfig.mainWindowSize = new int[]{(int) stage.getWidth(), (int) stage.getHeight()};
            guiConfig.mainWindowPosition = new int[]{(int) stage.getX(), (int) stage.getY()};
            guiConfig.isMainWindowMaximized = stage.isMaximized();
            mainWindow.dispose();
            config.store();
        });

        stage.show();
        mainWindow.afterShow();

        if (guiConfig.autoStartScan) {
            javafx.application.Platform.runLater(() -> mainWindow.startScan());
        }

        if (staticShowStartupInfo) {
            // TODO: startup info
        }

        staticInjector = null;
    }

    private static void loadFonts() {
        var fonts = new String[] {
            "/fonts/JetBrainsMono-Regular.ttf",
            "/fonts/JetBrainsMono-Bold.ttf",
            "/fonts/ChakraPetch-SemiBold.ttf"
        };
        for (var path : fonts) {
            var stream = FXGUI.class.getResourceAsStream(path);
            if (stream != null) {
                javafx.scene.text.Font.loadFont(stream, -1);
            }
        }
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public boolean isDisposed() {
        return primaryStage == null || !primaryStage.isShowing();
    }

    @Override
    public void stop() {
        if (mainWindow != null) {
            mainWindow.dispose();
        }
    }
}
