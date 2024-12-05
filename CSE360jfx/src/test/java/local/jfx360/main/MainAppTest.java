package local.jfx360.main;

import local.jfx360.utils.DatabaseUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import local.jfx360.utils.PasswordUtils;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
class MainAppTest {
    MainApp mainApp = new MainApp();

    // Test if the Database Exists
    @Test
    void doesDataBaseExistTest() {
        try (Connection conn = DatabaseUtil.getConnection("jdbc:mysql://localhost:3306/")) {
            String query = "CREATE DATABASE IF NOT EXISTS projectdb";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        boolean database = mainApp.doesDatabaseExist("projectdb");
        assertTrue(database);
    }

    @Test
    void doesUsersTableExist() throws SQLException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            Statement statement = conn.createStatement();
            String query = "SHOW TABLES LIKE 'users'";
            ResultSet resultSet = statement.executeQuery(query);
            assertTrue(resultSet.next());
        }
    }

    @Test
    void doesArticlesTableExist() throws SQLException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            Statement statement = conn.createStatement();
            String query = "SHOW TABLES LIKE 'help_articles'";
            ResultSet resultSet = statement.executeQuery(query);
            assertTrue(resultSet.next());
        }
    }

    @Test
    void doesHelpMessageTableExist() throws SQLException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            Statement statement = conn.createStatement();
            String query = "SHOW TABLES LIKE 'help_system_messages'";
            ResultSet resultSet = statement.executeQuery(query);
            assertTrue(resultSet.next());
        }
    }

    @Test
    public void doesAdminAccountExist() throws SQLException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            Statement statement = conn.createStatement();
            String query = "SELECT * FROM user_roles WHERE role_id LIKE '1'";
            ResultSet resultSet = statement.executeQuery(query);
            assertTrue(resultSet.next());
        }
    }


    private PasswordUtils passwordUtils;

    @BeforeEach
    void setUp() {
        passwordUtils = new PasswordUtils();
    }

    @Test
    void testHashPassword() {
        String password = "securePassword123";

        // Generate hashed password
        String hashedPassword = passwordUtils.hashPassword(password);

        // Ensure the hashed password follows "salt:hash" format
        assertNotNull(hashedPassword, "Hashed password should not be null");
        assertTrue(hashedPassword.contains(":"), "Hashed password should contain a colon separator");

        // Ensure both salt and hash are present
        String[] parts = hashedPassword.split(":");
        assertEquals(2, parts.length, "Hashed password should have a salt and a hash part");
    }

    @Test
    void testVerifyPasswordCorrect() {
        String password = "securePassword123";

        // Hash the password
        String hashedPassword = passwordUtils.hashPassword(password);

        // Verify using the correct password
        assertTrue(passwordUtils.verifyPassword(password, hashedPassword),
                "Password verification should succeed with the correct password");
    }

    @Test
    void testVerifyPasswordIncorrect() {
        String password = "securePassword123";
        String wrongPassword = "wrongPassword123";

        // Hash the password
        String hashedPassword = passwordUtils.hashPassword(password);

        // Verify using an incorrect password
        assertFalse(passwordUtils.verifyPassword(wrongPassword, hashedPassword),
                "Password verification should fail with an incorrect password");
    }

    @Test
    void testVerifyPasswordInvalidFormat() {
        String invalidStoredPassword = "invalidFormatPassword";

        // Verify using an improperly formatted stored password
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            passwordUtils.verifyPassword("anyPassword", invalidStoredPassword);
        });

        String expectedMessage = "Stored password must have the format 'salt:hash'";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage),
                "Exception message should indicate invalid format");
    }


}