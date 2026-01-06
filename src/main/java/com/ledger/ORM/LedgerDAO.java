package com.ledger.ORM;

import com.ledger.DomainModel.Ledger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LedgerDAO {
   private final Connection connection;

    public LedgerDAO(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Ledger ledger) {
        String sql = "INSERT INTO ledgers (user_id, name) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, ledger.getOwner().getId());
            stmt.setString(2, ledger.getName());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        ledger.setId(rs.getLong(1));
                        return true;
                    }
                }
            }
            return false;
        }catch (SQLException e){
            System.err.println("SQL Exception during ledger insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(Ledger ledger) {
        String sql = "DELETE FROM ledgers WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledger.getId());
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during ledger delete: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(Ledger ledger) {
        String sql = "UPDATE ledgers SET name = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ledger.getName());
            stmt.setLong(2, ledger.getId());
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during ledger update: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public Ledger getById(long id) {
        String sql = "SELECT id, name, user_id FROM ledgers WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try(ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Ledger ledger = new Ledger();
                    ledger.setId(rs.getLong("id"));
                    ledger.setName(rs.getString("name"));
                    return ledger;
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getById: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public Ledger getByNameAndOwnerId(String name, long ownerId){
        String sql = "SELECT id, name FROM ledgers WHERE name = ? AND user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setLong(2, ownerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Ledger ledger = new Ledger();
                ledger.setId(rs.getLong("id"));
                ledger.setName(rs.getString("name"));
                return ledger;
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getByNameAndOwnerId: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public List<Ledger> getLedgersByUserId(long userId) {
        List<Ledger> ledgers = new ArrayList<>();
        String sql = "SELECT * FROM ledgers WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try(ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Ledger ledger = new Ledger();
                    ledger.setId(rs.getLong("id"));
                    ledger.setName(rs.getString("name"));
                    ledgers.add(ledger);
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getLedgersByUserId: " + e.getMessage());
        }
        return ledgers;
    }
}
