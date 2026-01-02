package com.ledger.orm;

import com.ledger.domain.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AccountDAO {
    private final Connection connection;

    public AccountDAO(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Account account) {
        String sql = "INSERT INTO accounts (name, balance, user_id, included_in_asset, selectable) VALUES (?, ?, ?, ?, ?)";
        try(PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, account.getName());
            stmt.setBigDecimal(2, account.getBalance());
            stmt.setLong(3, account.getOwner().getId());
            stmt.setBoolean(4, account.getIncludedInAsset());
            stmt.setBoolean(5, account.getSelectable());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                return false;
            }
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    account.setId(rs.getLong(1));
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception during createAccount: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public Account getAccountById(long id) {
        String baseSql =  "SELECT * FROM accounts WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(baseSql)) {
            stmt.setLong(1, id);
            try(ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAccount(rs);
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getAccountById: " + e.getMessage());
        }
        return null;
    }

    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        Account account= new Account();
        account.setId(rs.getLong("id"));
        account.setName(rs.getString("name"));
        account.setBalance(rs.getBigDecimal("balance"));
        account.setIncludedInAsset(rs.getBoolean("included_in_asset"));
        account.setSelectable(rs.getBoolean("selectable"));
        return account;
    }

    @SuppressWarnings("SqlResolve")
    public List<Account> getAccountsByOwner(User owner) {
        List<Account> accounts = new ArrayList<>();
        String sql = " SELECT  * FROM accounts WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, owner.getId());
            try(ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Account account = mapResultSetToAccount(rs);
                    accounts.add(account);
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getAccountsByOwnerId: " + e.getMessage());
        }
        return accounts;
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(Account account) {
        String sql = "UPDATE accounts SET name = ?, balance = ?, included_in_asset = ?, selectable = ?  WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, account.getName());
            stmt.setBigDecimal(2, account.getBalance());
            stmt.setBoolean(3, account.getIncludedInAsset());
            stmt.setBoolean(4, account.getSelectable());
            stmt.setLong(5, account.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("SQL Exception during update: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(Account account) {
        String sql = "DELETE FROM accounts WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            int affected = stmt.executeUpdate();
            return affected > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during deleteAccount: " + e.getMessage());
            return false;
        }
    }

}
