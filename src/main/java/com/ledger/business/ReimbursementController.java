package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ReimbursementController {
    private final TransactionDAO transactionDAO;
    private final ReimbursementDAO reimbursementDAO;
    private final ReimbursementTxLinkDAO reimbursementTxLinkDAO;
    private final LedgerCategoryDAO ledgerCategoryDAO;
    private final AccountDAO accountDAO;

    public ReimbursementController(TransactionDAO transactionDAO, ReimbursementDAO reimbursementDAO,
                                   ReimbursementTxLinkDAO reimbursementTxLinkDAO,
                                   LedgerCategoryDAO ledgerCategoryDAO, AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
        this.reimbursementTxLinkDAO = reimbursementTxLinkDAO;
        this.transactionDAO = transactionDAO;
        this.reimbursementDAO = reimbursementDAO;
    }

//    public Reimbursement getReimbursementByTransaction (Transaction transaction) {
//        return reimbursementDAO.getByOriginalTransactionId(transaction.getId());
//    }

    public List<Reimbursement> getReimbursementsByLedger (Ledger ledger) {
        return reimbursementDAO.getByLedger(ledger);
    }

    public Reimbursement create(BigDecimal amount, Account fromAccount, Ledger ledger, String name) {
        if(name == null || name.isEmpty()) {
            name = "Reimbursement Plan";
        }
        if(amount == null) {
            return null;
        }
        if(fromAccount == null) {
            return null;
        }
        if(ledger == null) {
            return null;
        }

        Reimbursement reimbursement = new Reimbursement(amount, false, fromAccount, ledger, name);

        fromAccount.debit(amount);
        accountDAO.update(fromAccount);

        boolean inserted = reimbursementDAO.insert(reimbursement);
        if(!inserted) {
            return null;
        }
        return reimbursement;
    }

    public boolean claim (Reimbursement record, BigDecimal amount, Boolean isFinalClaim, Account toAccount,
                          LocalDate date) {
        if(record == null) {
            return false;
        }
        if(date == null) {
            date = LocalDate.now();
        }
        if(record.isEnded()) {
            return false;
        }
        if(amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if(amount == null) {
            amount = record.getRemainingAmount();
        }
        if(toAccount == null) {
            toAccount = record.getFromAccount();
        }

        if(amount.compareTo(record.getRemainingAmount()) == 0) { //full claim
            Transfer transfer = new Transfer(date,
                    "Reimbursement Claim",
                    null,
                    toAccount,
                    record.getRemainingAmount(),
                    record.getLedger());
            transactionDAO.insert(transfer);

            record.setEnded(true);
            record.setRemainingAmount(BigDecimal.ZERO);

            reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());

        } else if(amount.compareTo(record.getRemainingAmount()) < 0) { //partial claim
            BigDecimal diff = record.getRemainingAmount().subtract(amount);
            if(isFinalClaim) { //it's final claim
                Transfer transfer = new Transfer(date,
                        "Reimbursement Claim",
                        null,
                        toAccount,
                        amount,
                        record.getLedger());
                transactionDAO.insert(transfer);

                record.setRemainingAmount(diff);
                record.setEnded(true);

                reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
            } else { //partial
                Transfer transfer = new Transfer(date,
                        "Reimbursement Claim",
                        null,
                        toAccount,
                        amount,
                        record.getLedger());
                transactionDAO.insert(transfer);

                record.setRemainingAmount(diff);

                reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
            }
        } else { //over claim
            BigDecimal diff = amount.subtract(record.getRemainingAmount());

            Transfer transfer = new Transfer(date,
                    "Reimbursement Claim",
                    null,
                    toAccount,
                    record.getRemainingAmount(),
                    record.getLedger());
            transactionDAO.insert(transfer);

            Income income = new Income(
                    date,
                    diff,
                    "Over Reimbursement Income",
                    toAccount,
                    record.getLedger(),
                    ledgerCategoryDAO.getByNameAndLedger("Claim Income", record.getLedger()));
            transactionDAO.insert(income);

            record.setRemainingAmount(record.getRemainingAmount().subtract(amount));
            record.setEnded(true);

            reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
            reimbursementTxLinkDAO.insert(record.getId(), income.getId());
        }

        toAccount.credit(amount);
        return reimbursementDAO.update(record) && accountDAO.update(toAccount);
    }

    //edit only pending reimbursement
    //it can change from account, total reimbursed amount, isEnded
    public boolean editReimbursement(Reimbursement record, BigDecimal newAmount, Account newFromAccount, String newName) {
        if(record == null) {
            return false;
        }
        if(record.isEnded()) {
            return false;
        }
        if(newAmount != null && newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if(newAmount == null){
            newAmount = record.getAmount();
        }
        if(newName != null && !newName.isEmpty()) {
            record.setName(newName);
        }
        if(newFromAccount == null) {
            newFromAccount = record.getFromAccount();
        }

        Account oldFromAccount = record.getFromAccount();
        oldFromAccount.credit(record.getAmount());
        newFromAccount.debit(newAmount);
        accountDAO.update(oldFromAccount);
        accountDAO.update(newFromAccount);

        BigDecimal oldRemaining = record.getRemainingAmount(); //it's positive
        BigDecimal reimbursedAmount = record.getAmount().subtract(oldRemaining);
        if(newAmount.compareTo(reimbursedAmount) < 0) {
            //new amount is less than already reimbursed amount
            BigDecimal diff = reimbursedAmount.subtract(newAmount);
            record.setEnded(true);
            record.setRemainingAmount(newAmount.subtract(reimbursedAmount)); //negative

            Income income = new Income(
                    LocalDate.now(),
                    diff,
                    "Reimbursement Income",
                    newFromAccount,
                    record.getLedger(),
                    ledgerCategoryDAO.getByNameAndLedger("Claim Income", record.getLedger()));
            transactionDAO.insert(income);
            reimbursementTxLinkDAO.insert(record.getId(), income.getId());

        } else {
            BigDecimal newRemaining = newAmount.subtract(reimbursedAmount);
            record.setRemainingAmount(newRemaining);
        }
        record.setFromAccount(newFromAccount);
        record.setAmount(newAmount);
        return reimbursementDAO.update(record);
    }

    //delete reimbursement and all linked transactions
    public boolean delete(Reimbursement record) {
        if(record == null) {
            return false;
        }

        List<Transaction> linkedTxs = reimbursementTxLinkDAO.getTransactionsByReimbursement(record);

        for(Transaction tx : linkedTxs) {
            Account toAccount = tx.getToAccount();
            if (toAccount != null) {
                Account cached = accountDAO.getAccountById(toAccount.getId());
                cached.debit(tx.getAmount());
                accountDAO.update(cached);
            }
            transactionDAO.delete(tx);
        }

        Account fromAccount = record.getFromAccount();
        fromAccount.credit(record.getAmount());
        accountDAO.update(fromAccount);
        return reimbursementDAO.delete(record);
    }


}
