package local.jfx360.controllers;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import local.jfx360.utils.DatabaseUtil;

import static local.jfx360.utils.SimpleEncryption.decrypt;

public class StudentMainPage extends HelpSystemHelper {

    @FXML
    private Button logoutButton;
    @FXML
    private Button generalHelpButton;
    @FXML
    private Button specificHelpButton;
    @FXML
    private Label displaynameLabel;
    @FXML
    private Label displayroleLabel;
    @FXML
    private TextArea messageArea;
    @FXML
    private TextArea searchHistoryArea;
    @FXML
    private TextField searchTextField;
    @FXML
    private TableView<HelpArticle> helpArticlesTableView;
    @FXML
    private ComboBox<String> groupFilterComboBox;
    @FXML
    private TextArea articlePreviewArea;
    @FXML
    private Button viewFullArticleButton;


    // Table columns
    @FXML
    private TableColumn<HelpArticle, Long> sequenceColumn;
    @FXML
    private TableColumn<HelpArticle, String> articleTitleColumn;
    @FXML
    private TableColumn<HelpArticle, String> articleAuthorColumn;
    @FXML
    private TableColumn<HelpArticle, String> articleLevelColumn;
    @FXML
    private TableColumn<HelpArticle, String> articleAbstractColumn;

    private int userId;

    public void setUserId(int userId) {
        this.userId = userId;
        loadUserDetails();
        loadHelpHistory(); // Load help request history when user ID is set
    }

    @FXML
    public void initialize() {
        // Initialize message area with placeholder
        messageArea.setPromptText("Type your help request here...");
        searchHistoryArea.setEditable(false); // Make history area read-only

        // Add listeners to buttons
        generalHelpButton.setOnAction(event -> handleHelpRequest("general"));
        specificHelpButton.setOnAction(event -> handleHelpRequest("specific"));

        setupTableColumns();
        setupHelpSystem();
        setupSearchFunctionality();
    }

    /**
     * Initializes the tables for the student help page
     * This method:
     * - Fills in the columns for the list of articles
     * - Retrieves the information from the database
     */

