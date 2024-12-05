// ./src/main/java/local/jfx360/controllers/InstructorMainPage.java
package local.jfx360.controllers;

import com.dlsc.formsfx.model.structure.DataField;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import local.jfx360.utils.DatabaseUtil;
import javafx.geometry.Insets;

import static local.jfx360.utils.SimpleEncryption.decrypt;
import static local.jfx360.utils.SimpleEncryption.encrypt;
import java.io.IOException;
import java.sql.*;

/**
 * Controller class for the Instructor Main Page in the application.
 * This class handles various instructor functionalities such as
 * manipulating article data, searching, back up.
 */

public class InstructorMainPage extends HelpSystemHelper {
    @FXML
    private TabPane tabPane;
    @FXML
    private Button createGroupButton;
    @FXML
    private Label displaynameLabel;
    @FXML
    private Label displayroleLabel;
    @FXML
    private Button logoutButton;
    @FXML
    private TableView<HelpArticle> helpArticlesTableView;
    @FXML
    private TableView<GroupTableRecord> groupsTableView;
    @FXML
    private ComboBox<String> groupFilterComboBox;
    @FXML
    private TextField searchTextField;
    @FXML
    private TextArea articlePreviewArea;
    @FXML
    private TextField searchGroup;
    @FXML
    private Button createHelpButton;
    @FXML
    private Button backupButton;
    @FXML
    private Button restoreButton;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button viewButton;

    // Table columns
    @FXML
    private TableColumn<HelpArticle, Long> articleIdColumn;
    @FXML
    private TableColumn<HelpArticle, String> articleTitleColumn;
    @FXML
    private TableColumn<HelpArticle, String> articleLevelColumn;
    @FXML
    private TableColumn<HelpArticle, String> articleGroupsColumn;
    @FXML
    private TableColumn<HelpArticle, String> articleDescriptionColumn;
    @FXML
    private TableColumn<GroupTableRecord, String> groupNameColumn;
    @FXML
    private TableColumn<GroupTableRecord, String> articleCountColumn;
    @FXML
    private TableColumn<GroupTableRecord, String> membersColumn;

    @FXML
    private Tab adminRightsTab;  // Reference to the Admin Rights tab

    @FXML
    private TextField userIdField;

    @FXML
    private TableView<Integer> adminRightsTableView;

    @FXML
    private TableColumn<Integer, Integer> userIdColumn;

    @FXML
    private TableColumn<Integer, String> username;  // Note the type change

    private ObservableList<Integer> adminRightsList;
    private int userId;  // Current logged-in user

