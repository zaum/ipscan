package net.azib.ipscan.gui.fx;

import javafx.geometry.Pos;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import net.azib.ipscan.config.CommentsConfig;
import net.azib.ipscan.config.GUIConfig;
import net.azib.ipscan.config.Labels;
import net.azib.ipscan.core.ScanningResult;
import net.azib.ipscan.core.ScanningResultList;
import net.azib.ipscan.fetchers.CommentFetcher;
import net.azib.ipscan.fetchers.OpenerLaunchFetcher;

/**
 * The "Show IP Details" dialog - a JavaFX port of the SWT DetailsWindow.
 * Shows the scanned values for one host and allows editing its comment.
 */
public class FXDetailsDialog {

    private final GUIConfig guiConfig;
    private final CommentsConfig commentsConfig;
    private final ScanningResultList scanningResults;
    private final FXResultTable resultTable;
    private final ScanningResult result;
    private final int resultIndex;

    public FXDetailsDialog(
        GUIConfig guiConfig,
        CommentsConfig commentsConfig,
        ScanningResultList scanningResults,
        FXResultTable resultTable,
        ScanningResult result,
        int resultIndex
    ) {
        this.guiConfig = guiConfig;
        this.commentsConfig = commentsConfig;
        this.scanningResults = scanningResults;
        this.resultTable = resultTable;
        this.result = result;
        this.resultIndex = resultIndex;
    }

    public void open(Stage owner) {
        var dialog = new Dialog<Void>();
        dialog.initOwner(owner);
        dialog.setTitle(Labels.getLabel("title.details"));
        dialog.setHeaderText(result.getAddress().getHostAddress());
        dialog.setResizable(true);

        var commentField = new TextField();
        commentField.setPromptText(Labels.getLabel("text.comment.edit"));
        commentField.setPrefWidth(guiConfig.detailsWindowSize[0] - 40);
        var comment = commentsConfig.getComment(result);
        if (comment != null) commentField.setText(comment);

        var detailsText = new TextArea(buildDetailsText());
        detailsText.setEditable(false);
        detailsText.setWrapText(true);
        VBox.setVgrow(detailsText, Priority.ALWAYS);

        var content = new VBox(8);
        content.setAlignment(Pos.TOP_LEFT);
        var commentLabel = new Label(Labels.getLabel("text.comment.edit"));
        commentLabel.getStyleClass().add("status-label");
        content.getChildren().addAll(commentLabel, commentField, detailsText);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(guiConfig.detailsWindowSize[0], guiConfig.detailsWindowSize[1]);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);

        var cssUrl = getClass().getResource("/css/cyberpunk.css");
        if (cssUrl != null) {
            dialog.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("main-stage");

        commentField.textProperty().addListener((obs, old, value) -> {
            commentsConfig.setComment(result, value);
            var commentIdx = scanningResults.getFetcherIndex(CommentFetcher.ID);
            if (commentIdx >= 0 && commentIdx < result.getValues().size()) {
                result.setValue(commentIdx, value);
                resultTable.refreshResult(resultIndex);
            }
        });

        dialog.show();
    }

    private String buildDetailsText() {
        var newLine = System.getProperty("line.separator");
        var details = new StringBuilder(1024);
        var fetchers = scanningResults.getFetchers();
        var values = result.getValues();
        for (var i = 0; i < fetchers.size() && i < values.size(); i++) {
            // hide the Opener Launch column (it only holds a clickable triangle icon)
            if (fetchers.get(i).getId().equals(OpenerLaunchFetcher.ID)) continue;
            details.append(fetchers.get(i).getName()).append(":\t");
            var value = values.get(i);
            details.append(value != null ? value : "");
            details.append(newLine);
        }
        return details.toString();
    }
}
