package local.jfx360.main;

import javafx.application.Application;
import local.jfx360.utils.DatabaseUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class CreateSecondAccountTest {

    @Test
    void testCreateSecondAccount() {
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

        // Testing if the user added to the database is an Instructor
        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT * FROM user_roles WHERE role_id = '3'";
            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String role = rs.getString("role_id");
                assertEquals("3", role);
            } else {
                fail("Test Failed");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);

        }
    }
}