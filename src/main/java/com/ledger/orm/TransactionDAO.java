package com.ledger.orm;

import com.ledger.domain.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {
    private final Connection connection;
    private final LedgerCategoryDAO categoryDAO;
    private final AccountDAO accountDAO;
    private final LedgerDAO ledgerDAO;

    public TransactionDAO(Connection connection, LedgerCategoryDAO categoryDAO,
                          AccountDAO  accountDAO, LedgerDAO ledgerDAO) {
        this.accountDAO = accountDAO;
        this.ledgerDAO = ledgerDAO;
        this.categoryDAO = categoryDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public <T extends Transaction> boolean insert(T transaction) {
        // insert into transactions table
        String transactionSql = "INSERT INTO transactions (transaction_date, amount, note, from_account_id," +
                " to_account_id, ledger_id, category_id, dtype, is_reimbursable) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(transactionSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setObject(1, transaction.getDate());
            stmt.setBigDecimal(2, transaction.getAmount());
            stmt.setString(3, transaction.getNote());

            // insert fromAccount id
            if (transaction.getFromAccount() != null) {
                stmt.setLong(4, transaction.getFromAccount().getId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }

            //insert toAccount id
            if (transaction.getToAccount() != null) {
                stmt.setLong(5, transaction.getToAccount().getId());
            } else {
                stmt.setNull(5, Types.BIGINT);
            }

            // insert ledger id
            if (transaction.getLedger() != null) {
                stmt.setLong(6, transaction.getLedger().getId());
            } else {
                stmt.setNull(6, Types.BIGINT);
            }

            // insert category id
            if (transaction.getCategory() != null) {
                stmt.setLong(7, transaction.getCategory().getId());
            } else {
                stmt.setNull(7, Types.BIGINT);
            }

            stmt.setString(8, transaction.getType().name());
            stmt.setBoolean(9, transaction.isReimbursable());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                return false;
            }

            // get generated transaction ID
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    transaction.setId(rs.getLong(1));
                    return true;
                }
            }
            return false;
        }catch (SQLException e){
            System.err.println("SQL Exception during transaction insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(Transaction transaction) {
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transaction.getId());
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during transaction delete: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(Transaction transaction) {
        String sql = "UPDATE transactions SET transaction_date = ?, amount = ?, note = ?, from_account_id = ?, " +
                "to_account_id = ?, ledger_id = ?, category_id = ?, dtype = ?, is_reimbursable = ?  " +
                "WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setObject(1, transaction.getDate());
            stmt.setBigDecimal(2, transaction.getAmount());
            stmt.setString(3, transaction.getNote());

            // update fromAccount id
            if (transaction.getFromAccount() != null) {
                stmt.setLong(4, transaction.getFromAccount().getId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }

            // update toAccount id
            if (transaction.getToAccount() != null) {
                stmt.setLong(5, transaction.getToAccount().getId());
            } else {
                stmt.setNull(5, Types.BIGINT);
            }

            // update ledger id
            if (transaction.getLedger() != null) {
                stmt.setLong(6, transaction.getLedger().getId());
            } else {
                stmt.setNull(6, Types.BIGINT);
            }

            // update category id
            if (transaction.getCategory() != null) {
                stmt.setLong(7, transaction.getCategory().getId());
            } else {
                stmt.setNull(7, Types.BIGINT);
            }

            //update type
            stmt.setString(8, transaction.getType().name());

            stmt.setBoolean(9, transaction.isReimbursable());

            stmt.setLong(10, transaction.getId());

            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during transaction update: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public Transaction getById(Long id) {
        String sql = "SELECT t.*, " +
                "fa.id as from_account_id, fa.name as from_account_name, " +
                "ta.id as to_account_id, ta.name as to_account_name, " +
                "l.id as ledger_id, l.name as ledger_name, " +
                "c.id as category_id, c.name as category_name " +
                "FROM transactions t " +
                "LEFT JOIN accounts fa ON t.from_account_id = fa.id " +
                "LEFT JOIN accounts ta ON t.to_account_id = ta.id " +
                "LEFT JOIN ledgers l ON t.ledger_id = l.id " +
                "LEFT JOIN ledger_categories c ON t.category_id = c.id " +
                "WHERE t.id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTransaction(rs);
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getById: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getByLedgerId(Long ledgerId) {
        List<Transaction> transactions = new ArrayList<>();

        String sql = "SELECT t.*, " +
                "fa.id as from_account_id, fa.name as from_account_name, " +
                "ta.id as to_account_id, ta.name as to_account_name, " +
                "l.id as ledger_id, l.name as ledger_name, " +
                "c.id as category_id, c.name as category_name " +
                "FROM transactions t " +
                "LEFT JOIN accounts fa ON t.from_account_id = fa.id " +
                "LEFT JOIN accounts ta ON t.to_account_id = ta.id " +
                "LEFT JOIN ledgers l ON t.ledger_id = l.id " +
                "LEFT JOIN ledger_categories c ON t.category_id = c.id " +
                "WHERE t.ledger_id = ? " +
                "ORDER BY t.transaction_date DESC, t.id DESC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getByLedgerId: " + e.getMessage());
        }
        return transactions;
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getByCategoryId(Long categoryId) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT t.*, " +
                "fa.id as from_account_id, fa.name as from_account_name, " +
                "ta.id as to_account_id, ta.name as to_account_name, " +
                "l.id as ledger_id, l.name as ledger_name, " +
                "c.id as category_id, c.name as category_name " +
                "FROM transactions t " +
                "LEFT JOIN accounts fa ON t.from_account_id = fa.id " +
                "LEFT JOIN accounts ta ON t.to_account_id = ta.id " +
                "LEFT JOIN ledgers l ON t.ledger_id = l.id " +
                "LEFT JOIN ledger_categories c ON t.category_id = c.id " +
                "WHERE t.category_id = ? " +
                "ORDER BY t.transaction_date DESC, t.id DESC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, categoryId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getByCategoryId: " + e.getMessage());
        }
        return transactions;
    }


    @SuppressWarnings("SqlResolve")
    public List<Transaction> getByAccountId(Long accountId) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT t.*, " +
                "fa.id as from_account_id, fa.name as from_account_name, " +
                "ta.id as to_account_id, ta.name as to_account_name, " +
                "l.id as ledger_id, l.name as ledger_name, " +
                "c.id as category_id, c.name as category_name " +
                "FROM transactions t " +
                "LEFT JOIN accounts fa ON t.from_account_id = fa.id " +
                "LEFT JOIN accounts ta ON t.to_account_id = ta.id " +
                "LEFT JOIN ledgers l ON t.ledger_id = l.id " +
                "LEFT JOIN ledger_categories c ON t.category_id = c.id " +
                "WHERE t.from_account_id = ? OR t.to_account_id = ? " +
                "ORDER BY t.transaction_date DESC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, accountId);
            stmt.setLong(2, accountId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getByAccountId: " + e.getMessage());
        }
        return transactions;
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(Long id) {
        // delete from transactions table
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during transaction delete by id: " + e.getMessage());
            return false;
        }
    }

    // set account, category objects
    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        Transaction transaction;
        String dtype = rs.getString("dtype").toUpperCase();

        // based on dtype, create appropriate subclass instance
        switch (dtype) {
            case "TRANSFER":
                transaction = new Transfer();
                break;
            case "EXPENSE":
                transaction = new Expense();
                break;
            case "INCOME":
                transaction = new Income();
                break;
            default:
                System.err.println("Unknown transaction type: " + dtype);
                return null;
        }

        // set common fields
        transaction.setId(rs.getLong("id"));
        transaction.setDate(rs.getObject("transaction_date", LocalDate.class));
        transaction.setAmount(rs.getBigDecimal("amount"));
        transaction.setNote(rs.getString("note"));
        transaction.setType(TransactionType.valueOf(rs.getString("dtype")));
        transaction.setReimbursable(rs.getBoolean("is_reimbursable"));

        //set fromAccount
        if( rs.getLong("from_account_id") != 0) {
            transaction.setFromAccount(accountDAO.getAccountById(rs.getLong("from_account_id")));
        }
        //set toAccount
        if( rs.getLong("to_account_id") != 0) {
            transaction.setToAccount(accountDAO.getAccountById(rs.getLong("to_account_id")));
        }

        //set ledger
        if( rs.getLong("ledger_id") != 0) {
            transaction.setLedger(ledgerDAO.getById(rs.getLong("ledger_id")));
        }
        //set category
        if( rs.getLong("category_id") != 0) {
            transaction.setCategory(categoryDAO.getById(rs.getLong("category_id")));
        }
        return transaction;
    }

}
