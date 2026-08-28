package net.azib.ipscan.gui.fx;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.skin.TableColumnHeader;
import javafx.scene.control.skin.TableHeaderRow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import net.azib.ipscan.config.*;
import net.azib.ipscan.core.ScanningResult;
import net.azib.ipscan.core.ScanningResult.ResultType;
import net.azib.ipscan.core.ScanningResultList;
import net.azib.ipscan.core.state.ScanningState;
import net.azib.ipscan.core.state.StateMachine;
import net.azib.ipscan.core.state.StateMachine.Transition;
import net.azib.ipscan.core.state.StateTransitionListener;
import net.azib.ipscan.fetchers.*;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JavaFX TableView for scanning results.
 */
public class FXResultTable implements StateTransitionListener {

    private final TableView<ScanningResult> tableView;
    private final ObservableList<ScanningResult> items = FXCollections.observableArrayList();
    private final ScanningResultList scanningResults;
    private final GUIConfig guiConfig;
    private final FetcherRegistry fetcherRegistry;
    private final StateMachine stateMachine;
    private final DefaultOpenerConfig defaultOpenerConfig;
    private final CommentsConfig commentsConfig;

    private List<Fetcher> currentFetchers = new ArrayList<>();
    private Runnable triggerRescan;
    private boolean initialAutoFitPending = true;
    private final java.util.Set<javafx.scene.control.skin.TableColumnHeader> handledHeaders =
        java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    public FXResultTable(
        GUIConfig guiConfig,
        FetcherRegistry fetcherRegistry,
        ScanningResultList scanningResultList,
        StateMachine stateMachine,
        DefaultOpenerConfig defaultOpenerConfig,
        CommentsConfig commentsConfig
    ) {
        this.guiConfig = guiConfig;
        this.fetcherRegistry = fetcherRegistry;
        this.scanningResults = scanningResultList;
        this.stateMachine = stateMachine;
        this.defaultOpenerConfig = defaultOpenerConfig;
        this.commentsConfig = commentsConfig;

        tableView = new TableView<>();
        tableView.setItems(items);
        tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tableView.setTableMenuButtonVisible(false);

        // header nodes only exist once the skin is installed, so (re)attach the auto-fit
        // handler the first time the table is laid out
        tableView.skinProperty().addListener((obs, old, skin) -> {
            if (skin != null) installAutoFitHandler();
        });

        tableView.getColumns().addListener((ListChangeListener) change -> {
            // JavaFX keeps getColumns() in the visual order, so a user drag that reorders
            // columns fires this listener -> persist the new order (matches SWT saveColumnOrder).
            Platform.runLater(this::saveColumnOrder);
        });
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getStyleClass().add("table-view");
        tableView.setPlaceholder(new Label(""));

        tableView.sortPolicyProperty().set(t -> {
            var sortOrder = t.getSortOrder();
            if (sortOrder.isEmpty()) return true;
            var col = (TableColumn<ScanningResult, String>) sortOrder.get(0);
            var fetcher = (Fetcher) col.getUserData();
            if (fetcher != null) {
                var modelIndex = scanningResults.getFetcherIndex(fetcher.getId());
                if (modelIndex >= 0) {
                    boolean ascending = col.getSortType() != TableColumn.SortType.DESCENDING;
                    scanningResults.sort(modelIndex, ascending);
                    // re-align the view with the model so indices used by goto/find/export/delete match
                    syncItems();
                }
            }
            return true;
        });

        var contextMenu = new ContextMenu();

        var detailsItem = new MenuItem(Labels.getLabel("menu.commands.details").replace("&", ""));
        detailsItem.setOnAction(e -> showDetails());
        contextMenu.getItems().add(detailsItem);

        contextMenu.getItems().add(new SeparatorMenuItem());

        var rescanItem = new MenuItem(Labels.getLabel("menu.commands.rescan").replace("&", ""));
        rescanItem.setOnAction(e -> { if (triggerRescan != null) triggerRescan.run(); });
        contextMenu.getItems().add(rescanItem);

        var deleteItem = new MenuItem(Labels.getLabel("menu.commands.delete").replace("&", ""));
        deleteItem.setOnAction(e -> deleteSelected());
        contextMenu.getItems().add(deleteItem);

        contextMenu.getItems().add(new SeparatorMenuItem());

        var copyItem = new MenuItem(Labels.getLabel("menu.commands.copy").replace("&", ""));
        copyItem.setOnAction(e -> copyIP());
        contextMenu.getItems().add(copyItem);

        var copyDetailsItem = new MenuItem(Labels.getLabel("menu.commands.copyDetails").replace("&", ""));
        copyDetailsItem.setOnAction(e -> copyDetails());
        contextMenu.getItems().add(copyDetailsItem);

        contextMenu.getItems().add(new SeparatorMenuItem());

        var openersItem = new Menu(Labels.getLabel("menu.commands.show").replace("&", ""));
        openersItem.setOnAction(e -> rebuildOpenersMenu(openersItem));
        contextMenu.getItems().add(openersItem);

        var setOpenerItem = new Menu(Labels.getLabel("menu.commands.setOpener").replace("&", ""));
        setOpenerItem.setOnAction(e -> rebuildSetOpenerMenu(setOpenerItem));
        contextMenu.getItems().add(setOpenerItem);

        var openByItem = new MenuItem(Labels.getLabel("menu.commands.openBy").replace("&", ""));
        openByItem.setOnAction(e -> openByDefaultOpener());
        contextMenu.getItems().add(openByItem);

        contextMenu.getItems().add(new SeparatorMenuItem());

        var editOpenersItem = new MenuItem(Labels.getLabel("menu.commands.open.edit").replace("&", ""));
        editOpenersItem.setOnAction(e -> editOpeners());
        contextMenu.getItems().add(editOpenersItem);

        contextMenu.setOnShowing(e -> {
            var hasSelection = tableView.getSelectionModel().getSelectedItem() != null;
            detailsItem.setDisable(!hasSelection);
            rescanItem.setDisable(!hasSelection || !stateMachine.inState(ScanningState.IDLE));
            deleteItem.setDisable(!hasSelection || !stateMachine.inState(ScanningState.IDLE));
            copyItem.setDisable(!hasSelection);
            copyDetailsItem.setDisable(!hasSelection);
            openByItem.setDisable(!hasSelection);
            var defName = getDefaultOpenerName();
            openByItem.setText((defName != null ? Labels.getLabel("menu.commands.openBy").replace("&", "") + " " + defName : Labels.getLabel("menu.commands.openBy").replace("&", "")));
        });

        tableView.setContextMenu(contextMenu);

        // Right-click anywhere on the column header area shows the column visibility (toggle)
        // menu, while right-clicks on the rows keep the regular row context menu above.
        // Note: JavaFX cannot show a ContextMenu that has no items (onShowing won't even fire),
        // so the menu must be pre-populated here and only refreshed in onShowing.
        var columnVisibilityMenu = new ContextMenu();
        rebuildColumnVisibilityMenu(columnVisibilityMenu);
        columnVisibilityMenu.setOnShowing(e -> rebuildColumnVisibilityMenu(columnVisibilityMenu));
        tableView.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {
            if (e.isConsumed()) return;
            if (!(e.getTarget() instanceof Node target)) return;
            // walk up from the deepest node under the cursor; if the click happened
            // inside the table header, show the column visibility menu
            for (var n = target; n != null; n = n.getParent()) {
                if (n.getStyleClass().contains("column-header-background")) {
                    columnVisibilityMenu.show(tableView, e.getScreenX(), e.getScreenY());
                    e.consume();
                    return;
                }
            }
        });

