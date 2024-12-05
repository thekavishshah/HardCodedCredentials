// HelpSystemHelper.java
package local.jfx360.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import local.jfx360.utils.DatabaseUtil;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static local.jfx360.utils.SimpleEncryption.decrypt;
import static local.jfx360.utils.SimpleEncryption.encrypt;

import static local.jfx360.utils.DatabaseUtil.getConnection;

/**
 * <p> HelpSystemHelper Controller Class. </p>
 *
 * <p> Description: This class handles the functionality for help system helper (a.k.a article). </p>
 *
 */

public abstract class HelpSystemHelper {
    public static class HelpArticle implements Serializable {
        private static final long serialVersionUID = 1L;  // Add a serialVersionUID

        // Make all fields serializable
        Long id;
        String title;
        String description;
        String level;
        String keywords;
        String body;
        String referenceLinks;
        boolean isRestricted;
        String publicTitle;
        String publicDesc;
        String groups;
        Timestamp createdAt;
        Timestamp updatedAt;
        int createdBy;
        int lastModifiedBy;

        // Add default constructor
        public HelpArticle() {}

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getLevel() { return level; }
        public String getGroups() { return groups; }
        public String getAuthor() throws SQLException { return getAccount(); }
        // Add other getters as needed for TableView

        /**
         * - Converts the createdBy ID into the author's username
         * - Currently Does not work
         * @return
         * @throws SQLException
         */
        protected String getAccount() throws SQLException {
            String fullName = "";
            String query = "SELECT CONCAT_WS(' ', u.first_name, u.middle_name, u.last_name) AS full_name " +
                    "FROM help_articles ha " +
                    "JOIN users u ON ha.created_by = u.id " +
                    "WHERE ha.id = ?";

            try (Connection conn = DatabaseUtil.getConnection()) {
                PreparedStatement ps = conn.prepareStatement(query);
                ps.setLong(1, id);  // Using the article id

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    fullName = rs.getString("full_name").trim();
                } else {
                    return "Unknown Author";
                }
            } catch (SQLException e) {
                System.out.println("SQL Error: " + e.getMessage());
                e.printStackTrace();
                return "Error Getting Author";
            }

