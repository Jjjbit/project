package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.TransactionDAO;
import com.ledger.transaction.DbTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static java.util.Comparator.comparing;

public class TransactionController {
    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;

    public TransactionController(TransactionDAO transactionDAO, AccountDAO accountDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
    }

    public List<Transaction> getTransactionsByLedgerInRangeDate(Ledger ledger, LocalDate startDate, LocalDate endDate){
        return transactionDAO.getByLedgerId(ledger.getId()).stream()
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                .sorted((comparing(Transaction::getDate).reversed()))
                .toList();
    }
    public List<Transaction> getTransactionsByAccountInRangeDate(Account account, LocalDate startDate, LocalDate endDate) {
        return transactionDAO.getByAccountId(account.getId()).stream()
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                .sorted((comparing(Transaction::getDate).reversed()))
                .toList();
    }

//    public Income createIncome(Ledger ledger, Account toAccount, LedgerCategory category, String note, LocalDate date, BigDecimal amount) {
//        if (ledger == null) {
//            return null;
//        }
//        if (category == null) {
//            return null;
//        }
//        if (category.getType() != CategoryType.INCOME) {
//            return null;
//        }
//        if (amount == null) {
//            amount = BigDecimal.ZERO;
//        }
//        if (amount.compareTo(BigDecimal.ZERO) < 0) {
//            return null;
//        }
//        if( toAccount == null || !toAccount.getSelectable()) {
//            return null;
//        }
//        Income incomeTransaction = new Income(date != null ? date : LocalDate.now(), amount, note, toAccount,
//                ledger, category);
//        try {
//            if (!transactionDAO.insert(incomeTransaction)) {
//                return null;
//            }
//            toAccount.credit(amount);
//            if (!accountDAO.update(toAccount)) {
//                throw new SQLException("Account balance update failed");
//            }
//            return incomeTransaction;
//        } catch (Exception e) {
//            System.err.println("Error: " + e.getMessage());
//            if (incomeTransaction.getId() != 0) {
//                transactionDAO.delete(incomeTransaction);
//            }
//            return null;
//        }
//    }
    public Income createIncome(Ledger ledger, Account toAccount, LedgerCategory category, String note, LocalDate date, BigDecimal amount) {
        if (ledger == null) {
            return null;
        }
        if (category == null) {
            return null;
        }
        if (category.getType() != CategoryType.INCOME) {
            return null;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }
        if( toAccount == null || !toAccount.getSelectable()) {
            return null;
        }
        Income incomeTransaction = new Income(date != null ? date : LocalDate.now(), amount, note, toAccount,
                ledger, category);
        toAccount.credit(amount);
        return DbTransactionManager.getInstance().execute(() -> {
            if (!transactionDAO.insert(incomeTransaction)) throw new Exception("Failed to insert income transaction");
            if(!accountDAO.update(toAccount)) throw new Exception("Account balance update failed");
            return incomeTransaction;
        });
    }

//    public Expense createExpense(Ledger ledger, Account fromAccount, LedgerCategory category, String note, LocalDate date, BigDecimal amount) {
//        if (ledger == null) {
//            return null;
//        }
//        if (category == null) {
//            return null;
//        }
//        if (category.getType() != CategoryType.EXPENSE) {
//            return null;
//        }
//        if (amount == null) {
//            amount = BigDecimal.ZERO;
//        }
//        if (amount.compareTo(BigDecimal.ZERO) < 0) {
//            return null;
//        }
//        if( fromAccount == null || !fromAccount.getSelectable()) {
//            return null;
//        }
//        Expense expenseTransaction = new Expense(date != null ? date : LocalDate.now(), amount, note, fromAccount, ledger, category);
//        try {
//            if(!transactionDAO.insert(expenseTransaction)){
//                return null;
//            }
//            fromAccount.debit(amount);
//            if(!accountDAO.update(fromAccount)) throw new Exception("Account balance update failed");
//
//            return expenseTransaction;
//        }catch (Exception e) {
//            System.err.println("Error: " + e.getMessage());
//            if (expenseTransaction.getId() != 0) {
//                transactionDAO.delete(expenseTransaction);
//            }
//            return null;
//        }
//    }
    public Expense createExpense(Ledger ledger, Account fromAccount, LedgerCategory category, String note, LocalDate date, BigDecimal amount) {
        if (ledger == null) {
            return null;
        }
        if (category == null) {
            return null;
        }
        if (category.getType() != CategoryType.EXPENSE) {
            return null;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }
        if( fromAccount == null || !fromAccount.getSelectable()) {
            return null;
        }
        Expense expenseTransaction = new Expense(date != null ? date : LocalDate.now(), amount, note, fromAccount, ledger, category);
        fromAccount.debit(amount);
        return DbTransactionManager.getInstance().execute(() -> {
            if(!transactionDAO.insert(expenseTransaction)) throw new Exception("Failed to insert expense transaction");
            if(!accountDAO.update(fromAccount)) throw new Exception("Account balance update failed");
            return expenseTransaction;
        });
    }

//    public Transfer createTransfer(Ledger ledger, Account fromAccount, Account toAccount, String note, LocalDate date, BigDecimal amount) {
//        if( ledger == null) {
//            return null;
//        }
//        if (fromAccount != null && toAccount != null && fromAccount.getId() == toAccount.getId()) {
//            return null;
//        }
//        if( fromAccount == null && toAccount == null) {
//            return null;
//        }
//        if( fromAccount != null && !fromAccount.getSelectable()) {
//            return null;
//        }
//        if( toAccount != null && !toAccount.getSelectable()) {
//            return null;
//        }
//        if( amount == null) {
//            amount = BigDecimal.ZERO;
//        }
//        if (amount.compareTo(BigDecimal.ZERO) < 0) {
//            return null;
//        }
//        Transfer transferTransaction = new Transfer(date != null ? date : LocalDate.now(), note, fromAccount, toAccount, amount, ledger);
//        Connection connection = ConnectionManager.getInstance().getConnection();
//        try {
//            connection.setAutoCommit(false);
//            if(!transactionDAO.insert(transferTransaction)){
//                throw new SQLException("Failed to insert transfer transaction");
//            }
//
//            if (fromAccount != null) {
//                fromAccount.debit(amount);
//                if(!accountDAO.update(fromAccount)){
//                    throw new SQLException("Account balance update failed");
//                }
//            }
//            if (toAccount != null) {
//                toAccount.credit(amount);
//                if(!accountDAO.update(toAccount)){
//                    throw new SQLException("Account balance update failed");
//                }
//            }
//            connection.commit();
//            return transferTransaction;
//        }catch (Exception e) {
//            try {
//                System.err.println("Error: " + e.getMessage());
//                connection.rollback();
//            } catch (SQLException ex) {
//                System.err.println("Rollback failed: " + ex.getMessage());
//            }
//            return null;
//        }finally {
//            try {
//                connection.setAutoCommit(true);
//            } catch (SQLException e) {
//                System.err.println("Failed to reset auto-commit: " + e.getMessage());
//            }
//        }
//    }
    public Transfer createTransfer(Ledger ledger, Account fromAccount, Account toAccount, String note, LocalDate date, BigDecimal amount) {
        if( ledger == null) {
            return null;
        }
        if (fromAccount != null && toAccount != null && fromAccount.getId() == toAccount.getId()) {
            return null;
        }
        if( fromAccount == null && toAccount == null) {
            return null;
        }
        if( fromAccount != null && !fromAccount.getSelectable()) {
            return null;
        }
        if( toAccount != null && !toAccount.getSelectable()) {
            return null;
        }
        final BigDecimal finalAmount = (amount == null) ? BigDecimal.ZERO : amount;
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }
        Transfer transferTransaction = new Transfer(date != null ? date : LocalDate.now(), note, fromAccount, toAccount, finalAmount, ledger);