        tableView.setRowFactory(tv -> {
            var row = new TableRow<ScanningResult>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    var r = row.getItem();
                    var col = getClickedColumn(e.getX());
                    if (col != null) {
                        var fetcher = (Fetcher) col.getUserData();
                        if (fetcher == null) return; // auto-fit button column
                        // double-click on comment/opener columns is handled by inline editor / opener menu
                        if (CommentFetcher.ID.equals(fetcher.getId()) ||
                            OpenerColumnFetcher.ID.equals(fetcher.getId()) ||
                            OpenerLaunchFetcher.ID.equals(fetcher.getId())) return;
                    }
                    showDetails();
                }
            });
            return row;
        });

        tableView.setOnKeyPressed(e -> {
            if (e.isShortcutDown()) {
                switch (e.getCode()) {
                    case C -> {
                        copyIP();
                        e.consume();
                    }
                    case R -> {
                        if (triggerRescan != null && stateMachine.inState(ScanningState.IDLE)) triggerRescan.run();
                        e.consume();
                    }
                    case DIGIT1, NUMPAD1 -> { launchOpenerByIndex(0); e.consume(); }
                    case DIGIT2, NUMPAD2 -> { launchOpenerByIndex(1); e.consume(); }
                    case DIGIT3, NUMPAD3 -> { launchOpenerByIndex(2); e.consume(); }
                    case DIGIT4, NUMPAD4 -> { launchOpenerByIndex(3); e.consume(); }
                    case DIGIT5, NUMPAD5 -> { launchOpenerByIndex(4); e.consume(); }
                    case DIGIT6, NUMPAD6 -> { launchOpenerByIndex(5); e.consume(); }
                    case DIGIT7, NUMPAD7 -> { launchOpenerByIndex(6); e.consume(); }
                    case DIGIT8, NUMPAD8 -> { launchOpenerByIndex(7); e.consume(); }
                    case DIGIT9, NUMPAD9 -> { launchOpenerByIndex(8); e.consume(); }
                    default -> { }
                }
            } else if (e.getCode() == KeyCode.DELETE) {
                deleteSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                showDetails();
                e.consume();
            }
        });

        stateMachine.addTransitionListener(this);
    }

    private void launchOpenerByIndex(int index) {
        var config = Config.getConfig().forOpeners();
        var it = config.iterator();
        var count = 0;
        while (it.hasNext()) {
            var name = it.next();
            if (name.startsWith("opener.")) continue;
            if (count == index) {
                var opener = config.getOpener(name);
                if (opener == null || opener.execString == null) return;
                var r = tableView.getSelectionModel().getSelectedItem();
                if (r == null) return;
                launchOpener(r, opener, name);
                return;
            }
            count++;
        }
    }

    private TableColumn<ScanningResult, String> getClickedColumn(double x) {
        for (var col : tableView.getColumns()) {
            if (x <= col.getWidth()) return (TableColumn<ScanningResult, String>) col;
            x -= col.getWidth();
        }
        return null;
    }

    private void copyIP() {
        var r = tableView.getSelectionModel().getSelectedItem();
        if (r == null) return;
        var val = r.getValues().isEmpty() ? "" : String.valueOf(r.getValues().get(0));
        var content = new ClipboardContent();
        content.putString(val);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void copyDetails() {
        var r = tableView.getSelectionModel().getSelectedItem();
        if (r == null) return;
        var content = new ClipboardContent();
        content.putString(r.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void deleteSelected() {
        if (!stateMachine.inState(ScanningState.IDLE)) return;
        var sel = tableView.getSelectionModel().getSelectedIndices();
        if (sel.isEmpty()) return;
        var sorted = new ArrayList<>(sel);
        sorted.sort(java.util.Collections.reverseOrder());
        int[] indices = new int[sorted.size()];
        for (var i = 0; i < sorted.size(); i++) indices[i] = sorted.get(i);
        scanningResults.remove(indices);
        for (var idx : sorted) {
            items.remove(idx.intValue());
        }
    }

    private void showDetails() {
        var r = tableView.getSelectionModel().getSelectedItem();
        if (r == null) return;
        var index = tableView.getSelectionModel().getSelectedIndex();
        var stage = tableView.getScene() != null ? (javafx.stage.Stage) tableView.getScene().getWindow() : null;
        new FXDetailsDialog(guiConfig, commentsConfig, scanningResults, this, r, index).open(stage);
    }

    private void rebuildOpenersMenu(Menu menu) {
        menu.getItems().clear();
        var config = Config.getConfig().forOpeners();
        var it = config.iterator();
        while (it.hasNext()) {
            var name = it.next();
            if (name.startsWith("opener.")) continue;
            var opener = config.getOpener(name);
            if (opener == null) continue;
            var item = new MenuItem(name.replace("&", ""));
            item.setOnAction(e -> launchOpenerForSelected(name));
            menu.getItems().add(item);
        }
    }

    private void rebuildSetOpenerMenu(Menu menu) {
        menu.getItems().clear();
        var config = Config.getConfig().forOpeners();
        var defName = getDefaultOpenerName();
        var it = config.iterator();
        while (it.hasNext()) {
            var name = it.next();
            if (name.startsWith("opener.")) continue;
            var item = new CheckMenuItem(name.replace("&", ""));
            item.setSelected(name.equals(defName));
            item.setOnAction(e -> setDefaultOpener(name));
            menu.getItems().add(item);
        }
    }

    private String getDefaultOpenerName() {
        var r = tableView.getSelectionModel().getSelectedItem();
        if (r == null) return null;
        return defaultOpenerConfig.get(r.getAddress().getHostAddress());
    }

    private void setDefaultOpener(String openerName) {
        var sel = tableView.getSelectionModel().getSelectedItems();
        if (sel.isEmpty()) return;
        var openersConfig = Config.getConfig().forOpeners();
        for (var r : sel) {
            defaultOpenerConfig.set(r.getAddress().getHostAddress(), openerName);
            var openerIdx = scanningResults.getFetcherIndex(OpenerColumnFetcher.ID);
            if (openerIdx >= 0) r.setValue(openerIdx, openerName);
        }
        Platform.runLater(() -> refreshResult(tableView.getSelectionModel().getSelectedIndex()));
    }

    private void openByDefaultOpener() {
        var sel = tableView.getSelectionModel().getSelectedItems();
        if (sel.isEmpty()) return;
        var openersConfig = Config.getConfig().forOpeners();
        for (var r : new ArrayList<>(sel)) {
            var name = defaultOpenerConfig.get(r.getAddress().getHostAddress());
            if (name == null) continue;
            var opener = openersConfig.getOpener(name);
            if (opener == null || opener.execString == null) continue;
            launchOpener(r, opener, name);
        }
    }

    private void launchOpenerForSelected(String name) {
        var sel = tableView.getSelectionModel().getSelectedItems();
        if (sel.isEmpty()) return;
        var config = Config.getConfig().forOpeners();
        var opener = config.getOpener(name);
        if (opener == null || opener.execString == null) return;
        for (var r : new ArrayList<>(sel)) {
            launchOpener(r, opener, name);
        }
    }

    private void launchOpener(ScanningResult result, OpenersConfig.Opener opener, String name) {
        var cmd = prepareOpenerString(result, opener.execString);
        if (cmd == null) return;
        try {
            if (cmd.startsWith("http:") || cmd.startsWith("https:") || cmd.startsWith("ftp:") || cmd.startsWith("mailto:") || cmd.startsWith("\\\\")) {
                java.awt.Desktop.getDesktop().browse(new URI(cmd.replace(" ", "%20")));
            } else if (opener.inTerminal) {
                if (net.azib.ipscan.config.Platform.WINDOWS) {
                    new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", cmd).start();
                } else if (net.azib.ipscan.config.Platform.LINUX) {
                    new ProcessBuilder("x-terminal-emulator", "-e", "bash", "-c", cmd + "; read -p 'Press Enter to close...'").start();
                } else {
                    new ProcessBuilder("open", "-a", "Terminal", cmd).start();
                }
            } else {
                if (net.azib.ipscan.config.Platform.LINUX) {
                    new ProcessBuilder("sh", "-c", cmd).directory(opener.workingDir).start();
                } else {
                    new ProcessBuilder(splitCommand(cmd)).directory(opener.workingDir).start();
                }
            }
        } catch (Exception ex) {
            var alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(name);
            alert.setHeaderText(null);
            alert.setContentText(Labels.getLabel("exception.UserErrorException.opener.failed") + cmd);
            alert.show();
        }
    }

    private String prepareOpenerString(ScanningResult result, String openerString) {
        var pattern = java.util.regex.Pattern.compile("\\$\\{(.+?)\\}");
        var matcher = pattern.matcher(openerString);
        var sb = new StringBuilder(64);
        while (matcher.find()) {
            var fetcherId = matcher.group(1);
            var value = getScannedValue(result, fetcherId);
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String getScannedValue(ScanningResult result, String fetcherId) {
        var fetcherIndex = scanningResults.getFetcherIndex(fetcherId);
        if (fetcherIndex >= 0 && fetcherIndex < result.getValues().size()) {
            var value = result.getValues().get(fetcherIndex);
            if (value != null && !value.toString().isEmpty() && !value.toString().startsWith("[n/")) {
                return value.toString();
            }
        }
        return result.getAddress().getHostAddress();
    }

    static String[] splitCommand(String command) {
        var tokenizer = new java.util.StringTokenizer(command);
        var result = new ArrayList<String>();
        while (tokenizer.hasMoreTokens()) {
            var token = tokenizer.nextToken(" \t");
            try {
                if (token.startsWith("\"")) {
                    token = token.substring(1) + tokenizer.nextToken("\"");
                    tokenizer.nextToken(" \t");
                } else if (token.startsWith("'")) {
                    token = token.substring(1) + tokenizer.nextToken("'");
                    tokenizer.nextToken(" \t");
                }
            } catch (java.util.NoSuchElementException e) {
                // end of command reached
            }
            result.add(token);
        }
        return result.toArray(new String[0]);
    }

    private void editOpeners() {
        var stage = tableView.getScene() != null ? (javafx.stage.Stage) tableView.getScene().getWindow() : null;
        if (stage == null) return;
        new FXOpenersDialog(stage).show();
    }

    public void setTriggerRescan(Runnable triggerRescan) {
        this.triggerRescan = triggerRescan;
    }

    public void refreshResult(int index) {
        if (index >= 0 && index < items.size()) {
            var r = items.get(index);
            items.set(index, r);
        }
    }

    public void rebuildColumns() {
        Platform.runLater(() -> {
            tableView.getColumns().clear();
            currentFetchers = new ArrayList<>(fetcherRegistry.getSelectedFetchers());

            var ordered = new ArrayList<>(currentFetchers);
            var savedOrder = guiConfig.getColumnOrder();
            if (savedOrder != null) {
                ordered.clear();
                for (var id : savedOrder) {
                    for (var f : currentFetchers) {
                        if (f.getId().equals(id)) {
                            ordered.add(f);
                            break;
                        }
                    }
                }
                for (var f : currentFetchers) {
                    if (!ordered.contains(f)) ordered.add(f);
                }
            }

            var ipFetcher = currentFetchers.stream()
                .filter(f -> IPFetcher.ID.equals(f.getId()))
                .findFirst().orElse(null);
            if (ipFetcher != null) {
                ordered.remove(ipFetcher);
                ordered.add(0, ipFetcher);
            }

            // fixed leading column whose header button auto-sizes all columns
            tableView.getColumns().add(createAutoFitButtonColumn());

            for (var fetcher : ordered) {
                var col = createColumn(fetcher);
                tableView.getColumns().add(col);
            }

            syncItems();
            installAutoFitHandler();

            // on the first start auto-size all columns to fit their content
            if (initialAutoFitPending) {
                initialAutoFitPending = false;
                Platform.runLater(this::autoFitColumns);
            }
        });
    }

    private TableColumn<ScanningResult, String> createAutoFitButtonColumn() {
        var col = new TableColumn<ScanningResult, String>();
        col.getStyleClass().add("autofit-button");
        col.setMinWidth(46);
        col.setPrefWidth(46);
        col.setMaxWidth(46);
        col.setResizable(false);
        col.setReorderable(false);
        col.setSortable(false);
        col.setUserData(null);

        // use a Label (not a Button), so the click is not swallowed by the header's own
        // press/reorder handling and always reaches our handler
        var label = new Label("<->");
        label.setMaxWidth(44);
        label.setStyle("-fx-text-fill: #2FE6D9; -fx-cursor: hand; -fx-alignment: center; -fx-padding: 0;");
        label.setOnMouseClicked(e -> autoFitColumns());
        col.setGraphic(label);

        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(null);
            }
        });
        return col;
    }

    private void saveColumnOrder() {
        try {
            var ids = tableView.getColumns().stream()
                .filter(col -> col.getUserData() instanceof Fetcher)
                .map(col -> ((Fetcher) col.getUserData()).getId())
                .toArray(String[]::new);
            guiConfig.setColumnOrder(ids);
        }
        catch (Exception e) {
            // never let persistence break the UI thread
        }
    }

    /**
     * Attach a double-click handler to every column header so that double-clicking the
     * column separator auto-sizes all columns to fit their content.
     */
    private void installAutoFitHandler() {
        Platform.runLater(() -> {
            try {
                for (var node : tableView.lookupAll(".column-header")) {
                    if (!(node instanceof TableColumnHeader header)) continue;
                    if (!handledHeaders.add(header)) continue;
                    header.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                        // double-click near the right edge of a header = the column separator
                        if (e.getClickCount() == 2 && e.getX() >= header.getWidth() - 10) {
                            autoFitColumns();
                            e.consume();
                        }
                    });
                }
            }
            catch (Exception ignored) {}
        });
    }

    private void autoFitColumns() {
        // use the exact algorithm JavaFX applies when the user double-clicks a column
        // separator: it measures real rendered cells (actual font, padding, graphics),
        // which our hand-rolled text measurement could never match reliably.
        try {
            for (var node : tableView.lookupAll(".column-header")) {
                if (!(node instanceof TableColumnHeader header)) continue;
                if (header.getTableColumn() == null || !header.getTableColumn().isResizable()) continue;
                try {
                    var method = TableColumnHeader.class.getDeclaredMethod("resizeColumnToFitContent", int.class);
                    method.setAccessible(true);
                    method.invoke(header, 30);
                }
                catch (ReflectiveOperationException e) {
                    // fall back to the previous manual measurement if the skin internals change
                    autoFitColumnManually((TableColumn<ScanningResult, String>) header.getTableColumn());
                }
            }
        }
        catch (Exception ignored) {}
    }

    private void autoFitColumnManually(TableColumn<ScanningResult, String> col) {
        var fetcher = (Fetcher) col.getUserData();
        if (fetcher == null) return;
        double max = col.getText().length() * 8;
        var idx = scanningResults.getFetcherIndex(fetcher.getId());
        for (var result : items) {
            if (result == null || idx < 0 || idx >= result.getValues().size()) continue;
            var val = result.getValues().get(idx);
            if (val == null) continue;
            var s = val.toString();
            if (s.startsWith("[n/") || s.isEmpty()) continue;
            var tokens = s.split(",");
            double w = 0;
            for (var token : tokens) {
                if (!token.trim().isEmpty()) w += token.trim().length() * 8 + 10;
            }
            if (tokens.length > 1) w += (tokens.length - 1) * 3;
            max = Math.max(max, w + 30);
        }
        col.setPrefWidth(Math.min(Math.max(max, col.getMinWidth()), col.getMaxWidth()));
    }

    private TableColumn<ScanningResult, String> createColumn(Fetcher fetcher) {
        var col = new TableColumn<ScanningResult, String>(fetcher.getFullName());
        var width = guiConfig.getColumnWidth(fetcher);
        if (width > 0) col.setPrefWidth(width);
        col.setMinWidth(50); // columns must not be squeezable down to nothing
        col.setUserData(fetcher);
        col.setSortable(true);

        col.setComparator(getComparatorFor(fetcher));

        if (fetcher.getId().equals(IPFetcher.ID)) {
            col.setPrefWidth(180);
            col.setMaxWidth(300);
            col.setCellFactory(c -> new IPCell());
        } else if (fetcher.getId().equals(HostnameFetcher.ID)) {
            col.setPrefWidth(200);
            col.setMaxWidth(400);
            col.setCellFactory(c -> new DefaultCell());
        } else if (fetcher.getId().contains("ping") || fetcher.getId().contains("Ping") || fetcher instanceof PingFetcher) {
            col.setPrefWidth(90);
            col.setMaxWidth(130);
            col.setCellFactory(c -> new PingCell());
        } else if (fetcher.getId().equals(OpenerLaunchFetcher.ID) || fetcher.getId().equals(OpenerColumnFetcher.ID)) {
            col.setPrefWidth(130);
            col.setMaxWidth(400);
            col.setCellFactory(c -> new OpenerCell());
        } else if (fetcher.getId().equals("fetcher.comment")) {
            col.setPrefWidth(140);
            col.setMaxWidth(500);
            col.setCellFactory(c -> new CommentCell());
        } else if (fetcher.getId().contains("port") || fetcher.getId().contains("Port")) {
            col.setPrefWidth(160);
            col.setMaxWidth(500);
            col.setCellFactory(c -> new PortCell());
        } else {
            col.setPrefWidth(160);
            col.setMaxWidth(500);
            col.setCellFactory(c -> new DefaultCell());
        }

        col.setCellValueFactory(cd -> {
            var result = cd.getValue();
            if (result == null) return new ReadOnlyObjectWrapper<>("");
            var idx = scanningResults.getFetcherIndex(fetcher.getId());
            if (idx >= 0 && idx < result.getValues().size()) {
                var val = result.getValues().get(idx);
                return new ReadOnlyObjectWrapper<>(val != null ? val.toString() : "");
            }
            return new ReadOnlyObjectWrapper<>("");
        });

        col.widthProperty().addListener((obs, old, val) -> {
            if (val.intValue() > 0) {
                guiConfig.setColumnWidth(fetcher, val.intValue());
            }
        });

        return col;
    }

    public void addOrUpdateResultRow(ScanningResult result) {
        Platform.runLater(() -> {
            if (scanningResults.isRegistered(result)) {
                var idx = scanningResults.update(result);
                if (idx >= 0 && idx < items.size()) {
                    items.set(idx, result);
                }
            } else {
                var idx = items.size();
                scanningResults.registerAtIndex(idx, result);
                items.add(result);
            }
        });
    }

    public void removeAll() {
        Platform.runLater(() -> {
            items.clear();
            scanningResults.clear();
        });
    }

    /**
     * Refreshes the displayed results according to the current display method.
     * Always recomputes the whole displayed list from the model, so it is safe to call
     * any time the display method changes. Matches the SWT StatusBar.DisplayModeChangeListener
     * behaviour: ALIVE removes DEAD hosts, PORTS removes hosts without open ports, ALL keeps everything.
     */
    public void filterByDisplayMethod() {
        Platform.runLater(() -> {
            if (guiConfig.displayMethod != GUIConfig.DisplayMethod.ALL) {
                // work on a synchronized snapshot of the model, so a concurrently running
                // scan can neither break the iteration nor shift the removal indices
                var keepAddresses = new java.util.HashSet<InetAddress>();
                for (var r : scanningResults.getResultsSnapshot()) {
                    boolean keep;
                    if (guiConfig.displayMethod == GUIConfig.DisplayMethod.ALIVE) {
                        keep = r.getType().ordinal() >= ResultType.ALIVE.ordinal();
                    } else {
                        keep = r.getType() == ResultType.WITH_PORTS;
                    }
                    if (keep) keepAddresses.add(r.getAddress());
                }
                scanningResults.removeExcept(keepAddresses);
            }
            syncItems();
        });
    }

    private java.util.Comparator<String> getComparatorFor(Fetcher fetcher) {
        var id = fetcher.getId();
        if (id.equals(IPFetcher.ID)) {
            return (a, b) -> {
                if (a.isEmpty() || b.isEmpty()) return a.compareTo(b);
                try {
                    var sa = a.split("[\\s/]");
                    var sb = b.split("[\\s/]");
                    var ipa = sa[sa.length - 1];
                    var ipb = sb[sb.length - 1];
                    var ba = java.net.InetAddress.getByName(ipa).getAddress();
                    var bb = java.net.InetAddress.getByName(ipb).getAddress();
                    long la = ((ba[0] & 0xFFL) << 24) | ((ba[1] & 0xFFL) << 16) | ((ba[2] & 0xFFL) << 8) | (ba[3] & 0xFFL);
                    long lb = ((bb[0] & 0xFFL) << 24) | ((bb[1] & 0xFFL) << 16) | ((bb[2] & 0xFFL) << 8) | (bb[3] & 0xFFL);
                    return Long.compare(la, lb);
                } catch (Exception e) { return a.compareTo(b); }
            };
        }
        if (id.contains("ping") || id.contains("Ping") || fetcher instanceof PingFetcher) {
            return (a, b) -> {
                try {
                    var ia = Integer.parseInt(a.replaceAll("[^0-9]", ""));
                    var ib = Integer.parseInt(b.replaceAll("[^0-9]", ""));
                    return Integer.compare(ia, ib);
                } catch (NumberFormatException e) {
                    return a.compareTo(b);
                }
            };
        }
        if (id.contains("port") || id.contains("Port")) {
            return (a, b) -> {
                try {
                    var ia = Integer.parseInt(a.replaceAll("[^0-9]", ""));
                    var ib = Integer.parseInt(b.replaceAll("[^0-9]", ""));
                    return Integer.compare(ia, ib);
                } catch (NumberFormatException e) {
                    return a.compareTo(b);
                }
            };
        }
        return String::compareTo;
    }

    private void syncItems() {
        // snapshot under the model lock: safe while a scan is concurrently adding results
        items.setAll(scanningResults.getResultsSnapshot());
    }

    /**
     * Rebuilds the items of the column visibility (toggle) menu, so check marks
     * always reflect the current visibility of the columns.
     */
    private void rebuildColumnVisibilityMenu(ContextMenu menu) {
        menu.getItems().clear();
        var registered = fetcherRegistry.getRegisteredFetchers();
        var selected = fetcherRegistry.getSelectedFetchers();

        for (var fetcher : registered) {
            // the IP column must always remain visible, so it is not offered as a toggle option
            if (IPFetcher.ID.equals(fetcher.getId())) continue;

            var item = new CheckMenuItem(fetcher.getFullName());
            item.setStyle("-fx-text-fill: #E8F4F8;");
            item.setSelected(selected.contains(fetcher));
            item.setOnAction(ev -> toggleFetcher(fetcher));
            menu.getItems().add(item);
        }
    }

    private void toggleFetcher(Fetcher fetcher) {
        var selected = fetcherRegistry.getSelectedFetchers();
        var newSelection = new ArrayList<Fetcher>(selected);
        if (newSelection.contains(fetcher))
            newSelection.remove(fetcher);
        else
            newSelection.add(fetcher);

        var ids = newSelection.stream()
            .map(Fetcher::getId)
            .toArray(String[]::new);
        fetcherRegistry.updateSelectedFetchers(ids);
        scanningResults.syncFetchers(newSelection);
        rebuildColumns();
    }

    public Node getNode() {
        return tableView;
    }

    public TableView<ScanningResult> getTableView() {
        return tableView;
    }

    public ScanningResultList getScanningResults() {
        return scanningResults;
    }

    @Override
    public void transitionTo(ScanningState state, Transition transition) {
    }

    static class IPCell extends TableCell<ScanningResult, String> {
        private final HBox box = new HBox(8);
        private final Circle dot = new Circle(4.5);
        private final Label text = new Label();

        IPCell() {
            box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            text.setStyle("-fx-text-fill: #E8F4F8;");
            box.getChildren().addAll(dot, text);
            setText(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            text.setText(item);
            var result = getTableRow() != null ? getTableRow().getItem() : null;
            dot.setFill(javafx.scene.paint.Color.web("#5C7480"));
            if (result != null) {
                switch (result.getType()) {
                    case DEAD -> dot.setFill(javafx.scene.paint.Color.web("#FF4757"));
                    case ALIVE -> dot.setFill(javafx.scene.paint.Color.web("#3CFF9E"));
                    case WITH_PORTS -> dot.setFill(javafx.scene.paint.Color.web("#2FE6D9"));
                }
            }
            if (item.equals("[n/a]") || item.equals("[n/s]") || item.startsWith("[n/")) {
                text.setStyle("-fx-text-fill: #4A5A64;");
            } else {
                text.setStyle("-fx-text-fill: #E8F4F8;");
            }
            setGraphic(box);
        }
    }

    static class PingCell extends TableCell<ScanningResult, String> {
        private static final String ALIGN_STYLE = "-fx-alignment: center-right;";

        PingCell() { setStyle(ALIGN_STYLE); }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(ALIGN_STYLE);
                getStyleClass().removeAll("ping-fast", "ping-moderate", "ping-slow");
                return;
            }
            setText(item);
            getStyleClass().removeAll("ping-fast", "ping-moderate", "ping-slow");
            try {
                var clean = item.replaceAll("[^0-9]", "");
                if (!clean.isEmpty()) {
                    var ms = Integer.parseInt(clean);
                    if (ms < 10) { getStyleClass().add("ping-fast"); setStyle(ALIGN_STYLE + "-fx-text-fill: #3CFF9E;"); }
                    else if (ms < 300) { getStyleClass().add("ping-moderate"); setStyle(ALIGN_STYLE + "-fx-text-fill: #FFB020;"); }
                    else { getStyleClass().add("ping-slow"); setStyle(ALIGN_STYLE + "-fx-text-fill: #FF4757;"); }
                } else {
                    setStyle(ALIGN_STYLE + "-fx-text-fill: #4A5A64;");
                }
            } catch (NumberFormatException e) {
                setStyle(ALIGN_STYLE + "-fx-text-fill: #4A5A64;");
            }
        }
    }

    class OpenerCell extends TableCell<ScanningResult, String> {
        OpenerCell() {
            setOnMouseClicked(e -> {
                if (isEmpty() || getItem() == null || getItem().isEmpty()) return;
                var col = getTableColumn();
                if (col != null && OpenerLaunchFetcher.ID.equals(((Fetcher) col.getUserData()).getId())) {
                    launchOpener();
                } else if (e.getClickCount() == 2) {
                    var row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) {
                        showOpenerMenu(row);
                    }
                }
            });
        }

        private void launchOpener() {
            var row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row == null) return;
            var ip = row.getAddress().getHostAddress();
            var openerName = defaultOpenerConfig.get(ip);
            if (openerName == null) return;
            var openersConfig = Config.getConfig().forOpeners();
            var opener = openersConfig.getOpener(openerName);
            if (opener == null || opener.execString == null) return;

            var hostname = getHostname(row);
            var cmd = opener.execString
                .replace("${fetcher.ip}", ip)
                .replace("${fetcher.hostname}", hostname);

            if (cmd.contains("[n/a]") || cmd.contains("[n/s]")) return;

            try {
                if (cmd.startsWith("http://") || cmd.startsWith("https://") || cmd.startsWith("ftp://") || cmd.startsWith("mailto:")) {
                    var encoded = cmd.replace("%", "%25")
                        .replace(" ", "%20").replace("\n", "%0A").replace("\r", "%0D")
                        .replace("\"", "%22").replace("<", "%3C").replace(">", "%3E")
                        .replace("{", "%7B").replace("}", "%7D").replace("|", "%7C")
                        .replace("\\", "%5C").replace("^", "%5E").replace("[", "%5B").replace("]", "%5D")
                        .replace("`", "%60").replace("(", "%28").replace(")", "%29");
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(encoded));
                } else if (opener.inTerminal) {
                    if (net.azib.ipscan.config.Platform.WINDOWS) {
                        new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", cmd).start();
                    } else if (net.azib.ipscan.config.Platform.LINUX) {
                        new ProcessBuilder("x-terminal-emulator", "-e", "bash", "-c", cmd + "; read -p 'Press Enter to close...'").start();
                    } else {
                        new ProcessBuilder("open", "-a", "Terminal", cmd).start();
                    }
                } else {
                    new ProcessBuilder(cmd.split("\\s+")).start();
                }
            } catch (Exception ex) {
                showErrorAlert(ex.getMessage());
            }
        }

        private String getHostname(ScanningResult result) {
            var idx = scanningResults.getFetcherIndex(HostnameFetcher.ID);
            if (idx >= 0 && idx < result.getValues().size()) {
                var val = result.getValues().get(idx);
                if (val != null && !val.toString().isEmpty()
                    && !val.toString().startsWith("[n/")) return val.toString();
            }
            return result.getAddress().getHostAddress();
        }

        private void showOpenerMenu(ScanningResult result) {
            var config = Config.getConfig().forOpeners();
            var menu = new ContextMenu();
            var names = config.iterator();
            while (names.hasNext()) {
                var name = names.next();
                if (name.startsWith("opener.")) continue;
                var opener = config.getOpener(name);
                var item = new MenuItem(name.replace("&", ""));
                item.setStyle("-fx-text-fill: #E8F4F8;");
                item.setOnAction(ev -> {
                    menu.hide();
                    var ip = result.getAddress().getHostAddress();
                    defaultOpenerConfig.set(ip, name);
                    var openerIdx = scanningResults.getFetcherIndex(OpenerColumnFetcher.ID);
                    if (openerIdx >= 0) result.setValue(openerIdx, name);
                    var launchIdx = scanningResults.getFetcherIndex(OpenerLaunchFetcher.ID);
                    if (launchIdx >= 0) result.setValue(launchIdx, "▶");
                    var idx = scanningResults.update(result);
                    if (idx >= 0 && idx < items.size()) {
                        items.set(idx, result);
                    }
                });
                menu.getItems().add(item);
            }
            menu.show(this, javafx.geometry.Side.BOTTOM, 0, 0);
        }

        private void showErrorAlert(String msg) {
            var alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Opener");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.show();
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText("");
                setStyle("");
                return;
            }
            if (item.equals("[n/a]") || item.equals("[n/s]") || item.startsWith("[n/")) {
                setText(item);
                setStyle("-fx-text-fill: #4A5A64;");
                return;
            }
            if (item.isEmpty()) {
                setText("");
                return;
            }
            setText(item);
            setStyle("-fx-text-fill: #2FE6D9; -fx-cursor: hand;");
        }
    }

    class CommentCell extends TableCell<ScanningResult, String> {
        private TextField editor;

        CommentCell() {
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !isEmpty()) {
                    startEdit();
                }
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (editor == null) {
                editor = new TextField(getItem());
                editor.setOnAction(e -> commitEdit(editor.getText()));
                editor.focusedProperty().addListener((obs, old, focused) -> {
                    if (!focused) commitEdit(editor.getText());
                });
            } else {
                editor.setText(getItem());
            }
            setText(null);
            setGraphic(editor);
            editor.requestFocus();
        }

        @Override
        public void commitEdit(String newValue) {
            super.commitEdit(newValue);
            setGraphic(null);
            setText(newValue);
            var row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null) {
                var commentIdx = scanningResults.getFetcherIndex(CommentFetcher.ID);
                if (commentIdx >= 0 && commentIdx < row.getValues().size()) {
                    row.setValue(commentIdx, newValue);
                    commentsConfig.setComment(row, newValue);
                }
            }
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
                setGraphic(null);
                return;
            }
            if (!isEditing()) {
                setText(item);
                if (item.equals("[n/a]") || item.equals("[n/s]") || item.startsWith("[n/")) {
                    setStyle("-fx-text-fill: #4A5A64;");
                } else {
                    setStyle("-fx-text-fill: #E8F4F8;");
                }
            }
        }
    }

    static class DefaultCell extends TableCell<ScanningResult, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
                return;
            }
            setText(item);
            if (item.equals("[n/a]") || item.equals("[n/s]") || item.startsWith("[")) {
                setStyle("-fx-text-fill: #4A5A64;");
            } else {
                setStyle("-fx-text-fill: #E8F4F8;");
            }
        }
    }

    static class PortCell extends TableCell<ScanningResult, String> {
        private final HBox box = new HBox(3);

        PortCell() {
            box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            setGraphic(box);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || item.equals("[n/a]") || item.equals("[n/s]") || item.startsWith("[")) {
                box.getChildren().clear();
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #4A5A64;");
                    setGraphic(null);
                    setContentDisplay(ContentDisplay.TEXT_ONLY);
                }
                return;
            }
            setText(null);
            setStyle("");
            setGraphic(box);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            box.getChildren().clear();
            var ports = item.split("\s*,\s*");
            for (var port : ports) {
                if (port.trim().isEmpty()) continue;
                var label = new Label(port.trim());
                label.setStyle("-fx-background-color: rgba(0,229,255,0.10); -fx-text-fill: #00E5FF; -fx-padding: 0 4 0 4; -fx-background-radius: 3; -fx-border-color: rgba(0,229,255,0.25); -fx-border-radius: 3; -fx-border-width: 1; -fx-font-size: 10;");
                label.setMaxHeight(18);
                box.getChildren().add(label);
            }
        }
    }
}
