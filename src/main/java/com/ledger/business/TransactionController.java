package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static java.util.Comparator.comparing;

public class TransactionController {
    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;
    private final ReimbursementRecordDAO reimbursementRecordDAO;
    private final ReimbursementDAO reimbursementDAO;
    private final ReimbursementTxLinkDAO reimbursementTxLinkDAO;

    public TransactionController(TransactionDAO transactionDAO, AccountDAO accountDAO,
                                 ReimbursementRecordDAO reimbursementRecordDAO,
                                 ReimbursementDAO reimbursementDAO,
                                 ReimbursementTxLinkDAO reimbursementTxLinkDAO) {
        this.reimbursementDAO = reimbursementDAO;
        this.reimbursementRecordDAO = reimbursementRecordDAO;
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
        this.reimbursementTxLinkDAO = reimbursementTxLinkDAO;
    }

    public List<Transaction> getTransactionsByReimbursement(Reimbursement reimbursement) {
        return reimbursementTxLinkDAO.getTransactionsByReimbursement(reimbursement);
    }

    public List<Transaction> getTransactionsByLedgerInRangeDate(Ledger ledger, LocalDate startDate,
                                                                LocalDate endDate){
        return transactionDAO.getByLedgerId(ledger.getId()).stream()
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                //.filter(t -> !t.isReimbursable() || (t.getStatus() == ReimbursableStatus.FULL))
                .sorted((comparing(Transaction::getDate).reversed()))
                .toList();
    }

    public List<Transaction> getTransactionsByAccountInRangeDate(Account account, LocalDate startDate,
                                                                 LocalDate endDate) {
        return transactionDAO.getByAccountId(account.getId()).stream()
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                //.filter(t -> !t.isReimbursable() || (t.getStatus() == ReimbursableStatus.FULL))
                .sorted((comparing(Transaction::getDate).reversed()))
                .toList();

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
        transactionDAO.insert(incomeTransaction);
        toAccount.credit(amount);
        accountDAO.update(toAccount); //update balance in database

        return incomeTransaction;
    }

    public Expense createExpense(Ledger ledger, Account fromAccount, LedgerCategory category, String description,
                                 LocalDate date, BigDecimal amount
            , Boolean is_reimbursable
    ) {
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
            return null;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if(is_reimbursable == null) {
            is_reimbursable = false;
        }
        //ReimbursableStatus status = is_reimbursable ? ReimbursableStatus.PENDING : ReimbursableStatus.NONE;

        Expense expenseTransaction = new Expense(
                date != null ? date : LocalDate.now(),
                amount,
                description,
                fromAccount,
                ledger,
                category
                , is_reimbursable
                //, status
        );
        transactionDAO.insert(expenseTransaction);
        fromAccount.debit(amount);
        accountDAO.update(fromAccount); //update balance in database

        if(is_reimbursable) {
            Reimbursement reimbursement = new Reimbursement(
                    expenseTransaction,
                    amount,
                    ReimbursableStatus.PENDING, ledger
            );
            reimbursementDAO.insert(reimbursement);
        }
        return expenseTransaction;
    }

    public Transfer createTransfer(Ledger ledger, Account fromAccount, Account toAccount, String description,
                                   LocalDate date, BigDecimal amount) {
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
        if( amount == null) {
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
    }

    public boolean deleteTransaction(Transaction tx) {
        if (tx == null) {
            return false;
        }

        Account fromAccount = tx.getFromAccount();
        Account toAccount = tx.getToAccount();

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
    }

    //toAccount is null meaning no change
    //category is null meaning no change
    //ledger is null meaning no change
    //date is null meaning no change
    //amount is null meaning no change
    //note is null meaning removal of old note
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
            accountDAO.update(oldToAccount);

            if (!toAccount.getSelectable()) {
                return false;
            }

            //apply new account
            toAccount.credit(amount != null ? amount : oldAmount);
            income.setToAccount(toAccount);
            accountDAO.update(toAccount);
        }

        income.setAmount(amount != null ? amount : oldAmount);
        income.setDate(date != null ? date : income.getDate());
        income.setNote(note);
        return transactionDAO.update(income);
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
            accountDAO.update(oldFromAccount);

            if (!fromAccount.getSelectable()) {
                return false;
            }

            //apply new account
            fromAccount.debit(amount != null ? amount : oldAmount);
            expense.setFromAccount(fromAccount);
            accountDAO.update(fromAccount);
        }

        expense.setAmount(amount != null ? amount : oldAmount);
        expense.setDate(date != null ? date : expense.getDate());
        expense.setNote(note);
        return transactionDAO.update(expense);
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
        if(newFromAccount != null && !newFromAccount.getSelectable()) {
            return false;
        }
        if(newToAccount != null && !newToAccount.getSelectable()) {
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

        //fromAccount change, removal or addition
        if (newFromAccount != null && (oldFromAccount == null || newFromAccount.getId() != oldFromAccount.getId())
                || (newFromAccount == null && oldFromAccount != null)) {
            //rollback old fromAccount
            if (oldFromAccount != null) {
                oldFromAccount.credit(oldAmount);
                accountDAO.update(oldFromAccount);
            }

            //apply new fromAccount
            if (newFromAccount != null) {
                if (!newFromAccount.getSelectable()) {
                    return false;
                }
                BigDecimal amountToApply = amount != null ? amount : oldAmount;
                newFromAccount.debit(amountToApply);
                accountDAO.update(newFromAccount);
            }
            transfer.setFromAccount(newFromAccount); //apply new fromAccount (can be null)
        }

        //toAccount change
        if (newToAccount != null && (oldToAccount == null || newToAccount.getId()!= oldToAccount.getId())
                || (newToAccount == null && oldToAccount != null)) {
            //rollback old toAccount
            if (oldToAccount != null) {
                oldToAccount.debit(oldAmount);
                accountDAO.update(oldToAccount);
            }

            //apply new toAccount
            if( newToAccount != null) {
                if (!newToAccount.getSelectable()) {
                    return false;
                }
                BigDecimal amountToApply = amount != null ? amount : oldAmount;
                newToAccount.credit(amountToApply);
                accountDAO.update(newToAccount);
            }
            transfer.setToAccount(newToAccount); //apply new toAccount (can be null)
        }

        transfer.setAmount(amount != null ? amount : oldAmount);
        transfer.setDate(date != null ? date : transfer.getDate());
        transfer.setNote(note);
        return transactionDAO.update(transfer);
    }

    public boolean resetIsReimbursable(Expense expense) {
        if (expense == null) {
            return false;
        }
        if (expense.isReimbursable()) { //it's currently reimbursable
            expense.setReimbursable(false);
            //expense.setStatus(ReimbursableStatus.NONE);
            //List<ReimbursementRecord> records = reimbursementDAO.getByOriginalTransaction(expense);
            Reimbursement record = reimbursementDAO.getByOriginalTransactionId(expense.getId());
            if (record != null) {
                reimbursementDAO.delete(record);
            }
        }else{ //it's currently not reimbursable
            expense.setReimbursable(true);
            //expense.setStatus(ReimbursableStatus.PENDING);
            Reimbursement newRecord = new Reimbursement(expense,
                    expense.getAmount(),
                    ReimbursableStatus.PENDING, expense.getLedger()
            );
            reimbursementDAO.insert(newRecord);
        }
        return transactionDAO.update(expense);
    }

}
