package net.azib.ipscan.gui.fx;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import net.azib.ipscan.config.Config;
import net.azib.ipscan.config.CommentsConfig;
import net.azib.ipscan.config.Labels;
import net.azib.ipscan.config.Version;
import net.azib.ipscan.core.ScanningResult;
import net.azib.ipscan.core.ScanningResultList;
import net.azib.ipscan.core.state.ScanningState;
import net.azib.ipscan.core.state.StateMachine;
import net.azib.ipscan.core.state.StateMachine.Transition;
import net.azib.ipscan.core.state.StateTransitionListener;
import net.azib.ipscan.core.net.PingerRegistry;
import net.azib.ipscan.exporters.ExporterRegistry;
import net.azib.ipscan.exporters.ExportProcessor;
import net.azib.ipscan.exporters.TXTExporter;
import net.azib.ipscan.fetchers.Fetcher;
import net.azib.ipscan.fetchers.FetcherRegistry;
import net.azib.ipscan.gui.fx.feeders.FXFeederGUIRegistry;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class FXMainMenu {

    private final StateMachine stateMachine;
    private final FXResultTable resultTable;
    private final FXFeederGUIRegistry feederRegistry;
    private final java.util.function.Supplier<javafx.stage.Stage> stageSupplier;
    private final Set<MenuItem> scanningSensitiveItems = new HashSet<>();
    private final ExporterRegistry exporterRegistry;
    private final FetcherRegistry fetcherRegistry;
    private final ScanningResultList scanningResults;
    private final PingerRegistry pingerRegistry;
    private Runnable triggerRescan;
    private Runnable triggerStartScan;
    private Runnable displayMethodChangedListener;

    public FXMainMenu(
        StateMachine stateMachine,
        FXResultTable resultTable,
        FXFeederGUIRegistry feederRegistry,
        java.util.function.Supplier<javafx.stage.Stage> stageSupplier,
        ExporterRegistry exporterRegistry,
        FetcherRegistry fetcherRegistry,
        ScanningResultList scanningResults,
        PingerRegistry pingerRegistry
    ) {
        this.stateMachine = stateMachine;
        this.resultTable = resultTable;
        this.feederRegistry = feederRegistry;
        this.stageSupplier = stageSupplier;
        this.exporterRegistry = exporterRegistry;
        this.fetcherRegistry = fetcherRegistry;
        this.scanningResults = scanningResults;
        this.pingerRegistry = pingerRegistry;
    }

    public void setTriggerRescan(Runnable triggerRescan) {
        this.triggerRescan = triggerRescan;
    }

    public void setTriggerStartScan(Runnable triggerStartScan) {
        this.triggerStartScan = triggerStartScan;
    }

    public void setDisplayMethodChanged(Runnable displayMethodChangedListener) {
        this.displayMethodChangedListener = displayMethodChangedListener;
    }

    private static String lbl(String key) {
        return net.azib.ipscan.config.Labels.getLabel(key).replace("&", "");
    }

    private static void acc(MenuItem item, KeyCombination combo) {
        item.setAccelerator(combo);
    }

    public MenuBar createMenuBar() {
        var menuBar = new MenuBar();

        menuBar.getMenus().addAll(
            createScanMenu(),
            createGotoMenu(),
            createCommandsMenu(),
            createFavoritesMenu(),
            createToolsMenu(),
            createHelpMenu()
        );

        stateMachine.addTransitionListener(new MenuEnablerDisabler());
        return menuBar;
    }

    private Menu createScanMenu() {
        var menu = new Menu(lbl("menu.scan"));

        var startItem = new MenuItem(lbl("button.start"));
        startItem.setOnAction(e -> {
            if (triggerStartScan != null) {
                triggerStartScan.run();
            } else if (stateMachine.inState(ScanningState.IDLE)) {
                stateMachine.transitionToNext();
            }
        });
        scanningSensitiveItems.add(startItem);
        menu.getItems().add(startItem);

        menu.getItems().add(new SeparatorMenuItem());

        var exportAll = new MenuItem(lbl("menu.scan.exportAll"));
        exportAll.setOnAction(e -> exportResults(false));
        acc(exportAll, new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        menu.getItems().add(exportAll);

        var exportSel = new MenuItem(lbl("menu.scan.exportSelection"));
        exportSel.setOnAction(e -> exportResults(true));
        menu.getItems().add(exportSel);

        menu.getItems().add(new SeparatorMenuItem());

        var loadItem = new MenuItem(lbl("menu.scan.load"));
        loadItem.setOnAction(e -> loadResults());
        acc(loadItem, new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        menu.getItems().add(loadItem);

        menu.getItems().add(new SeparatorMenuItem());

        var quitItem = new MenuItem(lbl("menu.scan.quit"));
        quitItem.setOnAction(e -> {
            var stage = stageSupplier.get();
            if (stage != null) {
                stage.close();
            } else {
                System.exit(0);
            }
        });
        acc(quitItem, new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        menu.getItems().add(quitItem);
        return menu;
    }

    private Menu createGotoMenu() {
        var menu = new Menu(lbl("menu.goto"));

        var nextAlive = new MenuItem(lbl("menu.goto.next.aliveHost"));
        nextAlive.setOnAction(e -> gotoNext(ScanningResult.ResultType.ALIVE));
        acc(nextAlive, new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN));
        scanningSensitiveItems.add(nextAlive);
        menu.getItems().add(nextAlive);

        var nextDead = new MenuItem(lbl("menu.goto.next.deadHost"));
        nextDead.setOnAction(e -> gotoNext(ScanningResult.ResultType.DEAD));
        acc(nextDead, new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN));
        scanningSensitiveItems.add(nextDead);
        menu.getItems().add(nextDead);

        var nextPort = new MenuItem(lbl("menu.goto.next.openPort"));
        nextPort.setOnAction(e -> gotoNext(ScanningResult.ResultType.WITH_PORTS));
        acc(nextPort, new KeyCodeCombination(KeyCode.J, KeyCombination.SHORTCUT_DOWN));
        scanningSensitiveItems.add(nextPort);
        menu.getItems().add(nextPort);

        menu.getItems().add(new SeparatorMenuItem());

        var prevAlive = new MenuItem(lbl("menu.goto.prev.aliveHost"));
        prevAlive.setOnAction(e -> gotoPrev(ScanningResult.ResultType.ALIVE));
        acc(prevAlive, new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        scanningSensitiveItems.add(prevAlive);
        menu.getItems().add(prevAlive);

        var prevDead = new MenuItem(lbl("menu.goto.prev.deadHost"));
        prevDead.setOnAction(e -> gotoPrev(ScanningResult.ResultType.DEAD));
        acc(prevDead, new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        scanningSensitiveItems.add(prevDead);
        menu.getItems().add(prevDead);

        var prevPort = new MenuItem(lbl("menu.goto.prev.openPort"));
        prevPort.setOnAction(e -> gotoPrev(ScanningResult.ResultType.WITH_PORTS));
        acc(prevPort, new KeyCodeCombination(KeyCode.J, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        scanningSensitiveItems.add(prevPort);
        menu.getItems().add(prevPort);

        menu.getItems().add(new SeparatorMenuItem());

        var findItem = new MenuItem(lbl("menu.goto.find"));
        findItem.setOnAction(e -> showFind());
        acc(findItem, new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
        menu.getItems().add(findItem);
        return menu;
    }

    private void gotoNext(ScanningResult.ResultType type) {
        var table = resultTable.getTableView();
        var items = table.getItems();
        var sel = table.getSelectionModel();
        int start = sel.getSelectedIndex();
        for (int i = start + 1; i < items.size(); i++) {
            var r = items.get(i);
            if (type.matches(r.getType())) {
                sel.select(i);
                table.scrollTo(i);
                return;
            }
        }
        // rewind from the beginning
        if (start >= 0 && start < items.size()) {
            sel.clearSelection();
            for (int i = 0; i < items.size(); i++) {
                var r = items.get(i);
                if (type.matches(r.getType())) {
                    sel.select(i);
                    table.scrollTo(i);
                    return;
                }
            }
        }
    }

    private void gotoPrev(ScanningResult.ResultType type) {
        var table = resultTable.getTableView();
        var items = table.getItems();
        var sel = table.getSelectionModel();
        int start = sel.getSelectedIndex();
        for (int i = (start < 0 ? items.size() : start) - 1; i >= 0; i--) {
            var r = items.get(i);
            if (type.matches(r.getType())) {
                sel.select(i);
                table.scrollTo(i);
                return;
            }
        }
        // rewind from the end
        if (start >= 0 && start < items.size()) {
            sel.clearSelection();
            for (int i = items.size() - 1; i >= 0; i--) {
                var r = items.get(i);
                if (type.matches(r.getType())) {
                    sel.select(i);
                    table.scrollTo(i);
                    return;
                }
            }
        }
    }

    private Menu createCommandsMenu() {
        var menu = new Menu(lbl("menu.commands"));

        var detailsItem = new MenuItem(lbl("menu.commands.details"));
        detailsItem.setOnAction(e -> showDetails());
        menu.getItems().add(detailsItem);

        var rescanItem = new MenuItem(lbl("menu.commands.rescan"));
        rescanItem.setOnAction(e -> { if (triggerRescan != null) triggerRescan.run(); });
        acc(rescanItem, new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN));
        scanningSensitiveItems.add(rescanItem);
        menu.getItems().add(rescanItem);

        menu.getItems().add(new SeparatorMenuItem());

        var deleteItem = new MenuItem(lbl("menu.commands.delete"));
        acc(deleteItem, new KeyCodeCombination(KeyCode.DELETE));
        deleteItem.setOnAction(e -> {
            var table = resultTable.getTableView();
            var sel = table.getSelectionModel().getSelectedIndices();
            if (sel.isEmpty()) return;
            var sorted = new java.util.ArrayList<>(sel);
            sorted.sort(java.util.Collections.reverseOrder());
            int[] indices = new int[sorted.size()];
            for (var i = 0; i < sorted.size(); i++) indices[i] = sorted.get(i);
            scanningResults.remove(indices);
            for (var idx : sorted) {
                table.getItems().remove(idx.intValue());
            }
        });
        scanningSensitiveItems.add(deleteItem);
        menu.getItems().add(deleteItem);

        var copyItem = new MenuItem(lbl("menu.commands.copy"));
        acc(copyItem, new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
        copyItem.setOnAction(e -> {
            var r = resultTable.getTableView().getSelectionModel().getSelectedItem();
            if (r == null) return;
            var ipVal = r.getValues().isEmpty() ? "" : r.getValues().get(0);
            var clipboard = Clipboard.getSystemClipboard();
            var content = new ClipboardContent();
            content.putString(ipVal != null ? ipVal.toString() : "");
            clipboard.setContent(content);
        });
        menu.getItems().add(copyItem);

        var copyDetailsItem = new MenuItem(lbl("menu.commands.copyDetails"));
        copyDetailsItem.setOnAction(e -> copyDetails());
        menu.getItems().add(copyDetailsItem);

        menu.getItems().add(new SeparatorMenuItem());

        var editOpeners = new MenuItem(lbl("menu.commands.open.edit"));
        editOpeners.setOnAction(e -> editOpeners());
        menu.getItems().add(editOpeners);

        var selAll = new MenuItem("Select All");
        selAll.setOnAction(e -> resultTable.getTableView().getSelectionModel().selectAll());
        scanningSensitiveItems.add(selAll);
        menu.getItems().add(selAll);
        return menu;
    }

    private Menu createFavoritesMenu() {
        var menu = new Menu(lbl("menu.favorites"));

        var addItem = new MenuItem(lbl("menu.favorites.add"));
        acc(addItem, new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN));
        addItem.setOnAction(e -> addFavorite());
        scanningSensitiveItems.add(addItem);
        menu.getItems().add(addItem);

        var editItem = new MenuItem(lbl("menu.favorites.edit"));
        editItem.setOnAction(e -> editFavorites());
        menu.getItems().add(editItem);

        menu.getItems().add(new SeparatorMenuItem());

        // populate the favorites list each time the menu is shown
        menu.setOnShowing(e -> rebuildFavoritesMenu(menu));
        return menu;
    }

    private void rebuildFavoritesMenu(Menu menu) {
        // keep the first 3 items (Add, Edit, separator), remove the rest
        while (menu.getItems().size() > 3) {
            menu.getItems().remove(menu.getItems().size() - 1);
        }
        var favorites = Config.getConfig().forFavorites();
        for (var name : favorites) {
            var item = new MenuItem(name);
            item.setDisable(!stateMachine.inState(ScanningState.IDLE));
            item.setOnAction(e -> selectFavorite(name));
            menu.getItems().add(item);
        }
    }

    private void selectFavorite(String key) {
        var favorites = Config.getConfig().forFavorites();
        feederRegistry.select(favorites.getFeederId(key));
        feederRegistry.current().unserialize(favorites.getSerializedParts(key));
        var stage = stageSupplier.get();
        if (stage != null) {
            stage.setTitle(key + " - " + Version.NAME);
        }
        // try to start scanning immediately
        if (triggerStartScan != null) {
            triggerStartScan.run();
        } else if (stateMachine.inState(ScanningState.IDLE)) {
            stateMachine.transitionToNext();
        }
    }

    private Menu createToolsMenu() {
        var menu = new Menu(lbl("menu.tools"));

        var prefsItem = new MenuItem(lbl("menu.tools.preferences"));
        prefsItem.setOnAction(e -> {
            var stage = stageSupplier.get();
            if (stage != null) {
                var config = Config.getConfig();
                new FXPreferencesDialog(config, config.forScanner(), config.forGUI(), pingerRegistry).open(stage);
                // open() is modal (showAndWait); once it returns, refresh everything that
                // depends on the display method, which may have been changed in the dialog
                if (displayMethodChangedListener != null) {
                    displayMethodChangedListener.run();
                }
            }
        });
        menu.getItems().add(prefsItem);

        var fetchersItem = new MenuItem(lbl("menu.tools.fetchers"));
        fetchersItem.setOnAction(e -> openFetchersDialog());
        menu.getItems().add(fetchersItem);

        var selectMenu = new Menu(lbl("menu.tools.select"));
        var selAlive = new MenuItem(lbl("menu.tools.select.alive"));
        selAlive.setOnAction(e -> selectByType(ScanningResult.ResultType.ALIVE));
        scanningSensitiveItems.add(selAlive);
        selectMenu.getItems().add(selAlive);

        var selDead = new MenuItem(lbl("menu.tools.select.dead"));
        selDead.setOnAction(e -> selectByType(ScanningResult.ResultType.DEAD));
        scanningSensitiveItems.add(selDead);
        selectMenu.getItems().add(selDead);

        var selPorts = new MenuItem(lbl("menu.tools.select.withPorts"));
        selPorts.setOnAction(e -> selectByType(ScanningResult.ResultType.WITH_PORTS));
        scanningSensitiveItems.add(selPorts);
        selectMenu.getItems().add(selPorts);

        var selNoPorts = new MenuItem(lbl("menu.tools.select.withoutPorts"));
        selNoPorts.setOnAction(e -> selectWithoutPorts());
        scanningSensitiveItems.add(selNoPorts);
        selectMenu.getItems().add(selNoPorts);

        var selInvert = new MenuItem(lbl("menu.tools.select.invert"));
        acc(selInvert, new KeyCodeCombination(KeyCode.I, KeyCombination.SHORTCUT_DOWN));
        selInvert.setOnAction(e -> {
            var table = resultTable.getTableView();
            var sel = table.getSelectionModel();
            var bits = new java.util.BitSet();
            for (var idx : sel.getSelectedIndices()) bits.set(idx);
            sel.clearSelection();
            for (int i = 0; i < table.getItems().size(); i++) {
                if (!bits.get(i)) sel.select(i);
            }
        });
        scanningSensitiveItems.add(selInvert);
        selectMenu.getItems().add(selInvert);

        menu.getItems().add(selectMenu);

        var statsItem = new MenuItem(lbl("menu.tools.scanStatistics"));
        statsItem.setOnAction(e -> showStatistics());
        menu.getItems().add(statsItem);

        return menu;
    }

    private void selectByType(ScanningResult.ResultType type) {
        var table = resultTable.getTableView();
        var items = table.getItems();
        table.getSelectionModel().clearSelection();
        for (int i = 0; i < items.size(); i++) {
            var t = items.get(i).getType();
            // Select Alive selects everything from ALIVE upwards (matches SWT SelectAlive)
            boolean desired;
            if (type == ScanningResult.ResultType.ALIVE) {
                desired = t.ordinal() >= ScanningResult.ResultType.ALIVE.ordinal();
            } else {
                desired = t == type;
            }
            if (desired) {
                table.getSelectionModel().select(i);
            }
        }
    }

    private void selectWithoutPorts() {
        var table = resultTable.getTableView();
        var items = table.getItems();
        table.getSelectionModel().clearSelection();
        for (int i = 0; i < items.size(); i++) {
            // matches SWT SelectWithoutPorts: only plain ALIVE hosts without open ports
            if (items.get(i).getType() == ScanningResult.ResultType.ALIVE) {
                table.getSelectionModel().select(i);
            }
        }
    }

    private Menu createHelpMenu() {
        var menu = new Menu(lbl("menu.help"));

        var gettingStarted = new MenuItem(lbl("menu.help.gettingStarted"));
        gettingStarted.setOnAction(e -> openUrl("https://angryip.org/documentation/"));
        acc(gettingStarted, new KeyCodeCombination(KeyCode.F1));
        menu.getItems().add(gettingStarted);

        var website = new MenuItem(lbl("menu.help.website"));
        website.setOnAction(e -> openUrl("https://angryip.org/"));
        menu.getItems().add(website);

        var faq = new MenuItem(lbl("menu.help.faq"));
        faq.setOnAction(e -> openUrl("https://angryip.org/faq/"));
        menu.getItems().add(faq);

        var issues = new MenuItem(lbl("menu.help.issues"));
        issues.setOnAction(e -> openUrl("https://github.com/az8a/ipscan/issues"));
        menu.getItems().add(issues);

        var plugins = new MenuItem(lbl("menu.help.plugins"));
        plugins.setOnAction(e -> openUrl("https://angryip.org/plugins/"));
        menu.getItems().add(plugins);

        var cmdline = new MenuItem(lbl("menu.help.cmdLine"));
        cmdline.setOnAction(e -> showCommandLineHelp());
        menu.getItems().add(cmdline);

        menu.getItems().add(new SeparatorMenuItem());

        var checkVer = new MenuItem(lbl("menu.help.checkVersion"));
        checkVer.setOnAction(e -> checkVersion());
        menu.getItems().add(checkVer);

        menu.getItems().add(new SeparatorMenuItem());

        var aboutItem = new MenuItem(lbl("menu.help.about"));
        aboutItem.setOnAction(e -> showAbout());
        menu.getItems().add(aboutItem);
        return menu;
    }

    private void openUrl(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ignored) {}
    }

    // === Feature implementations ===

    private void exportResults(boolean selectedOnly) {
        var stage = stageSupplier.get();
        var fileChooser = new FileChooser();
        fileChooser.setTitle(lbl(selectedOnly ? "menu.scan.exportSelection" : "menu.scan.exportAll"));
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("TXT (*.txt)", "*.txt"),
            new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"),
            new FileChooser.ExtensionFilter("XML (*.xml)", "*.xml"),
            new FileChooser.ExtensionFilter("IP List (*.lst)", "*.lst"),
            new FileChooser.ExtensionFilter("SQL (*.sql)", "*.sql")
        );

        var file = fileChooser.showSaveDialog(stage);
        if (file == null) return;

        try {
            var exporter = exporterRegistry.createExporter(file.getName());
            var processor = new ExportProcessor(exporter, file, false);
            ExportProcessor.ScanningResultFilter filter = null;
            if (selectedOnly) {
                var sel = new HashSet<>(resultTable.getTableView().getSelectionModel().getSelectedIndices());
                filter = (i, r) -> sel.contains(i);
            }
            processor.process(scanningResults, filter);
        } catch (Exception e) {
            showAlert(lbl("menu.scan.exportAll"), e.getMessage());
        }
    }

    private void loadResults() {
        var stage = stageSupplier.get();
        var fileChooser = new FileChooser();
        fileChooser.setTitle(lbl("menu.scan.load"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT (*.txt)", "*.txt"));

        var file = fileChooser.showOpenDialog(stage);
        if (file == null) return;

        try {
            // load as a new scan: select the range feeder and init scan info
            feederRegistry.select("feeder.range");
            var feeder = feederRegistry.current().createFeeder();
            scanningResults.initNewScan(feeder);

            var txtExporter = new TXTExporter();
            var results = txtExporter.importResults(file.getAbsolutePath(), feederRegistry.current());
            resultTable.removeAll();
            for (var r : results) {
                resultTable.addOrUpdateResultRow(r);
            }
            resultTable.rebuildColumns();
        } catch (Exception e) {
            showAlert(lbl("menu.scan.load"), e.getMessage());
        }
    }

    private void showDetails() {
        var table = resultTable.getTableView();
        var r = table.getSelectionModel().getSelectedItem();
        if (r == null) return;
        var index = table.getSelectionModel().getSelectedIndex();
        var stage = stageSupplier.get();
        if (stage == null) return;
        new FXDetailsDialog(Config.getConfig().forGUI(), new CommentsConfig(Config.getConfig()), scanningResults, resultTable, r, index).open(stage);
    }

    private void copyDetails() {
        var r = resultTable.getTableView().getSelectionModel().getSelectedItem();
        if (r == null) return;

        var fetchers = scanningResults.getFetchers();
        var vals = r.getValues();
        var content = new StringBuilder();
        for (int i = 0; i < fetchers.size() && i < vals.size(); i++) {
            if (i > 0) content.append('\n');
            content.append(fetchers.get(i).getName()).append(": ").append(vals.get(i) != null ? vals.get(i) : "");
        }

        var clipboard = Clipboard.getSystemClipboard();
        var cc = new ClipboardContent();
        cc.putString(content.toString());
        clipboard.setContent(cc);
    }

    private void editOpeners() {
        var stage = stageSupplier.get();
        if (stage == null) return;
        new FXOpenersDialog(stage).show();
    }

    private void addFavorite() {
        var dialog = new TextInputDialog(feederRegistry.current().getInfo());
        dialog.initOwner(stageSupplier.get());
        dialog.setTitle(lbl("title.favorite.add"));
        dialog.setHeaderText(lbl("text.favorite.add"));
        dialog.showAndWait().ifPresent(name -> {
            if (name.isEmpty()) return;
            var favorites = Config.getConfig().forFavorites();
            if (favorites.get(name) != null) {
                showAlert(lbl("menu.favorites.add"), Labels.getLabel("exception.UserErrorException.favorite.alreadyExists"));
                return;
            }
            favorites.add(name, feederRegistry.current());
            favorites.store();
        });
    }

    private void editFavorites() {
        var favorites = Config.getConfig().forFavorites();
        var stage = stageSupplier.get();
        if (stage == null) return;
        new FXFavoritesDialog(stage, favorites).show();
    }

    private void openFetchersDialog() {
        var dialog = new Dialog<Void>();
        dialog.initOwner(stageSupplier.get());
        dialog.setTitle(lbl("menu.tools.fetchers"));
        dialog.setResizable(true);

        var listView = new ListView<CheckBox>();
        var allFetchers = fetcherRegistry.getRegisteredFetchers();
        var selectedIds = new java.util.HashSet<String>();
        for (var f : fetcherRegistry.getSelectedFetchers()) {
            selectedIds.add(f.getId());
        }

        for (var f : allFetchers) {
            var cb = new CheckBox(f.getFullName());
            cb.setSelected(selectedIds.contains(f.getId()));
            cb.setUserData(f.getId());
            listView.getItems().add(cb);
        }

        var selectAllBtn = new Button("Select All");
        selectAllBtn.setOnAction(e -> listView.getItems().forEach(cb -> cb.setSelected(true)));

        var deselectAllBtn = new Button("Deselect All");
        deselectAllBtn.setOnAction(e -> listView.getItems().forEach(cb -> cb.setSelected(false)));

        var btnBar = new HBox(8, selectAllBtn, deselectAllBtn);
        var content = new VBox(8, btnBar, listView);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyCss(dialog.getDialogPane());

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                var ids = listView.getItems().stream()
                    .filter(CheckBox::isSelected)
                    .map(cb -> (String) cb.getUserData())
                    .toArray(String[]::new);
                fetcherRegistry.updateSelectedFetchers(ids);
                scanningResults.syncFetchers(fetcherRegistry.getSelectedFetchers().stream().toList());
                resultTable.rebuildColumns();
            }
            return null;
        });
        dialog.showAndWait();
    }

    private void showStatistics() {
        if (!scanningResults.isInfoAvailable()) {
            showAlert(lbl("menu.tools.scanStatistics"), Labels.getLabel("exception.UserErrorException.commands.noResults"));            return;
        }
        var info = scanningResults.getScanInfo();
        var ln = System.getProperty("line.separator");
        var msg = new StringBuilder();
        msg.append(lbl("text.scan.time.total")).append(timeToText(info.getScanTime())).append(ln);
        msg.append(lbl("text.scan.time.average"));
        if (info.getHostCount() > 0)
            msg.append(timeToText((double) info.getScanTime() / info.getHostCount()));
        else
            msg.append("N/A");
        msg.append(ln).append(ln);
        msg.append(scanningResults.getFeederName()).append(ln);
        msg.append(scanningResults.getFeederInfo()).append(ln).append(ln);
        msg.append(lbl("text.scan.hosts.total")).append(info.getHostCount()).append(ln);
        msg.append(lbl("text.scan.hosts.alive")).append(info.getAliveCount()).append(ln);
        if (info.getWithPortsCount() > 0)
            msg.append(lbl("text.scan.hosts.ports")).append(info.getWithPortsCount()).append(ln);

        var header = lbl(info.isCompletedNormally() ? "text.scan.completed" : "text.scan.incomplete");
        var alert = new Alert(Alert.AlertType.INFORMATION);
        var stage = stageSupplier.get();
        if (stage != null) alert.initOwner(stage);
        alert.setTitle(lbl("menu.tools.scanStatistics"));
        alert.setHeaderText(header);
        alert.setContentText(msg.toString());
        applyCss(alert.getDialogPane());
        alert.show();
    }

    private static String timeToText(double scanTime) {
        var totalSeconds = scanTime / 1000;
        var totalMinutes = totalSeconds / 60;
        var totalHours = totalMinutes / 60;
        var format = new java.text.DecimalFormat("#.##");
        if (totalHours >= 1)
            return format.format(totalHours) + lbl("unit.hour");
        if (totalMinutes >= 1)
            return format.format(totalMinutes) + lbl("unit.minute");
        return format.format(totalSeconds) + lbl("unit.second");
    }

    private void showFind() {
        var dialog = new TextInputDialog();
        dialog.initOwner(stageSupplier.get());
        dialog.setTitle(lbl("menu.goto.find"));
        dialog.setHeaderText(lbl("menu.goto.find"));
        dialog.showAndWait().ifPresent(text -> {
            if (text.isEmpty()) return;
            var table = resultTable.getTableView();
            int start = table.getSelectionModel().getSelectedIndex() + 1;
            if (start <= 0) start = 0;
            var idx = scanningResults.findText(text, start);
            if (idx >= 0) {
                table.getSelectionModel().select(idx);
                table.scrollTo(idx);
            } else {
                showAlert(lbl("menu.goto.find"), lbl("text.find.notFound"));
            }
        });
    }

    private void showCommandLineHelp() {
        var msg = "Angry IP Scanner " + Version.getVersion() + "\n"
            + Version.WEBSITE + "\n\n"
            + "Usage: java -jar ipscan.jar [options]\n\n"
            + "Options:\n"
            + "  -s <start IP>        Start IP address\n"
            + "  -e <end IP>          End IP address\n"
            + "  -p <ports>           Port range\n"
            + "  -o <file>            Export results to file\n"
            + "  -f <format>          Export format (txt, csv, xml, lst, sql)\n"
            + "  -l <file>            Load results from file\n"
            + "  -q                   Quiet mode (no GUI)\n"
            + "  -v                   Display version\n"
            + "  -h                   Display this help";

        var ta = new TextArea(msg);
        ta.setEditable(false);
        ta.setPrefWidth(520);
        ta.setPrefHeight(360);

        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stageSupplier.get());
        alert.setTitle(lbl("menu.help.cmdLine"));
        alert.setHeaderText(null);
        alert.getDialogPane().setContent(ta);
        applyCss(alert.getDialogPane());
        alert.show();
    }

    private void checkVersion() {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stageSupplier.get());
        alert.setTitle(lbl("menu.help.checkVersion"));
        alert.setHeaderText(Version.NAME + " " + Version.getVersion());
        alert.setContentText(lbl("state.retrievingVersion"));
        applyCss(alert.getDialogPane());
        alert.show();

        new Thread(() -> {
            try {
                var url = new java.net.URL(Version.LATEST_VERSION_URL);
                var conn = url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    var latestVersion = reader.readLine();
                    Platform.runLater(() -> {
                        alert.setHeaderText(Version.NAME + " " + Version.getVersion());
                        if (latestVersion != null && !latestVersion.trim().equals(Version.getVersion())) {
                            alert.setContentText(lbl("text.version.old").replace("%LATEST", latestVersion.trim()).replace("%VERSION", Version.getVersion()) + "\n" + Version.DOWNLOAD_URL);
                        } else {
                            alert.setContentText(lbl("text.version.latest"));
                        }
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    alert.setHeaderText(Version.NAME + " " + Version.getVersion());
                    alert.setContentText(lbl("exception.UserErrorException.version.latestFailed"));
                });
            }
        }).start();
    }

    private void showAbout() {
        var msg = lbl("text.about")
            .replace("%NAME", Version.NAME)
            .replace("%VERSION", Version.getVersion())
            .replace("%DATE", Version.getBuildDate())
            .replace("%COPYLEFT", Version.COPYLEFT)
            .replace("%JAVA", System.getProperty("java.version"))
            .replace("%OS", System.getProperty("os.name"));
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(lbl("menu.help.about"));
        alert.setHeaderText(Version.NAME + " " + Version.getVersion());
        alert.setContentText(msg);
        applyCss(alert.getDialogPane());
        alert.show();
    }

    // === Helper methods ===

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            var alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(stageSupplier.get());
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            applyCss(alert.getDialogPane());
            alert.show();
        });
    }

    private void applyCss(DialogPane pane) {
        var cssUrl = getClass().getResource("/css/cyberpunk.css");
        if (cssUrl != null) {
            var scene = pane.getScene();
            if (scene != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
        }
        pane.getStyleClass().add("main-stage");
    }

    class MenuEnablerDisabler implements StateTransitionListener {
        @Override
        public void transitionTo(ScanningState state, Transition transition) {
            if (transition != Transition.START && transition != Transition.COMPLETE) return;
            var enabled = state == ScanningState.IDLE;
            for (var item : scanningSensitiveItems) {
                item.setDisable(!enabled);
            }
        }
    }
}