    private void setupTableColumns() {
        // Set up the table columns
        sequenceColumn.setCellValueFactory(cellData ->
                new SimpleObjectProperty<>(cellData.getValue().getId()));

        articleTitleColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getTitle()));

        articleLevelColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getLevel()));

        articleAuthorColumn.setCellValueFactory(cellData ->
        {
            try {
                return new SimpleStringProperty(cellData.getValue().getAuthor());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        articleAbstractColumn.setCellValueFactory(cellData ->
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
        viewFullArticleButton.setOnAction(e -> viewSelectedArticle());

        // Load initial data
        loadArticles(helpArticlesTableView);
    }


    private void handleHelpRequest(String requestType) {
        if (!validateRequest(requestType)) {
            return;
        }

        String message = messageArea.getText().trim();

        String query = "INSERT INTO help_system_messages (user_id, message_type, content) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, userId);
            pstmt.setString(2, requestType.toLowerCase());
            pstmt.setString(3, message);

            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                showAlert("Success", "Your help request has been submitted.", AlertType.INFORMATION);
                messageArea.clear();
                loadHelpHistory();
            } else {
                showAlert("Error", "Failed to submit help request.", AlertType.ERROR);
            }

        } catch (SQLException e) {
            handleDatabaseError("Failed to submit help request", e);
        }
    }

    private boolean validateRequest(String requestType) {
        String message = messageArea.getText().trim();

        if (message.isEmpty()) {
            showAlert("Error", "Please enter a message before submitting.", AlertType.WARNING);
            return false;
        }

        if (!requestType.equalsIgnoreCase("general") && !requestType.equalsIgnoreCase("specific")) {
            showAlert("Error", "Invalid request type.", AlertType.WARNING);
            return false;
        }

        if (message.length() > 65535) { // TEXT column type limit
            showAlert("Error", "Message is too long. Please shorten your message.", AlertType.WARNING);
            return false;
        }

        return true;
    }

    private void loadHelpHistory() {
        String query = """
            SELECT message_type, content, created_at 
            FROM help_system_messages 
            WHERE user_id = ? 
            ORDER BY created_at DESC
            """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, userId);
            StringBuilder history = new StringBuilder();

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("message_type");
                    String content = rs.getString("content");
                    String createdAt = rs.getTimestamp("created_at").toString();

                    history.append("Type: ").append(type.toUpperCase()).append("\n");
                    history.append("Date: ").append(createdAt).append("\n");
                    history.append("Message: ").append(content).append("\n");
                    history.append("----------------------------------------\n");
                }
            }

            searchHistoryArea.setText(history.toString());

        } catch (SQLException e) {
            handleDatabaseError("Failed to load help history", e);
        }
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
                ObservableList<HelpSystemHelper.HelpArticle> articles = FXCollections.observableArrayList();

                while (rs.next()) {
                    HelpSystemHelper.HelpArticle article = new HelpSystemHelper.HelpArticle();
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
     * - Author
     * - Abstract
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
                    "WHERE ha.title LIKE ? OR ha.description LIKE ? OR ha.keywords LIKE ?" +
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

    private void loadUserDetails() {
        String query = """
            SELECT u.username, r.name AS role_name 
            FROM users u 
            JOIN user_roles_view urv ON u.id = urv.user_id 
            JOIN roles r ON urv.role_id = r.id 
            WHERE u.id = ?
            """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String usernameVal = rs.getString("username");
                    String roleName = rs.getString("role_name");

                    displaynameLabel.setText(usernameVal);
                    displayroleLabel.setText("Student " + roleName);
                }
            }
        } catch (SQLException e) {
            handleDatabaseError("Failed to load user details", e);
        }
    }

    private void handleDatabaseError(String message, SQLException e) {
        String errorCode = e.getSQLState() != null ? " (SQL State: " + e.getSQLState() + ")" : "";
        showError("Database Error", message + ": " + e.getMessage() + errorCode);
    }

    void showError(String title, String content) {
        showAlert(title, content, AlertType.ERROR);
    }

    private void showAlert(String title, String content, AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

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
            showError("Navigation Error", "Failed to load login page: " + e.getMessage());
        }
    }

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
                    // Retrieve group ID
                    Long groupId = rs.getLong("group_id");

                    // Check authorization
                    boolean isAuthorized = checkAuthorization(userId, groupId);

                    StringBuilder preview = new StringBuilder();
                    preview.append("Title: ").append(rs.getString("title")).append("\n\n");
                    preview.append("Level: ").append(rs.getString("level")).append("\n\n");

                    String groups = rs.getString("group_names");
                    preview.append("Groups: ").append(groups != null ? groups : "None").append("\n\n");

                    preview.append("Description:\n").append(rs.getString("description")).append("\n\n");

                    // Conditionally show content based on authorization
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
                // First, find the group ID for this article
                String groupQuery = "SELECT group_id FROM help_article_group_mapping WHERE article_id = ?";
                Long groupId = null;

                try (PreparedStatement groupStmt = conn.prepareStatement(groupQuery)) {
                    groupStmt.setLong(1, selected.getId());
                    ResultSet groupRs = groupStmt.executeQuery();

                    if (groupRs.next()) {
                        groupId = groupRs.getLong("group_id");
                    }
                }

                // Then fetch the article details
                String query = "SELECT * FROM help_articles WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setLong(1, selected.getId());
                    ResultSet rs = pstmt.executeQuery();

                    if (rs.next()) {
                        StringBuilder content = new StringBuilder();
                        content.append("Title: ").append(rs.getString("title")).append("\n\n");
                        content.append("Description:\n").append(rs.getString("description")).append("\n\n");

                        // Proper authorization check using the retrieved group ID
                        boolean isAuthorized = (groupId != null) && checkAuthorization(userId, groupId);

                        if (isAuthorized) {
                            content.append("Content:\n").append(decrypt(rs.getString("body"))).append("\n\n");
                        } else {
                            content.append("Content: [Access Restricted]\n\n");
                        }

                        String referenceLinks = rs.getString("reference_links");
                        content.append("Reference Links:\n")
                                .append(referenceLinks != null ? referenceLinks : "No references");

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

    private boolean checkAuthorization(long userId, long groupId) throws SQLException {
        String authQuery = "SELECT COUNT(*) FROM student_group_mapping WHERE group_id = ? AND user_id = ?";

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

