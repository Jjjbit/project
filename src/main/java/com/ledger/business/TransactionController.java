package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

public class TransactionController {
    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;

    public TransactionController(TransactionDAO transactionDAO, AccountDAO accountDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
    }

    public Income createIncome(Ledger ledger, Account toAccount, LedgerCategory category, String description,
                               LocalDate date, BigDecimal amount) {
        if (ledger == null) {
            return null;
        }

        if (category == null) {
            return null;
        }

        if (category.getType() != CategoryType.INCOME) {
            return null;
        }

        if (toAccount == null) {
            return null;
        }

        if (!toAccount.getSelectable()) {
            return null;
        }
        if (amount == null) {
            return null;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Income incomeTransaction = new Income(
                date != null ? date : LocalDate.now(),
                amount,
                description,
                toAccount,
                ledger,
                category
        );
        try {
            transactionDAO.insert(incomeTransaction);
            toAccount.credit(amount);
            accountDAO.update(toAccount); //update balance in database

            return incomeTransaction;
        } catch (SQLException e) {
            System.err.println("Error creating income transaction: " + e.getMessage());
            return null;
        }
    }

    public Expense createExpense(Ledger ledger, Account fromAccount, LedgerCategory category, String description,
                                 LocalDate date, BigDecimal amount) {

        if (category == null) {
            return null;
        }

        if (category.getType() != CategoryType.EXPENSE) {
            return null;
        }

        if (amount == null) {
            return null;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Expense expenseTransaction = new Expense(
                date != null ? date : LocalDate.now(),
                amount,
                description,
                fromAccount,
                ledger,
                category
        );
        try {
            transactionDAO.insert(expenseTransaction);
            fromAccount.debit(amount);
            accountDAO.update(fromAccount); //update balance in database

            return expenseTransaction;
        } catch (SQLException e) {
            System.err.println("Error creating expense transaction: " + e.getMessage());
            return null;
        }
    }

    public Transfer createTransfer(Ledger ledger, Account fromAccount, Account toAccount, String description,
                                   LocalDate date, BigDecimal amount) {

        if (fromAccount != null && toAccount != null && fromAccount.getId() == toAccount.getId()) {
            return null;
        }
        if( fromAccount == null && toAccount == null) {
            return null;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Transfer transferTransaction = new Transfer(
                date != null ? date : LocalDate.now(),
                description,
                fromAccount,
                toAccount,
                amount,
                ledger
        );
        try {
            transactionDAO.insert(transferTransaction);

            if (fromAccount != null) {
                fromAccount.debit(amount);
                accountDAO.update(fromAccount);
            }
            if (toAccount != null) {
                toAccount.credit(amount);
                accountDAO.update(toAccount);
            }
            return transferTransaction;
        } catch (SQLException e) {
            System.err.println("Error creating transfer transaction: " + e.getMessage());
            return null;
        }
    }

    public boolean deleteTransaction(Transaction tx) {
        if (tx == null) {
            return false;
        }

        Account fromAccount = tx.getFromAccount();
        Account toAccount = tx.getToAccount();

        try {
            switch (tx.getType()) {
                case INCOME:
                    toAccount.debit(tx.getAmount());
                    accountDAO.update(toAccount);
                    break;
                case EXPENSE:
                    fromAccount.credit(tx.getAmount());
                    accountDAO.update(fromAccount);
                    break;
                case TRANSFER:
                    if (fromAccount != null) {
                        fromAccount.credit(tx.getAmount());
                        accountDAO.update(fromAccount);
                    }
                    if (toAccount != null) {
                        toAccount.debit(tx.getAmount());
                        accountDAO.update(toAccount);
                    }
                    break;
            }

            return transactionDAO.delete(tx);
        } catch (SQLException e) {
            System.err.println("Error deleting transaction: " + e.getMessage());
            return false;
        }
    }


    //toAccount is null meaning no change
    //category is null meaning no change
    //ledger is null meaning no change
    //date is null meaning no change
    //amount is null meaning no change
    public boolean updateIncome(Income income, Account toAccount, LedgerCategory category, String note,
                                LocalDate date, BigDecimal amount, Ledger ledger) {
        if (income == null) {
            return false;
        }

        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        } //change amount and amount>0

        BigDecimal oldAmount = income.getAmount();
        Account oldToAccount = income.getToAccount();
        LedgerCategory oldCategory = income.getCategory();
        Ledger oldLedger = income.getLedger();

        if (ledger != null && ledger.getId() != oldLedger.getId()) {
            income.setLedger(ledger);
        }

        if (category != null && category.getId() != oldCategory.getId()) {
            if (category.getType() != CategoryType.INCOME) {
                return false;
            }

            income.setCategory(category);
        }

        if (toAccount != null && toAccount.getId() != oldToAccount.getId()) { //change account
            //rollback old account
            oldToAccount.debit(oldAmount);
            try {
                accountDAO.update(oldToAccount);
            } catch (SQLException e) {
                System.err.println("Error updating old toAccount during income update: " + e.getMessage());
                return false;
            }

            if (!toAccount.getSelectable()) {
                return false;
            }

            //apply new account
            toAccount.credit(amount != null ? amount : oldAmount);
            income.setToAccount(toAccount);
            try {
                accountDAO.update(toAccount);
            } catch (SQLException e) {
                System.err.println("Error updating new toAccount during income update: " + e.getMessage());
                return false;
            }
        }

        income.setAmount(amount != null ? amount : oldAmount);
        income.setDate(date != null ? date : income.getDate());
        income.setNote(note);
        try {
            return transactionDAO.update(income);
        } catch (SQLException e) {
            System.err.println("Error updating income transaction: " + e.getMessage());
            return false;
        }
    }

    public boolean updateExpense(Expense expense, Account fromAccount, LedgerCategory category, String note,
                                 LocalDate date, BigDecimal amount, Ledger ledger) {
        if (expense == null) {
            return false;
        }

        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        } //change amount and amount>0

        BigDecimal oldAmount = expense.getAmount();
        Account oldFromAccount = expense.getFromAccount();
        LedgerCategory oldCategory = expense.getCategory();
        Ledger oldLedger = expense.getLedger();

        if (ledger != null && ledger.getId() != oldLedger.getId()) {
            expense.setLedger(ledger);
        }

        if (category != null && category.getId() != oldCategory.getId()) {
            if (category.getType() != CategoryType.EXPENSE) {
                return false;
            }

            expense.setCategory(category);
        }

        if (fromAccount != null && fromAccount.getId() != oldFromAccount.getId()) { //change account
            //rollback old account
            oldFromAccount.credit(oldAmount);
            try {
                accountDAO.update(oldFromAccount);
            } catch (SQLException e) {
                System.err.println("Error updating old fromAccount during expense update: " + e.getMessage());
                return false;
            }

            if (!fromAccount.getSelectable()) {
                return false;
            }

            //apply new account
            fromAccount.debit(amount != null ? amount : oldAmount);
            expense.setFromAccount(fromAccount);
            try {
                accountDAO.update(fromAccount);
            } catch (SQLException e) {
                System.err.println("Error updating new fromAccount during expense update: " + e.getMessage());
                return false;
            }
        }

        expense.setAmount(amount != null ? amount : oldAmount);
        expense.setDate(date != null ? date : expense.getDate());
        expense.setNote(note);
        try {
            return transactionDAO.update(expense);
        } catch (SQLException e) {
            System.err.println("Error updating expense transaction: " + e.getMessage());
            return false;
        }
    }

    //newFromAccount or newToAccount can be null meaning removal of that account

    public boolean updateTransfer(Transfer transfer, Account newFromAccount, Account newToAccount,
                                  String note, LocalDate date, BigDecimal amount, Ledger ledger) {
        if (transfer == null) {
            return false;
        }

        if( newFromAccount == null && newToAccount == null) {
            return false;
        }

        if (newFromAccount != null && newToAccount != null && newFromAccount.getId() == newToAccount.getId()) {
            return false;
        }

        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        } //change amount and amount>0

        BigDecimal oldAmount = transfer.getAmount();
        Account oldFromAccount = transfer.getFromAccount();
        Account oldToAccount = transfer.getToAccount();
        Ledger oldLedger = transfer.getLedger();

        if (ledger != null && ledger.getId() != oldLedger.getId()) {
            transfer.setLedger(ledger);
        } //change ledger

        //fromAccount change or removal/addition
        if (newFromAccount != null && (oldFromAccount == null || newFromAccount.getId() != oldFromAccount.getId())
                || (newFromAccount == null && oldFromAccount != null)) {
            //rollback old fromAccount
            if (oldFromAccount != null) {
                oldFromAccount.credit(oldAmount);
                try {
                    accountDAO.update(oldFromAccount);
                } catch (SQLException e) {
                    System.err.println("Error updating old fromAccount during transfer update: " + e.getMessage());
                    return false;
                }
            }

            //apply new fromAccount
            if (newFromAccount != null) {
                if (!newFromAccount.getSelectable()) {
                    return false;
                }
                BigDecimal amountToApply = amount != null ? amount : oldAmount;
                newFromAccount.debit(amountToApply);

                try {
                    accountDAO.update(newFromAccount);
                } catch (SQLException e) {
                    System.err.println("Error updating new fromAccount during transfer update: " + e.getMessage());
                    return false;
                }
            }

            transfer.setFromAccount(newFromAccount); //apply new fromAccount (can be null)
        }

        //toAccount change
        if (newToAccount != null && (oldToAccount == null || newToAccount.getId()!= oldToAccount.getId())
                || (newToAccount == null && oldToAccount != null)) {
            //rollback old toAccount
            if (oldToAccount != null) {
                oldToAccount.debit(oldAmount);
                try {
                    accountDAO.update(oldToAccount);
                } catch (SQLException e) {
                    System.err.println("Error updating old toAccount during transfer update: " + e.getMessage());
                    return false;
                }
            }

            //apply new toAccount
            if( newToAccount != null) {
                if (!newToAccount.getSelectable()) {
                    return false;
                }
                BigDecimal amountToApply = amount != null ? amount : oldAmount;
                newToAccount.credit(amountToApply);

                try {
                    accountDAO.update(newToAccount);
                } catch (SQLException e) {
                    System.err.println("Error updating new toAccount during transfer update: " + e.getMessage());
                    return false;
                }
            }
            transfer.setToAccount(newToAccount); //apply new toAccount (can be null)
        }

        transfer.setAmount(amount != null ? amount : oldAmount);
        transfer.setDate(date != null ? date : transfer.getDate());
        transfer.setNote(note);
        try {
            return transactionDAO.update(transfer);
        } catch (SQLException e) {
            System.err.println("Error updating transfer transaction: " + e.getMessage());
            return false;
        }
    }

}
