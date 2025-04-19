
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.*;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PDFBookmarkViewerDualModeFinal extends Application {

    private PDDocument document;
    private PDFRenderer renderer;
    private File currentFile;

    private int currentPageIndex = 0;
    private final ImageView pagedImageView = new ImageView();
    private final VBox scrollablePages = new VBox(10);
    private final ScrollPane scrollPane = new ScrollPane();
    private final Map<TreeItem<String>, PDPageDestination> bookmarkDestinations = new HashMap<>();
    private final TreeView<String> bookmarkTree = new TreeView<>();

    private final BorderPane pagedViewPane = new BorderPane();
    private final VBox scrollableViewPane = new VBox(scrollPane);
    private final StackPane viewerContainer = new StackPane();

    private final ComboBox<String> viewModeSelector = new ComboBox<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("PDF Viewer with Dual Mode");

        Button openButton = new Button("Open PDF");
        Button addBookmarkButton = new Button("Add Bookmark to Current Page");
        Button saveButton = new Button("Save PDF");
        Button prevPageButton = new Button("< Prev");
        Button nextPageButton = new Button("Next >");

        viewModeSelector.getItems().addAll("Paged View", "Scrollable View");
        viewModeSelector.setValue("Paged View");

        HBox navBox = new HBox(10, prevPageButton, nextPageButton);
        navBox.setAlignment(Pos.CENTER);
        navBox.setPadding(new Insets(10));

        VBox modeBox = new VBox(5, new Label("View Mode:"), viewModeSelector);
        VBox controlBox = new VBox(10, openButton, addBookmarkButton, saveButton, modeBox);
        controlBox.setPadding(new Insets(10));

        bookmarkTree.setPrefWidth(200);
        bookmarkTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<String> selected = bookmarkTree.getSelectionModel().getSelectedItem();
                if (selected != null && bookmarkDestinations.containsKey(selected)) {
                    try {
                        int pageIndex = bookmarkDestinations.get(selected).retrievePageNumber();
                        currentPageIndex = pageIndex;
                        renderPagedView();
                        scrollToPageInScrollableView(pageIndex);
                    } catch (Exception e) {
                        showAlert("Error", "Failed to jump to bookmark.");
                    }
                }
            }
        });

        scrollPane.setContent(scrollablePages);
        scrollPane.setFitToWidth(true);
        scrollPane.setPadding(new Insets(10));

        pagedImageView.setPreserveRatio(true);
        pagedImageView.setFitWidth(800);
        pagedViewPane.setCenter(pagedImageView);
        pagedViewPane.setBottom(navBox);
        BorderPane.setAlignment(navBox, Pos.CENTER);

        viewerContainer.getChildren().addAll(pagedViewPane, scrollableViewPane);
        scrollableViewPane.setVisible(false);

        HBox root = new HBox(10, controlBox, bookmarkTree, viewerContainer);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        scene.setOnKeyPressed(this::handleKeyInput);

        openButton.setOnAction(e -> openPDF(primaryStage));
        saveButton.setOnAction(e -> savePDF());
        addBookmarkButton.setOnAction(e -> addBookmark());
        nextPageButton.setOnAction(e -> changePage(1));
        prevPageButton.setOnAction(e -> changePage(-1));
        viewModeSelector.setOnAction(e -> switchViewMode());
    }

    private void handleKeyInput(KeyEvent event) {
        if (viewModeSelector.getValue().equals("Paged View")) {
            if (event.getCode() == KeyCode.PAGE_DOWN || event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.RIGHT) {
                changePage(1);
            } else if (event.getCode() == KeyCode.PAGE_UP || event.getCode() == KeyCode.UP || event.getCode() == KeyCode.LEFT) {
                changePage(-1);
            }
        }
    }

    private void openPDF(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                if (document != null) document.close();
                document = PDDocument.load(file);
                currentFile = file;
                renderer = new PDFRenderer(document);
                currentPageIndex = 0;
                renderPagedView();
                renderScrollableView();
                loadBookmarks();
            } catch (IOException ex) {
                showAlert("Error", "Failed to open PDF: " + ex.getMessage());
            }
        }
    }

    private void renderPagedView() {
        try {
            BufferedImage bufferedImage = renderer.renderImageWithDPI(currentPageIndex, 150);
            Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
            pagedImageView.setImage(fxImage);
        } catch (IOException ex) {
            showAlert("Error", "Failed to render page.");
        }
    }

    private void changePage(int delta) {
        if (document == null) return;
        int newIndex = currentPageIndex + delta;
        if (newIndex >= 0 && newIndex < document.getNumberOfPages()) {
            currentPageIndex = newIndex;
            renderPagedView();
        }
    }

    private void renderScrollableView() {
        scrollablePages.getChildren().clear();
        try {
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                final int pageIndex = i;
                BufferedImage bufferedImage = renderer.renderImageWithDPI(i, 100);
                Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                ImageView pageImageView = new ImageView(fxImage);
                pageImageView.setPreserveRatio(true);
                pageImageView.setFitWidth(800);
                pageImageView.setOnMouseClicked((MouseEvent event) -> {
                    currentPageIndex = pageIndex;
                    renderPagedView();  // keep paged view in sync
                });
                scrollablePages.getChildren().add(pageImageView);
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to render scrollable view.");
        }
    }

    private void scrollToPageInScrollableView(int pageIndex) {
        if (pageIndex >= 0 && pageIndex < scrollablePages.getChildren().size()) {
            Node target = scrollablePages.getChildren().get(pageIndex);
            scrollPane.setVvalue(target.getLayoutY() / scrollablePages.getHeight());
        }
    }

    private void switchViewMode() {
        boolean isPaged = viewModeSelector.getValue().equals("Paged View");
        pagedViewPane.setVisible(isPaged);
        scrollableViewPane.setVisible(!isPaged);
    }

    private void addBookmark() {
        if (document == null) return;

        TextInputDialog dialog = new TextInputDialog("Bookmark Page " + (currentPageIndex + 1));
        dialog.setTitle("Add Bookmark");
        dialog.setHeaderText("Enter a name for the bookmark:");
        dialog.setContentText("Title:");

        dialog.showAndWait().ifPresent(title -> {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDDocumentOutline outline = catalog.getDocumentOutline();
            if (outline == null) {
                outline = new PDDocumentOutline();
                catalog.setDocumentOutline(outline);
            }

            PDPageFitDestination destination = new PDPageFitDestination();
            destination.setPage(document.getPage(currentPageIndex));

            PDOutlineItem bookmark = new PDOutlineItem();
            bookmark.setTitle(title);
            bookmark.setDestination(destination);
            outline.addLast(bookmark);
            outline.openNode();
            loadBookmarks();
        });
    }

    private void loadBookmarks() {
        bookmarkDestinations.clear();
        TreeItem<String> rootItem = new TreeItem<>("Bookmarks");
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline != null) {
            for (PDOutlineItem item : outline.children()) {
                TreeItem<String> childItem = new TreeItem<>(item.getTitle());
                try {
                    bookmarkDestinations.put(childItem, (PDPageDestination) item.getDestination());
                } catch (Exception ignored) {}
                rootItem.getChildren().add(childItem);
            }
        }
        bookmarkTree.setRoot(rootItem);
        bookmarkTree.setShowRoot(true);
    }

    private void savePDF() {
        if (document == null || currentFile == null) return;
        try {
            document.save(currentFile);
            showAlert("Info", "PDF saved successfully.");
        } catch (IOException ex) {
            showAlert("Error", "Failed to save PDF: " + ex.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
