// ./src/main/java/local/jfx360/controllers/AdminMainPage.java
package local.jfx360.controllers;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.collections.ObservableList;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import local.jfx360.utils.DatabaseUtil;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Random;
import javafx.collections.FXCollections;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TextArea;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * Controller class for the Admin Main Page in the application.
 * This class handles administrator functionalities including:
 *
 * User Management:
 * - Managing users and roles
 * - Generating invitation codes
 * - Resetting user passwords
 *
 * Help System Management (Phase 2):
 * - Creating, updating, viewing and deleting help articles
 * - Managing article groups (e.g., Eclipse articles, IntelliJ articles)
 * - Backing up and restoring help system data
 * - Supporting multiple article groupings (articles can belong to multiple groups)
 * - Filtering articles by group
 * - Managing restricted/sensitive content
 */
public class AdminMainPage extends HelpSystemHelper{

    @FXML
    private Label displaynameLabel;

    @FXML
    private Label displayroleLabel;

    @FXML
    private TextField usernameTextField;

    @FXML
    private TextField roleTextField;

    @FXML
    private TableView<ObservableList<String>> userTableView;

    @FXML
    private TableColumn<ObservableList<String>, String> usernameColumn;

    @FXML
    private TableColumn<ObservableList<String>, String> fullnameColumn;

    @FXML
    private TableColumn<ObservableList<String>, String> rolesColumn;

    @FXML
    private TextArea displayConsole;

    @FXML
    private Button logoutButton;

    @FXML
    private TableView<HelpArticle> helpArticlesTableView;
    @FXML
    private ComboBox<String> groupFilterComboBox;
    @FXML
    private TextArea articlePreviewArea;
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
    @FXML
    private ComboBox<String> roleComboBox;


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
    private Tab adminRightsTab;  // Reference to the Admin Rights tab

    @FXML
    private TextField userIdField;

    @FXML
    private TableView<Integer> adminRightsTableView;

    @FXML
    private TableColumn<Integer, Integer> userIdColumn;

    @FXML
    private TabPane tabPane;

    @FXML
    private TableColumn<Integer, String> username;  // Note the type change

    private ObservableList<Integer> adminRightsList;

    public int userId;

    /**
     * Initializes the Admin Main Page controller.
     * Sets up table columns, loads users, and initializes the help system components.
     */
    @FXML
    private void initialize() {
        roleComboBox.setItems(FXCollections.observableArrayList("Admin", "Instructor", "Student"));
    }