        return DbTransactionManager.getInstance().execute(() -> {
            if(!transactionDAO.insert(transferTransaction)) throw new Exception("Failed to insert transfer transaction");

            if (fromAccount != null) {
                fromAccount.debit(finalAmount);
                if(!accountDAO.update(fromAccount)) throw new Exception("Account balance update failed");
            }
            if (toAccount != null) {
                toAccount.credit(finalAmount);
                if(!accountDAO.update(toAccount)) throw new Exception("Account balance update failed");
            }
            return transferTransaction;
        });
    }

    public boolean deleteTransaction(Transaction tx) {
        if (tx == null) return false;
        Boolean deleted = DbTransactionManager.getInstance().execute(() -> {
            if (!transactionDAO.delete(tx)) throw new Exception("Delete transaction failed");

            Account toAccount = null;
            Account fromAccount = null;
            if( tx.getToAccount() != null) {
                toAccount = accountDAO.getAccountById(tx.getToAccount().getId());
            }
            if( tx.getFromAccount() != null) {
                fromAccount = accountDAO.getAccountById(tx.getFromAccount().getId());
            }
            switch (tx.getType()) {
                case INCOME:
                    if(toAccount != null) {
                        toAccount.debit(tx.getAmount());
                        if (!accountDAO.update(toAccount)) throw new Exception("Update toAccount failed");
                    }
                    break;
                case EXPENSE:
                    if (fromAccount != null) {
                        fromAccount.credit(tx.getAmount());
                        if (!accountDAO.update(fromAccount)) throw new Exception("Update fromAccount failed");
                    }
                    break;
                case TRANSFER:
                    //rollback fromAccount
                    if (fromAccount != null) {
                        fromAccount.credit(tx.getAmount());
                        if(!accountDAO.update(fromAccount)) throw new Exception("Update fromAccount failed");
                    }
                    //rollback toAccount
                    if (toAccount != null) {
                        toAccount.debit(tx.getAmount());
                        if(!accountDAO.update(toAccount)) throw new Exception("Update toAccount failed");
                    }
                    break;
            }
            return true;
        });
        return deleted != null && deleted;
    }

