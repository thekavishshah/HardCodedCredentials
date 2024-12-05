// ./src/main/java/local/jfx360/controllers/ResetAccount.java
package local.jfx360.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import local.jfx360.utils.DatabaseUtil;

/**
 * <p> ResetAccount Controller Class. </p>
 *
 * <p> Description: This class handles the functionality for resetting a user's account password.
 * It allows users to enter a new password, validates the input, updates the database,
 * and redirects back to the login screen upon successful password reset. </p>
 *
 */

public class ResetAccount {

    @FXML
    private TextField newPasswordTextField;

    @FXML
    private TextField reenterPasswordTextField;

    @FXML
    private Label warningmessageTextField;

    @FXML
    private Button confirmButton;

    private int userId; // The ID of the user resetting their account

    /**
     * Method to set the user ID passed from the login screen.
     *
     * @param userId the ID of the user whose password is being reset
     */
    public void setUserId(int userId) {
        this.userId = userId;
    }

    /**
     * Method to handle confirming the new password.
     */
    @FXML
    private void handleConfirmButtonAction() {
        String newPassword = newPasswordTextField.getText();
        String reenterPassword = reenterPasswordTextField.getText();

        // Validate that both password fields are not empty
        if (newPassword.isEmpty() || reenterPassword.isEmpty()) {
            warningmessageTextField.setText("All fields must be filled out.");
            return;
        }

        // Validate that both entered passwords match
        if (!newPassword.equals(reenterPassword)) {
            warningmessageTextField.setText("Passwords do not match!");
            return;
        }

        // Update the password in the database and reset the one-time password flag
        try (Connection conn = DatabaseUtil.getConnection()) {
            String updatePasswordQuery = "UPDATE users SET password = ?, is_one_time_password = 0 WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updatePasswordQuery)) {
                pstmt.setString(1, newPassword);
                pstmt.setInt(2, userId);

                pstmt.executeUpdate();

                // Redirect back to the login screen after successfully resetting the password
                loadLoginScene();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Database error: " + e.getMessage());
        }
    }

    /**
     * Method to load the login scene after resetting the password.
     */
    private void loadLoginScene() {
        try {
            Parent loginRoot = FXMLLoader.load(getClass().getResource("/local/jfx360/fxml/Login.fxml"));
            Scene loginScene = new Scene(loginRoot);

            Stage currentStage = (Stage) confirmButton.getScene().getWindow();
            currentStage.setScene(loginScene);
            currentStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Error loading the login screen.");
        }
    }
}
