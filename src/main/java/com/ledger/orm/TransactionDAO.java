package com.ledger.orm;

import com.ledger.domain.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransactionDAO {
    private Connection connection;

    public TransactionDAO(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public <T extends Transaction> boolean insert(T transaction) throws SQLException {
        // insert into transactions table
        String transactionSql = "INSERT INTO transactions (transaction_date, amount, note, from_account_id," +
                " to_account_id, ledger_id, category_id, dtype) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                return false;
            }

            // get generated transaction ID
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    Long transactionId = rs.getLong(1);
                    transaction.setId(transactionId);
                    return true;
                }
            }
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(Transaction transaction) throws SQLException {
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transaction.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(Transaction transaction) throws SQLException {
        String sql = "UPDATE transactions SET transaction_date = ?, amount = ?, note = ?, from_account_id = ?, " +
                "to_account_id = ?, ledger_id = ?, category_id = ?, dtype = ?  " +
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
            stmt.setLong(9, transaction.getId());

            return stmt.executeUpdate() > 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean deleteByLedgerId(Long ledgerId) throws SQLException {
        String sql = "DELETE FROM transactions WHERE ledger_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);
            return stmt.executeUpdate() > 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public Transaction getById(Long id) throws SQLException {
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
        }
        return null;
    }

    //set ledger
    @SuppressWarnings("SqlResolve")
    public List<Transaction> getByLedgerId(Long ledgerId) throws SQLException {
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
        }
        return transactions;
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getByLedger(Ledger ledger, LocalDate startDate, LocalDate endDate) throws SQLException {
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
                "AND t.date BETWEEN ? AND ? " +
                "ORDER BY t.transaction_date DESC, t.id DESC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledger.getId());
            stmt.setObject(2, startDate);
            stmt.setObject(3, endDate);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getOutgoingByAccountId(Long accountId) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT t.*, " +
                "fa.id as from_account_id, fa.name as from_account_name, " +
                "l.id as ledger_id, l.name as ledger_name, " +
                "c.id as category_id, c.name as category_name " +
                "FROM transactions t " +
                "LEFT JOIN accounts fa ON t.from_account_id = fa.id " +
                "LEFT JOIN accounts ta ON t.to_account_id = ta.id " +
                "LEFT JOIN ledgers l ON t.ledger_id = l.id " +
                "LEFT JOIN ledger_categories c ON t.category_id = c.id " +
                "WHERE t.from_account_id = ? " +
                "ORDER BY t.transaction_date DESC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, accountId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }
    @SuppressWarnings("SqlResolve")
    public List<Transaction> getIncomingByAccountId(Long accountId) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT t.*, " +
                "ta.id as to_account_id, ta.name as to_account_name, " +
                "l.id as ledger_id, l.name as ledger_name, " +
                "c.id as category_id, c.name as category_name " +
                "FROM transactions t " +
                "LEFT JOIN accounts fa ON t.from_account_id = fa.id " +
                "LEFT JOIN accounts ta ON t.to_account_id = ta.id " +
                "LEFT JOIN ledgers l ON t.ledger_id = l.id " +
                "LEFT JOIN ledger_categories c ON t.category_id = c.id " +
                "WHERE t.to_account_id = ? " +
                "ORDER BY t.transaction_date DESC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, accountId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getByAccountIdInRangeDate(Account account, LocalDate startDate, LocalDate endDate) throws SQLException {
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
                "WHERE (t.from_account_id = ? OR t.to_account_id = ?) " +
                "AND t.date BETWEEN ? AND ? " +
                "ORDER BY t.date DESC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            stmt.setLong(2, account.getId());
            stmt.setObject(3, startDate);
            stmt.setObject(4, endDate);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getByAccountId(Long accountId) throws SQLException {
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
        }
        return transactions;
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(Long id) throws SQLException {
        // delete from transactions table
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    //not set account, ledger, category objects
    private Transaction mapResultSetToTransaction(ResultSet rs) throws SQLException {
        Transaction transaction;
        String dtype = rs.getString("dtype");

        // based on dtype, create appropriate subclass instance
        switch (dtype) {
            case "Transfer":
                transaction = new Transfer();
                break;
            case "Expense":
                transaction = new Expense();
                break;
            case "Income":
                transaction = new Income();
                break;
            default:
                transaction = new Transaction() {};
        }

        // set common fields
        transaction.setId(rs.getLong("id"));
        transaction.setDate(rs.getObject("transaction_date", LocalDate.class));
        transaction.setAmount(rs.getBigDecimal("amount"));
        transaction.setNote(rs.getString("note"));
        transaction.setType(TransactionType.valueOf(rs.getString("dtype")));
        return transaction;
    }

    @SuppressWarnings("SqlResolve")
    public Map<String, Object> getLedgerSummary(Long ledgerId, LocalDate startDate,
                                              LocalDate endDate) throws SQLException {
        Map<String, Object> summary = new LinkedHashMap<>();
        String sql = "SELECT " +
                "COALESCE(SUM(CASE WHEN transaction_type = 'INCOME' THEN amount ELSE 0 END), 0) as total_income, " +
                "COALESCE(SUM(CASE WHEN transaction_type = 'EXPENSE' THEN amount ELSE 0 END), 0) as total_expense " +
                "FROM transactions " +
                "WHERE ledger_id = ? " +
                "AND date BETWEEN ? AND ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);
            stmt.setObject(2, startDate);
            stmt.setObject(3, endDate);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal totalIncome = rs.getBigDecimal("total_income");
                    BigDecimal totalExpense = rs.getBigDecimal("total_expense");

                    summary.put("totalIncome", totalIncome);
                    summary.put("totalExpense", totalExpense);
                    summary.put("netChange", totalIncome.subtract(totalExpense));
                }
            }
            return summary;
        }
    }

    @SuppressWarnings("SqlResolve")
    public Map<String, BigDecimal> getAccountBalanceSummary(Long accountId, LocalDate startDate,
                                                            LocalDate endDate) throws SQLException {
        Map<String, BigDecimal> summary = new LinkedHashMap<>();

        String sql = "SELECT " +
                "COALESCE(SUM(CASE WHEN to_account_id = ? THEN amount ELSE 0 END), 0) as total_income, " +
                "COALESCE(SUM(CASE WHEN from_account_id = ? THEN amount ELSE 0 END), 0) as total_expense " +
                "FROM transactions " +
                "WHERE (from_account_id = ? OR to_account_id = ?) " +
                "AND date BETWEEN ? AND ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, accountId);
            stmt.setLong(2, accountId);
            stmt.setLong(3, accountId);
            stmt.setLong(4, accountId);
            stmt.setObject(5, startDate);
            stmt.setObject(6, endDate);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal totalIncome = rs.getBigDecimal("total_income");
                    BigDecimal totalExpense = rs.getBigDecimal("total_expense");

                    summary.put("totalIncome", totalIncome);
                    summary.put("totalExpense", totalExpense);
                    summary.put("netChange", totalIncome.subtract(totalExpense));
                }
            }
        }

        return summary;
    }

    public List<Transaction> get(Long accountId, Long ledgerId, Long categoryId, LocalDate startDate,
                                 LocalDate endDate) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE 1=1");
        if (accountId != null) {
            sql.append(" AND (from_account_id = ? OR to_account_id = ?)");
        }
        if (ledgerId != null) {
            sql.append(" AND ledger_id = ?");
        }
        if (categoryId != null) {
            sql.append(" AND category_id = ?");
        }
        if (startDate != null) {
            sql.append(" AND transaction_date >= ?");
        }
        if (endDate != null) {
            sql.append(" AND transaction_date <= ?");
        }
        sql.append(" ORDER BY transaction_date DESC");

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (accountId != null) {
                stmt.setLong(index++, accountId);
            }
            if (accountId != null) {
                stmt.setLong(index++, accountId);
            }
            if (ledgerId != null) {
                stmt.setLong(index++, ledgerId);
            }
            if( categoryId != null) {
                stmt.setLong(index++, categoryId);
            }
            if (startDate != null) {
                stmt.setDate(index++, Date.valueOf(startDate));
            }

            if (endDate != null) {
                stmt.setDate(index++, Date.valueOf(endDate));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }
        return transactions;
    }

    public List<Transaction> get(Map<String, Object> filters, LocalDate startDate, LocalDate endDate) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();

        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE 1=1");

        for (String key : filters.keySet()) {
            sql.append(" AND ").append(key).append(" = ?");
        }


        if (startDate != null) {
            sql.append(" AND transaction_date >= ?");
        }
        if (endDate != null) {
            sql.append(" AND transaction_date <= ?");
        }

        sql.append(" ORDER BY transaction_date DESC");

        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            int index = 1;

            //set condition values
            for (Object value : filters.values()) {
                if (value instanceof Long) {
                    stmt.setLong(index++, (Long) value);
                } else if (value instanceof Integer) {
                    stmt.setInt(index++, (Integer) value);
                } else if (value instanceof BigDecimal) {
                    stmt.setBigDecimal(index++, (BigDecimal) value);
                } else if (value instanceof LocalDate) {
                    stmt.setDate(index++, Date.valueOf((LocalDate) value));
                } else if (value == null) {
                    stmt.setNull(index++, Types.NULL);
                } else {
                    stmt.setObject(index++, value);
                }
            }


            if (startDate != null) {
                stmt.setDate(index++, Date.valueOf(startDate));
            }
            if (endDate != null) {
                stmt.setDate(index++, Date.valueOf(endDate));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(mapResultSetToTransaction(rs));
                }
            }
        }

        return transactions;
    }


}
