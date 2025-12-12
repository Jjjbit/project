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

    public List<Reimbursement> getReimbursementsByLedger (Ledger ledger) {
        return reimbursementDAO.getByLedger(ledger);
    }

    public Reimbursement create(BigDecimal amount, Account fromAccount, Ledger ledger, LedgerCategory category) {
        if(category == null) {
            return null;
        }
        if(amount == null) {
            return null;
        }
        if(amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if(fromAccount == null) {
            return null;
        }
        if(fromAccount.getBalance().compareTo(amount) < 0) {
            return null;
        }
        if(ledger == null) {
            return null;
        }
        Transfer transfer = new Transfer(LocalDate.now(), "Reimbursement Funding", fromAccount, null,
                amount, ledger);
        transactionDAO.insert(transfer);
        Reimbursement reimbursement = new Reimbursement(amount, false, fromAccount, ledger, category, transfer);
        boolean inserted = reimbursementDAO.insert(reimbursement);
        fromAccount.debit(amount);
        accountDAO.update(fromAccount);
        reimbursementTxLinkDAO.insert(reimbursement, transfer);
        if(!inserted) {
            return null;
        }
        return reimbursement;
    }

    public boolean claim (Reimbursement record, BigDecimal amount, Boolean isFinalClaim, Account toAccount, LocalDate date) {
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
            Transfer transfer = new Transfer(date, "Reimbursement Claim", null, toAccount, record.getRemainingAmount(),
                    record.getLedger());
            transactionDAO.insert(transfer);
            record.setEnded(true);
            record.setRemainingAmount(BigDecimal.ZERO);
            reimbursementTxLinkDAO.insert(record, transfer);
        } else if(amount.compareTo(record.getRemainingAmount()) < 0) { //partial claim
            if(isFinalClaim == null) {
                return false;
            }
            BigDecimal diff = record.getRemainingAmount().subtract(amount);
            Transfer transfer = new Transfer(date, "Reimbursement Claim", null, toAccount, amount, record.getLedger());
            transactionDAO.insert(transfer);
            record.setRemainingAmount(diff);
            reimbursementTxLinkDAO.insert(record, transfer);

            if(isFinalClaim) { //it's final claim
                record.setRemainingAmount(diff);
                record.setEnded(true);
                Expense expense = new Expense(date, diff, "Unclaimed Reimbursement Expense", null, record.getLedger(),
                        record.getLedgerCategory());
                transactionDAO.insert(expense);
                reimbursementTxLinkDAO.insert(record, expense);
            }
        } else { //over claim
            BigDecimal diff = amount.subtract(record.getRemainingAmount());
            Transfer transfer = new Transfer(date, "Reimbursement Claim", null, toAccount, record.getRemainingAmount(),
                    record.getLedger());
            transactionDAO.insert(transfer);
            Income income = new Income(date, diff, "Over Reimbursement Income", toAccount, record.getLedger(),
                    ledgerCategoryDAO.getByNameAndLedger("Claim Income", record.getLedger()));
            transactionDAO.insert(income);
            record.setRemainingAmount(record.getRemainingAmount().subtract(amount));
            record.setEnded(true);
            reimbursementTxLinkDAO.insert(record, transfer);
            reimbursementTxLinkDAO.insert(record, income);
        }
        toAccount.credit(amount);
        return reimbursementDAO.update(record) && accountDAO.update(toAccount);
    }

    //edit only pending reimbursement
    public boolean editReimbursement(Reimbursement record, BigDecimal newAmount) {
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
        Transaction first = reimbursementTxLinkDAO.getTransactionsByReimbursement(record).getFirst();
        first.setAmount(newAmount);
        transactionDAO.update(first);

        record.getFromAccount().credit(record.getAmount());
        record.getFromAccount().debit(newAmount);
        accountDAO.update(record.getFromAccount());

        BigDecimal oldRemaining = record.getRemainingAmount(); //it's always positive
        BigDecimal reimbursedAmount = record.getAmount().subtract(oldRemaining);

        if(newAmount.compareTo(reimbursedAmount) < 0) {
            //new amount is less than already reimbursed amount
            BigDecimal diff = reimbursedAmount.subtract(newAmount);
            record.setEnded(true);
            record.setRemainingAmount(newAmount.subtract(reimbursedAmount)); //negative

            List<Transaction> linkedTxs = reimbursementTxLinkDAO.getTransactionsByReimbursement(record);
            for (int i = linkedTxs.size() - 1; i >= 0; i--) {
                Transaction tx = linkedTxs.get(i);
                BigDecimal amount = tx.getAmount();
                if (amount.compareTo(diff) > 0) {
                    BigDecimal newTxAmount = amount.subtract(diff);
                    tx.setAmount(newTxAmount);
                    Account toAccount = tx.getToAccount();
                    if (toAccount != null) {
                        Account cached = accountDAO.getAccountById(toAccount.getId());
                        cached.debit(diff);
                        accountDAO.update(cached);
                    }
                    transactionDAO.update(tx);
                    break;
                }
            }
            Income income = new Income(LocalDate.now(), diff, "Reimbursement Income", record.getFromAccount(),
                    record.getLedger(), ledgerCategoryDAO.getByNameAndLedger("Claim Income", record.getLedger()));
            transactionDAO.insert(income);
            reimbursementTxLinkDAO.insert(record, income);
            Account cached = accountDAO.getAccountById(record.getFromAccount().getId());
            cached.credit(diff);
            accountDAO.update(record.getFromAccount());

        } else { //new amount is greater than or equal to already reimbursed amount
            BigDecimal newRemaining = newAmount.subtract(reimbursedAmount);
            record.setRemainingAmount(newRemaining);
            boolean isEnded = newRemaining.compareTo(BigDecimal.ZERO) == 0;
            record.setEnded(isEnded);
        }
        record.setAmount(newAmount);
        return reimbursementDAO.update(record);
    }

    //delete reimbursement and all linked transactions
    public boolean delete(Reimbursement record) {
        if(record == null) {
            return false;
        }

        List<Transaction> linkedTxs = reimbursementTxLinkDAO.getTransactionsByReimbursement(record).stream()
                .filter(tx -> tx.getId() != record.getOriginalTransaction().getId())
                .toList();

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
        return transactionDAO.delete(record.getOriginalTransaction());
    }
}
