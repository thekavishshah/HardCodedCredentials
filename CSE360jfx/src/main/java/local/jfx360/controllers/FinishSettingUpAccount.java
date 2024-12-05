// ./src/main/java/local/jfx360/controllers/FinishSettingUpAccount.java
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
 * <p> FinishSettingUpAccount Controller Class. </p>
 *
 * <p> Description: This class handles the functionality for completing the user account setup.
 * It allows users to input additional details such as first name, last name, middle name,
 * preferred first name, and email address. The class validates the input, updates the user
 * information in the database, and redirects to the login screen upon successful completion. </p>
 *
 */
public class FinishSettingUpAccount {

    @FXML
    private TextField firstnameTextField;

    @FXML
    private TextField lastnameTextField;

    @FXML
    private TextField middlenameTextField;

    @FXML
    private TextField preferredfirstnameTextField;

    @FXML
    private TextField youremailTextField;

    @FXML
    private Label warningmessageTextField;

    @FXML
    private Button confirmButton;

    private int userId; // Variable to hold user ID

    /**
     * Sets the user ID for the account being set up.
     *
     * @param userId The ID of the user
     */
    public void setUserId(int userId) {
        this.userId = userId;
    }

    /**
     * Handles the action when the confirm button is pressed. This method validates user input,
     * updates the user's information in the database, and redirects to the login screen.
     */
    @FXML
    private void handleConfirmButtonAction() {
        String firstName = firstnameTextField.getText();
        String lastName = lastnameTextField.getText();
        String preferredFirstName = preferredfirstnameTextField.getText();
        String email = youremailTextField.getText();

        // Validate that required fields are not empty
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            warningmessageTextField.setText("First name, last name, and email must be filled out.");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {
            // Insert user details into the users table using the userId
            String updateUserDetailsQuery = "UPDATE users SET first_name = ?, middle_name = ?, " +
                    "last_name = ?, preferred_first_name = ?, email_address = ? WHERE id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateUserDetailsQuery)) {
                updateStmt.setString(1, firstName);
                updateStmt.setString(2, firstName);
                updateStmt.setString(3, lastName);

                // If preferredFirstName is empty, insert NULL into the database
                if (preferredFirstName.isEmpty()) {
                    updateStmt.setNull(4, java.sql.Types.VARCHAR);
                } else {
                    updateStmt.setString(4, preferredFirstName);
                }

                updateStmt.setString(5, email);
                updateStmt.setInt(6, userId); // Use the user ID to update the correct record

                updateStmt.executeUpdate();
            }

            // Redirect to Login.fxml
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
        } catch (SQLException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Database error: " + e.getMessage());
        }
    }
}