    /**
     * Initializes the Instructor Main Page controller.
     * Sets up table columns, search functionality, and help system components.
     */
    @FXML
    private void initialize() {

        setupTableColumns();
        setupHelpSystem();
        setupSearchFunctionality();

        groupNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        articleCountColumn.setCellValueFactory(new PropertyValueFactory<>("articleCount"));
        membersColumn.setCellValueFactory(new PropertyValueFactory<>("memberCount"));

        loadGroupsTable();

        searchGroup.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.trim().isEmpty()) {
                loadGroupsTable();
            } else {
                searchGroups();
            }
        });

        // Explicitly define both columns
        userIdColumn.setCellValueFactory(cellData -> {
            Integer userId = cellData.getValue();
            return new ReadOnlyObjectWrapper<>(userId);
        });

        username.setCellValueFactory(cellData -> {
            Integer userId = cellData.getValue();
            try (Connection conn = DatabaseUtil.getConnection()) {
                String query = "SELECT username FROM users WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setInt(1, userId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return new SimpleStringProperty(rs.getString("username"));
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return new SimpleStringProperty("Unknown");
        });

        // Ensure the TableView is using the correct list
        adminRightsTableView.setItems(adminRightsList);

        // Add a listener to the tab selection
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == adminRightsTab) {
                // Check if the current user is an admin
                if (!isUserInAdminRights(userId)) {
                    // Show an alert and switch back to the previous tab
                    showAlert("You do not have permission to access Admin Rights");
                    tabPane.getSelectionModel().select(oldTab);
                }
            }
        });
    }

    /**
     * Configures the table columns for the help articles table view.
     * Sets up cell value factories for each column to display article properties:
     * - Article ID
     * - Title
     * - Level
     * - Groups
     * - Description
     */
    private void setupTableColumns() {
        // Set up the table columns
        articleIdColumn.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getId()));

        articleTitleColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getTitle()));

        articleLevelColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getLevel()));

        articleGroupsColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getGroups()));

        articleDescriptionColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getDescription()));
    }

    /**
     * Initializes the help system interface components and their behaviors.
     * This method:
     * - Sets up article preview functionality
     * - Configures the preview area settings
     * - Initializes group filtering with combo box
     * - Sets up event listeners for UI components
     * - Configures action buttons for article management
     * - Loads initial article data
     */
    private void setupHelpSystem() {
        // Setup article preview
        helpArticlesTableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        loadArticlePreview(newValue.getId());
                    } else {
                        articlePreviewArea.clear();
                    }
                });

        // Initialize preview area
        articlePreviewArea.setWrapText(true);
        articlePreviewArea.setEditable(false);

        // Load groups into combo box
        loadGroups(groupFilterComboBox);

        // Add "All Articles" option to combo box
        ObservableList<String> items = groupFilterComboBox.getItems();
        items.addFirst("All Articles");
        groupFilterComboBox.setValue("All Articles");

        // Setup group filter listener
        groupFilterComboBox.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal == null || newVal.equals("All Articles")) {
                        loadArticles(helpArticlesTableView);
                    } else {
                        loadArticlesByGroup(newVal);
                    }
                });

        // Setup buttons
        createHelpButton.setOnAction(e -> createHelpArticle(helpArticlesTableView, userId));
        backupButton.setOnAction(e -> backupArticles(groupFilterComboBox.getValue()));
        restoreButton.setOnAction(e -> showRestoreDialog());
        editButton.setOnAction(e -> editSelectedArticle());
        deleteButton.setOnAction(e -> deleteSelectedArticle());
        viewButton.setOnAction(e -> viewSelectedArticle());

        // Load initial data
        loadArticles(helpArticlesTableView);
    }

    /**
     * Loads and displays articles belonging to a specific group.
     * Queries the database for articles associated with the given group name
     * and updates the table view with the filtered results.
     *
     * @param group The name of the group to filter articles by
     */
    private void loadArticlesByGroup(String group) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT ha.*, GROUP_CONCAT(hag.name) as group_names " +
                    "FROM help_articles ha " +
                    "LEFT JOIN help_article_group_mapping hagm ON ha.id = hagm.article_id " +
                    "LEFT JOIN help_article_groups hag ON hagm.group_id = hag.id " +
                    "WHERE hag.name = ? " +
                    "GROUP BY ha.id";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, group);
                ResultSet rs = pstmt.executeQuery();
                ObservableList<HelpArticle> articles = FXCollections.observableArrayList();

                while (rs.next()) {
                    HelpArticle article = new HelpArticle();
                    article.id = rs.getLong("id");
                    article.title = rs.getString("title");
                    article.description = rs.getString("description");
                    article.level = rs.getString("level");
                    article.groups = rs.getString("group_names");
                    articles.add(article);
                }

                helpArticlesTableView.setItems(articles);
            }
        } catch (SQLException e) {
            showError("Load Error", e.getMessage());
        }
    }

    /**
     * Sets up real-time search functionality for the help articles.
     * Adds a listener to the search text field that:
     * - Reloads all articles when the search field is empty
     * - Filters articles based on search text when content is entered
     */
    private void setupSearchFunctionality() {
        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) {
                loadArticles(helpArticlesTableView);
            } else {
                searchArticles(newValue);
            }
        });
    }

    /**
     * Searches for articles based on the provided search text.
     * Performs a case-insensitive search across multiple fields:
     * - Article title
     * - Description
     * - Keywords
     * Results are grouped by article ID and include associated group names.
     *
     * @param searchText The text to search for in articles
     */
    private void searchArticles(String searchText) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT ha.*, GROUP_CONCAT(hag.name) as group_names " +
                    "FROM help_articles ha " +
                    "LEFT JOIN help_article_group_mapping hagm ON ha.id = hagm.article_id " +
                    "LEFT JOIN help_article_groups hag ON hagm.group_id = hag.id " +
                    "WHERE ha.title LIKE ? OR ha.description LIKE ? OR ha.keywords LIKE ? " +
                    "GROUP BY ha.id";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                String searchPattern = "%" + searchText + "%";
                pstmt.setString(1, searchPattern);
                pstmt.setString(2, searchPattern);
                pstmt.setString(3, searchPattern);

                ResultSet rs = pstmt.executeQuery();
                ObservableList<HelpArticle> articles = FXCollections.observableArrayList();

                while (rs.next()) {
                    HelpArticle article = new HelpArticle();
                    article.id = rs.getLong("id");
                    article.title = rs.getString("title");
                    article.description = rs.getString("description");
                    article.level = rs.getString("level");
                    article.groups = rs.getString("group_names");
                    articles.add(article);
                }

                helpArticlesTableView.setItems(articles);
            }
        } catch (SQLException e) {
            showError("Search Error", e.getMessage());
        }
    }

    /**
     * Loads and displays a preview of the selected article.
     * Retrieves comprehensive article details including:
     * - Title and level
     * - Associated groups
     * - Description and main content
     * - Reference links (if available)
     * Formats the content in a structured layout for preview display.
     *
     * @param articleId The ID of the article to preview
     */
    private void loadArticlePreview(Long articleId) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT ha.*, " +
                    "(SELECT GROUP_CONCAT(DISTINCT hag.name) " +
                    "FROM help_article_group_mapping hagm " +
                    "JOIN help_article_groups hag ON hagm.group_id = hag.id " +
                    "WHERE hagm.article_id = ha.id) as group_names, " +
                    "(SELECT group_id FROM help_article_group_mapping WHERE article_id = ha.id) as group_id " +
                    "FROM help_articles ha " +
                    "WHERE ha.id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setLong(1, articleId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    StringBuilder preview = new StringBuilder();
                    preview.append("Title: ").append(rs.getString("title")).append("\n\n");
                    preview.append("Level: ").append(rs.getString("level")).append("\n\n");

                    String groups = rs.getString("group_names");
                    preview.append("Groups: ").append(groups != null ? groups : "None").append("\n\n");

                    preview.append("Description:\n").append(rs.getString("description")).append("\n\n");

                    // Get the single group ID directly from the result set
                    Long groupId = rs.getLong("group_id");

                    // Proper authorization check
                    boolean isAuthorized = checkAuthorization(userId, groupId);

                    if (isAuthorized) {
                        preview.append("Content:\n").append(decrypt(rs.getString("body"))).append("\n\n");
                    } else {
                        preview.append("Content: [Access Restricted]\n\n");
                    }

                    String references = rs.getString("reference_links");
                    if (references != null && !references.isEmpty()) {
                        preview.append("References:\n").append(references);
                    }

                    articlePreviewArea.setText(preview.toString());
                } else {
                    articlePreviewArea.setText("Article not found.");
                }
            }
        } catch (SQLException e) {
            showError("Preview Error", e.getMessage());
        }
    }


    /**
     * Displays a confirmation dialog for article restoration.
     * Provides options to:
     * - Merge with existing articles
     * - Replace existing articles
     * - Cancel the operation
     * After successful restoration:
     * - Clears all active filters
     * - Refreshes the article view
     * - Clears the preview area
     */
    private void showRestoreDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Restore Articles");
        alert.setContentText("Do you want to merge with existing articles?");
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);

        alert.showAndWait().ifPresent(response -> {
            if (response != ButtonType.CANCEL) {
                // Pass the TableView to the restore method
                restoreArticles(response == ButtonType.YES, helpArticlesTableView);

                // Clear any filters
                groupFilterComboBox.setValue(null);
                searchTextField.clear();

                // Clear the preview area
                articlePreviewArea.clear();
            }
        });
    }

    /**
     * Handles the editing of a selected help article.
     * Performs the following checks and operations:
     * - Validates user session and permissions
     * - Creates and displays an edit dialog
     * - Updates the article if changes are confirmed
     * - Refreshes the article list after successful update
     */
    private void editSelectedArticle() {
        HelpArticle selected = helpArticlesTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // First verify that we have a valid userId
            if (userId <= 0) {
                showError("Edit Error", "Invalid user session. Please try logging in again.");
                return;
            }

            // Debug print to verify userId
            System.out.println("Current userId: " + userId);

            Dialog<HelpArticle> dialog = createEditDialog(selected);
            dialog.showAndWait().ifPresent(article -> {
                try {
                    // Verify user exists in database before proceeding
                    try (Connection conn = DatabaseUtil.getConnection();
                         PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
                        pstmt.setInt(1, userId);
                        ResultSet rs = pstmt.executeQuery();
                        if (!rs.next()) {
                            showError("Edit Error", "User verification failed. Please log in again.");
                            return;
                        }
                        // User exists, proceed with update
                        updateArticle(article);
                        loadArticles(helpArticlesTableView);
                    }
                } catch (SQLException e) {
                    showError("Database Error", "Failed to verify user: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            showError("Edit Error", "Please select an article to edit.");
        }
    }

    /**
     * Creates and configures a dialog for editing help articles.
     * The dialog includes fields for:
     * - Basic article information (title, description, level)
     * - Content management (keywords, body, references)
     * - Restricted content handling (public title/description)
     * - Group assignments
     *
     * @param article The article to be edited
     * @return A configured Dialog instance for article editing
     */
    private Dialog<HelpArticle> createEditDialog(HelpArticle article) {
        Dialog<HelpArticle> dialog = new Dialog<>();
        dialog.setTitle("Edit Help Article");
        dialog.setHeaderText("Edit Article: " + article.getTitle());

        // Create form fields
        TextField titleField = new TextField(article.title);
        TextArea descriptionArea = new TextArea(article.description);
        ComboBox<String> levelCombo = new ComboBox<>(
                FXCollections.observableArrayList("beginner", "intermediate", "advanced", "expert")
        );
        levelCombo.setValue(article.level);

        TextField groupsField = new TextField(article.groups);

        // Load the full article data from database to populate all fields
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT * FROM help_articles WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setLong(1, article.getId());
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    TextField keywordsField = new TextField(rs.getString("keywords"));
                    TextArea bodyArea = new TextArea(rs.getString("body"));
                    TextArea referenceLinksArea = new TextArea(rs.getString("reference_links"));
                    CheckBox restrictedCheck = new CheckBox("Contains Sensitive Information");
                    restrictedCheck.setSelected(rs.getBoolean("is_restricted"));
                    TextField publicTitleField = new TextField(rs.getString("public_title"));
                    TextArea publicDescArea = new TextArea(rs.getString("public_desc"));

                    // Layout the form
                    GridPane grid = new GridPane();
                    grid.setHgap(10);
                    grid.setVgap(10);
                    grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

                    grid.add(new Label("Title:"), 0, 0);
                    grid.add(titleField, 1, 0);
                    grid.add(new Label("Description:"), 0, 1);
                    grid.add(descriptionArea, 1, 1);
                    grid.add(new Label("Level:"), 0, 2);
                    grid.add(levelCombo, 1, 2);
                    grid.add(new Label("Groups (comma separated):"), 0, 3);
                    grid.add(groupsField, 1, 3);
                    grid.add(new Label("Keywords:"), 0, 4);
                    grid.add(keywordsField, 1, 4);
                    grid.add(new Label("Content:"), 0, 5);
                    grid.add(bodyArea, 1, 5);
                    grid.add(new Label("Reference Links:"), 0, 6);
                    grid.add(referenceLinksArea, 1, 6);
                    grid.add(restrictedCheck, 1, 7);
                    grid.add(new Label("Public Title:"), 0, 8);
                    grid.add(publicTitleField, 1, 8);
                    grid.add(new Label("Public Description:"), 0, 9);
                    grid.add(publicDescArea, 1, 9);

                    // Set preferred sizes for TextAreas
                    descriptionArea.setPrefRowCount(3);
                    bodyArea.setPrefRowCount(10);
                    referenceLinksArea.setPrefRowCount(3);
                    publicDescArea.setPrefRowCount(3);

                    // Enable/disable public fields based on restricted checkbox
                    publicTitleField.setDisable(!restrictedCheck.isSelected());
                    publicDescArea.setDisable(!restrictedCheck.isSelected());
                    restrictedCheck.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                        publicTitleField.setDisable(!isSelected);
                        publicDescArea.setDisable(!isSelected);
                    });

                    dialog.getDialogPane().setContent(grid);
                    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                    dialog.setResultConverter(buttonType -> {
                        if (buttonType == ButtonType.OK) {
                            HelpArticle updatedArticle = new HelpArticle();
                            updatedArticle.id = article.getId();
                            updatedArticle.title = titleField.getText();
                            updatedArticle.description = descriptionArea.getText();
                            updatedArticle.level = levelCombo.getValue();
                            updatedArticle.keywords = keywordsField.getText();
                            updatedArticle.body = bodyArea.getText();
                            updatedArticle.referenceLinks = referenceLinksArea.getText();
                            updatedArticle.isRestricted = restrictedCheck.isSelected();
                            updatedArticle.publicTitle = restrictedCheck.isSelected() ? publicTitleField.getText() : null;
                            updatedArticle.publicDesc = restrictedCheck.isSelected() ? publicDescArea.getText() : null;
                            updatedArticle.groups = groupsField.getText();
                            // Don't set lastModifiedBy here - it will be handled in updateArticle
                            return updatedArticle;
                        }
                        return null;
                    });
                }
            }
        } catch (SQLException e) {
            showError("Load Error", "Failed to load article data: " + e.getMessage());
        }

        return dialog;
    }

    /**
     * Updates an article in the database with new information.
     * Performs the following operations in a transaction:
     * - Updates the article's main content and metadata
     * - Manages group mappings (removes old, adds new)
     * - Records the last modifier and timestamp
     * - Handles restricted content settings
     *
     * @param article The article with updated information
     */
    private void updateArticle(HelpArticle article) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                System.out.println("Updating article with userId: " + userId);

                // Update article details
                String sql = "UPDATE help_articles SET " +
                        "title = ?, description = ?, level = ?, " +
                        "keywords = ?, body = ?, reference_links = ?, " +
                        "is_restricted = ?, public_title = ?, " +
                        "public_desc = ?, last_modified_by = ?, " +
                        "updated_at = CURRENT_TIMESTAMP " +
                        "WHERE id = ?";

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, article.title);
                    pstmt.setString(2, article.description);
                    pstmt.setString(3, article.level);
                    pstmt.setString(4, article.keywords);
                    pstmt.setString(5, article.body);
                    pstmt.setString(6, article.referenceLinks);
                    pstmt.setBoolean(7, article.isRestricted);
                    pstmt.setString(8, article.publicTitle);
                    pstmt.setString(9, article.publicDesc);
                    pstmt.setInt(10, userId);
                    pstmt.setLong(11, article.id);

                    int updatedRows = pstmt.executeUpdate();
                    if (updatedRows == 0) {
                        throw new SQLException("No rows were updated - article may not exist");
                    }
                }

                // Delete existing group mappings
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "DELETE FROM help_article_group_mapping WHERE article_id = ?")) {
                    pstmt.setLong(1, article.id);
                    pstmt.executeUpdate();
                }

                // Check if user has admin rights
                try (PreparedStatement checkAdmin = conn.prepareStatement(
                        "SELECT userid FROM AdminRights WHERE userid = ?")) {
                    checkAdmin.setInt(1, userId);
                    ResultSet rs = checkAdmin.executeQuery();
                    if (!rs.next()) {
                        // Insert into AdminRights if not exists
                        try (PreparedStatement insertAdmin = conn.prepareStatement(
                                "INSERT INTO AdminRights (userid) VALUES (?)")) {
                            insertAdmin.setInt(1, userId);
                            insertAdmin.executeUpdate();
                        }
                    }
                }

                // Insert new group mappings with is_special_access and role_id
                if (article.groups != null && !article.groups.isEmpty()) {
                    String insertMappingSql =
                            "INSERT INTO help_article_group_mapping " +
                                    "(article_id, group_id, is_special_access, role_id) " +
                                    "VALUES (?, ?, true, ?)";

                    try (PreparedStatement pstmt = conn.prepareStatement(insertMappingSql)) {
                        String[] groupNames = article.groups.split(",");
                        for (String groupName : groupNames) {
                            groupName = groupName.trim();
                            if (!groupName.isEmpty()) {
                                int groupId = ensureGroupExists(conn, groupName);
                                pstmt.setLong(1, article.id);
                                pstmt.setInt(2, groupId);
                                pstmt.setInt(3, userId);  // Set role_id to current userId
                                pstmt.executeUpdate();
                            }
                        }
                    }
                }

                conn.commit();
                showInformationAlert("Success", "Article updated successfully!");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            showError("Update Error", "Failed to update article: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Displays an information alert dialog.
     * Used for showing success messages and important notifications.
     *
     * @param title The title of the alert dialog
     * @param content The message to display
     */
    private void showInformationAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Handles the deletion of a selected help article.
     * Performs the following steps:
     * - Confirms deletion with user
     * - Removes group mappings
     * - Deletes the article
     * - Refreshes the article list
     */
    private void deleteSelectedArticle() {
        HelpArticle selected = helpArticlesTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Article");
            alert.setHeaderText("Confirm Deletion");
            alert.setContentText("Are you sure you want to delete this article?");

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try (Connection conn = DatabaseUtil.getConnection()) {
                        // First delete mappings
                        String deleteMappingsSql = "DELETE FROM help_article_group_mapping WHERE article_id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(deleteMappingsSql)) {
                            pstmt.setLong(1, selected.getId());
                            pstmt.executeUpdate();
                        }

                        // Then delete article
                        String deleteArticleSql = "DELETE FROM help_articles WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(deleteArticleSql)) {
                            pstmt.setLong(1, selected.getId());
                            pstmt.executeUpdate();
                        }

                        loadArticles(helpArticlesTableView);
                    } catch (SQLException e) {
                        showError("Delete Error", e.getMessage());
                    }
                }
            });
        } else {
            showError("Delete Error", "Please select an article to delete.");
        }
    }

    /**
     * Displays a dialog showing the full content of a selected article.
     * Shows comprehensive article information including:
     * - Title and description
     * - Main content
     * - Reference links
     */
    private void viewSelectedArticle() {
        HelpArticle selected = helpArticlesTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("View Article");
            dialog.setHeaderText(selected.getTitle());

            TextArea contentArea = new TextArea();
            contentArea.setWrapText(true);
            contentArea.setEditable(false);
            contentArea.setPrefRowCount(20);
            contentArea.setPrefColumnCount(50);

            try (Connection conn = DatabaseUtil.getConnection()) {
                String query = "SELECT * FROM help_articles WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setLong(1, selected.getId());
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        StringBuilder content = new StringBuilder();
                        content.append("Title: ").append(rs.getString("title")).append("\n\n");
                        content.append("Description:\n").append(rs.getString("description")).append("\n\n");
                        content.append("Content:\n").append(rs.getString("body")).append("\n\n");
                        content.append("Reference Links:\n").append(rs.getString("reference_links"));
                        contentArea.setText(content.toString());
                    }
                }
            } catch (SQLException e) {
                showError("View Error", e.getMessage());
            }

            dialog.getDialogPane().setContent(contentArea);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.show();
        } else {
            showError("View Error", "Please select an article to view.");
        }
    }

    /**
     * Sets the user ID for the current session and updates user information display.
     * Validates the user ID and throws an exception if invalid.
     * After setting the ID, updates the display with user's name and roles.
     *
     * @param userId The ID of the logged-in user
     * @throws IllegalArgumentException if userId is less than or equal to 0
     */


    /**
     * Verifies if the current user session is valid by checking the database.
     * A session is considered valid if:
     * - The userId is positive
     * - The user exists in the database
     *
     * @return true if the user session is valid, false otherwise
     */
    private boolean isValidUserSession() {
        if (userId <= 0) {
            return false;
        }

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
            pstmt.setInt(1, userId);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Updates the display of current user information.
     * Retrieves and shows:
     * - User's full name (preferred name if available)
     * - All assigned roles (comma-separated)
     * Updates the corresponding UI labels with the retrieved information.
     */
    private void updateCurrentUserInfo() {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT u.username, " +
                    "CONCAT(COALESCE(u.preferred_first_name, u.first_name), ' ', u.last_name) AS full_name, " +
                    "GROUP_CONCAT(r.name SEPARATOR ', ') AS roles " +
                    "FROM users u " +
                    "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
                    "LEFT JOIN roles r ON ur.role_id = r.id " +
                    "WHERE u.id = ? " +
                    "GROUP BY u.id";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    displaynameLabel.setText(rs.getString("full_name"));
                    displayroleLabel.setText(rs.getString("roles"));
                }
            }
        } catch (SQLException e) {
            showError("User Info Error", e.getMessage());
        }
    }

    /**
     * Handles the logout process by returning to the login screen.
     * Loads the login FXML and switches the current scene to the login view.
     * Shows an error dialog if the logout process fails.
     */
    @FXML
    private void handleLogoutButton() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/local/jfx360/fxml/Login.fxml"));
            Parent loginRoot = loader.load();
            Scene loginScene = new Scene(loginRoot);
            Stage currentStage = (Stage) logoutButton.getScene().getWindow();
            currentStage.setScene(loginScene);
            currentStage.show();
        } catch (IOException e) {
            showError("Logout Error", e.getMessage());
        }
    }

    /**
     * Loads and displays all help articles in the table view.
     * Retrieves articles with their associated groups and displays:
     * - Article ID and title
     * - Description and level
     * - Associated groups (concatenated)
     *
     * @param tableView The TableView to populate with articles
     */
    @Override
    protected void loadArticles(TableView<HelpArticle> tableView) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT ha.*, GROUP_CONCAT(hag.name) as group_names " +
                    "FROM help_articles ha " +
                    "LEFT JOIN help_article_group_mapping hagm ON ha.id = hagm.article_id " +
                    "LEFT JOIN help_article_groups hag ON hagm.group_id = hag.id " +
                    "GROUP BY ha.id";

            try (PreparedStatement pstmt = conn.prepareStatement(query);
                 ResultSet rs = pstmt.executeQuery()) {

                ObservableList<HelpArticle> articles = FXCollections.observableArrayList();

                while (rs.next()) {
                    HelpArticle article = new HelpArticle();
                    article.id = rs.getLong("id");
                    article.title = rs.getString("title");
                    article.description = rs.getString("description");
                    article.level = rs.getString("level");
                    article.groups = rs.getString("group_names");
                    articles.add(article);
                }

                tableView.setItems(articles);
            }
        } catch (SQLException e) {
            showError("Load Error", e.getMessage());
        }
    }

    public static class UserRecord {
        private final IntegerProperty id = new SimpleIntegerProperty();
        private final StringProperty fullName = new SimpleStringProperty();
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        public UserRecord(int id, String fullName) {
            this.id.set(id);
            this.fullName.set(fullName);
        }

        public int getId() { return id.get(); }
        public String getFullName() { return fullName.get(); }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean value) { selected.set(value); }
        public BooleanProperty selectedProperty() { return selected; }
    }

    private void loadStudentsIntoTable(TableView<UserRecord> table) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT u.id, CONCAT(u.first_name, ' ', u.last_name) as full_name " +
                    "FROM users u " +
                    "JOIN user_roles ur ON u.id = ur.user_id " +
                    "JOIN roles r ON ur.role_id = r.id " +
                    "WHERE r.name = 'Student'";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                ResultSet rs = pstmt.executeQuery();
                ObservableList<UserRecord> students = FXCollections.observableArrayList();
                while (rs.next()) {
                    students.add(new UserRecord(
                            rs.getInt("id"),
                            rs.getString("full_name")
                    ));
                }
                table.setItems(students);
            }
        } catch (SQLException e) {
            showError("Load Error", "Failed to load students: " + e.getMessage());
        }
    }

    @FXML
    private void createNewGroupTab() {
        // Create new tab
        Tab newGroupTab = new Tab("New Group");

        // Create content for the tab
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 10, 20, 10));

        // Group name field
        HBox nameBox = new HBox(10);
        ComboBox<String> groupComboBox = new ComboBox<>();
        loadGroups(groupComboBox); // Load existing groups
        nameBox.getChildren().addAll(new Label("Select Group:"), groupComboBox);

        // Students table
        TableView<UserRecord> studentsTable = new TableView<>();

        // Existing columns
        TableColumn<UserRecord, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<UserRecord, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fullName"));

        TableColumn<UserRecord, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));

        // Add delete button column
        TableColumn<UserRecord, Void> deleteCol = new TableColumn<>("Actions");
        deleteCol.setCellFactory(col -> new TableCell<>() {
            private final Button deleteButton = new Button("Remove");
            {
                deleteButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
                deleteButton.setOnAction(e -> {
                    UserRecord student = getTableView().getItems().get(getIndex());
                    removeStudentFromGroup(student, groupComboBox.getValue());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        studentsTable.getColumns().addAll(idCol, nameCol, selectCol, deleteCol);
        studentsTable.setEditable(true);

        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));

        studentsTable.getColumns().addAll(idCol, nameCol, selectCol);
        studentsTable.setEditable(true);

        // Load students
        loadStudentsIntoTable(studentsTable);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        Button saveButton = new Button("Save Group");
        Button cancelButton = new Button("Cancel");

        saveButton.setOnAction(e -> saveNewGroup(groupComboBox.getValue(), studentsTable, newGroupTab));
        cancelButton.setOnAction(e -> tabPane.getTabs().remove(newGroupTab));

        buttonBox.getChildren().addAll(saveButton, cancelButton);

        // Add all to content
        content.getChildren().addAll(nameBox, studentsTable, buttonBox);
        newGroupTab.setContent(content);

        // Add and select the new tab
        tabPane.getTabs().add(newGroupTab);
        tabPane.getSelectionModel().select(newGroupTab);
    }

    protected void loadGroups(ComboBox<String> groupComboBox) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT name FROM help_article_groups ORDER BY name";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                ResultSet rs = pstmt.executeQuery();
                ObservableList<String> groups = FXCollections.observableArrayList();
                while (rs.next()) {
                    groups.add(rs.getString("name"));
                }
                groupComboBox.setItems(groups);
            }
        } catch (SQLException e) {
            showError("Load Groups Error", e.getMessage());
        }
    }

    private void saveNewGroup(String groupName, TableView<UserRecord> studentsTable, Tab tab) {
        if (groupName == null || groupName.trim().isEmpty()) {
            showError("Validation Error", "Please select a group");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Get group ID
                int groupId;
                String groupQuery = "SELECT id FROM help_article_groups WHERE name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(groupQuery)) {
                    pstmt.setString(1, groupName);
                    ResultSet rs = pstmt.executeQuery();
                    if (!rs.next()) {
                        throw new SQLException("Group not found");
                    }
                    groupId = rs.getInt("id");
                }

                // Check if the instructor is authorized for the group
                String authQuery = "SELECT COUNT(*) FROM help_article_group_roles WHERE group_id = ? AND user_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(authQuery)) {
                    pstmt.setInt(1, groupId);
                    pstmt.setInt(2, userId);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next() && rs.getInt(1) == 0) {
                        showError("Authorization Error", "You are not authorized to manage this group.");
                        conn.rollback();
                        return;
                    }
                }

                // Save selected students
                String mappingSql = "INSERT INTO student_group_mapping (user_id, group_id) VALUES (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(mappingSql)) {
                    for (UserRecord student : studentsTable.getItems()) {
                        if (student.isSelected()) {
                            pstmt.setInt(1, student.getId());
                            pstmt.setInt(2, groupId);
                            pstmt.addBatch();
                        }
                    }
                    pstmt.executeBatch();
                }

                conn.commit();
                showInformationAlert("Success", "Students added to group successfully!");
                tabPane.getTabs().remove(tab);

                // Add this line to refresh the groups table
                loadGroupsTable();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            showError("Save Error", "Failed to save group members: " + e.getMessage());
        }
    }



    public static class GroupTableRecord {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty articleCount = new SimpleStringProperty();
        private final StringProperty memberCount = new SimpleStringProperty();

        public GroupTableRecord(String name, String articles, String members) {
            this.name.set(name);
            this.articleCount.set(articles);
            this.memberCount.set(members);
        }

        public String getName() { return name.get(); }
        public String getArticleCount() { return articleCount.get(); }
        public String getMemberCount() { return memberCount.get(); }

        public StringProperty nameProperty() { return name; }
        public StringProperty articleCountProperty() { return articleCount; }
        public StringProperty memberCountProperty() { return memberCount; }
    }

    private void loadGroupsTable() {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT hag.id, hag.name " +
                    "FROM help_article_groups hag";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                ResultSet rs = pstmt.executeQuery();
                ObservableList<GroupTableRecord> groups = FXCollections.observableArrayList();
                while (rs.next()) {
                    String groupName = rs.getString("name");
                    int groupId = rs.getInt("id");
                    String articles = getArticleTitles(groupId);
                    String members = getMemberNames(groupName);
                    groups.add(new GroupTableRecord(groupName, articles, members));
                }
                groupsTableView.setItems(groups);
            }
        } catch (SQLException e) {
            showError("Load Error", "Failed to load groups: " + e.getMessage());
        }
    }

    @FXML
    private void searchGroups() {
        if (searchGroup == null) return;

        String searchText = "%" + searchGroup.getText() + "%";
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT hag.id, hag.name " +
                    "FROM help_article_groups hag " +
                    "WHERE hag.name LIKE ?";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, searchText);
                ResultSet rs = pstmt.executeQuery();
                ObservableList<GroupTableRecord> groups = FXCollections.observableArrayList();
                while (rs.next()) {
                    String groupName = rs.getString("name");
                    int groupId = rs.getInt("id");
                    String articles = getArticleTitles(groupId);
                    String members = getMemberNames(groupName);
                    groups.add(new GroupTableRecord(groupName, articles, members));
                }
                groupsTableView.setItems(groups);
            }
        } catch (SQLException e) {
            showError("Search Error", "Failed to search groups: " + e.getMessage());
        }
    }

    private String getArticleTitles(int groupId) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT GROUP_CONCAT(ha.title SEPARATOR ', ') as titles " +
                    "FROM help_articles ha " +
                    "JOIN help_article_group_mapping hagm ON ha.id = hagm.article_id " +
                    "WHERE hagm.group_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, groupId);
                ResultSet rs = pstmt.executeQuery();
                return rs.next() ? rs.getString("titles") : "";
            }
        } catch (SQLException e) {
            return "Error loading articles";
        }
    }

    private String getMemberNames(String groupName) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT GROUP_CONCAT(CONCAT(u.first_name, ' ', u.last_name) SEPARATOR ', ') as names " +
                    "FROM users u " +
                    "JOIN student_group_mapping ugm ON u.id = ugm.user_id " +
                    "JOIN help_article_groups hag ON ugm.group_id = hag.id " +
                    "WHERE hag.name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, groupName);
                ResultSet rs = pstmt.executeQuery();
                return rs.next() ? rs.getString("names") : "";
            }
        } catch (SQLException e) {
            return "Error loading members";
        }
    }

    @FXML
    private void deleteSelectedGroup() {
        GroupTableRecord selectedGroup = groupsTableView.getSelectionModel().getSelectedItem();
        if (selectedGroup == null) {
            showError("Delete Error", "Please select a group to delete");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Group");
        alert.setHeaderText("Confirm Deletion");
        alert.setContentText("Are you sure you want to delete the group: " + selectedGroup.getName() + "?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try (Connection conn = DatabaseUtil.getConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        // First delete user group mappings
                        String deleteUserMappings = "DELETE FROM student_group_mapping WHERE group_id IN " +
                                "(SELECT id FROM help_article_groups WHERE name = ?)";

                        // Then delete article group mappings
                        String deleteArticleMappings = "DELETE FROM help_article_group_mapping WHERE group_id IN " +
                                "(SELECT id FROM help_article_groups WHERE name = ?)";

                        // Finally delete the group
                        String deleteGroup = "DELETE FROM help_article_groups WHERE name = ?";

                        // Execute deletions in order
                        try (PreparedStatement pstmt1 = conn.prepareStatement(deleteUserMappings);
                             PreparedStatement pstmt2 = conn.prepareStatement(deleteArticleMappings);
                             PreparedStatement pstmt3 = conn.prepareStatement(deleteGroup)) {

                            pstmt1.setString(1, selectedGroup.getName());
                            pstmt1.executeUpdate();

                            pstmt2.setString(1, selectedGroup.getName());
                            pstmt2.executeUpdate();

                            pstmt3.setString(1, selectedGroup.getName());
                            pstmt3.executeUpdate();
                        }

                        conn.commit();
                        showInformationAlert("Success", "Group deleted successfully!");
                        loadGroupsTable(); // Refresh the table

                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    }
                } catch (SQLException e) {
                    showError("Delete Error", "Failed to delete group: " + e.getMessage());
                }
            }
        });
    }
    private void removeStudentFromGroup(UserRecord student, String groupName) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "DELETE ugm FROM student_group_mapping ugm " +
                    "JOIN help_article_groups hag ON ugm.group_id = hag.id " +
                    "WHERE ugm.user_id = ? AND hag.name = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, student.getId());
                pstmt.setString(2, groupName);
                int result = pstmt.executeUpdate();

                if (result > 0) {
                    showInformationAlert("Success", "Student removed from group");
                    loadGroupsTable(); // Refresh the groups table
                }
            }
        } catch (SQLException e) {
            showError("Error", "Failed to remove student from group: " + e.getMessage());
        }
    }

    @Override
    public void setUserId(int userId) {
        this.userId = userId;
        System.out.println("Setting userId to: " + userId);

        initializeAdminRights(userId);
        updateCurrentUserInfo();

        // Check if the current user is an admin before showing admin rights tab
        if (isUserInAdminRights(userId)) {
            // Prepare the list
            adminRightsList = FXCollections.observableArrayList();
            adminRightsTableView.setItems(adminRightsList);

            // Load admin rights
            loadAdminRights();
            adminRightsTab.setDisable(false);
        } else {
            adminRightsTab.setDisable(true);
        }
    }

    private boolean isUserInAdminRights(int userId) {
        String query = "SELECT COUNT(*) FROM AdminRights WHERE userid = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0; // Returns true if count is greater than 0
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking user in AdminRights: " + e.getMessage());
        }
        return false; // Default to false if any error occurs
    }

    private void initializeAdminRights(int userId) {
        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement insertStmt = null;

        try {
            // Ensure connection is not null
            conn = DatabaseUtil.getConnection();
            if (conn == null) {
                throw new SQLException("Failed to establish database connection");
            }

            // Disable auto-commit for transaction control
            conn.setAutoCommit(false);

            // First check if user exists in AdminRights
            String checkUserSQL = "SELECT userid FROM AdminRights";
            checkStmt = conn.prepareStatement(checkUserSQL);

            try (ResultSet userRs = checkStmt.executeQuery()) {
                if (!userRs.next()) {
                    // User doesn't exist, insert them
                    String insertSQL = "INSERT INTO AdminRights (userid) VALUES (?)";
                    insertStmt = conn.prepareStatement(insertSQL);
                    insertStmt.setInt(1, userId);
                    insertStmt.executeUpdate();

                    System.out.println("Added user " + userId + " to AdminRights");
                }
            }

            // Commit transaction
            conn.commit();

        } catch (SQLException e) {
            // Rollback transaction in case of error
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }

            // More detailed error logging
            System.err.println("Database Error Details:");
            System.err.println("Error Code: " + e.getErrorCode());
            System.err.println("SQL State: " + e.getSQLState());
            e.printStackTrace();
        } finally {
            // Ensure resources are closed
            try {
                if (checkStmt != null) checkStmt.close();
                if (insertStmt != null) insertStmt.close();
                if (conn != null) {
                    conn.setAutoCommit(true);  // Reset to default
                    conn.close();
                }
            } catch (SQLException closeEx) {
                closeEx.printStackTrace();
            }
        }
    }

    @FXML
    private void handleAddAdminRights() {
        if (!isUserInAdminRights(userId)) {
            showAlert("You do not have permission to modify admin rights");
            return;
        }

        try {
            String username = userIdField.getText().trim();

            if (username.isEmpty()) {
                showAlert("Please enter a username");
                return;
            }

            try (Connection conn = DatabaseUtil.getConnection()) {
                String query = "SELECT id, username FROM users WHERE username = ?";

                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setString(1, username);

                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            Integer userId = rs.getInt("id");
                            String foundUsername = rs.getString("username");

                            // Check if user is already an admin
                            if (!adminRightsList.contains(userId)) {
                                addAdminRightToDatabase(userId);

                                // Add to admin rights list
                                adminRightsList.add(userId);

                                // Update TableView
                                adminRightsTableView.setItems(adminRightsList);

                                // Show success message
                                showAlert("Admin rights granted to " + foundUsername);

                                // Clear input field
                                userIdField.clear();
                            } else {
                                showAlert("User " + foundUsername + " is already an admin");
                            }
                        } else {
                            showAlert("Username not found");
                        }
                    }
                }
            } catch (SQLException e) {
                showError("Database Error", e.getMessage());
            }
        } catch (Exception e) {
            showAlert("Error processing username");
        }
    }

    @FXML
    private void handleRemoveAdminRights() {
        try {
            int userId = Integer.parseInt(userIdField.getText());

            // Check if user is an admin
            if (adminRightsList.contains(userId)) {
                // Remove from database
                removeAdminRightFromDatabase(userId);

                // Remove from local list and update table
                adminRightsList.remove(Integer.valueOf(userId));
                adminRightsTableView.setItems(adminRightsList);

                // Clear input field
                userIdField.clear();
            } else {
                showAlert("User is not an admin");
            }
        } catch (NumberFormatException e) {
            showAlert("Invalid User ID");
        }
    }

    private void loadAdminRights() {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT userid FROM AdminRights")) {

            adminRightsList.clear();
            while (rs.next()) {
                adminRightsList.add(rs.getInt("userid"));
            }

            adminRightsTableView.setItems(adminRightsList);
        } catch (SQLException e) {
            showAlert("Error loading admin rights: " + e.getMessage());
            e.printStackTrace(); // Add this to log the full stack trace
        }
    }

    private void addAdminRightToDatabase(int userId) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO AdminRights (userid) VALUES (?) ON DUPLICATE KEY UPDATE userid = userid")) {

            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            showAlert("Error adding admin right: " + e.getMessage());
            e.printStackTrace(); // Add this to log the full stack trace
        }
    }

    private void removeAdminRightFromDatabase(int userId) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM AdminRights WHERE userid = ?")) {

            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            showAlert("Error removing admin right: " + e.getMessage());
            e.printStackTrace(); // Add this to log the full stack trace
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Admin Rights Management");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean checkAuthorization(long userId, long groupId) throws SQLException {
        String authQuery = "SELECT COUNT(*) FROM help_article_group_roles WHERE group_id = ? AND user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(authQuery)) {
            pstmt.setLong(1, groupId);
            pstmt.setLong(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

}

