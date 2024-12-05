// ./src/main/java/local/jfx360/controllers/CreateAccount.java
package local.jfx360.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

import local.jfx360.utils.DatabaseUtil;
import local.jfx360.utils.PasswordUtils;

/**
 * CreateAccount Controller Class
 *
 * Handles the functionality for creating new user accounts in the system.
 * Key responsibilities include:
 * - Validating user input for username and password
 * - Generating unique user IDs
 * - Processing invitation codes for role assignment
 * - Creating database records for new users
 * - Managing the initial role assignment process
 * - Handling the first-user scenario (automatic admin role assignment)
 */
public class CreateAccount {
    @FXML
    private TextField usernameTextField;
    @FXML
    private PasswordField passwordPasswordField;
    @FXML
    private PasswordField reenterpasswordPasswordField;
    @FXML
    private Label warningmessageTextField;
    @FXML
    private Button confirmButton;

    private int userId; // Store the new user's ID
    private int roleId = 1;
    private int invitationCodeId = 0;

    /**
     * Handles the account creation process when the confirm button is pressed.
     * Process flow:
     * 1. Validates user input (username, password)
     * 2. Checks for existing username
     * 3. Generates unique user ID
     * 4. Creates user record in database
     * 5. Handles role assignment based on:
     *    - Invitation code if provided
     *    - First user check (assigns admin role)
     * 6. Redirects to account setup completion
     */
    @FXML
    private void handleConfirmButtonAction() {
        String username = usernameTextField.getText();
        String password = passwordPasswordField.getText();
        String reenterPassword = reenterpasswordPasswordField.getText();

        // Validate inputs
        if (username.isEmpty() || password.isEmpty() || reenterPassword.isEmpty()) {
            warningmessageTextField.setText("All fields must be filled out.");
            return;
        }

        if (!password.equals(reenterPassword)) {
            warningmessageTextField.setText("Passwords do not match!");
            return;
        }

        // Database interaction
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Check if the username is already taken
            String checkUserQuery = "SELECT id FROM users WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkUserQuery)) {
                checkStmt.setString(1, username);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    warningmessageTextField.setText("Username already exists!");
                    return;
                }
            }

            // Generate a unique user ID first
            int newUserId = generateUniqueUserId(conn);

            // Hash the password using PasswordUtils
            String hashedPassword = PasswordUtils.hashPassword(password);

            // Insert the new user into the `users` table
            String insertUserQuery = "INSERT INTO users (id, email_address, username, password, first_name, last_name) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertUserQuery)) {
                insertStmt.setInt(1, newUserId);
                insertStmt.setString(2, username + "@example.com"); // Placeholder email for now
                insertStmt.setString(3, username);
                insertStmt.setString(4, hashedPassword); // Store hashed password
                insertStmt.setString(5, "FirstName"); // Placeholder values for first name
                insertStmt.setString(6, "LastName");  // Placeholder values for last name

                int affectedRows = insertStmt.executeUpdate();
                if (affectedRows == 0) {
                    warningmessageTextField.setText("Failed to create user.");
                    return;
                }

                userId = newUserId; // Store the new user's ID

                if (this.invitationCodeId != 0) {
                    // Assign the role based on the invitation code
                    String assignRoleQuery = "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)";
                    try (PreparedStatement assignRoleStmt = conn.prepareStatement(assignRoleQuery)) {
                        assignRoleStmt.setInt(1, userId);
                        assignRoleStmt.setInt(2, roleId); // Use the roleId from the invitation code
                        assignRoleStmt.executeUpdate();
                    }

                    // Mark the invitation code as used
                    String updateCodeQuery = "UPDATE invitation_codes SET is_used = TRUE WHERE id = ?";
                    try (PreparedStatement updateCodeStmt = conn.prepareStatement(updateCodeQuery)) {
                        updateCodeStmt.setInt(1, invitationCodeId); // Use the codeId from the invitation code
                        updateCodeStmt.executeUpdate();
                    }
                } else {
                    // Check if this is the first user in the `users` table
                    String countUsersQuery = "SELECT COUNT(*) AS user_count FROM users";
                    try (PreparedStatement countStmt = conn.prepareStatement(countUsersQuery);
                         ResultSet countRs = countStmt.executeQuery()) {
                        if (countRs.next() && countRs.getInt("user_count") == 1) {
                            // Assign the Admin role to the first user
                            String assignAdminRoleQuery = "INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = 'Admin'";
                            try (PreparedStatement assignRoleStmt = conn.prepareStatement(assignAdminRoleQuery)) {
                                assignRoleStmt.setInt(1, userId);
                                assignRoleStmt.executeUpdate();
                            }
                        }
                    }
                }
            }

            // Redirect to FinishSettingUpAccount.fxml, passing the user ID
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/local/jfx360/fxml/FinishSettingUpAccount.fxml"));
                Parent finishSetupRoot = loader.load();
                FinishSettingUpAccount controller = loader.getController();
                controller.setUserId(userId); // Pass the user ID to the next scene
                Scene finishSetupScene = new Scene(finishSetupRoot);

                Stage currentStage = (Stage) confirmButton.getScene().getWindow();
                currentStage.setScene(finishSetupScene);
                currentStage.show();
            } catch (IOException e) {
                e.printStackTrace();
                warningmessageTextField.setText("Error loading the next screen.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Database error: " + e.getMessage());
        }
    }

    /**
     * Sets the invitation code information for the account creation process.
     *
     * @param invitationCodeId The ID of the invitation code
     * @param roleId The ID of the role associated with the invitation code
     */
    public void setInvitationCodeInfo(int invitationCodeId, int roleId) {
        this.invitationCodeId = invitationCodeId;
        this.roleId = roleId;
    }

    /**
     * Generates a unique user ID that doesn't exist in the database.
     *
     * @param conn The database connection
     * @return A unique user ID
     * @throws SQLException if a database access error occurs
     */
    private int generateUniqueUserId(Connection conn) throws SQLException {
        Random random = new Random();
        int newId;
        boolean idExists;

        do {
            // Generate a random number between 1 and 1,000,000
            newId = random.nextInt(1000000) + 1;

            // Check if this ID already exists in the database
            String query = "SELECT COUNT(*) FROM users WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, newId);
                ResultSet rs = stmt.executeQuery();
                rs.next();
                idExists = rs.getInt(1) > 0;
            }
        } while (idExists);

        return newId;
    }
}