//    public boolean deleteTransaction(Transaction tx) {
//        if (tx == null) {
//            return false;
//        }
//        Account toAccount = null;
//        Account fromAccount = null;
//        if( tx.getToAccount() != null) {
//            toAccount = accountDAO.getAccountById(tx.getToAccount().getId());
//        }
//        if( tx.getFromAccount() != null) {
//            fromAccount = accountDAO.getAccountById(tx.getFromAccount().getId());
//        }
//        Connection connection = ConnectionManager.getInstance().getConnection();
//        try {
//            connection.setAutoCommit(false);
//            if (!transactionDAO.delete(tx)) throw new SQLException("Delete transaction failed");
//            switch (tx.getType()) {
//                case INCOME:
//                    if(toAccount!= null) {
//                        toAccount = accountDAO.getAccountById(tx.getToAccount().getId());
//                        toAccount.debit(tx.getAmount());
//                        if (!accountDAO.update(toAccount)) throw new SQLException("Update toAccount failed");
//                    }
//                    break;
//                case EXPENSE:
//                    if (fromAccount != null) {
//                        fromAccount = accountDAO.getAccountById(tx.getFromAccount().getId());
//                        fromAccount.credit(tx.getAmount());
//                        if (!accountDAO.update(fromAccount)) throw new SQLException("Update fromAccount failed");
//                    }
//                    break;
//                case TRANSFER:
//                    //rollback fromAccount
//                    if (fromAccount != null) {
//                        fromAccount = accountDAO.getAccountById(tx.getFromAccount().getId());
//                        fromAccount.credit(tx.getAmount());
//                        if(!accountDAO.update(fromAccount)) throw new SQLException("Update fromAccount failed");
//                    }
//                    //rollback toAccount
//                    if (toAccount != null) {
//                        toAccount = accountDAO.getAccountById(tx.getToAccount().getId());
//                        toAccount.debit(tx.getAmount());
//                        if(!accountDAO.update(toAccount)) throw new SQLException("Update toAccount failed");
//                    }
//                    break;
//            }
//            connection.commit();
//            return true;
//        }catch (SQLException e) {
//            System.err.println("Error: " + e.getMessage());
//            try {
//               connection.rollback();
//            } catch (SQLException ex) {
//                System.err.println("Rollback failed: " + ex.getMessage());
//            }
//            return false;
//        }finally {
//            try {
//                connection.setAutoCommit(true);
//            } catch (SQLException e) {
//                System.err.println("Failed to reset auto-commit: " + e.getMessage());
//            }
//        }
//    }

    public boolean updateIncome(Income income, Account toAccount, LedgerCategory category, String note, LocalDate date, BigDecimal amount, Ledger ledger) {
        if (income == null || toAccount == null || category == null || ledger == null || amount == null || date == null || !toAccount.getSelectable()) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        BigDecimal oldAmount = income.getAmount();
        Account oldToAccount = income.getToAccount();
        LedgerCategory oldCategory = income.getCategory();
        Ledger oldLedger = income.getLedger();

        if (ledger.getId() != oldLedger.getId()) { //change ledger
            income.setLedger(ledger);
        }
        if (category.getId() != oldCategory.getId()) { //change category
            if (category.getType() != CategoryType.INCOME) {
                return false;
            }
            income.setCategory(category);
        }

        Boolean updated = DbTransactionManager.getInstance().execute(() -> {
            oldToAccount.debit(oldAmount);
            if (!accountDAO.update(oldToAccount)) throw new Exception("Failed to rollback old account");
            toAccount.credit(amount);
            income.setToAccount(toAccount);
            if (!accountDAO.update(toAccount)) throw new Exception("Failed to apply new account");
            income.setAmount(amount);
            income.setDate(date);
            income.setNote(note);
            if(!transactionDAO.update(income)) throw new Exception("Failed to update income transaction");
            return true;
        });
        return updated != null && updated;
    }

