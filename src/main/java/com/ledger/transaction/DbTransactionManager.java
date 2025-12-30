package com.ledger.transaction;

import com.ledger.orm.ConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;

public final class DbTransactionManager {
    private static DbTransactionManager instance;
    private DbTransactionManager() {
    }
    public static DbTransactionManager getInstance() {
        if (instance == null) {
            instance = new DbTransactionManager();
        }
        return instance;
    }

    public <T> T execute(DbTransactionAction<T> action) {
        Connection connection = ConnectionManager.getInstance().getConnection();
        try {
            connection.setAutoCommit(false);
            T result = action.execute();
            connection.commit();
            return result;
        } catch (Exception e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException rollbackEx) {
                System.err.println("Critical Error: Rollback failed! " + rollbackEx.getMessage());
            }
            System.err.println("Transaction Database rolled back due to: " + e.getMessage());
            return null;
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                System.err.println("Could not reset auto-commit: " + e.getMessage());
            }
        }
    }
}
