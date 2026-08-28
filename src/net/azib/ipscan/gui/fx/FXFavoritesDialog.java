package net.azib.ipscan.gui.fx;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.azib.ipscan.config.FavoritesConfig;
import net.azib.ipscan.config.Labels;

/**
 * "Manage favorites" dialog - a JavaFX port of the SWT EditFavoritesDialog.
 * Allows rearranging, renaming and deleting favorites.
 */
public class FXFavoritesDialog {

    private final Stage owner;
    private final FavoritesConfig favoritesConfig;
    private final ListView<String> listView = new ListView<>();

    public FXFavoritesDialog(Stage owner, FavoritesConfig favoritesConfig) {
        this.owner = owner;
        this.favoritesConfig = favoritesConfig;
    }

    public void show() {
        var dialog = new Dialog<Void>();
        dialog.initOwner(owner);
        dialog.setTitle(Labels.getLabel("title.favorite.edit"));

        var messageLabel = new Label(Labels.getLabel("text.favorite.edit"));
        messageLabel.setWrapText(true);

        listView.setPrefSize(330, 200);
        for (var name : favoritesConfig) {
            listView.getItems().add(name);
        }

        var upBtn = new Button(Labels.getLabel("button.up"));
        upBtn.setTooltip(new Tooltip(Labels.getLabel("button.up.hint")));
        var downBtn = new Button(Labels.getLabel("button.down"));
        downBtn.setTooltip(new Tooltip(Labels.getLabel("button.down.hint")));
        var renameBtn = new Button(Labels.getLabel("button.rename"));
        var deleteBtn = new Button(Labels.getLabel("button.delete"));

        upBtn.setOnAction(e -> move(-1));
        downBtn.setOnAction(e -> move(1));
        renameBtn.setOnAction(e -> rename());
        deleteBtn.setOnAction(e -> {
            var sel = listView.getSelectionModel().getSelectedIndex();
            if (sel < 0) return;
            listView.getItems().remove(sel);
        });
        deleteBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        renameBtn.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

        var sideBar = new VBox(8, upBtn, downBtn, renameBtn, deleteBtn);
        sideBar.setAlignment(Pos.TOP_CENTER);

        var listPane = new HBox(8, listView, sideBar);
        HBox.setHgrow(listView, Priority.ALWAYS);

        var content = new VBox(10, messageLabel, listPane);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyCss(dialog.getDialogPane());

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                favoritesConfig.update(listView.getItems().toArray(new String[0]));
                favoritesConfig.store();
            }
            return null;
        });
        dialog.showAndWait();
    }

    private void move(int delta) {
        var idx = listView.getSelectionModel().getSelectedIndex();
        var newIdx = idx + delta;
        if (idx < 0 || newIdx < 0 || newIdx >= listView.getItems().size()) return;
        var item = listView.getItems().remove(idx);
        listView.getItems().add(newIdx, item);
        listView.getSelectionModel().select(newIdx);
    }

    private void rename() {
        if (favoritesConfig.size() == 0) return;
        var idx = Math.max(listView.getSelectionModel().getSelectedIndex(), 0);
        var oldName = listView.getItems().get(idx);
        var dialog = new TextInputDialog(oldName);
        dialog.initOwner(owner);
        dialog.setTitle(Labels.getLabel("title.rename"));
        dialog.setHeaderText(null);
        dialog.showAndWait().ifPresent(newName -> {
            if (newName != null && !newName.isEmpty()) {
                favoritesConfig.add(newName, favoritesConfig.remove(oldName));
                listView.getItems().set(idx, newName);
            }
        });
        listView.requestFocus();
    }

    private void applyCss(DialogPane pane) {
        var cssUrl = getClass().getResource("/css/cyberpunk.css");
        if (cssUrl != null) {
            pane.getStylesheets().add(cssUrl.toExternalForm());
        }
        pane.getStyleClass().add("main-stage");
    }
}
