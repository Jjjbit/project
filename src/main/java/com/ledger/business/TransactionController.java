package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.LedgerDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

public class TransactionController {
    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;
    private final LedgerDAO ledgerDAO;

    public TransactionController(TransactionDAO transactionDAO,
                                 AccountDAO accountDAO,
                                 LedgerDAO ledgerDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
        this.ledgerDAO = ledgerDAO;
    }

    public Income createIncome(Ledger ledger, Account toAccount, LedgerCategory category, String description,
                               LocalDate date, BigDecimal amount) {
        if(ledger == null){
            return null;
        }

        if(category == null){
            return null;
        }

        if(category.getType() != CategoryType.INCOME){
            return null;
        }

        if( toAccount == null){
            return null;
        }

        if(!toAccount.getSelectable()){
            return null;
        }
        if(amount == null){
            return null;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if(!category.getLedger().equals(ledger)){
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
            category.getTransactions().add(incomeTransaction);
            toAccount.credit(amount);
            toAccount.getTransactions().add(incomeTransaction);
            //toAccount.getIncomingTransactions().add(incomeTransaction);
            accountDAO.update(toAccount); //update balance in database

            ledger.getTransactions().add(incomeTransaction);

            return incomeTransaction;
        } catch (SQLException e){
            System.err.println("Error creating income transaction: " + e.getMessage());
            return null;
        }
    }

    public Expense createExpense(Ledger ledger, Account fromAccount, LedgerCategory category, String description,
                                 LocalDate date, BigDecimal amount) {

        if(category == null){
            return null;
        }

        if(category.getType() != CategoryType.EXPENSE){
            return null;
        }
        if(!category.getLedger().equals(ledger)){
            return null;
        }

        if(amount == null){
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
            category.getTransactions().add(expenseTransaction);
            fromAccount.debit(amount);
            fromAccount.getTransactions().add(expenseTransaction);
            //fromAccount.getOutgoingTransactions().add(expenseTransaction);
            accountDAO.update(fromAccount); //update balance in database

            ledger.getTransactions().add(expenseTransaction);


            return expenseTransaction;
        }catch (SQLException e){
            System.err.println("Error creating expense transaction: " + e.getMessage());
            return null;
        }
    }

    public Transfer createTransfer(Ledger ledger, Account fromAccount, Account toAccount, String description,
                                   LocalDate date, BigDecimal amount) {

        if(fromAccount != null && toAccount != null && fromAccount.getId().equals(toAccount.getId())){
            return null;
        }

        if(amount == null){
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
                fromAccount.getTransactions().add(transferTransaction);
                //fromAccount.getOutgoingTransactions().add(transferTransaction);
                accountDAO.update(fromAccount);
            }
            if (toAccount != null) {
                toAccount.credit(amount);
                toAccount.getTransactions().add(transferTransaction);
                //toAccount.getIncomingTransactions().add(transferTransaction);
                accountDAO.update(toAccount);
            }
            if (ledger != null) {
                ledger.getTransactions().add(transferTransaction);
            }
            return transferTransaction;
        }catch (SQLException e){
            System.err.println("Error creating transfer transaction: " + e.getMessage());
            return null;
        }
    }

