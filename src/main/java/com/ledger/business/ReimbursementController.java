package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

//    public Reimbursement create(Transaction originalTransaction, BigDecimal amount, Ledger ledger, Account fromAccount) {
//        if(originalTransaction == null || amount == null || ledger == null) {
//            return null;
//        }
//        if(!originalTransaction.isReimbursable()) {
//            originalTransaction.setReimbursable(true);
//            transactionDAO.update(originalTransaction);
//        }
//        if(fromAccount == null) {
//            return null;
//        }
//        Reimbursement reimbursement = new Reimbursement(
//                //originalTransaction,
//                amount,
//                ReimbursableStatus.PENDING,
//                //ledger
//                fromAccount
//        );
//        boolean inserted = reimbursementDAO.insert(reimbursement);
//        if(!inserted) {
//            return null;
//        }
//        return reimbursement;
//    }

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

//    public boolean claim (Reimbursement record, BigDecimal amount, Boolean isFinalClaim, Account toAccount,
//                          LocalDate date) {
//        if(record == null) {
//            return false;
//        }
//        if(date == null) {
//            date = LocalDate.now();
//        }
//        if(record.getReimbursementStatus() == ReimbursableStatus.FULL) {
//            return false;
//        }
//        if(amount == null) {
//            amount = record.getAmount();
//        }
//
//        Expense expense = (Expense) record.getOriginalTransaction();
//        if(toAccount == null) {
//            toAccount = expense.getFromAccount();
//        }
//
//        if(amount.compareTo(record.getRemainingAmount())  == 0) { //full claim
//            Transfer transfer = new Transfer(date,
//                    "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
//                    null,
//                    toAccount,
//                    amount,
//                    expense.getLedger());
//            transfer.setReimbursable(true);
//            transactionDAO.insert(transfer);
//
//            record.setStatus(ReimbursableStatus.FULL);
//            record.setRemainingAmount(BigDecimal.ZERO);
//
//            reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
//
//        } else if (amount.compareTo(record.getRemainingAmount()) < 0) { //partial claim
//            BigDecimal diff = record.getRemainingAmount().subtract(amount);
//
//            if(isFinalClaim) { //full
//                Transfer transfer = new Transfer(date,
//                        "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
//                        null,
//                        toAccount,
//                        amount,
//                        expense.getLedger());
//                transfer.setReimbursable(true);
//                transactionDAO.insert(transfer);
//
//                record.setRemainingAmount(diff);
//                record.setStatus(ReimbursableStatus.FULL);
//
//                expense.setReimbursable(false);
//                expense.setAmount(diff);
//
//                reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
//                transactionDAO.update(expense);
//            } else { //partial
//                Transfer transfer = new Transfer(date,
//                        "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
//                        null,
//                        toAccount,
//                        amount,
//                        expense.getLedger());
//                transfer.setReimbursable(true);
//                transactionDAO.insert(transfer);
//
//                record.setRemainingAmount(diff);
//
//                reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
//            }
//        } else { //over claim
//            BigDecimal diff = amount.subtract(record.getAmount());
//            Transfer transfer = new Transfer(date,
//                    "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
//                    null,
//                    toAccount,
//                    record.getAmount(),
//                    expense.getLedger());
//            transfer.setReimbursable(true);
//            transactionDAO.insert(transfer);
//
//            Income income = new Income(
//                    date,
//                    diff,
//                    "Over Reimbursement Income for Expense: " + expense.getCategory().getName(),
//                    toAccount,
//                    expense.getLedger(),
//                    ledgerCategoryDAO.getByNameAndLedger("Claim Income", expense.getLedger()));
//            transactionDAO.insert(income);
//
//            reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
//            reimbursementTxLinkDAO.insert(record.getId(), income.getId());
//
//            record.setStatus(ReimbursableStatus.FULL);
//            record.setRemainingAmount(BigDecimal.ZERO);
//        }
//
//        toAccount.credit(amount);
//        return reimbursementDAO.update(record) && accountDAO.update(toAccount);
//    }

    //edit only pending reimbursement
    //it can change from account, total reimbursed amount, isEnded
    public boolean editReimbursement(Reimbursement record, BigDecimal newAmount, Account newFromAccount) {
        if(record == null) {
            return false;
        }
        if(record.isEnded() || record.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if(newAmount != null && newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if(newAmount == null){
            newAmount = record.getAmount();
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

    //delete reimbursement, all linked transactions linked to reimbursement and original transaction
//    public boolean delete(Reimbursement record) {
//        if(record == null) {
//            return false;
//        }
//        List<Transaction> linkedTxs = reimbursementTxLinkDAO.getTransactionsByReimbursement(record);
//        Map<Long, Account> accountCache = new HashMap<>();
//        for(Transaction tx : linkedTxs) {
//            Account toAccount = tx.getToAccount();
//            if (toAccount != null) {
//                Account cached = accountCache.computeIfAbsent(toAccount.getId(), id -> toAccount); //an account can be rolled back only once
//                cached.debit(tx.getAmount());
//                accountDAO.update(cached);
//            }
//
//            transactionDAO.delete(tx);
//        }
//
//        Transaction originalTx = record.getOriginalTransaction();
//        Account fromAccount = originalTx.getFromAccount();
//        fromAccount.credit(record.getAmount());
//
//        return transactionDAO.delete(originalTx) && accountDAO.update(fromAccount);
//    }

    //??
    public boolean cancelClaims( Reimbursement record) {
        if(record == null) {
            return false;
        }
        if(!record.isEnded()) {
            return false;
        }

        List<Transaction> linkedTxs = reimbursementTxLinkDAO.getTransactionsByReimbursement(record);
        Map<Long, Account> accountCache = new HashMap<>();

        for(Transaction tx : linkedTxs) {
            Account toAccount = tx.getToAccount();
            if (toAccount != null) {
                Account cached = accountCache.computeIfAbsent(toAccount.getId(), id -> toAccount); //an account can be rolled back only once
                cached.debit(tx.getAmount());
                accountDAO.update(cached);
            }

            transactionDAO.delete(tx);
        }

        record.setEnded(false);
        record.setRemainingAmount(record.getAmount());
        return reimbursementDAO.update(record);
    }

    //cancel only completed reimbursement
    //cancel all transactions linked to reimbursement and reset reimbursement but keep original transaction and reimbursement
//    public boolean cancelClaims( Reimbursement record) {
//        if(record == null) {
//            return false;
//        }
//        if(record.getReimbursementStatus() == ReimbursableStatus.PENDING) {
//            return false;
//        }
//
//        Expense expense = (Expense) record.getOriginalTransaction();
//        List<Transaction> linkedTxs = reimbursementTxLinkDAO.getTransactionsByReimbursement(record);
//        Map<Long, Account> accountCache = new HashMap<>();
//
//        for(Transaction tx : linkedTxs) {
//            Account toAccount = tx.getToAccount();
//            if (toAccount != null) {
//                Account cached = accountCache.computeIfAbsent(toAccount.getId(), id -> toAccount); //an account can be rolled back only once
//                cached.debit(tx.getAmount());
//                accountDAO.update(cached);
//            }
//
//            transactionDAO.delete(tx);
//        }
//
//        record.setStatus(ReimbursableStatus.PENDING);
//        record.setRemainingAmount(record.getAmount());
//        expense.setReimbursable(true);
//        return transactionDAO.update(expense) && reimbursementDAO.update(record);
//    }
}
