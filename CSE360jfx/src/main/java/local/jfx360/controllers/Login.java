// ./src/main/java/local/jfx360/controllers/Login.java
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
import java.time.LocalDateTime;

import local.jfx360.utils.DatabaseUtil;
import local.jfx360.utils.PasswordUtils;

/**
 * Login Controller Class.
 *
 * Handles the login functionality for the application.
 */
public class Login {

    @FXML
    private TextField usernameTextField;

    @FXML
    private PasswordField passwordPasswordField;

    @FXML
    private TextField invitationCodeTextField;

    @FXML
    private Label warningmessageTextField;

    @FXML
    private Button loginButton;

    @FXML
    private Button createNewAccountButton;

    /**
     * Handles the login button action. Validates user credentials and redirects to appropriate scenes.
     */
    @FXML
    public void handleLoginButtonAction() {
        String username = usernameTextField.getText();
        String password = passwordPasswordField.getText();

        // Validate that the username and password fields are not empty
        if (username.isEmpty() || password.isEmpty()) {
            warningmessageTextField.setText("Username and password must not be empty.");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            // Query to get the stored hashed password
            String loginQuery = "SELECT id, password, is_one_time_password, one_time_password_expiration FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(loginQuery)) {
                stmt.setString(1, username);

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int userId = rs.getInt("id");
                    String storedPasswordHash = rs.getString("password");
                    boolean isOneTimePassword = rs.getBoolean("is_one_time_password");

                    // Verify the password using PasswordUtils
                    if (PasswordUtils.verifyPassword(password,storedPasswordHash)) {
                        // If the password is a one-time password, check if it is still valid
                        if (isOneTimePassword) {
                            LocalDateTime expiration = rs.getTimestamp("one_time_password_expiration").toLocalDateTime();
                            if (expiration.isAfter(LocalDateTime.now())) {
                                // Redirect to ResetAccount scene to allow password reset
                                loadResetAccountScene(userId);
                            } else {
                                // The one-time password has expired, show an error message
                                warningmessageTextField.setText("The one-time password has expired.");
                            }
                        } else {
                            // Regular login flow, redirect to SelectRole scene
                            loadSelectRoleScene(userId);
                        }
                    } else {
                        // Incorrect password
                        warningmessageTextField.setText("Incorrect username or password.");
                    }
                } else {
                    // Username not found
                    warningmessageTextField.setText("Incorrect username or password.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Database error: " + e.getMessage());
        }
    }

    // Other methods remain unchanged...

    // Loads the SelectRole scene after successful login.
    private void loadSelectRoleScene(int userId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/local/jfx360/fxml/SelectRole.fxml"));
            Parent selectRoleRoot = loader.load();

            // Pass the userId to the next controller
            SelectRole controller = loader.getController();
            controller.setUserId(userId);

            Scene selectRoleScene = new Scene(selectRoleRoot);
            Stage currentStage = (Stage) loginButton.getScene().getWindow();
            currentStage.setScene(selectRoleScene);
            currentStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Error loading the SelectRole scene.");
        }
    }

    // Loads the ResetAccount scene for users with a one-time password.
    private void loadResetAccountScene(int userId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/local/jfx360/fxml/ResetAccount.fxml"));
            Parent resetAccountRoot = loader.load();

            // Pass the userId to the ResetAccount controller
            ResetAccount controller = loader.getController();
            controller.setUserId(userId);

            Scene resetAccountScene = new Scene(resetAccountRoot);
            Stage currentStage = (Stage) loginButton.getScene().getWindow();
            currentStage.setScene(resetAccountScene);
            currentStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Error loading the ResetAccount scene.");
        }
    }

/**
     * Handles the creation of a new account using an invitation code.
     */
    @FXML
    private void handleCreateNewAccountButton() {
        String invitationCode = invitationCodeTextField.getText().trim();

        if (invitationCode.isEmpty()) {
            warningmessageTextField.setText("Please enter an invitation code.");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            String checkCodeQuery = "SELECT id, role_id, expiration, is_used FROM invitation_codes WHERE code = ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkCodeQuery)) {
                stmt.setString(1, invitationCode);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int codeId = rs.getInt("id");
                    int roleId = rs.getInt("role_id");
                    LocalDateTime expiration = rs.getTimestamp("expiration").toLocalDateTime();
                    boolean isUsed = rs.getBoolean("is_used");

                    if (isUsed) {
                        warningmessageTextField.setText("This invitation code has already been used.");
                    } else if (expiration.isBefore(LocalDateTime.now())) {
                        warningmessageTextField.setText("This invitation code has expired.");
                    } else {
                        // Code is valid, redirect to create account scene
                        loadCreateAccountScene(codeId, roleId);
                    }
                } else {
                    warningmessageTextField.setText("Invalid invitation code.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Database error: " + e.getMessage());
        }
    }

    /**
     * Loads the CreateAccount scene after validating the invitation code.
     *
     * @param invitationCodeId The ID of the valid invitation code
     * @param roleId The ID of the role associated with the invitation code
     */
    private void loadCreateAccountScene(int invitationCodeId, int roleId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/local/jfx360/fxml/CreateAccount.fxml"));
            Parent createAccountRoot = loader.load();

            // Pass the invitationCodeId and roleId to the next controller
            CreateAccount controller = loader.getController();
            controller.setInvitationCodeInfo(invitationCodeId, roleId);

            Scene createAccountScene = new Scene(createAccountRoot);
            Stage currentStage = (Stage) createNewAccountButton.getScene().getWindow();
            currentStage.setScene(createAccountScene);
            currentStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Error loading the Create Account scene.");
        }
    }
}
