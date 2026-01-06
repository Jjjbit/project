package com.ledger.ORM;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConnectionManager {
    private static Connection connection;
    private static final String url = "jdbc:postgresql://localhost:5432/ledger_db";
    private static final String user = "postgres";
    private static final String password = "5858";
    private static ConnectionManager instance;

    private ConnectionManager(){}

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    public Connection getConnection()  {
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
                connection = DriverManager.getConnection(url, user, password);
            } catch (SQLException e) {
                System.err.println("Error connecting to database: " + e.getMessage());
            }
        }
        return connection;
    }
}
