package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.TransactionDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class TransactionManager {
    private final TransactionDAO transactionDAO;
    private final EntityManager em;

    public TransactionManager(TransactionDAO transactionDAO, EntityManager em) {
        this.transactionDAO = transactionDAO;
        this.em = em;
    }

    public Transaction createTransaction(Ledger ledger, Account account, CategoryComponent category, BigDecimal amount, LocalDate date, String notes, TransactionType type, Account toAccount){
        Transaction transaction;
        switch (type) {
            case EXPENSE:
                transaction = new Expense(date, amount, notes, account, ledger, category);
                return persistTransaction(transaction);
            case INCOME:
                transaction = new Income(date, amount, notes, account, ledger, category);
                return persistTransaction(transaction);
            case TRANSFER:
                transaction = new Transfer(date, notes, account, toAccount, amount, ledger);
                return persistTransaction(transaction);
            default:
                throw new IllegalArgumentException("Unsupported transaction type: " + type);
        }
    }

    public void deleteTransaction(Transaction tx){
        EntityTransaction et = em.getTransaction();
        try {
            et.begin();
            undoTransaction(tx);
            transactionDAO.delete(tx.getId());
            et.commit();
        }catch(Exception e){
            if (et.isActive()) {
                et.rollback();
            }
            throw e;
        }
    }
    public Transaction editTransaction(Long txId, Ledger ledger, Account account, CategoryComponent category, BigDecimal amount, LocalDate date, String notes, Account toAccount) {
        Transaction tx = transactionDAO.findById(txId);
        EntityTransaction et = em.getTransaction();
        try{
            et.begin();
            if (tx == null) {
                throw new IllegalArgumentException("Transaction not found");
            }
            undoTransaction(tx);
            tx.setAmount(amount);
            tx.setDate(date);
            tx.setNote(notes);
            tx.setCategory(category);
            tx.setLedger(ledger);
            if (tx.getAccount() != null && !tx.getAccount().equals(account)) {
                tx.setAccount(account);
            }

            if (tx.getType() == TransactionType.TRANSFER && toAccount != null) {
                ((Transfer) tx).setToAccount(toAccount);
            }
            tx.execute();
            transactionDAO.update(tx);
            et.commit();
            return tx;
        }catch(Exception e){
            if (et.isActive()) {
                et.rollback();
            }
            throw e;
        }

    }



    private Transaction persistTransaction (Transaction tx){
            EntityTransaction et =  em.getTransaction();
            try {
                et.begin();
                tx.execute();
                transactionDAO.save(tx);
                et.commit();
                return tx;
            }catch(Exception e){
                if (et.isActive()) {
                    et.rollback();
                }
                throw e;
            }
    }

    private void undoTransaction(Transaction tx) {
        if(tx instanceof Expense) {
            tx.getAccount().credit(tx.getAmount());
            tx.getAccount().removeTransaction(tx);
        } else if(tx instanceof Income) {
            tx.getAccount().debit(tx.getAmount());
            tx.getAccount().removeTransaction(tx);
        } else if(tx instanceof Transfer) {
            Transfer transfer = (Transfer) tx;
            transfer.getAccount().credit(transfer.getAmount());
            transfer.getAccount().removeTransaction(tx);
            transfer.getToAccount().debit(transfer.getAmount());
            transfer.getToAccount().removeTransaction(tx);
        }
        tx.getLedger().removeTransaction(tx);
        tx.getCategory().removeTransaction(tx);
    }



}
