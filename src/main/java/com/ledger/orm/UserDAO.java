package com.ledger.orm;

import com.ledger.domain.Account;
import com.ledger.domain.Budget;
import com.ledger.domain.Ledger;
import com.ledger.domain.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {
    private Connection connection;

    public UserDAO(Connection connection){
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean register(User user) throws SQLException {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                //generate ID
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        user.setId(generatedKeys.getLong(1));
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public User login (String username, String password) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new User(
                        rs.getString("username"),
                        rs.getString("password")
                );
            }
            return null;
        }
    }

    @SuppressWarnings("SqlResolve")
    public User getUserById(Long id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return buildFullUserFromResultSet(rs);
            }
            return null;
        }
    }


    @SuppressWarnings("SqlResolve")
    public User getUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return buildFullUserFromResultSet(rs);
            }
            return null;
        }
    }
    private User buildFullUserFromResultSet(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));

        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        AccountDAO accountDAO = new AccountDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection);

        List<Ledger> ledgers = ledgerDAO.getLedgersByUserId(user.getId());
        List<Account> accounts = accountDAO.getAccountByOwnerId(user.getId());
        List<Budget> budgets = budgetDAO.getBudgetsByUserId(user.getId());

        user.setLedgers(ledgers != null ? ledgers : new ArrayList<>());
        user.setAccounts(accounts != null ? accounts : new ArrayList<>());
        user.setBudgets(budgets != null ? budgets : new ArrayList<>());

        for (Ledger l : user.getLedgers()) {
            l.setOwner(user);
        }
        for (Account a : user.getAccounts()) {
            a.setOwner(user);
        }
        for (Budget b : user.getBudgets()) {
            b.setOwner(user);
        }

        return user;
    }

    @SuppressWarnings("SqlResolve")
    public boolean updateUser(User user) throws SQLException {
        String sql = "UPDATE users SET username = ?, password = ? WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setLong(3, user.getId());

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean deleteUser(Long id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }


}