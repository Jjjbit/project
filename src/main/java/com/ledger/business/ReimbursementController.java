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

    public Reimbursement getReimbursementByTransaction (Transaction transaction) {
        return reimbursementDAO.getByOriginalTransactionId(transaction.getId());
    }

    public List<Reimbursement> getReimbursementsByLedger (Ledger ledger) {
        return reimbursementDAO.getByLedger(ledger);
    }

    public Reimbursement create(Transaction originalTransaction, BigDecimal amount, Ledger ledger) {
        if(originalTransaction == null || amount == null || ledger == null) {
            return null;
        }
        if(!originalTransaction.isReimbursable()) {
            originalTransaction.setReimbursable(true);
            transactionDAO.update(originalTransaction);
        }
        Reimbursement reimbursement = new Reimbursement(
                originalTransaction,
                amount,
                ReimbursableStatus.PENDING,
                ledger
        );
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
        if(record.getReimbursementStatus() == ReimbursableStatus.FULL) {
            return false;
        }
        if(amount == null) {
            amount = record.getAmount();
        }

        Expense expense = (Expense) record.getOriginalTransaction();
        if(toAccount == null) {
            toAccount = expense.getFromAccount();
        }

        if(amount.compareTo(record.getRemainingAmount())  == 0) { //full claim
//            Income transfer = new Income(
//                    date,
//                    amount,
//                    "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
//                    toAccount,
//                    expense.getLedger(),
//                    ledgerCategoryDAO.getByNameAndLedger("Claim Recorded", expense.getLedger()));
//            transactionDAO.insert(transfer);
            Transfer transfer = new Transfer(date,
                    "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
                    null,
                    toAccount,
                    amount,
                    expense.getLedger());
            transfer.setReimbursable(true);
            transactionDAO.insert(transfer);

            record.setStatus(ReimbursableStatus.FULL);
            //record.setAmount(BigDecimal.ZERO);
            record.setRemainingAmount(BigDecimal.ZERO);

            reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());

        } else if (amount.compareTo(record.getRemainingAmount()) < 0) { //partial claim
            BigDecimal diff = record.getRemainingAmount().subtract(amount);
            //expense.setAmount(expense.getAmount().subtract(amount));

            if(isFinalClaim) { //full
//                Income transfer = new Income(
//                        date,
//                        amount,
//                        "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
//                        toAccount,
//                        expense.getLedger(),
//                        ledgerCategoryDAO.getByNameAndLedger("Claim Recorded", expense.getLedger()));
//                transactionDAO.insert(transfer);

                Transfer transfer = new Transfer(date,
                        "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
                        null,
                        toAccount,
                        amount,
                        expense.getLedger());
                transfer.setReimbursable(true);
                transactionDAO.insert(transfer);

                record.setRemainingAmount(diff);
                record.setStatus(ReimbursableStatus.FULL);

                expense.setReimbursable(false);
                expense.setAmount(diff);
//                Expense finalPersonalExpense = new Expense(
//                        date,
//                        diff,
//                        expense.getNote(),
//                        expense.getFromAccount(),
//                        expense.getLedger(),
//                        expense.getCategory(),
//                        false
//                );
//                transactionDAO.insert(finalPersonalExpense);

                reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
                transactionDAO.update(expense);
            } else { //partial
//                Income transfer = new Income(
//                        date,
//                        amount,
//                        "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
//                        toAccount,
//                        expense.getLedger(),
//                        ledgerCategoryDAO.getByNameAndLedger("Claim Recorded", expense.getLedger()));
//                transactionDAO.insert(transfer);

                Transfer transfer = new Transfer(date,
                        "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
                        null,
                        toAccount,
                        amount,
                        expense.getLedger());
                transfer.setReimbursable(true);
                transactionDAO.insert(transfer);

                record.setRemainingAmount(diff);

                reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
                //transactionDAO.update(expense);
            }
        } else { //over claim
            BigDecimal diff = amount.subtract(record.getAmount());
//            Income transfer = new Income(
//                    date,
//                    reimbursement.getAmount(),
//                    "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
//                    toAccount,
//                    expense.getLedger(),
//                    ledgerCategoryDAO.getByNameAndLedger("Claim Recorded", expense.getLedger()));
//            transactionDAO.insert(transfer);
            Transfer transfer = new Transfer(date,
                    "Reimbursement Claim for Expense: " + expense.getCategory().getName(),
                    null,
                    toAccount,
                    record.getAmount(),
                    expense.getLedger());
            transfer.setReimbursable(true);
            transactionDAO.insert(transfer);

            Income income = new Income(
                    date,
                    diff,
                    "Over Reimbursement Income for Expense: " + expense.getCategory().getName(),
                    toAccount,
                    expense.getLedger(),
                    ledgerCategoryDAO.getByNameAndLedger("Claim Income", expense.getLedger()));
            transactionDAO.insert(income);

            reimbursementTxLinkDAO.insert(record.getId(), transfer.getId());
            reimbursementTxLinkDAO.insert(record.getId(), income.getId());

            record.setStatus(ReimbursableStatus.FULL);
            record.setRemainingAmount(BigDecimal.ZERO);
        }

        toAccount.credit(amount);
        return reimbursementDAO.update(record) && accountDAO.update(toAccount);
    }

    //edit only pending reimbursement
    public boolean editReimbursement(Reimbursement record, BigDecimal newAmount) {
        if(record == null || newAmount == null) {
            return false;
        }
        if(record.getReimbursementStatus() != ReimbursableStatus.PENDING) {
            return false;
        }
        record.setAmount(newAmount);
        BigDecimal oldRemaining = record.getRemainingAmount();
        if(newAmount.compareTo(oldRemaining) < 0) {
            record.setRemainingAmount(BigDecimal.ZERO);
            record.setStatus(ReimbursableStatus.FULL);
        } else if (newAmount.compareTo(oldRemaining) > 0) {
            BigDecimal diff = newAmount.subtract(oldRemaining);
            record.setRemainingAmount(diff);
        }
        return reimbursementDAO.update(record);
    }

    //delete reimbursement, all linked transactions linked to reimbursement and original transaction
    public boolean delete(Reimbursement record) {
        if(record == null) {
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

        Transaction originalTx = record.getOriginalTransaction();
        Account fromAccount = originalTx.getFromAccount();
        fromAccount.credit(record.getAmount());

        return transactionDAO.delete(originalTx) && accountDAO.update(fromAccount);
    }

    //cancel only completed reimbursement
    //cancel all transactions linked to reimbursement and reset reimbursement but keep original transaction and reimbursement
    public boolean cancelClaims( Reimbursement record) {
        if(record == null) {
            return false;
        }
        if(record.getReimbursementStatus() == ReimbursableStatus.PENDING) {
            return false;
        }

        Expense expense = (Expense) record.getOriginalTransaction();
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

        record.setStatus(ReimbursableStatus.PENDING);
        record.setRemainingAmount(record.getAmount());
        expense.setReimbursable(true);
        return transactionDAO.update(expense) && reimbursementDAO.update(record);
    }
}
