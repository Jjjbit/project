package com.ledger.orm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionManager {
    private static Connection connection;
    private static final String URL = "jdbc:h2:mem:ledger_db;DB_CLOSE_DELAY=-1";
    //private static final String URL = "jdbc:postgresql://localhost:5432/ledger_db";
    //private static final String USER = "postgres";
    //private static final String PASSWORD = "your_password";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private ConnectionManager() {}

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
        }
        return connection;
    }

}
