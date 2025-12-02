package com.ledger.orm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionManager {
    private static Connection connection;
    private static final String URL = "jdbc:postgresql://localhost:5432/ledger_db";
    private static final String USER = "postgres";
    private static final String PASSWORD = "5858";

    public static Connection getConnection()  {
        boolean shouldReconnect = false;

        if (connection == null) { //does not exist yet connection
            shouldReconnect = true;
        } else { //exists connection
            //verify if the existing connection is still valid
            try {
                if (connection.isClosed()) {
                    shouldReconnect = true;
                }
            } catch (SQLException e) {
                System.err.println("SQL Exception while checking connection validity.");
                shouldReconnect = true;
            }
        }

        if (shouldReconnect) {
            try {
                // try to establish a new connection
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
            } catch (SQLException e) {
                System.err.println("Error connecting to database: " + e.getMessage());
            }
        }

        return connection;
    }


}
