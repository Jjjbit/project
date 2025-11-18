package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class LoanController {
    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;

    public LoanController(AccountDAO accountDAO, TransactionDAO transactionDAO) {
        this.accountDAO = accountDAO;
        this.transactionDAO = transactionDAO;
    }

    public BorrowingAccount createBorrowingAccount(User user, String name, BigDecimal amount,
                                                   String note, boolean includeInAssets,
                                                   boolean selectable, Account toAccount,
                                                   LocalDate date, Ledger ledger) {
        try{
            if(ledger == null){
                return null;
            }

            LocalDate transactionDate = date != null ? date : LocalDate.now();
            BorrowingAccount borrowingAccount = new BorrowingAccount(name, amount, note, includeInAssets, selectable,
                    user, transactionDate);
            accountDAO.createBorrowingAccount(borrowingAccount); //insert borrowing account to db
            //user.getAccounts().add(borrowingAccount);

            String description = toAccount != null
                    ? borrowingAccount.getName() + " to " + toAccount.getName()
                    : borrowingAccount.getName() + " to External Account";

            Transaction tx = new Transfer(transactionDate,
                    description,
                    borrowingAccount,
                    toAccount,
                    amount,
                    ledger);
            transactionDAO.insert(tx); //insert transaction to db
            //borrowingAccount.getTransactions().add(tx);

            //ledger.getTransactions().add(tx);

            if (toAccount != null) {
                toAccount.credit(amount);
                //toAccount.getTransactions().add(tx);
                accountDAO.update(toAccount); //update balance in db
            }

            return borrowingAccount;
        }catch (SQLException e){
            System.err.println("SQL Exception during createBorrowingAccount: " + e.getMessage());
            return null;
        }
    }

    public LendingAccount createLendingAccount(User user, String name, BigDecimal amount,
                                               String note, boolean includeInAssets,
                                               boolean selectable, Account fromAccount,
                                               LocalDate date, Ledger ledger) {
        try {
            if(ledger == null){
                return null;
            }

            LocalDate transactionDate = date != null ? date : LocalDate.now();
            LendingAccount lendingAccount = new LendingAccount(
                    name,
                    amount,
                    note,
                    includeInAssets,
                    selectable,
                    user,
                    transactionDate);
            accountDAO.createLendingAccount(lendingAccount); //insert lending account to db
            //user.getAccounts().add(lendingAccount);

            String description = fromAccount != null
                    ? fromAccount.getName() + " to " + lendingAccount.getName()
                    : "External Account to " + lendingAccount.getName();

            Transaction tx = new Transfer(transactionDate,
                    description,
                    fromAccount,
                    lendingAccount,
                    amount,
                    ledger);
            transactionDAO.insert(tx); //insert transaction to db
            //lendingAccount.getTransactions().add(tx);

            //ledger.getTransactions().add(tx);

            if (fromAccount != null) {
                fromAccount.debit(amount);
                //fromAccount.getTransactions().add(tx);
                accountDAO.update(fromAccount); //update balance in db
            }

            return lendingAccount;
        }catch (SQLException e){
            System.err.println("SQL Exception during createLendingAccount: " + e.getMessage());
            return null;
        }
    }

    public boolean editBorrowingAccount(Account account, String name, BigDecimal amount, String notes,
                                        Boolean includedInNetAsset, Boolean selectable, Boolean isEnded) {
        try {
            if (!(account instanceof BorrowingAccount)) {
                return false;
            }

            if (name != null) account.setName(name);
            if (amount != null) {
                ((BorrowingAccount) account).setBorrowingAmount(amount);
                ((BorrowingAccount) account).setRemainingAmount(amount);
            }
            if (notes != null) account.setNotes(notes);
            if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
            if (selectable != null) account.setSelectable(selectable);
            if (isEnded != null) {
                ((BorrowingAccount) account).setIsEnded(isEnded);
            }

            return accountDAO.update(account);
        }catch (SQLException e){
            System.err.println("SQL Exception during editBorrowingAccount: " + e.getMessage());
            return false;
        }
    }

    public boolean editLendingAccount(Account account, String name,
                                      BigDecimal balance, String notes,
                                      Boolean includedInNetAsset, Boolean selectable, Boolean isEnded) {
        try {
            if (!(account instanceof LendingAccount)) {
                return false;
            }

            if (name != null) account.setName(name);
            if (balance != null) account.setBalance(balance);
            if (notes != null) account.setNotes(notes);
            if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
            if (selectable != null) account.setSelectable(selectable);
            if (isEnded != null) ((LendingAccount) account).setIsEnded(isEnded);

            return accountDAO.update(account);
        }catch (SQLException e){
            System.err.println("SQL Exception during editLendingAccount: " + e.getMessage());
            return false;
        }
    }

    public boolean payBorrowing(BorrowingAccount borrowingAccount, BigDecimal amount,
                                Account fromAccount, Ledger ledger) {
        try {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            User user = borrowingAccount.getOwner();
            /*if (fromAccount != null && !fromAccount.getOwner().getId().equals(user.getId())) {
                return false;
            }*/
            if (fromAccount != null && fromAccount.getOwner().getId() != user.getId()) {
                return false;
            }
            if (amount.compareTo(borrowingAccount.getRemainingAmount()) > 0) {
                return false;
            }
            if (ledger != null) {
                /*if (!ledger.getOwner().getId().equals(user.getId())) {
                    return false;
                }*/
                if (ledger.getOwner().getId() != user.getId()) {
                    return false;
                }
            }

            Transaction tx = new Transfer(LocalDate.now(),
                    "Repay Borrowing",
                    borrowingAccount,
                    fromAccount,
                    amount,
                    ledger);
            transactionDAO.insert(tx); //insert transaction to db
            borrowingAccount.repay(tx, amount); //reduce remaining amount, update status and add incoming transaction

            if (fromAccount != null) {
                fromAccount.debit(amount);
                //fromAccount.getTransactions().add(tx);
                accountDAO.update(fromAccount); //update to account balance in db
            }

            /*if (ledger != null) {
                ledger.getTransactions().add(tx);
            }*/
            return accountDAO.update(borrowingAccount); //update borrowing account borrowing amount in db
        }catch (SQLException e){
            System.err.println("SQL Exception during payBorrowing: " + e.getMessage());
            return false;
        }
    }

    public boolean receiveLending(LendingAccount lendingAccount, BigDecimal amount,
                                  Account toAccount, Ledger ledger) {
        try {

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            User user = lendingAccount.getOwner();
            /*if (toAccount != null && !toAccount.getOwner().getId().equals(user.getId())) {
                return false;
            }*/
            if (toAccount != null && toAccount.getOwner().getId() != user.getId()) {
                return false;
            }

            Transaction tx = new Transfer(LocalDate.now(),
                    "Receive Lending",
                    toAccount,
                    lendingAccount,
                    amount,
                    ledger);
            transactionDAO.insert(tx); //insert transaction to db
            lendingAccount.receiveRepayment(tx, amount); //increase lending amount, update status and add outgoing transaction

            if (toAccount != null) {
                toAccount.credit(amount);
                //toAccount.getTransactions().add(tx);
                accountDAO.update(toAccount); //update from account balance in db
            }

            /*if (ledger != null) {
                ledger.getTransactions().add(tx);
            }*/
            return accountDAO.update(lendingAccount); //update lending account lending amount in db
        }catch (SQLException e){
            System.err.println("SQL Exception during receiveLending: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(Account account, boolean deleteTransactions) {

        try {
            List<Transaction> transactions = transactionDAO.getByAccountId(account.getId());
            if (deleteTransactions) {
                for (Transaction tx : transactions) {
                    transactionDAO.delete(tx);
                }
            } else {
                for (Transaction tx : transactions) {
                    Account fromAcc = tx.getFromAccount();
                    Account toAcc = tx.getToAccount();
                    /*if(fromAcc != null && fromAcc.getId().equals(account.getId())) {
                        tx.setFromAccount(null);
                    }
                    if(toAcc != null && toAcc.getId().equals(account.getId())) {
                        tx.setToAccount(null);
                    }*/
                    if(fromAcc != null && fromAcc.getId() == account.getId()) {
                        tx.setFromAccount(null);
                    }
                    if(toAcc != null && toAcc.getId() == account.getId()) {
                        tx.setToAccount(null);
                    }
                    transactionDAO.update(tx);
                }
            }

            //User owner = account.getOwner();
            //owner.getAccounts().remove(account);
            return accountDAO.deleteAccount(account);
        } catch (SQLException e) {
            System.err.println("SQL Exception during deleteAccount: " + e.getMessage());
            return false;
        }
    }
}
