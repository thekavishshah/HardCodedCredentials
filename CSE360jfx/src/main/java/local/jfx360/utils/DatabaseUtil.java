// ./src/main/java/local/jfx360/utils/DatabaseUtil.java
package local.jfx360.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseUtil {
    private static final String URL = "jdbc:mysql://localhost:3306/projectdb";
    private static final String USER = "root";
    private static final String PASSWORD = "cse360!!!";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // Ensure MySQL JDBC driver is loaded
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            // Handle the error appropriately
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static Connection getConnection(String url) throws SQLException {
        return DriverManager.getConnection(url, USER, PASSWORD);
    }
}