    public boolean deleteTransaction(Transaction tx) {
        if(tx == null){
            return false;
        }

        Account fromAccount = tx.getFromAccount();
        Account toAccount = tx.getToAccount();
        LedgerCategory category = tx.getCategory();
        Ledger ledger = tx.getLedger();

        try {
            if (tx instanceof Income) {
                if (toAccount != null) {
                    toAccount.debit(tx.getAmount());
                    toAccount.getTransactions().remove(tx);
                    //toAccount.getIncomingTransactions().remove(tx);
                    accountDAO.update(toAccount);
                }
            } else if (tx instanceof Expense) {
                if (fromAccount != null) {
                    fromAccount.credit(tx.getAmount());
                    fromAccount.getTransactions().remove(tx);
                    //fromAccount.getOutgoingTransactions().remove(tx);
                    accountDAO.update(fromAccount);
                }
            } else if (tx instanceof Transfer) {
                if (fromAccount != null) {
                    fromAccount.credit(tx.getAmount());
                    fromAccount.getTransactions().remove(tx);
                    //fromAccount.getOutgoingTransactions().remove(tx);
                    accountDAO.update(fromAccount);
                }
                if (toAccount != null) {
                    toAccount.debit(tx.getAmount());
                    toAccount.getTransactions().remove(tx);
                    //toAccount.getIncomingTransactions().remove(tx);
                    accountDAO.update(toAccount);
                }
            }

            if (category != null) {
                category.getTransactions().remove(tx);
            }
            if (ledger != null) {
                ledger.getTransactions().remove(tx);
            }

            return transactionDAO.delete(tx);
        } catch (SQLException e) {
            System.err.println("Error deleting transaction: " + e.getMessage());
            return false;
        }
    }

