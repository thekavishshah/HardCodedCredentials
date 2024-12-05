// ./src/main/java/local/jfx360/main/MainApp.java
package local.jfx360.main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import local.jfx360.utils.DatabaseUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * <p> MainApp Class. </p>
 *
 * <p> Description: This class serves as the entry point for the JavaFX application.
 * It handles the main scene transitions, loads FXML files for different views,
 * and manages the initial setup of the application, including database checks
 * and user existence verification. </p>
 *
 */
public class MainApp extends Application {

    private static Stage primaryStage;

    /**
     * The main entry point for all JavaFX applications.
     * This method sets up the initial scene and performs necessary checks.
     *
     * @param stage the primary stage for this application
     * @throws IOException if an error occurs during FXML loading
     */
    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        primaryStage.setTitle("Project Application");

        // Check if the database exists TODO: fix sql code
//        if (!doesDatabaseExist("projectdb")) {
//            // Execute the SQL script to set up the database
//            String filePath = "sqlscript.txt";
//            String sqlScript = new String(Files.readAllBytes(Paths.get(filePath)));
//            createDatabaseAndTables(sqlScript);
//        }

        // Check if any users exist
        boolean hasUsers = checkIfUsersExist();

        if (hasUsers) {
            // Load Login scene
            loadScene("Login.fxml");
        } else {
            // Load CreateAccount scene for the first user
            loadScene("CreateAccount.fxml");
        }

        primaryStage.show();
    }

    public boolean doesDatabaseExist(String dbName) {
        try (Connection conn = DatabaseUtil.getConnection();
             ResultSet rs = conn.getMetaData().getCatalogs()) {

            while (rs.next()) {
                String catalog = rs.getString(1);
                if (dbName.equals(catalog)) {
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            // Handle exception (possibly show an error dialog)
        }
        return false;
    }

    public boolean checkIfUsersExist() {
        String query = "SELECT COUNT(*) AS user_count FROM users";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                int count = rs.getInt("user_count");
                return count > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            // Handle exception (possibly show an error dialog)
        }
        return false;
    }

    public static void loadScene(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/local/jfx360/fxml/" + fxmlFile));
            Scene scene = new Scene(loader.load());
            primaryStage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
            // Handle exception (possibly show an error dialog)
        }
    }

    private void createDatabaseAndTables(String sqlScript) {
        try (Connection conn = DatabaseUtil.getConnection();
             Statement stmt = conn.createStatement()) {

            // Split SQL script into individual statements
            String[] sqlStatements = sqlScript.split(";");

            for (String statement : sqlStatements) {
                if (!statement.trim().isEmpty()) {
                    stmt.execute(statement.trim());
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            // Handle exceptions (possibly show an error dialog)
        }
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch();
    }
}