//    public boolean updateIncome(Income income, Account toAccount, LedgerCategory category, String note, LocalDate date, BigDecimal amount, Ledger ledger) {
//        if (income == null || toAccount == null || category == null || amount == null|| ledger == null || date == null || !toAccount.getSelectable()) {
//            return false;
//        }
//        if (amount.compareTo(BigDecimal.ZERO) < 0) {
//            return false;
//        }
//        BigDecimal oldAmount = income.getAmount();
//        Account oldToAccount = income.getToAccount();
//        LedgerCategory oldCategory = income.getCategory();
//        Ledger oldLedger = income.getLedger();
//        if (ledger.getId() != oldLedger.getId()) { //change ledger
//            income.setLedger(ledger);
//        }
//        if (category.getId() != oldCategory.getId()) { //change category
//            if (category.getType() != CategoryType.INCOME) {
//                return false;
//            }
//            income.setCategory(category);
//        }
//        Connection connection= ConnectionManager.getInstance().getConnection();
//        try {
//            connection.setAutoCommit(false);
//
//            oldToAccount.debit(oldAmount);
//            if (!accountDAO.update(oldToAccount)) throw new SQLException("Failed to rollback old account");
//            toAccount.credit(amount);
//            income.setToAccount(toAccount);
//            if (!accountDAO.update(toAccount)) throw new SQLException("Failed to apply new account");
//
//            income.setAmount(amount);
//            income.setDate(date);
//            income.setNote(note);
//            if (!transactionDAO.update(income)) throw new SQLException("Failed to update transaction");
//
//            connection.commit();
//            return true;
//        } catch (SQLException e) {
//            try {
//                connection.rollback();
//            } catch (SQLException rollbackEx) {
//                System.err.println("Rollback failed: " + rollbackEx.getMessage());
//            }
//            return false;
//        } finally {
//            try {
//                connection.setAutoCommit(true);
//            } catch (SQLException finalEx) {
//                System.err.println("Failed to reset auto-commit: " + finalEx.getMessage());
//            }
//        }
//    }

    public boolean updateExpense(Expense expense, Account fromAccount, LedgerCategory category, String note, LocalDate date, BigDecimal amount, Ledger ledger) {
        if (expense == null || fromAccount == null || ledger == null || date == null || category == null || amount == null || !fromAccount.getSelectable()) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        } //change amount and amount>0
        BigDecimal oldAmount = expense.getAmount();
        Account oldFromAccount = expense.getFromAccount();
        LedgerCategory oldCategory = expense.getCategory();
        Ledger oldLedger = expense.getLedger();
        if (ledger.getId() != oldLedger.getId()) {
            expense.setLedger(ledger);
        }
        if (category.getId() != oldCategory.getId()) {
            if (category.getType() != CategoryType.EXPENSE) {
                return false;
            }
            expense.setCategory(category);
        }
        Boolean updated = DbTransactionManager.getInstance().execute(() -> {
            oldFromAccount.credit(oldAmount);
            if (!accountDAO.update(oldFromAccount)) throw new Exception("Failed to rollback old account");
            fromAccount.debit(amount);
            expense.setFromAccount(fromAccount);
            if (!accountDAO.update(fromAccount)) throw new Exception("Failed to apply new account");
            expense.setAmount(amount);
            expense.setDate(date);
            expense.setNote(note);
            if (!transactionDAO.update(expense)) throw new Exception("Failed to update expense transaction");
            return true;
        });
        return updated != null && updated;
    }

