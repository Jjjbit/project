package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class AccountController {
    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;

    public AccountController(AccountDAO accountDAO,
                             TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
    }

    public BasicAccount createBasicAccount(String name, BigDecimal balance,
                                           AccountType type, AccountCategory category,
                                           User owner, String notes, boolean includedInNetWorth,
                                           boolean selectable) throws SQLException {
        BasicAccount account = new BasicAccount(name,
                balance,
                notes,
                includedInNetWorth,
                selectable,
                type,
                category,
                owner);
        accountDAO.createBasicAccount(account);
        owner.getAccounts().add(account);
        return account;
    }

    public CreditAccount createCreditAccount(String name, String notes,
                                             BigDecimal balance, boolean includedInNetAsset,
                                             boolean selectable, User user,
                                             AccountType type, BigDecimal creditLimit,
                                             BigDecimal currentDebt, Integer billDate,
                                             Integer dueDate) throws SQLException {
        if(type == null){
            return null;
        }
        if(creditLimit == null || creditLimit.compareTo(BigDecimal.ZERO) <= 0){
            return null;
        }
        if(currentDebt == null){
            currentDebt = BigDecimal.ZERO;
        }
        if(currentDebt.compareTo(creditLimit) > 0){
            return null;
        }

        CreditAccount account = new CreditAccount(name,
                balance,
                user,
                notes,
                includedInNetAsset,
                selectable,
                creditLimit,
                currentDebt,
                billDate,
                dueDate,
                type);
        accountDAO.createCreditAccount(account);
        user.getAccounts().add(account);
        return account;
    }

    public LoanAccount createLoanAccount(String name, String notes,
                                         boolean includedInNetAsset, User user,
                                         int totalPeriods, int repaidPeriods,
                                         BigDecimal annualInterestRate, BigDecimal loanAmount,
                                         Account receivingAccount, LocalDate repaymentDate,
                                         LoanAccount.RepaymentType repaymentType) throws SQLException {
        LoanAccount account = new LoanAccount(name,
                user,
                notes,
                includedInNetAsset,
                totalPeriods,
                repaidPeriods,
                annualInterestRate,
                loanAmount,
                receivingAccount,
                repaymentDate,
                repaymentType);
        accountDAO.createLoanAccount(account); //insert loan account to db
        user.getAccounts().add(account);

        Transaction tx = new Transfer(LocalDate.now(),
                "Loan disbursement",
                account,
                receivingAccount,
                loanAmount,
                null);
        transactionDAO.insert(tx); //insert transaction to db
        account.getOutgoingTransactions().add(tx);

        return account;
    }

    public BorrowingAccount createBorrowingAccount(User user, String name, BigDecimal amount,
                                                   String note, boolean includeInAssets,
                                                   boolean selectable, Account toAccount,
                                                   LocalDate date) throws SQLException {
        LocalDate transactionDate = date != null ? date : LocalDate.now();
        BorrowingAccount borrowingAccount = new BorrowingAccount(
                name,
                amount,
                note,
                includeInAssets,
                selectable,
                user,
                transactionDate);
        accountDAO.createBorrowingAccount(borrowingAccount); //insert borrowing account to db
        user.getAccounts().add(borrowingAccount);

        String description = toAccount != null
                ? borrowingAccount.getName() + " to " + toAccount.getName()
                : borrowingAccount.getName() + " to External Account";

        Transaction tx = new Transfer(transactionDate,
                description,
                borrowingAccount,
                toAccount,
                amount,
                null);
        transactionDAO.insert(tx); //insert transaction to db

        if (toAccount != null) {
            toAccount.credit(amount);
            toAccount.getIncomingTransactions().add(tx);
            accountDAO.update(toAccount); //update balance in db
        }

        borrowingAccount.getOutgoingTransactions().add(tx);
        return borrowingAccount;
    }

    public LendingAccount createLendingAccount(User user, String name, BigDecimal amount,
                                             String note, boolean includeInAssets,
                                             boolean selectable, Account fromAccount,
                                             LocalDate date) throws SQLException {
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
        user.getAccounts().add(lendingAccount);

        String description = fromAccount != null
                ? fromAccount.getName() + " to " + lendingAccount.getName()
                : "External Account to " + lendingAccount.getName();

        Transaction tx = new Transfer(transactionDate,
                description,
                fromAccount,
                lendingAccount,
                amount,
                null);
        transactionDAO.insert(tx); //insert transaction to db
        lendingAccount.getIncomingTransactions().add(tx);

        if (fromAccount != null) {
            fromAccount.debit(amount);
            fromAccount.getOutgoingTransactions().add(tx);
            accountDAO.update(fromAccount); //update balance in db
        }
        return lendingAccount;
    }

    public boolean deleteAccount(Account account, boolean deleteTransactions) throws SQLException {
        List<Transaction> transactions = transactionDAO.getByAccountId(account.getId());
        if (deleteTransactions) {
            for (Transaction tx : transactions) {
                Ledger ledger = tx.getLedger();
                LedgerCategory category = tx.getCategory();
                if (ledger != null) {
                    ledger.getTransactions().remove(tx);
                }
                if (category != null) {
                    category.getTransactions().remove(tx);
                }
                transactionDAO.delete(tx);
            }
        } else {
            for (Transaction tx : transactions) {
                if (tx.getFromAccount() != null) tx.setFromAccount(null);
                if (tx.getToAccount() != null) tx.setToAccount(null);
                transactionDAO.update(tx);
            }
        }

        User owner = account.getOwner();
        owner.getAccounts().remove(account);
        return accountDAO.deleteAccount(account);
    }


    public boolean editBasicAccount(Account account, String name,
                                    BigDecimal balance, String notes,
                                    Boolean includedInNetAsset, Boolean selectable) throws SQLException {

        if (!(account instanceof BasicAccount)) {
            return false;
        }

        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        if (notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);

        return accountDAO.update(account);
    }
    public boolean editCreditAccount(Account account, String name,
                                     BigDecimal balance, String notes,
                                     Boolean includedInNetAsset, Boolean selectable,
                                     BigDecimal creditLimit, BigDecimal currentDebt,
                                     Integer billDate, Integer dueDate) throws SQLException {

        if (!(account instanceof CreditAccount)) {
            return false;
        }

        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        if (notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);
        if (creditLimit != null) ((CreditAccount) account).setCreditLimit(creditLimit);
        if (currentDebt != null) ((CreditAccount) account).setCurrentDebt(currentDebt);
        if (billDate != null) ((CreditAccount) account).setBillDay(billDate);
        if (dueDate != null) ((CreditAccount) account).setDueDay(dueDate);

        return accountDAO.update(account);
    }

    public boolean editLoanAccount(Account account, String name,
                                   String notes, Boolean includedInNetAsset,
                                   Integer totalPeriods, Integer repaidPeriods,
                                   BigDecimal annualInterestRate, BigDecimal loanAmount,
                                   LocalDate repaymentDate, LoanAccount.RepaymentType repaymentType) throws SQLException {

        if (!(account instanceof LoanAccount)) {
            return false;
        }

        if (name != null) account.setName(name);
        if (notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (totalPeriods != null) ((LoanAccount) account).setTotalPeriods(totalPeriods);
        if (repaidPeriods != null) ((LoanAccount) account).setRepaidPeriods(repaidPeriods);
        if (annualInterestRate != null) ((LoanAccount) account).setAnnualInterestRate(annualInterestRate);
        if (loanAmount != null) ((LoanAccount) account).setLoanAmount(loanAmount);
        if (repaymentDate != null) ((LoanAccount) account).setRepaymentDate(repaymentDate);
        if (repaymentType != null) ((LoanAccount) account).setRepaymentType(repaymentType);

        ((LoanAccount) account).updateRemainingAmount();

        return accountDAO.update(account);
    }

    public boolean editBorrowingAccount(Account account, String name,
                                        BigDecimal amount, String notes,
                                        Boolean includedInNetAsset, Boolean selectable, Boolean isEnded) throws SQLException {

        if (!(account instanceof BorrowingAccount)) {
            return false;
        }

        if (name != null) account.setName(name);
        if ( amount != null){
            ((BorrowingAccount) account).setBorrowingAmount(amount);
            ((BorrowingAccount) account).setRemainingAmount(amount);
        }
        if( notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);
        if (isEnded != null) {
            ((BorrowingAccount) account).setIsEnded(isEnded);
        }

        return accountDAO.update(account);
    }

    public boolean editLendingAccount(Account account, String name,
                                      BigDecimal balance, String notes,
                                      Boolean includedInNetAsset, Boolean selectable, Boolean isEnded) throws SQLException {

        if (!(account instanceof LendingAccount)) {
            return false;
        }

        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        if( notes != null) account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);
        if (isEnded != null) ((LendingAccount) account).setIsEnded(isEnded);

        return accountDAO.update(account);
    }


    public boolean hideAccount(Account account) throws SQLException {
        account.hide();
        return accountDAO.update(account);
    }

    public boolean repayDebt(Account creditAccount, BigDecimal amount,
                          Account fromAccount, Ledger ledger) throws SQLException {

        if (!(creditAccount instanceof CreditAccount)) {
            return false;
        }

        if(fromAccount != null){
            if(fromAccount.getOwner() != creditAccount.getOwner()){
                return false;
            }
        }

        if (amount == null) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (amount.compareTo(((CreditAccount) creditAccount).getCurrentDebt()) > 0) {
            return false;
        }

        Transaction tx = new Transfer(LocalDate.now(),
                "Repay credit account debt",
                fromAccount,
                creditAccount,
                amount,
                ledger);
        transactionDAO.insert(tx);
        ((CreditAccount) creditAccount).repayDebt(tx);


        if (fromAccount != null) {
            fromAccount.debit(amount);
            fromAccount.getOutgoingTransactions().add(tx);
            accountDAO.update(fromAccount); //update from account balance in db
        }

        if (ledger != null) {
            ledger.getTransactions().add(tx);
        }
        return accountDAO.update(creditAccount); //update credit account balance and current debt in db

    }

    public boolean repayLoan(LoanAccount loanAccount,
                             Account fromAccount, Ledger ledger) throws SQLException {


        if (loanAccount.getRepaidPeriods() >= loanAccount.getTotalPeriods()) {
            return false;
        }

        if(fromAccount != null){
            if(fromAccount.getOwner() != loanAccount.getOwner()){
                return false;
            }
        }


        User user= loanAccount.getOwner();
        if(ledger != null){
            if(!ledger.getOwner().getId().equals(user.getId())){
                return false;
            }
        }

        BigDecimal repayAmount = loanAccount.getMonthlyRepayment(loanAccount.getRepaidPeriods()+1);
        Transaction tx = new Transfer(LocalDate.now(),
                "Loan Repayment",
                fromAccount,
                loanAccount,
                repayAmount,
                ledger);
        transactionDAO.insert(tx); //insert transaction to db

        //loanAccount.repayLoan(tx, repayAmount); //reduce remaining amount and increase repaid period
        loanAccount.repayLoan(tx); //reduce remaining amount and increase repaid period

        if (fromAccount != null) {
            fromAccount.debit(repayAmount); //reduce from account balance
            fromAccount.getOutgoingTransactions().add(tx);
            accountDAO.update(fromAccount); //update from account balance in db
        }

        if (ledger != null) {
            ledger.getTransactions().add(tx);
        }
        return accountDAO.update(loanAccount); //update loan account remaining amount in db
    }

    public boolean payBorrowing(BorrowingAccount borrowingAccount, BigDecimal amount,
                              Account fromAccount, Ledger ledger) throws SQLException {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        User user = borrowingAccount.getOwner();
        if (fromAccount != null && !fromAccount.getOwner().getId().equals(user.getId())) {
            return false;
        }
        if( amount.compareTo(borrowingAccount.getRemainingAmount()) > 0){
            return false;
        }
        if( ledger != null){
            if(!ledger.getOwner().getId().equals(user.getId())){
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
            fromAccount.getOutgoingTransactions().add(tx);
            accountDAO.update(fromAccount); //update to account balance in db
        }

        if (ledger != null) {
            ledger.getTransactions().add(tx);
        }
        return accountDAO.update(borrowingAccount); //update borrowing account borrowing amount in db
    }

    public boolean receiveLending(LendingAccount lendingAccount, BigDecimal amount,
                                 Account toAccount, Ledger ledger) throws SQLException {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        User user = lendingAccount.getOwner();
        if (toAccount != null && !toAccount.getOwner().getId().equals(user.getId())) {
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
            toAccount.getIncomingTransactions().add(tx);
            accountDAO.update(toAccount); //update from account balance in db
        }

        if (ledger != null) {
            ledger.getTransactions().add(tx);
        }
        return accountDAO.update(lendingAccount); //update lending account lending amount in db
    }



}



