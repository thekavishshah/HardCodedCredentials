package local.jfx360.main;

import javafx.application.Application;
import local.jfx360.main.MainApp;
import local.jfx360.utils.DatabaseUtil;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
class CreateAccountTest {

    @Test
    void testCreateAccount() {
        // Empty the Database
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "DELETE FROM users";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Launch MainApp
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try {
                Application.launch(MainApp.class);
            } finally {
                latch.countDown();
            }
        });

        thread.setDaemon(true);
        thread.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        // Testing if the first user added to the database is admin
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT * FROM user_roles";
            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String role = rs.getString("role_id");
                assertEquals("1", role);
            } else {
                fail("Test Failed");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);

        }
    }
}