//    public boolean updateExpense(Expense expense, Account fromAccount, LedgerCategory category, String note, LocalDate date, BigDecimal amount, Ledger ledger) {
//        if (expense == null || fromAccount == null || category == null || ledger == null || amount == null || date == null || !fromAccount.getSelectable()) {
//            return false;
//        }
//        if (amount.compareTo(BigDecimal.ZERO) < 0) {
//            return false;
//        }
//        BigDecimal oldAmount = expense.getAmount();
//        Account oldFromAccount = expense.getFromAccount();
//        LedgerCategory oldCategory = expense.getCategory();
//        Ledger oldLedger = expense.getLedger();
//        if (ledger.getId() != oldLedger.getId()) { //change ledger
//            expense.setLedger(ledger);
//        }
//        if (category.getId() != oldCategory.getId()) { //change category
//            if (category.getType() != CategoryType.EXPENSE) {
//                return false;
//            }
//            expense.setCategory(category);
//        }
//        Connection connection= ConnectionManager.getInstance().getConnection();
//        try {
//            connection.setAutoCommit(false); //
//
//            oldFromAccount.credit(oldAmount);
//            if (!accountDAO.update(oldFromAccount)) throw new SQLException("Failed to rollback old account");
//
//            fromAccount.debit(amount);
//            expense.setFromAccount(fromAccount);
//            if (!accountDAO.update(fromAccount)) throw new SQLException("Failed to apply new account");
//
//            expense.setAmount(amount);
//            expense.setDate(date);
//            expense.setNote(note);
//            if (!transactionDAO.update(expense)) throw new SQLException("Failed to update transaction");
//
//            connection.commit();
//            return true;
//        } catch (SQLException e) {
//            try {
//                connection.rollback();
//            } catch (SQLException rollbackEx) {
//                System.err.println("Rollback failed: " + rollbackEx.getMessage());
//            }
//            return false;
//        } finally {
//            try {
//                connection.setAutoCommit(true);
//            } catch (SQLException finalEx) {
//                System.err.println("Failed to reset auto-commit: " + finalEx.getMessage());
//            }
//        }
//    }
//    public boolean updateTransfer(Transfer transfer, Account newFromAccount, Account newToAccount, String note, LocalDate date, BigDecimal amount, Ledger ledger) {
//        if (transfer == null || ledger == null || date == null || amount == null) {
//            return false;
//        }
//        if( newFromAccount == null && newToAccount == null) {
//            return false;
//        }
//        if (newFromAccount != null && newToAccount != null && newFromAccount.getId() == newToAccount.getId()) {
//            return false;
//        }
//        if(newFromAccount != null && !newFromAccount.getSelectable()) {
//            return false;
//        }
//        if(newToAccount != null && !newToAccount.getSelectable()) {
//            return false;
//        }
//        if (amount.compareTo(BigDecimal.ZERO) < 0) {
//            return false;
//        }
//        BigDecimal oldAmount = transfer.getAmount();
//        Account oldFromAccount = transfer.getFromAccount();
//        Account oldToAccount = transfer.getToAccount();
//        Ledger oldLedger = transfer.getLedger();
//        if (ledger.getId() != oldLedger.getId()) {
//            transfer.setLedger(ledger);
//        } //change ledger
//
//        Connection connection= ConnectionManager.getInstance().getConnection();
//        try {
//            connection.setAutoCommit(false);
//            if (oldFromAccount != null) {
//                oldFromAccount.credit(oldAmount);
//                if (!accountDAO.update(oldFromAccount)) throw new SQLException("Failed to rollback old from account");
//            }
//            if (oldToAccount != null) {
//                oldToAccount.debit(oldAmount);
//                if (!accountDAO.update(oldToAccount)) throw new SQLException("Failed to rollback old to account");
//            }
//
//            if (newFromAccount != null) {
//                newFromAccount.debit(amount);
//                if (!accountDAO.update(newFromAccount)) throw new SQLException("Failed to apply new from account");
//            }
//            if (newToAccount != null) {
//                newToAccount.credit(amount);
//                if (!accountDAO.update(newToAccount)) throw new SQLException("Failed to apply new to account");
//            }
//            transfer.setFromAccount(newFromAccount);
//            transfer.setToAccount(newToAccount);
//            transfer.setAmount(amount);
//            transfer.setDate(date);
//            transfer.setNote(note);
//            if (!transactionDAO.update(transfer)) throw new SQLException("Failed to update Transfer record");
//            connection.commit();
//            return true;
//        } catch (SQLException e) {
//            System.err.println("Failed to update Transfer: " + e.getMessage());
//            try {
//                connection.rollback();
//            } catch (SQLException rollbackEx) {
//                System.err.println("Error during rollback: " + rollbackEx.getMessage());
//            }
//            return false;
//        } finally {
//            try {
//                connection.setAutoCommit(true);
//            } catch (SQLException e) {
//                System.err.println("Failed to reset auto-commit: " + e.getMessage());
//            }
//        }
//    }

    public boolean updateTransfer(Transfer transfer, Account newFromAccount, Account newToAccount, String note, LocalDate date, BigDecimal amount, Ledger ledger) {
        if (transfer == null || ledger == null || date == null) {
            return false;
        }
        if( newFromAccount == null && newToAccount == null) {
            return false;
        }
        if (newFromAccount != null && newToAccount != null && newFromAccount.getId() == newToAccount.getId()) {
            return false;
        }
        if(newFromAccount != null && !newFromAccount.getSelectable()) {
            return false;
        }
        if(newToAccount != null && !newToAccount.getSelectable()) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        } //change amount and amount>0
        BigDecimal oldAmount = transfer.getAmount();
        Account oldFromAccount = transfer.getFromAccount();
        Account oldToAccount = transfer.getToAccount();
        Ledger oldLedger = transfer.getLedger();
        if (ledger.getId() != oldLedger.getId()) {
            transfer.setLedger(ledger);
        } //change ledger
        Boolean updated = DbTransactionManager.getInstance().execute(() -> {
            //rollback old accounts
            if (oldFromAccount != null) {
                oldFromAccount.credit(oldAmount);
                if (!accountDAO.update(oldFromAccount)) throw new Exception("Failed to rollback old from account");
            }
            if (oldToAccount != null) {
                oldToAccount.debit(oldAmount);
                if (!accountDAO.update(oldToAccount)) throw new Exception("Failed to rollback old to account");
            }
            //apply new accounts
            if (newFromAccount != null) {
                newFromAccount.debit(amount);
                if (!accountDAO.update(newFromAccount)) throw new Exception("Failed to apply new from account");
            }
            if (newToAccount != null) {
                newToAccount.credit(amount);
                if (!accountDAO.update(newToAccount)) throw new Exception("Failed to apply new to account");
            }
            transfer.setFromAccount(newFromAccount);
            transfer.setToAccount(newToAccount);
            transfer.setAmount(amount);
            transfer.setDate(date);
            transfer.setNote(note);
            if (!transactionDAO.update(transfer)) throw new Exception("Failed to update Transfer record");
            return true;
        });
        return updated != null && updated;
    }
}
