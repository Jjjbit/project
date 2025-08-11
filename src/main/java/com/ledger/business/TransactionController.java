package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.CategoryComponentDAO;
import com.ledger.orm.LedgerDAO;
import com.ledger.orm.TransactionDAO;
import jakarta.transaction.Transactional;

import java.time.YearMonth;
import java.util.List;

public class TransactionController {
    private TransactionDAO transactionDAO;
    private AccountDAO accountDAO;
    private LedgerDAO ledgerDAO;
    private CategoryComponentDAO categoryComponentDAO;

    public TransactionController(TransactionDAO transactionDAO, AccountDAO accountDAO, LedgerDAO ledgerDAO, CategoryComponentDAO categoryComponentDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
        this.ledgerDAO = ledgerDAO;
        this.categoryComponentDAO = categoryComponentDAO;
    }

    @Transactional
    public void createTransaction(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        if (transaction.getAccount() == null || transaction.getAccount().getId() == null) {
            throw new IllegalArgumentException("Transaction account cannot be null");
        }
        if (transaction.getLedger() == null || transaction.getLedger().getId() == null) {
            throw new IllegalArgumentException("Transaction ledger cannot be null");
        }
        if(transaction.getLedger().getOwner()!= transaction.getAccount().getOwner()){
            throw new IllegalArgumentException("Transaction account owner must match the ledger owner");
        }

        transaction.execute();

        User owner = transaction.getLedger().getOwner();
        if (owner == null) {
            throw new IllegalArgumentException("Transaction ledger owner cannot be null");
        }

        owner.updateTotalAssets();
        owner.updateTotalLiabilities();
        owner.updateNetAsset();

        transactionDAO.save(transaction);
    }

    /*@Transactional
    public void changeLedger(Long transactionId, Long newLedgerId){
        Ledger newLedger = ledgerDAO.findById(newLedgerId);
        if (newLedger == null) {
            throw new IllegalArgumentException("New ledger not found");
        }
        Transaction transaction = transactionDAO.findById(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found");
        }
        transaction.changeLedger(newLedger);
        transactionDAO.update(transaction);
    }*/

    @Transactional
    public void addTransactionToLedger(Long ledgerId, Long transactionId) { //for transaction that is not in a ledger
        Ledger ledger = ledgerDAO.findById(ledgerId);

        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        Transaction transaction = transactionDAO.findById(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found");
        }
        ledger.addTransaction(transactionDAO.findById(transactionId));
        //ledgerDAO.update(ledger);
    }

    @Transactional
    public void removeTransactionFromLedger(Long ledgerId, Long transactionId) {
        Ledger ledger = ledgerDAO.findById(ledgerId);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        Transaction transaction = transactionDAO.findById(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found");
        }
        if (!ledger.getTransactions().contains(transaction)) {
            throw new IllegalArgumentException("Transaction not found in the specified ledger");
        }
        ledger.removeTransaction(transactionDAO.findById(transactionId));
        //ledgerDAO.update(ledger);
    }

    @Transactional
    public void deleteTransaction(Long transactionId) {
        Transaction transaction = transactionDAO.findById(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found");
        }
        transactionDAO.delete(transactionId);
    }

    //TODO
    @Transactional
    public void modifyTransaction(Long transactionId, Transaction updateTransaction) {
        Transaction transaction = transactionDAO.findById(transactionId);
        if (transaction != null) {
            transaction.setAmount(updateTransaction.getAmount());
            transaction.setDate(updateTransaction.getDate());
            transaction.setNote(updateTransaction.getNote());
            transaction.setAccount(updateTransaction.getAccount());
            transaction.setCategory(updateTransaction.getCategory());
            transaction.setType(updateTransaction.getType());
            transaction.setLedger(updateTransaction.getLedger());
        }
        transactionDAO.update(transaction);
    }

    public List<Transaction> getTransactionsForMonthByLedgerId(Long ledgerId, YearMonth month) {
        Ledger ledger = ledgerDAO.findById(ledgerId);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        return ledger.getTransactionsForMonth(month);
    }

    public List<Transaction> getTransactionsForMonthByAccountId(Long accountId, YearMonth month) {
        Account account = accountDAO.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        return account.getTransactionsForMonth(month);
    }


    public List<Transaction> getTransactionsForMonthByCategoryId(Long categoryId, YearMonth month) {
        CategoryComponent category = categoryComponentDAO.findById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        return category.getTransactionsForMonth(month);
    }

}