            return fullName;
        }
    }

    /**
     * Initializes the HelpSystemHelper controller.
     */
    protected void createHelpArticle(TableView<HelpArticle> tableView, int userId) {
        Dialog<HelpArticle> dialog = new Dialog<>();
        dialog.setTitle("Create Help Article");

        // Create form fields
        TextField titleField = new TextField();
        TextArea descriptionArea = new TextArea();
        ComboBox<String> levelCombo = new ComboBox<>(
                FXCollections.observableArrayList("beginner", "intermediate", "advanced", "expert")
        );
        TextField groupsField = new TextField();
        TextField keywordsField = new TextField();
        TextArea bodyArea = new TextArea();
        TextArea referenceLinksArea = new TextArea();
        CheckBox restrictedCheck = new CheckBox("Contains Sensitive Information");
        TextField publicTitleField = new TextField();
        TextArea publicDescArea = new TextArea();

        // Layout the form
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.addRow(0, new Label("Title:"), titleField);
        grid.addRow(1, new Label("Description:"), descriptionArea);
        grid.addRow(2, new Label("Level:"), levelCombo);
        grid.addRow(3, new Label("Groups (comma separated):"), groupsField);
        grid.addRow(4, new Label("Keywords:"), keywordsField);
        grid.addRow(5, new Label("Body:"), bodyArea);
        grid.addRow(6, new Label("Reference Links:"), referenceLinksArea);
        grid.addRow(7, restrictedCheck);
        grid.addRow(8, new Label("Public Title:"), publicTitleField);
        grid.addRow(9, new Label("Public Description:"), publicDescArea);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Enable/disable public fields based on restricted checkbox
        restrictedCheck.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            publicTitleField.setDisable(!isSelected);
            publicDescArea.setDisable(!isSelected);
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                HelpArticle article = new HelpArticle();
                article.id = generateUniqueId();
                article.title = titleField.getText();
                article.description = descriptionArea.getText();
                article.level = levelCombo.getValue();
                article.keywords = keywordsField.getText();
                article.body = bodyArea.getText();
                article.referenceLinks = referenceLinksArea.getText();
                article.isRestricted = restrictedCheck.isSelected();
                article.publicTitle = restrictedCheck.isSelected() ? publicTitleField.getText() : null;
                article.publicDesc = restrictedCheck.isSelected() ? publicDescArea.getText() : null;
                article.groups = groupsField.getText();
                article.createdBy = userId;
                article.lastModifiedBy = userId;
                return article;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(article -> {
            saveArticle(article);
            loadArticles(tableView);
        });
    }

    /**
     * Backup articles by group
     *
     * @param group the group that we want to backup
     */
    protected void backupArticles(String group) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Backup File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Backup Files", "*.backup")
        );

        File file = fileChooser.showSaveDialog(new Stage());
        if (file != null) {
            try (Connection conn = getConnection()) {
                // Build the query based on whether a group filter is applied
                StringBuilder queryBuilder = new StringBuilder(
                        "SELECT ha.*, " +
                                "(SELECT GROUP_CONCAT(hag.name) " +
                                "FROM help_article_group_mapping hagm " +
                                "JOIN help_article_groups hag ON hagm.group_id = hag.id " +
                                "WHERE hagm.article_id = ha.id) as group_names " +
                                "FROM help_articles ha"
                );

                if (group != null && !group.isEmpty()) {
                    queryBuilder.append(" WHERE EXISTS (SELECT 1 FROM help_article_group_mapping hagm " +
                            "JOIN help_article_groups hag ON hagm.group_id = hag.id " +
                            "WHERE hagm.article_id = ha.id AND hag.name = ?)");
                }

                try (PreparedStatement pstmt = conn.prepareStatement(queryBuilder.toString())) {
                    if (group != null && !group.isEmpty()) {
                        pstmt.setString(1, group);
                    }

                    ResultSet rs = pstmt.executeQuery();
                    List<HelpArticle> articles = new ArrayList<>();

                    while (rs.next()) {
                        HelpArticle article = new HelpArticle();
                        article.id = rs.getLong("id");
                        article.title = rs.getString("title");
                        article.description = rs.getString("description");
                        article.level = rs.getString("level");
                        article.keywords = rs.getString("keywords");
                        article.body = rs.getString("body");
                        article.referenceLinks = rs.getString("reference_links");
                        article.isRestricted = rs.getBoolean("is_restricted");
                        article.publicTitle = rs.getString("public_title");
                        article.publicDesc = rs.getString("public_desc");
                        article.groups = rs.getString("group_names");
                        article.createdAt = rs.getTimestamp("created_at");
                        article.updatedAt = rs.getTimestamp("updated_at");
                        article.createdBy = rs.getInt("created_by");
                        article.lastModifiedBy = rs.getInt("last_modified_by");

                        articles.add(article);
                    }

                    // Write the backup file
                    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
                        out.writeObject(articles);
                        showInformationAlert("Backup Success", "Successfully backed up " + articles.size() + " articles.");
                    }
                }
            } catch (Exception e) {
                showError("Backup Error", "Failed to backup articles: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Printout error/alert into UI
     *
     */
    private void showInformationAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    protected void restoreArticles(boolean merge, TableView<HelpArticle> tableView) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Backup File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Backup Files", "*.backup")
        );

        File file = fileChooser.showOpenDialog(new Stage());
        if (file != null) {
            try (Connection conn = getConnection()) {
                // Read the backup file first before making any database changes
                List<HelpArticle> articles;
                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                    articles = (List<HelpArticle>) in.readObject();
                }

                // Set a longer timeout for the transaction
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET SESSION innodb_lock_wait_timeout=150");
                }

                conn.setAutoCommit(false);
                try {
                    if (!merge) {
                        // Delete in correct order to avoid foreign key constraints
                        try (Statement stmt = conn.createStatement()) {
                            // Disable foreign key checks temporarily
                            stmt.execute("SET FOREIGN_KEY_CHECKS=0");

                            // Delete in batches to avoid long locks
                            stmt.execute("DELETE FROM help_article_group_mapping");
                            stmt.execute("DELETE FROM help_articles");

                            // Re-enable foreign key checks
                            stmt.execute("SET FOREIGN_KEY_CHECKS=1");
                        }
                    }

                    // Batch insert the articles
                    int batchSize = 50;
                    int count = 0;
                    int restoredCount = 0;

                    String insertArticle = "INSERT INTO help_articles (id, title, description, level, keywords, " +
                            "body, reference_links, is_restricted, public_title, public_desc, " +
                            "created_by, last_modified_by, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

                    try (PreparedStatement pstmt = conn.prepareStatement(insertArticle)) {
                        for (HelpArticle article : articles) {
                            if (merge) {
                                // Check if article exists
                                try (PreparedStatement checkStmt = conn.prepareStatement(
                                        "SELECT id FROM help_articles WHERE id = ?")) {
                                    checkStmt.setLong(1, article.id);
                                    if (checkStmt.executeQuery().next()) {
                                        continue; // Skip existing articles in merge mode
                                    }
                                }
                            }

                            pstmt.setLong(1, article.id);
                            pstmt.setString(2, article.title);
                            pstmt.setString(3, article.description);
                            pstmt.setString(4, article.level);
                            pstmt.setString(5, article.keywords);
                            pstmt.setString(6, article.body);
                            pstmt.setString(7, article.referenceLinks);
                            pstmt.setBoolean(8, article.isRestricted);
                            pstmt.setString(9, article.publicTitle);
                            pstmt.setString(10, article.publicDesc);
                            pstmt.setInt(11, article.createdBy);
                            pstmt.setInt(12, article.lastModifiedBy);
                            pstmt.addBatch();

                            if (++count % batchSize == 0) {
                                pstmt.executeBatch();
                            }
                            restoredCount++;
                        }
                        // Execute any remaining batch
                        if (count % batchSize != 0) {
                            pstmt.executeBatch();
                        }
                    }

                    // Handle group mappings in batches
                    if (restoredCount > 0) {
                        String insertMapping = "INSERT INTO help_article_group_mapping (article_id, group_id) " +
                                "SELECT ?, ? FROM dual WHERE NOT EXISTS (" +
                                "SELECT 1 FROM help_article_group_mapping " +
                                "WHERE article_id = ? AND group_id = ?)";
                        try (PreparedStatement pstmt = conn.prepareStatement(insertMapping)) {
                            count = 0;
                            for (HelpArticle article : articles) {
                                // Skip group mappings for articles that weren't restored in merge mode
                                if (merge) {
                                    try (PreparedStatement checkStmt = conn.prepareStatement(
                                            "SELECT id FROM help_articles WHERE id = ?")) {
                                        checkStmt.setLong(1, article.id);
                                        if (!checkStmt.executeQuery().next()) {
                                            continue;
                                        }
                                    }
                                }

                                if (article.groups != null && !article.groups.isEmpty()) {
                                    String[] groupNames = article.groups.split(",");
                                    for (String groupName : groupNames) {
                                        groupName = groupName.trim();
                                        if (!groupName.isEmpty()) {
                                            int groupId = ensureGroupExists(conn, groupName);
                                            pstmt.setLong(1, article.id);
                                            pstmt.setInt(2, groupId);
                                            pstmt.setLong(3, article.id);
                                            pstmt.setInt(4, groupId);
                                            pstmt.addBatch();

                                            if (++count % batchSize == 0) {
                                                pstmt.executeBatch();
                                            }
                                        }
                                    }
                                }
                            }
                            // Execute any remaining batch
                            if (count % batchSize != 0) {
                                pstmt.executeBatch();
                            }
                        }
                    }

                    conn.commit();

                    // Reload the table after successful restoration
                    if (tableView != null) {
                        int finalRestoredCount = restoredCount;
                        Platform.runLater(() -> {
                            loadArticles(tableView);
                            showInformationAlert("Restore Success",
                                    String.format("Successfully restored %d articles%s.",
                                            finalRestoredCount,
                                            merge ? " (skipped existing articles)" : ""));
                        });
                    }

                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    // Reset the timeout to default
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("SET SESSION innodb_lock_wait_timeout=50");
                    }
                    conn.setAutoCommit(true);
                }

            } catch (Exception e) {
                Platform.runLater(() ->
                        showError("Restore Error", "Failed to restore articles: " + e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    /**
     * Save article
     *
     */
    private void saveArticle(HelpArticle article) {
        try (Connection conn = getConnection()) {
            // Insert the article
            String sql = "INSERT INTO help_articles (id, title, description, level, keywords, " +
                    "body, reference_links, is_restricted, public_title, public_desc, " +
                    "created_by, last_modified_by) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, article.id);
                pstmt.setString(2, article.title);
                pstmt.setString(3, article.description);
                pstmt.setString(4, article.level);
                pstmt.setString(5, article.keywords);
                pstmt.setString(6,
                        article.isRestricted ? encrypt(article.body) : article.body);
                pstmt.setString(7, article.referenceLinks);
                pstmt.setBoolean(8, article.isRestricted);
                pstmt.setString(9, article.publicTitle);
                pstmt.setString(10, article.publicDesc);
                pstmt.setInt(11, article.createdBy);
                pstmt.setInt(12, article.lastModifiedBy);

                pstmt.executeUpdate();
            }

            // Handle groups
            if (article.groups != null && !article.groups.isEmpty()) {
                String[] groupNames = article.groups.split(",");
                for (String groupName : groupNames) {
                    groupName = groupName.trim();
                    // Ensure the group exists
                    int groupId = ensureGroupExists(conn, groupName);
                    // Map the article to the group
                    mapArticleToGroup(conn, article.id, groupId);
                }
            }

            // Query to update help_article_group_roles
            String queryAdminRights = "SELECT userid FROM AdminRights"; // Step 1: Query AdminRights
            String queryGroupMapping = "SELECT group_id FROM help_article_group_mapping WHERE article_id = ?"; // Step 2: Query group mapping

            try (PreparedStatement adminStmt = conn.prepareStatement(queryAdminRights);
                 ResultSet adminRs = adminStmt.executeQuery()) {

                // Fetch all user IDs from AdminRights
                List<Integer> userIds = new ArrayList<>();
                while (adminRs.next()) {
                    userIds.add(adminRs.getInt("userid"));
                }

                // Fetch group IDs associated with the article
                try (PreparedStatement groupStmt = conn.prepareStatement(queryGroupMapping)) {
                    groupStmt.setLong(1, article.id); // Set the article ID to find associated groups

                    try (ResultSet groupRs = groupStmt.executeQuery()) {
                        List<Integer> groupIds = new ArrayList<>();
                        while (groupRs.next()) {
                            groupIds.add(groupRs.getInt("group_id"));
                        }

                        // Insert into help_article_group_roles
                        String insertRoleSql = "INSERT INTO help_article_group_roles (group_id, user_id) VALUES (?, ?)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertRoleSql)) {
                            for (int groupId : groupIds) {
                                for (int userId : userIds) {
                                    insertStmt.setInt(1, groupId); // Insert group ID
                                    insertStmt.setInt(2, userId); // Insert user ID (role ID)
                                    insertStmt.addBatch(); // Add to batch for efficiency
                                }
                            }
                            insertStmt.executeBatch(); // Execute batch insert
                        }
                    }
                }
            }

        } catch (SQLException e) {
            showError("Save Error", e.getMessage());
        }
    }



    /**
     * Save article
     *
     */
    int ensureGroupExists(Connection conn, String groupName) throws SQLException {
        // First try to find the existing group
        String selectSql = "SELECT id FROM help_article_groups WHERE name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
            pstmt.setString(1, groupName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }

        // If not found, create new group
        String insertSql = "INSERT INTO help_article_groups (name) VALUES (?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, groupName);
            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
            throw new SQLException("Failed to create group: " + groupName);
        }
    }

    void mapArticleToGroup(Connection conn, long articleId, int groupId) throws SQLException {
        String sql = "INSERT IGNORE INTO help_article_group_mapping (article_id, group_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, articleId);
            pstmt.setInt(2, groupId);
            pstmt.executeUpdate();
        }
    }

    public abstract void setUserId(int userId);

    /**
     * Load article to the ui from the information in tableview
     *
     */
    protected void loadArticles(TableView<HelpArticle> tableView) {
        try (Connection conn = getConnection()) {
            String query = "SELECT ha.*, GROUP_CONCAT(hag.name) as group_names " +
                    "FROM help_articles ha " +
                    "LEFT JOIN help_article_group_mapping hagm ON ha.id = hagm.article_id " +
                    "LEFT JOIN help_article_groups hag ON hagm.group_id = hag.id " +
                    "GROUP BY ha.id";

            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                ResultSet rs = pstmt.executeQuery();
                ObservableList<HelpArticle> articles = FXCollections.observableArrayList();

                while (rs.next()) {
                    HelpArticle article = new HelpArticle();
                    article.id = rs.getLong("id");
                    article.title = rs.getString("title");
                    article.description = rs.getString("description");
                    article.level = rs.getString("level");
                    article.groups = rs.getString("group_names");
                    article.createdAt = rs.getTimestamp("created_at");
                    article.updatedAt = rs.getTimestamp("updated_at");
                    articles.add(article);
                }

                tableView.setItems(articles);
            }
        } catch (SQLException e) {
            showError("Load Error", e.getMessage());
        }
    }

    /**
     * Load groups in the combobox button for displaying group for other operation
     *
     */
    protected void loadGroups(ComboBox<String> groupFilterComboBox) {
        try (Connection conn = getConnection()) {
            String query = "SELECT name FROM help_article_groups ORDER BY name";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                ResultSet rs = pstmt.executeQuery();
                ObservableList<String> groups = FXCollections.observableArrayList();
                while (rs.next()) {
                    groups.add(rs.getString("name"));
                }
                groupFilterComboBox.setItems(groups);
            }
        } catch (SQLException e) {
            showError("Load Groups Error", e.getMessage());
        }
    }

    /**
     * Generate id for articles using times
     *
     */
    private Long generateUniqueId() {
        return System.currentTimeMillis();
    }

    /**
     * Printout error/alert into UI
     *
     */
    void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