    public void setUserId(int userId) {
        this.userId = userId;
        updateCurrentUserInfo();

        System.out.println("Setting userId to: " + userId);

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

    /**
     * Sets up the help system components including:
     * - Article preview functionality
     * - Table columns for displaying articles
     * - Group filtering
     * - Buttons for article management and backup/restore operations
     */
    private void setupHelpSystem() {

    }

    /**
     * Loads articles from the database into the table view.
     * Groups multiple articles and concatenates their group names.
     *
     * @param tableView The TableView to load articles into
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

//    // Add method to format the preview text nicely
//    private String formatPreviewText(String text) {
//        if (text == null || text.isEmpty()) {
//            return "N/A";
//        }
//        // Limit preview length if needed
//        if (text.length() > 500) {
//            return text.substring(0, 497) + "...";
//        }
//        return text;
//    }

    /**
     * Loads a detailed preview of the selected article.
     * Displays article metadata, content, references, and restriction status.
     *
     * @param articleId The ID of the article to preview
     */
    private void loadArticlePreview(Long articleId) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Fixed SQL query to avoid using 'groups' as a column alias
            String query = "SELECT ha.*, " +
                    "(SELECT GROUP_CONCAT(DISTINCT hag.name) " +
                    "FROM help_article_group_mapping hagm " +
                    "JOIN help_article_groups hag ON hagm.group_id = hag.id " +
                    "WHERE hagm.article_id = ha.id) as group_names " +  // Changed 'groups' to 'group_names'
                    "FROM help_articles ha " +
                    "WHERE ha.id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setLong(1, articleId);
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    StringBuilder preview = new StringBuilder();
                    preview.append("Title: ").append(rs.getString("title")).append("\n\n");
                    preview.append("Level: ").append(rs.getString("level")).append("\n\n");

                    String groups = rs.getString("group_names");  // Changed to match the new alias
                    preview.append("Groups: ").append(groups != null ? groups : "None").append("\n\n");

                    preview.append("Description:\n").append(rs.getString("description")).append("\n\n");
                    preview.append("Content:\n").append(rs.getString("body")).append("\n\n");

                    String references = rs.getString("reference_links");
                    if (references != null && !references.isEmpty()) {
                        preview.append("References:\n").append(references).append("\n\n");
                    }

                    boolean isRestricted = rs.getBoolean("is_restricted");
                    if (isRestricted) {
                        preview.append("\n[This article contains restricted information]\n");
                    }

                    articlePreviewArea.setText(preview.toString());
                } else {
                    articlePreviewArea.setText("Article not found.");
                }
            }
        } catch (SQLException e) {
            articlePreviewArea.setText("Error loading article preview: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads articles filtered by a specific group.
     * Supports the grouping requirement for help articles.
     *
     * @param group The name of the group to filter by
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

    private void updateCurrentUserInfo() {
        String query = "SELECT u.username, " +
                "CONCAT(COALESCE(u.preferred_first_name, u.first_name), ' ', u.last_name) AS full_name, " +
                "GROUP_CONCAT(r.name SEPARATOR ', ') AS roles " +
                "FROM users u " +
                "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
                "LEFT JOIN roles r ON ur.role_id = r.id " +
                "WHERE u.id = ? " +
                "GROUP BY u.id";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, this.userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    displaynameLabel.setText(rs.getString("full_name"));
                    displayroleLabel.setText(rs.getString("roles"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            displayConsole.setText("Error updating user info: " + e.getMessage());
        }
    }

    private void setupTableColumns() {
        usernameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().get(0)));
        fullnameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().get(1)));
        rolesColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().get(2)));
    }

    private void loadUsers() {
        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
        String query = "SELECT u.username, " +
                "CONCAT(COALESCE(u.preferred_first_name, u.first_name), ' ', u.last_name) AS full_name, " +
                "GROUP_CONCAT(r.name SEPARATOR ', ') AS roles " +
                "FROM users u " +
                "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
                "LEFT JOIN roles r ON ur.role_id = r.id " +
                "GROUP BY u.id";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                row.add(rs.getString("username"));
                row.add(rs.getString("full_name"));
                row.add(rs.getString("roles"));
                data.add(row);
            }

            userTableView.setItems(data);
        } catch (SQLException e) {
            e.printStackTrace();
            displayConsole.setText("Error loading users: " + e.getMessage());
        }
    }

    private String generateRandomStr() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @FXML
    private void generateInvitationCode() {
        String role = roleComboBox.getSelectionModel().getSelectedItem();
        if (role.isEmpty()) {
            displayConsole.setText("Please enter a role.");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            String checkRoleQuery = "SELECT id FROM roles WHERE name = ?";
            int roleId;
            try (PreparedStatement pstmt = conn.prepareStatement(checkRoleQuery)) {
                pstmt.setString(1, role);
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    displayConsole.setText("Role does not exist.");
                    return;
                }
                roleId = rs.getInt("id");
            }

            String invitationCode = generateRandomStr();
            LocalDateTime expiration = LocalDateTime.now().plusHours(24);

            String insertCodeQuery = "INSERT INTO invitation_codes (code, role_id, expiration) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertCodeQuery)) {
                pstmt.setString(1, invitationCode);
                pstmt.setInt(2, roleId);
                pstmt.setTimestamp(3, Timestamp.valueOf(expiration));
                pstmt.executeUpdate();
            }

            displayConsole.setText("Invitation code: " + invitationCode);

        } catch (SQLException e) {
            e.printStackTrace();
            displayConsole.setText("Error generating invitation code: " + e.getMessage());
        }
    }

    @FXML
    private void generatePassword() {
        String username = usernameTextField.getText().trim();
        if (username.isEmpty()) {
            displayConsole.setText("Please enter a username.");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            String updatePasswordQuery = "UPDATE users SET password = ?, is_one_time_password = TRUE, one_time_password_expiration = ? WHERE username = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updatePasswordQuery)) {
                String newPassword = generateRandomStr();
                pstmt.setString(1, newPassword);
                pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
                pstmt.setString(3, username);

                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    displayConsole.setText("New password for " + username + ": " + newPassword);
                } else {
                    displayConsole.setText("User not found.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            displayConsole.setText("Error generating password: " + e.getMessage());
        }
    }

    @FXML
    private void deleteUser() {
        String username = usernameTextField.getText().trim();
        if (username.isEmpty()) {
            displayConsole.setText("Please enter a username.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete User");
        alert.setHeaderText("Are you sure?");
        alert.setContentText("This will permanently delete the user " + username + ". Are you sure?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try (Connection conn = DatabaseUtil.getConnection()) {
                    String deleteUserQuery = "DELETE FROM users WHERE username = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteUserQuery)) {
                        pstmt.setString(1, username);
                        int affectedRows = pstmt.executeUpdate();
                        if (affectedRows > 0) {
                            displayConsole.setText("User " + username + " deleted successfully.");
                            loadUsers();
                        } else {
                            displayConsole.setText("User not found.");
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    displayConsole.setText("Error deleting user: " + e.getMessage());
                }
            } else {
                displayConsole.setText("User deletion canceled.");
            }
        });
    }

    @FXML
    private void addRole() {
        String username = usernameTextField.getText().trim();
        String role = roleComboBox.getSelectionModel().getSelectedItem();
        if (username.isEmpty() || role.isEmpty()) {
            displayConsole.setText("Please enter both username and role.");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            String checkUserQuery = "SELECT id FROM users WHERE username = ?";
            int userId;
            try (PreparedStatement pstmt = conn.prepareStatement(checkUserQuery)) {
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    displayConsole.setText("User not found.");
                    return;
                }
                userId = rs.getInt("id");
            }

            String checkRoleQuery = "SELECT id FROM roles WHERE name = ?";
            int roleId;
            try (PreparedStatement pstmt = conn.prepareStatement(checkRoleQuery)) {
                pstmt.setString(1, role);
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    displayConsole.setText("Role does not exist.");
                    return;
                }
                roleId = rs.getInt("id");
            }

            String addRoleQuery = "INSERT IGNORE INTO user_roles (user_id, role_id) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(addRoleQuery)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, roleId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    displayConsole.setText("Role " + role + " added to user " + username);
                    loadUsers();
                } else {
                    displayConsole.setText("User already has this role.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            displayConsole.setText("Error adding role: " + e.getMessage());
        }
    }

    @FXML
    private void removeRole() {
        String username = usernameTextField.getText().trim();
        String role = roleComboBox.getSelectionModel().getSelectedItem();
        if (username.isEmpty() || role.isEmpty()) {
            displayConsole.setText("Please enter both username and role.");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            String removeRoleQuery = "DELETE ur FROM user_roles ur " +
                    "JOIN users u ON ur.user_id = u.id " +
                    "JOIN roles r ON ur.role_id = r.id " +
                    "WHERE u.username = ? AND r.name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(removeRoleQuery)) {
                pstmt.setString(1, username);
                pstmt.setString(2, role);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    displayConsole.setText("Role " + role + " removed from user " + username);
                    loadUsers();
                } else {
                    displayConsole.setText("User does not have this role or user/role not found.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            displayConsole.setText("Error removing role: " + e.getMessage());
        }
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
            e.printStackTrace();
            displayConsole.setText("Error logging out: " + e.getMessage());
        }
    }

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
     * Updates an article in the database.
     * Handles both article content and group mappings.
     * Maintains article history by tracking last modified user.
     *
     * @param article The article with updated information
     */
    private void updateArticle(HelpArticle article) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Debug print
                System.out.println("Updating article with userId: " + userId);

                // Update the article
                String sql = "UPDATE help_articles SET " +
                        "title = ?, " +
                        "description = ?, " +
                        "level = ?, " +
                        "keywords = ?, " +
                        "body = ?, " +
                        "reference_links = ?, " +
                        "is_restricted = ?, " +
                        "public_title = ?, " +
                        "public_desc = ?, " +
                        "last_modified_by = ?, " +
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
                    pstmt.setInt(10, userId);  // Use the class's userId
                    pstmt.setLong(11, article.id);

                    int updatedRows = pstmt.executeUpdate();
                    if (updatedRows == 0) {
                        throw new SQLException("No rows were updated - article may not exist");
                    }
                }

                // Handle group mappings
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "DELETE FROM help_article_group_mapping WHERE article_id = ?")) {
                    pstmt.setLong(1, article.id);
                    pstmt.executeUpdate();
                }

                if (article.groups != null && !article.groups.isEmpty()) {
                    String[] groupNames = article.groups.split(",");
                    for (String groupName : groupNames) {
                        groupName = groupName.trim();
                        if (!groupName.isEmpty()) {
                            int groupId = ensureGroupExists(conn, groupName);
                            mapArticleToGroup(conn, article.id, groupId);
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

    private void showInformationAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Creates a dialog for editing help articles.
     * Supports editing of all article fields including:
     * - Title and description (both public and restricted versions)
     * - Content level (beginner to expert)
     * - Group assignments
     * - Keywords for search
     * - Content body
     * - Reference links
     * - Restriction status
     *
     * @param article The article to edit
     * @return Dialog configured for article editing
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
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Admin Rights Management");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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

    public void manageGroupAccess(ActionEvent actionEvent) {
    }
}