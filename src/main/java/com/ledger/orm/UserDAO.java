package com.ledger.orm;

import com.ledger.domain.User;

import java.sql.*;

public class UserDAO {
    private final Connection connection;

    public UserDAO(Connection connection){
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean register(User user) {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());

            //execute query insert and return number of affected rows
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                //generate ID
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) { //return ResultSet with generated keys
                    if (generatedKeys.next()) {
                        user.setId(generatedKeys.getLong(1));
                        return true;
                    }
                }
            }
            return false;
        }catch (SQLException e){
            System.err.println("SQL Exception during registration: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public User getUserByUsername(String username){
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            try(ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) { //if exists user with that username
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setPassword(rs.getString("password"));
                    return user;
                }
            }
            return null;
        }catch (SQLException e){
            System.err.println("SQL Exception during getUserByUsername: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(User user) {
        String sql = "UPDATE users SET username = ?, password = ? WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setLong(3, user.getId());

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during updateUser: " + e.getMessage());
            return false;
        }
    }
}