    public boolean updateTransaction(Transaction tx, Account fromAccount, Account toAccount,
                                     LedgerCategory category, String note, LocalDate date, BigDecimal amount,
                                     Ledger ledger) {
        if (tx == null) {
            return false;
        }

        try {
            Ledger oldLedger = tx.getLedger();
            if (ledger != null) { //ledger change
                if (ledgerDAO.getById(ledger.getId()) == null) {
                    return false;
                }

                if (oldLedger != null && !oldLedger.getId().equals(ledger.getId())) {
                    oldLedger.getTransactions().remove(tx);
                    ledger.getTransactions().add(tx);
                    tx.setLedger(ledger);
                } else if (oldLedger == null) {
                    ledger.getTransactions().add(tx);
                    tx.setLedger(ledger);
                }
            }

            LedgerCategory oldCategory = tx.getCategory();
            if (category != null) { //category change
                if (ledger != null) {
                    if (!category.getLedger().getId().equals(ledger.getId())) {
                        return false;
                    }
                } else {
                    if (!category.getLedger().getId().equals(oldLedger.getId())) {
                        return false;
                    }
                }

                if (tx instanceof Income && category.getType() != CategoryType.INCOME) {
                    return false;
                }
                if (tx instanceof Expense && category.getType() != CategoryType.EXPENSE) {
                    return false;
                }
                if (oldCategory != null && !category.getId().equals(oldCategory.getId())) {
                    oldCategory.getTransactions().remove(tx);
                    category.getTransactions().add(tx);
                    tx.setCategory(category);
                } else if (oldCategory == null) {
                    category.getTransactions().add(tx);
                    tx.setCategory(category);
                }
            }


            if (tx instanceof Expense) {
                if (toAccount != null) { //change toAccount
                    return false;
                }

            }
            if (tx instanceof Income) {
                if (fromAccount != null) { //change fromAccount
                    return false;
                }
            }
            if (tx instanceof Transfer) {
                if (fromAccount != null && toAccount != null) { //change toAccount and fromAccount
                    if (fromAccount.equals(toAccount)) {
                        return false;
                    }
                }
            }

            if (amount != null) { //change amount
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    return false;
                }
            } else {
                amount = tx.getAmount();
            }
            Account prevFromAccount = tx.getFromAccount();
            Account prevToAccount = tx.getToAccount();
            if (amount.compareTo(tx.getAmount()) != 0) { //change amount
                if (fromAccount == null || fromAccount.getId().equals(prevFromAccount.getId())) { //fromAccount not changed or not provided
                    if (prevFromAccount != null) { //change amount only
                        prevFromAccount.credit(tx.getAmount()); //rollback previous amount
                        prevFromAccount.debit(amount);
                        accountDAO.update(prevFromAccount); //update balance in database
                    }
                } else { //change fromAccount and amount
                    if (accountDAO.getAccountById(fromAccount.getId()) == null) {
                        return false;
                    }
                    if (!fromAccount.getSelectable()) {
                        return false;
                    }
                    if (fromAccount instanceof LoanAccount) {
                        return false;
                    }


                    prevFromAccount.credit(tx.getAmount());
                    prevFromAccount.getTransactions().remove(tx);
                    //prevFromAccount.getOutgoingTransactions().remove(tx);
                    accountDAO.update(prevFromAccount);

                    fromAccount.debit(amount);
                    fromAccount.getTransactions().add(tx);
                    //fromAccount.getOutgoingTransactions().add(tx);
                    tx.setFromAccount(fromAccount);
                    accountDAO.update(fromAccount);
                }

                if (toAccount == null || toAccount.getId().equals(prevToAccount.getId())) { //toAccount not changed
                    if (prevToAccount != null) {
                        prevToAccount.debit(tx.getAmount());
                        prevToAccount.credit(amount);
                        accountDAO.update(prevToAccount);
                    }
                } else { //toAccount and amount changed
                    if (accountDAO.getAccountById(toAccount.getId()) == null) {
                        return false;
                    }
                    if (!toAccount.getSelectable()) {
                        return false;
                    }


                    prevToAccount.debit(tx.getAmount());
                    prevToAccount.getTransactions().remove(tx);
                    //prevToAccount.getIncomingTransactions().remove(tx);
                    accountDAO.update(prevToAccount);

                    toAccount.credit(amount);
                    toAccount.getTransactions().add(tx);
                    //toAccount.getIncomingTransactions().add(tx);
                    tx.setToAccount(toAccount);
                    accountDAO.update(toAccount);
                }
                tx.setAmount(amount);
            } else { //amount not changed
                //account changed
                if ((fromAccount != null && (prevFromAccount == null || !fromAccount.getId().equals(prevFromAccount.getId()))) ||
                        (toAccount != null && (prevToAccount == null || !toAccount.getId().equals(prevToAccount.getId())))) {

                    //rollback previous transaction
                    if (prevFromAccount != null) {
                        prevFromAccount.credit(tx.getAmount());
                        prevFromAccount.getTransactions().remove(tx);
                        //prevFromAccount.getOutgoingTransactions().remove(tx);
                        accountDAO.update(prevFromAccount);
                    }
                    if (prevToAccount != null) {
                        prevToAccount.debit(tx.getAmount());
                        prevToAccount.getTransactions().remove(tx);
                        //prevToAccount.getIncomingTransactions().remove(tx);
                        accountDAO.update(prevToAccount);
                    }

                    //apply new transaction
                    if (fromAccount != null) {
                        if (accountDAO.getAccountById(fromAccount.getId()) == null) {
                            return false;
                        }
                        if (!fromAccount.getSelectable()) {
                            return false;
                        }
                        if (fromAccount.getBalance().compareTo(amount) < 0) {
                            return false;
                        }
                        fromAccount.debit(amount);
                        fromAccount.getTransactions().add(tx);
                        //fromAccount.getOutgoingTransactions().add(tx);
                        tx.setFromAccount(fromAccount);
                        accountDAO.update(fromAccount);
                    } else {
                        tx.setFromAccount(null);
                    }
                    if (toAccount != null) {
                        if (accountDAO.getAccountById(toAccount.getId()) == null) {
                            return false;
                        }
                        if (!toAccount.getSelectable()) {
                            return false;
                        }
                        if (toAccount instanceof LoanAccount) {
                            return false;
                        }
                        toAccount.credit(amount);
                        toAccount.getTransactions().add(tx);
                        //toAccount.getIncomingTransactions().add(tx);
                        tx.setToAccount(toAccount);
                        accountDAO.update(toAccount);
                    } else {
                        tx.setToAccount(null);
                    }
                }
            }

            tx.setDate(date != null ? date : tx.getDate());
            tx.setNote(note != null ? note : tx.getNote());
            return transactionDAO.update(tx);

        } catch (SQLException e) {
            System.err.println("Error updating transaction: " + e.getMessage());
            return false;
        }
    }



}
