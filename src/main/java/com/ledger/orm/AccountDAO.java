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
    public boolean createBasicAccount(BasicAccount account) throws SQLException {
        String sql = "INSERT INTO accounts (dtype, name, balance, account_type, account_category, user_id, notes, is_hidden, included_in_net_asset, selectable) " +
                "VALUES ('BasicAccount', ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, account.getName());
            stmt.setBigDecimal(2, account.getBalance());
            stmt.setString(3, account.getType().name());
            stmt.setString(4, account.getCategory().name());
            stmt.setLong(5, account.getOwner().getId());
            stmt.setString(6, account.getNotes());
            stmt.setBoolean(7, account.getHidden());
            stmt.setBoolean(8, account.getIncludedInNetAsset());
            stmt.setBoolean(9, account.getSelectable());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                return false;
            }

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                Long accountId = rs.getLong(1);
                account.setId(accountId);
            }
            //insert into basic_account table
            String basicSql = "INSERT INTO basic_account (id) VALUES (?)";
            try (PreparedStatement basicStmt = connection.prepareStatement(basicSql)) {
                basicStmt.setLong(1, account.getId());
                int basicAffectedRows = basicStmt.executeUpdate();
                if (basicAffectedRows == 0) {
                    return false;
                }
            }
            return true;
        }

    }

    @SuppressWarnings("SqlResolve")
    public boolean createCreditAccount(CreditAccount account) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String accountSql = "INSERT INTO accounts (dtype, name, balance, account_type, account_category, user_id, notes, is_hidden, included_in_net_asset, selectable) " +
                    "VALUES ('CreditAccount', ?, ?, ?, ?, ?, ?, ?, ?, ?)";


            long accountId;
            try (PreparedStatement stmt = connection.prepareStatement(accountSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, account.getName());
                stmt.setBigDecimal(2, account.getBalance());
                stmt.setString(3, account.getType().name());
                stmt.setString(4, account.getCategory().name());
                stmt.setLong(5, account.getOwner().getId());
                stmt.setString(6, account.getNotes());
                stmt.setBoolean(7, account.getHidden());
                stmt.setBoolean(8, account.getIncludedInNetAsset());
                stmt.setBoolean(9, account.getSelectable());

                //esecute account insert and return affected rows
                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    connection.rollback();
                    return false;
                }

                //get generated account id
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        accountId = rs.getLong(1);
                        account.setId(accountId);
                    } else {
                        connection.rollback();
                        return false;
                    }
                }
            }

            //add to credit_account table
            String creditSql = "INSERT INTO credit_account (id, credit_limit, current_debt, bill_date, due_date) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(creditSql)) {
                stmt.setLong(1, accountId);
                stmt.setBigDecimal(2, account.getCreditLimit());
                stmt.setBigDecimal(3, account.getCurrentDebt());
                stmt.setObject(4, account.getBillDay());
                stmt.setObject(5, account.getDueDay());

                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    connection.rollback();
                    return false;
                }
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean createLoanAccount(LoanAccount account) throws SQLException {
        connection.setAutoCommit(false);
        try {
            //insert into accounts table
            String accountSql = "INSERT INTO accounts (dtype, name, balance, account_type, account_category, user_id, notes, is_hidden, included_in_net_asset, selectable) " +
                    "VALUES ('LoanAccount', ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            long accountId;
            try (PreparedStatement stmt = connection.prepareStatement(accountSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, account.getName());
                stmt.setBigDecimal(2, account.getBalance());
                stmt.setString(3, account.getType().name());
                stmt.setString(4, account.getCategory().name());
                stmt.setLong(5, account.getOwner().getId());
                stmt.setString(6, account.getNotes());
                stmt.setBoolean(7, account.getHidden());
                stmt.setBoolean(8, account.getIncludedInNetAsset());
                stmt.setBoolean(9, account.getSelectable());
                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    connection.rollback();
                    return false;
                }
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        accountId = rs.getLong(1);
                        account.setId(accountId);
                    } else {
                        connection.rollback();
                        return false;
                    }
                }
            }

            //insert into loan_account table
            String loanSql = "INSERT INTO loan_account (id, total_periods, repaid_periods, annual_interest_rate, loan_amount, repayment_date, repayment_type, loan_remaining_amount, is_ended)" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(loanSql)) {
                stmt.setLong(1, accountId);
                stmt.setInt(2, account.getTotalPeriods());
                stmt.setInt(3, account.getRepaidPeriods());
                stmt.setBigDecimal(4, account.getAnnualInterestRate());
                stmt.setBigDecimal(5, account.getLoanAmount());
                stmt.setDate(6, Date.valueOf(account.getRepaymentDay()));
                stmt.setString(7, account.getRepaymentType().name());
                stmt.setBigDecimal(8, account.getRemainingAmount());
                stmt.setBoolean(9, account.getIsEnded());

                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    connection.rollback();
                    return false;
                }
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean createBorrowingAccount(BorrowingAccount account) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String accountSql = "INSERT INTO accounts (dtype, name, balance, account_type, account_category, user_id, notes, is_hidden, included_in_net_asset, selectable) " +
                    "VALUES ('BorrowingAccount', ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            long accountId;
            try (PreparedStatement stmt = connection.prepareStatement(accountSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, account.getName());
                stmt.setBigDecimal(2, account.getBalance());
                stmt.setString(3, account.getType().name());
                stmt.setString(4, account.getCategory().name());
                stmt.setLong(5, account.getOwner().getId());
                stmt.setString(6, account.getNotes());
                stmt.setBoolean(7, account.getHidden());
                stmt.setBoolean(8, account.getIncludedInNetAsset());
                stmt.setBoolean(9, account.getSelectable());

                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    connection.rollback();
                    return false;
                }

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        accountId = rs.getLong(1);
                        account.setId(accountId);
                    } else {
                        connection.rollback();
                        return false;
                    }
                }
            }

            String borrowingSql = "INSERT INTO borrowing_account (id, is_ended, borrowing_date, borrowing_amount, borrowing_remaining_amount) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(borrowingSql)) {
                stmt.setLong(1, accountId);
                stmt.setBoolean(2, account.getIsEnded());
                stmt.setDate(3, Date.valueOf(account.getBorrowingDate()));
                stmt.setBigDecimal(4, account.getBorrowingAmount());
                stmt.setBigDecimal(5, account.getRemainingAmount());

                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    connection.rollback();
                    return false;
                }
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean createLendingAccount(LendingAccount account) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String accountSql = "INSERT INTO accounts (dtype, name, balance, account_type, account_category, user_id, notes, is_hidden, included_in_net_asset, selectable) " +
                    "VALUES ('LendingAccount', ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            long accountId;
            try (PreparedStatement stmt = connection.prepareStatement(accountSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, account.getName());
                stmt.setBigDecimal(2, account.getBalance());
                stmt.setString(3, account.getType().name());
                stmt.setString(4, account.getCategory().name());
                stmt.setLong(5, account.getOwner().getId());
                stmt.setString(6, account.getNotes());
                stmt.setBoolean(7, account.getHidden());
                stmt.setBoolean(8, account.getIncludedInNetAsset());
                stmt.setBoolean(9, account.getSelectable());

                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    connection.rollback();
                    return false;
                }

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        accountId = rs.getLong(1);
                        account.setId(accountId);
                    } else {
                        connection.rollback();
                        return false;
                    }
                }
            }

            String lendingSql = "INSERT INTO lending_account (id, is_ended, lending_date) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(lendingSql)) {
                stmt.setLong(1, accountId);
                stmt.setBoolean(2, account.getIsEnded());
                stmt.setDate(3, java.sql.Date.valueOf(account.getDate()));

                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    connection.rollback();
                    return false;
                }
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @SuppressWarnings("SqlResolve")
    public Account getAccountById(Long id) throws SQLException {
        String baseSql =  "SELECT DISTINCT a.id AS account_id, a.name, a.balance, a.account_type, a.account_category, " +
                "a.notes, a.is_hidden, a.included_in_net_asset, a.selectable, a.user_id, a.dtype, " +

                "ca.credit_limit, ca.current_debt, ca.bill_date, ca.due_date, " +
                "la.total_periods, la.repaid_periods, la.annual_interest_rate, la.loan_amount, " +
                "la.repayment_date, la.repayment_type, la.loan_remaining_amount, la.is_ended, " +
                "ba.is_ended as borrowing_ended, ba.borrowing_date, ba.borrowing_amount, ba.borrowing_remaining_amount, " +
                "len.is_ended as lending_ended, len.lending_date " +
                "FROM accounts a " +
                "LEFT JOIN credit_account ca ON a.id = ca.id " +
                "LEFT JOIN loan_account la ON a.id = la.id " +
                "LEFT JOIN borrowing_account ba ON a.id = ba.id " +
                "LEFT JOIN lending_account len ON a.id = len.id " +
                "WHERE a.id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(baseSql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToAccount(rs);
            }
        }
        return null;
    }

    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        String dtype = rs.getString("dtype");
        Account account;

        switch (dtype) {
            case "BasicAccount":
                account = new BasicAccount();
                break;
            case "CreditAccount":
                CreditAccount creditAccount = new CreditAccount();
                creditAccount.setCreditLimit(rs.getBigDecimal("credit_limit"));
                creditAccount.setCurrentDebt(rs.getBigDecimal("current_debt"));
                creditAccount.setBillDay(rs.getInt("bill_date"));
                creditAccount.setDueDay(rs.getInt("due_date"));
                account = creditAccount;
                break;
            case "LoanAccount":
                LoanAccount loanAccount = new LoanAccount();
                loanAccount.setTotalPeriods(rs.getInt("total_periods"));
                loanAccount.setRepaidPeriods(rs.getInt("repaid_periods"));
                loanAccount.setAnnualInterestRate(rs.getBigDecimal("annual_interest_rate"));
                loanAccount.setLoanAmount(rs.getBigDecimal("loan_amount"));
                loanAccount.setRepaymentDate(rs.getDate("repayment_date") != null ?
                        rs.getDate("repayment_date").toLocalDate() : null);
                loanAccount.setRepaymentType(rs.getString("repayment_type") != null ?
                        LoanAccount.RepaymentType.valueOf(rs.getString("repayment_type")) : null);
                loanAccount.setRemainingAmount(rs.getBigDecimal("loan_remaining_amount"));
                loanAccount.setEnded(rs.getBoolean("is_ended"));
                account = loanAccount;
                break;
            case "BorrowingAccount":
                BorrowingAccount borrowingAccount = new BorrowingAccount();
                borrowingAccount.setIsEnded(rs.getBoolean("borrowing_ended"));
                borrowingAccount.setBorrowingDate(rs.getDate("borrowing_date") != null ?
                        rs.getDate("borrowing_date").toLocalDate() : null);
                borrowingAccount.setBorrowingAmount(rs.getBigDecimal("borrowing_amount"));
                borrowingAccount.setRemainingAmount(rs.getBigDecimal("borrowing_remaining_amount"));
                account = borrowingAccount;
                break;
            case "LendingAccount":
                LendingAccount lendingAccount = new LendingAccount();
                lendingAccount.setIsEnded(rs.getBoolean("lending_ended"));
                lendingAccount.setDate(rs.getDate("lending_date") != null ?
                        rs.getDate("lending_date").toLocalDate() : null);
                account = lendingAccount;
                break;
            default:
                throw new SQLException("Unknown account type: " + dtype);
        }

        // Common fields
        account.setId(rs.getLong("account_id"));
        account.setName(rs.getString("name"));
        account.setBalance(rs.getBigDecimal("balance"));
        account.setType(AccountType.valueOf(rs.getString("account_type")));
        account.setCategory(AccountCategory.valueOf(rs.getString("account_category")));
        account.setNotes(rs.getString("notes"));
        account.setHidden(rs.getBoolean("is_hidden"));
        account.setIncludedInNetAsset(rs.getBoolean("included_in_net_asset"));
        account.setSelectable(rs.getBoolean("selectable"));
        return account;
    }

    @SuppressWarnings("SqlResolve")
    public List<Account> getAccountsByOwnerId(Long ownerId) throws SQLException {
        List<Account> accounts = new ArrayList<>();

        String sql = " SELECT DISTINCT a.id AS account_id, a.name, a.balance, a.account_type, a.account_category, " +
                "a.notes, a.is_hidden, a.included_in_net_asset, a.selectable, a.user_id, a.dtype," +

                "ca.credit_limit, ca.current_debt, ca.bill_date, ca.due_date, " +
                "la.total_periods, la.repaid_periods, la.annual_interest_rate, la.loan_amount, " +
                "la.repayment_date, la.repayment_type, la.loan_remaining_amount, la.is_ended, " +
                "ba.is_ended as borrowing_ended, ba.borrowing_date, ba.borrowing_amount,  ba.borrowing_remaining_amount, " +
                "len.is_ended as lending_ended, len.lending_date " +
                "FROM accounts a " +
                "LEFT JOIN credit_account ca ON a.id = ca.id " +
                "LEFT JOIN loan_account la ON a.id = la.id " +
                "LEFT JOIN borrowing_account ba ON a.id = ba.id " +
                "LEFT JOIN lending_account len ON a.id = len.id " +
                "WHERE a.user_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ownerId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Account account = mapResultSetToAccount(rs);
                accounts.add(account);
            }
        }
        return accounts;
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(Account account) throws SQLException {
        String sql = "UPDATE accounts SET name = ?, balance = ?, account_type = ?, account_category = ?, " +
                "notes = ?, included_in_net_asset = ?, selectable = ? , is_hidden = ?  " +
                "WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, account.getName());
            stmt.setBigDecimal(2, account.getBalance());
            stmt.setString(3, account.getType() != null ? account.getType().name() : null);
            stmt.setString(4, account.getCategory() != null ? account.getCategory().name() : null);
            stmt.setString(5, account.getNotes());
            stmt.setBoolean(6, account.getIncludedInNetAsset());
            stmt.setBoolean(7, account.getSelectable());
            stmt.setBoolean(8, account.getHidden());
            stmt.setLong(9, account.getId());
            stmt.executeUpdate();
        }

        if (account instanceof CreditAccount credit) {
            String sqlCredit = "UPDATE credit_account SET credit_limit=?, current_debt=?, bill_date=?, due_date=? " +
                    "WHERE id=?";
            try (PreparedStatement stmt = connection.prepareStatement(sqlCredit)) {
                stmt.setBigDecimal(1, credit.getCreditLimit());
                stmt.setBigDecimal(2, credit.getCurrentDebt());

                if (credit.getBillDay() != null) {
                    stmt.setInt(3, credit.getBillDay());
                }else {
                    stmt.setNull(3, Types.INTEGER);
                }

                if (credit.getDueDay() != null) {
                    stmt.setInt(4, credit.getDueDay());
                }else {
                    stmt.setNull(4, Types.INTEGER);
                }
                stmt.setLong(5, credit.getId());
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }

        } else if (account instanceof LoanAccount loan) {
            String sqlLoan = "UPDATE loan_account SET total_periods=?, repaid_periods=?, annual_interest_rate=?, " +
                    "loan_amount=?, repayment_date=?, repayment_type=?, loan_remaining_amount=?, is_ended=? " +
                    "WHERE id=?";
            try (PreparedStatement stmt = connection.prepareStatement(sqlLoan)) {
                stmt.setInt(1, loan.getTotalPeriods());
                stmt.setInt(2, loan.getRepaidPeriods());
                stmt.setBigDecimal(3, loan.getAnnualInterestRate());
                stmt.setBigDecimal(4, loan.getLoanAmount());

                /*if (loan.getReceivingAccount() != null) {
                    stmt.setLong(5, loan.getReceivingAccount().getId());
                }else {
                    stmt.setNull(5, Types.BIGINT);
                }*/
                if (loan.getRepaymentDay() != null) {
                    stmt.setDate(5, Date.valueOf(loan.getRepaymentDay()));
                } else {
                    stmt.setNull(5, Types.DATE);
                }
                if (loan.getRepaymentType() != null) {
                    stmt.setString(6, loan.getRepaymentType().name());
                }else {
                    stmt.setNull(6, java.sql.Types.VARCHAR);
                }
                stmt.setBigDecimal(7, loan.getRemainingAmount());
                stmt.setBoolean(8, loan.getIsEnded());
                stmt.setLong(9, loan.getId());

                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }

        } else if (account instanceof BorrowingAccount borrowing) {
            String sqlBorrow = "UPDATE borrowing_account SET is_ended=?, borrowing_date=?, borrowing_amount=?, " +
                    "borrowing_remaining_amount =? " +
                    "WHERE id=?";
            try (PreparedStatement stmt = connection.prepareStatement(sqlBorrow)) {
                stmt.setBoolean(1, borrowing.getIsEnded());

                if (borrowing.getBorrowingDate() != null) {
                    stmt.setDate(2, Date.valueOf(borrowing.getBorrowingDate()));
                }else {
                    stmt.setNull(2, Types.DATE);
                }
                stmt.setBigDecimal(3, borrowing.getBorrowingAmount());
                stmt.setBigDecimal(4, borrowing.getRemainingAmount());
                stmt.setLong(5, borrowing.getId());
                //stmt.executeUpdate();
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }

        } else if (account instanceof LendingAccount lending) {
            String sqlLend = "UPDATE lending_account SET is_ended=?, lending_date=? WHERE id=?";
            try (PreparedStatement stmt = connection.prepareStatement(sqlLend)) {
                stmt.setBoolean(1, lending.getIsEnded());
                if (lending.getDate() != null)
                    stmt.setDate(2, java.sql.Date.valueOf(lending.getDate()));
                else
                    stmt.setNull(2, java.sql.Types.DATE);

                stmt.setLong(3, lending.getId());
                //stmt.executeUpdate();
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }

        } else if (account instanceof BasicAccount) {
            // No additional fields to update for BasicAccount
            String sqlBasic = "UPDATE basic_account SET id=? WHERE id=?";
            try (PreparedStatement stmt = connection.prepareStatement(sqlBasic)) {
                stmt.setLong(1, account.getId());
                stmt.setLong(2, account.getId());
                //stmt.executeUpdate();
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean deleteAccount(Account account) throws SQLException {
        connection.setAutoCommit(false); // Start transaction

        String sql = "DELETE FROM accounts WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            int affected = stmt.executeUpdate();
            return affected > 0;
        }

    }

}
