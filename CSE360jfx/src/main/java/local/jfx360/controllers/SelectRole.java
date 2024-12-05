// ./s
package local.jfx360.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import local.jfx360.utils.DatabaseUtil;

/**
 * <p> SelectRole Controller Class. </p>
 *
 * <p> Description: This class handles the role selection functionality for the application.
 * It dynamically loads user roles from the database, creates buttons for each role,
 * and redirects the user to the appropriate main page based on the selected role. </p>
 *
 * <p> Copyright: Copyright (c) 2024 </p>
 *
 * @author [Your Name]
 *
 * @version 1.00 2024-[MM-DD] Initial implementation
 */

public class SelectRole {

    @FXML
    private VBox rolesVBox; // VBox to dynamically hold buttons
    @FXML
    private Label warningmessageTextField;

    public int userId;

    public void setUserId(int userId) {
        this.userId = userId;
        loadUserRoles();
    }

    /**
     * Loads user roles from the database and dynamically creates buttons for each role.
     */
    private void loadUserRoles() {
        try (Connection conn = DatabaseUtil.getConnection()) {
            // Query to get the roles associated with the userId
            String query = "SELECT r.name FROM roles r "
                    + "JOIN user_roles ur ON r.id = ur.role_id "
                    + "WHERE ur.user_id = ? ORDER BY "
                    + "CASE r.name "
                    + "  WHEN 'Admin' THEN 1 "
                    + "  WHEN 'Instructor' THEN 2 "
                    + "  WHEN 'Student' THEN 3 "
                    + "  ELSE 4 END";

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();

                List<String> roles = new ArrayList<>();

                // Add all roles to the list
                while (rs.next()) {
                    roles.add(rs.getString("name"));
                }

                // Dynamically add buttons based on user roles
                for (int i = 0; i < roles.size(); i++) {
                    String role = roles.get(i);
                    Button roleButton = new Button(role);
                    roleButton.setPrefHeight(43);
                    roleButton.setPrefWidth(262);

                    // Set action based on the role
                    switch (role) {
                        case "Admin":
                            roleButton.setOnAction(e -> redirectToAdminMainPage());
                            break;
                        case "Instructor":
                            roleButton.setOnAction(e -> redirectToInstructorMainPage());
                            break;
                        case "Student":
                            roleButton.setOnAction(e -> redirectToStudentMainPage());
                            break;
                    }

                    rolesVBox.getChildren().add(roleButton); // Add button to the VBox
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Database error: " + e.getMessage());
        }
    }


    private void redirectToAdminMainPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/local/jfx360/fxml/AdminMainPage.fxml"));
            Parent adminRoot = loader.load();
            AdminMainPage adminController = loader.getController();
            adminController.setUserId(this.userId);

            Scene adminScene = new Scene(adminRoot);
            Stage currentStage = (Stage) rolesVBox.getScene().getWindow();
            currentStage.setScene(adminScene);
//            currentStage.setMaximized(true);
            currentStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Error loading the Admin page.");
        }
    }

    private void redirectToInstructorMainPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/local/jfx360/fxml/InstructorMainPage.fxml"));
            Parent instructorRoot = loader.load();

            InstructorMainPage controller = loader.getController();
            controller.setUserId(this.userId);

            Scene instructorScene = new Scene(instructorRoot);
            Stage currentStage = (Stage) rolesVBox.getScene().getWindow();
            currentStage.setScene(instructorScene);
//            currentStage.setMaximized(true);
            currentStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Error loading Instructor main page.");
        }
    }

    private void redirectToStudentMainPage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/local/jfx360/fxml/StudentMainPage.fxml"));
            Parent studentRoot = loader.load();

            StudentMainPage controller = loader.getController();
            controller.setUserId(this.userId);

            Scene studentScene = new Scene(studentRoot);
            Stage currentStage = (Stage) rolesVBox.getScene().getWindow();
            currentStage.setScene(studentScene);
//            currentStage.setMaximized(true);
            currentStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            warningmessageTextField.setText("Error loading the Student main page.");
        }
    }
}
