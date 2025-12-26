package com.ledger.business;

import com.ledger.domain.Account;
import com.ledger.domain.User;
import com.ledger.orm.*;

import java.math.BigDecimal;
import java.util.List;

public class AccountController {
    private final AccountDAO accountDAO;
//    private final DebtPaymentDAO  debtPaymentDAO;
//    private final LoanTxLinkDAO loanTxLinkDAO;
//    private final BorrowingTxLinkDAO borrowingTxLinkDAO;
//    private final LendingTxLinkDAO lendingTxLinkDAO;

    public AccountController(AccountDAO accountDAO) {
//        this.lendingTxLinkDAO = lendingTxLinkDAO;
//        this.borrowingTxLinkDAO = borrowingTxLinkDAO;
//        this.loanTxLinkDAO = loanTxLinkDAO;
//        this.debtPaymentDAO = debtPaymentDAO;
        this.accountDAO = accountDAO;
    }

    public List<Account> getAccounts(User user) {
        return accountDAO.getAccountsByOwnerId(user.getId());
    }
    public List<Account> getSelectableAccounts(User user) {
        return getAccounts(user).stream()
                .filter(Account::getSelectable)
                .toList();
    }
//    public List<BorrowingAccount> getBorrowingAccounts(User user) {
//        return getAccounts(user).stream()
//                .filter(account -> account instanceof BorrowingAccount)
//                .map(account -> (BorrowingAccount) account)
//                .toList();
//    }
//    public List<LendingAccount> getLendingAccounts(User user) {
//        return getAccounts(user).stream()
//                .filter(account -> account instanceof LendingAccount)
//                .map(account -> (LendingAccount) account)
//                .toList();
//    }
//    public List<CreditAccount> getCreditCardAccounts(User user) {
//        return getAccounts(user).stream()
//                .filter(account -> account instanceof CreditAccount)
//                .filter(account -> account.getType() == AccountType.CREDIT_CARD)
//                .map(account -> (CreditAccount) account)
//                .toList();
//    }
//    public List<LoanAccount> getLoanAccounts(User user) {
//        return getAccounts(user).stream()
//                .filter(account -> account instanceof LoanAccount)
//                .map(account -> (LoanAccount) account)
//                .toList();
//    }

    public Account createAccount(String name, BigDecimal balance, User owner, boolean includedInNetWorth, boolean selectable) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
            balance = BigDecimal.ZERO;
        }
        Account account = new Account(name, balance, owner, includedInNetWorth, selectable);
        boolean result = accountDAO.createAccount(account);
        if(result){
            return account;
        } else {
            return null;
        }
    }

//    public CreditAccount createCreditAccount(String name, String notes, BigDecimal balance, boolean includedInNetAsset,
//                                             boolean selectable, User user, AccountType type, BigDecimal creditLimit,
//                                             BigDecimal currentDebt, Integer billDate, Integer dueDate) {
//        if (name == null || name.isEmpty()) {
//            return null;
//        }
//        if (type == null) {
//            return null;
//        }
//        if(creditLimit == null || creditLimit.compareTo(BigDecimal.ZERO) <= 0){
//            return null;
//        }
//        if (currentDebt == null) {
//            currentDebt = BigDecimal.ZERO;
//        }
//        if (currentDebt.compareTo(creditLimit) > 0) {
//            return null;
//        }
//        if (balance == null) {
//            balance = BigDecimal.ZERO;
//        }
//        CreditAccount account = new CreditAccount(name, balance, user, notes, includedInNetAsset, selectable, creditLimit,
//                currentDebt, billDate, dueDate, type);
//        boolean result = accountDAO.createCreditAccount(account);
//        if(result){
//            return account;
//        } else {
//            return null;
//        }
//    }
//
//    public LoanAccount createLoanAccount(String name, String notes, boolean includedInNetAsset, User user, int totalPeriods, int repaidPeriods, BigDecimal annualInterestRate,
//                                         BigDecimal loanAmount, Account receivingAccount, LocalDate repaymentDate, LoanAccount.RepaymentType repaymentType, Ledger ledger) {
//        if (name == null || name.isEmpty()) {
//            return null;
//        }
//        if(totalPeriods <=0 || totalPeriods > 480){ //max 40 years
//            return null;
//        }
//        if(repaidPeriods > totalPeriods){
//            return null;
//        }
//        if(ledger == null){
//            return null;
//        }
//        if (repaymentDate == null) {
//            return null;
//        }
//        if (repaymentType == null) {
//            repaymentType = LoanAccount.RepaymentType.EQUAL_INTEREST;
//        }
//        if(loanAmount == null || loanAmount.compareTo(BigDecimal.ZERO) < 0){
//            return null;
//        }
//        if(receivingAccount != null){
//            if(!receivingAccount.getSelectable()){
//                return null;
//            }
//        }
//        LoanAccount account = new LoanAccount(name, user, notes, includedInNetAsset, totalPeriods, repaidPeriods,
//                annualInterestRate, loanAmount, repaymentDate, repaymentType);
//        boolean result = accountDAO.createLoanAccount(account); //insert loan account to db
//        Transaction tx = new Transfer(LocalDate.now(), "Loan disbursement", account, receivingAccount,
//                loanAmount, ledger);
//        transactionDAO.insert(tx); //insert transaction to db
//        loanTxLinkDAO.insert(account, tx); //insert loan payment record to db
//        if (receivingAccount != null) {
//            receivingAccount.credit(loanAmount);
//            accountDAO.update(receivingAccount); //update balance in db
//        }
//        if(result){return account;}
//        else {return null;}
//    }
//
//    public BorrowingAccount createBorrowingAccount(User user, String name, BigDecimal amount, String note,
//                                                   boolean includeInAssets, boolean selectable, Account toAccount,
//                                                   LocalDate date, Ledger ledger) {
//        if (name == null || name.isEmpty()) {
//            return null;
//        }
//        if(amount == null || amount.compareTo(BigDecimal.ZERO) < 0){
//            return null;
//        }
//        if(toAccount != null){
//            if(!toAccount.getSelectable()){
//                return null;
//            }
//        }
//        LocalDate transactionDate = date != null ? date : LocalDate.now();
//        BorrowingAccount borrowingAccount = new BorrowingAccount(name, amount, note, includeInAssets, selectable, user,
//                transactionDate);
//        boolean result = accountDAO.createBorrowingAccount(borrowingAccount); //insert borrowing account to db
//        String description = toAccount != null
//                ? borrowingAccount.getName() + " to " + toAccount.getName()
//                : borrowingAccount.getName() + " to External Account";
//        Transaction tx = new Transfer(transactionDate, description, borrowingAccount, toAccount, amount, ledger);
//        transactionDAO.insert(tx); //insert transaction to db
//        borrowingTxLinkDAO.insert(borrowingAccount, tx); //insert borrowing payment record to db
//        if (toAccount != null) {
//            toAccount.credit(amount);
//            accountDAO.update(toAccount); //update balance in db
//        }
//        if(result){
//            return borrowingAccount;
//        } else {
//            return null;
//        }
//    }
//
//    public LendingAccount createLendingAccount(User user, String name, BigDecimal amount, String note,
//                                               boolean includeInAssets, boolean selectable, Account fromAccount,
//                                               LocalDate date, Ledger ledger) {
//        if (name == null || name.isEmpty()) {
//            return null;
//        }
//        if(amount == null || amount.compareTo(BigDecimal.ZERO) < 0){
//            return null;
//        }
//        if(fromAccount != null){
//            if(!fromAccount.getSelectable()){
//                return null;
//            }
//        }
//        LocalDate transactionDate = date != null ? date : LocalDate.now();
//        LendingAccount lendingAccount = new LendingAccount(name, amount, note, includeInAssets, selectable, user,
//                transactionDate);
//        boolean result = accountDAO.createLendingAccount(lendingAccount); //insert lending account to db
//        String description = fromAccount != null
//                ? fromAccount.getName() + " to " + lendingAccount.getName()
//                : "External Account to " + lendingAccount.getName();
//        Transaction tx = new Transfer(transactionDate, description, fromAccount, lendingAccount, amount, ledger);
//        transactionDAO.insert(tx); //insert transaction to db
//        lendingTxLinkDAO.insert(lendingAccount, tx); //insert lending receiving record to db
//        if (fromAccount != null) {
//            fromAccount.debit(amount);
//            accountDAO.update(fromAccount); //update balance in db
//        }
//        if(result){
//            return lendingAccount;
//        } else {
//            return null;
//        }
//    }

    public boolean deleteAccount(Account account) { //keep all transactions
        return accountDAO.deleteAccount(account);
    }

    //name, balance, includedInNetAsset, selectable are null means no change
    public boolean editAccount(Account account, String newName, BigDecimal newBalance, Boolean newIncludedInNetAsset,
                               Boolean newSelectable) {
        if (newName != null){
            if(newName.isEmpty()){
                return false;
            }
            account.setName(newName);
        }
        if (newBalance != null){
            if(newBalance.compareTo(BigDecimal.ZERO) < 0){
                return false;
            }
            account.setBalance(newBalance);
        }
        if (newIncludedInNetAsset != null) account.setIncludedInNetAsset(newIncludedInNetAsset);
        if (newSelectable != null) account.setSelectable(newSelectable);
        return accountDAO.update(account);
    }

//    public boolean editCreditAccount(CreditAccount account, String name, BigDecimal balance, String notes, Boolean includedInNetAsset,
//                                     Boolean selectable, BigDecimal creditLimit, BigDecimal currentDebt, Integer billDate, Integer dueDate) {
//        if (name != null) {
//            if(name.isEmpty()){
//                return false;
//            }
//            account.setName(name);
//        }
//        if (balance != null) {
//            if(balance.compareTo(BigDecimal.ZERO) < 0){
//                return false;
//            }
//            account.setBalance(balance);
//        }
//        account.setNotes(notes);
//        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
//        if (selectable != null) account.setSelectable(selectable);
//        if (creditLimit != null){
//            if(creditLimit.compareTo(BigDecimal.ZERO) <= 0){
//                return false;
//            }
//            account.setCreditLimit(creditLimit);
//        }
//        if (currentDebt != null) account.setCurrentDebt(currentDebt);
//        if (account.getCurrentDebt().compareTo(account.getCreditLimit()) > 0) {
//            return false;
//        }
//        if (billDate != null) account.setBillDay(billDate);
//        if (dueDate != null) account.setDueDay(dueDate);
//        return accountDAO.update(account);
//    }
//
//    public boolean editLoanAccount(LoanAccount account, String name, String notes, Boolean includedInNetAsset, Integer totalPeriods, Integer repaidPeriods,
//                                   BigDecimal annualInterestRate, BigDecimal loanAmount, LocalDate repaymentDate, LoanAccount.RepaymentType repaymentType) {
//        if (name != null) {
//            if (name.isEmpty()) {
//                return false;
//            }
//            account.setName(name);
//        }
//        account.setNotes(notes);
//        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
//        if (totalPeriods != null){
//            if(totalPeriods<=0 || totalPeriods > 480){ //max 40 years
//                return false;
//            }
//            account.setTotalPeriods(totalPeriods);
//        }
//        if (repaidPeriods != null) {
//            account.setRepaidPeriods(repaidPeriods);
//        }
//        if(account.getTotalPeriods() < account.getRepaidPeriods()){
//            return false;
//        }
//        if (annualInterestRate != null) account.setAnnualInterestRate(annualInterestRate);
//        if (loanAmount != null) {
//            if(loanAmount.compareTo(BigDecimal.ZERO) < 0){
//                return false;
//            }
//            account.setLoanAmount(loanAmount);
//        }
//        if (repaymentDate != null) account.setRepaymentDate(repaymentDate);
//        if (repaymentType != null) account.setRepaymentType(repaymentType);
//        account.setRemainingAmount(account.calculateRemainingAmount());
//        account.checkAndUpdateStatus();
//        return accountDAO.update(account);
//    }
//
//    public boolean editBorrowingAccount(BorrowingAccount account, String name, BigDecimal amount, String notes,
//                                        Boolean includedInNetAsset, Boolean selectable, Boolean isEnded) {
//        if (name != null){
//            if (name.isEmpty()){
//                return false;
//            }
//            account.setName(name);
//        }
//        if (amount != null) {
//            if(amount.compareTo(BigDecimal.ZERO) < 0){
//                return false;
//            }
//            BigDecimal oldAmount = account.getBorrowingAmount();
//            account.setBorrowingAmount(amount);
//            BigDecimal paidAmount = oldAmount.subtract(account.getRemainingAmount());
//            account.setRemainingAmount(amount.subtract(paidAmount));
//            account.checkAndUpdateStatus();
//        }
//        account.setNotes(notes);
//        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
//        if (selectable != null) account.setSelectable(selectable);
//        if (isEnded != null) {
//            account.setIsEnded(isEnded);
//        }
//        return accountDAO.update(account);
//    }
//
//    public boolean editLendingAccount(LendingAccount account, String name, BigDecimal balance, String notes,
//                                      Boolean includedInNetAsset, Boolean selectable, Boolean isEnded) {
//        if (name != null){
//            if (name.isEmpty()){
//                return false;
//            }
//            account.setName(name);
//        }
//        if (balance != null){
//            if(balance.compareTo(BigDecimal.ZERO) < 0){
//                return false;
//            }
//            account.setBalance(balance);
//            account.checkAndUpdateStatus();
//        }
//        account.setNotes(notes);
//        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
//        if (selectable != null) account.setSelectable(selectable);
//        if (isEnded != null) account.setIsEnded(isEnded);
//        return accountDAO.update(account);
//    }
//
//
//    public boolean repayDebt(CreditAccount creditAccount, BigDecimal amount, Account fromAccount, Ledger ledger) {
//        if(amount == null){
//            amount = creditAccount.getCurrentDebt();
//        }
//        if (amount.compareTo(BigDecimal.ZERO) < 0) {
//            return false;
//        }
//        if (amount.compareTo(creditAccount.getCurrentDebt()) > 0) {
//            return false;
//        }
//        if(fromAccount != null && !fromAccount.getSelectable()){
//            return false;
//        }
//        Transaction tx = new Transfer(LocalDate.now(), "Repay credit account debt", fromAccount, creditAccount,
//                amount, ledger);
//        transactionDAO.insert(tx);
//        creditAccount.repayDebt(amount);
//        if (fromAccount != null) {
//            fromAccount.debit(amount);
//            accountDAO.update(fromAccount); //update from account balance in db
//        }
//        debtPaymentDAO.insert(creditAccount, tx); //insert debt payment record to db
//        return accountDAO.update(creditAccount); //update credit account balance and current debt in db
//    }
//
//    public boolean repayLoan(LoanAccount loanAccount, Account fromAccount, Ledger ledger) {
//        if (loanAccount.getRepaidPeriods() >= loanAccount.getTotalPeriods() || loanAccount.getIsEnded()) {
//            return false;
//        }
//        if(fromAccount != null && !fromAccount.getSelectable()){
//            return false;
//        }
//        BigDecimal repayAmount = loanAccount.getMonthlyRepayment(loanAccount.getRepaidPeriods() + 1);
//        Transaction tx = new Transfer(LocalDate.now(), "Loan Repayment", fromAccount, loanAccount, repayAmount,
//                ledger);
//        transactionDAO.insert(tx); //insert transaction to db
//        loanTxLinkDAO.insert(loanAccount, tx); //insert loan payment record to db
//        loanAccount.repayLoan(); //reduce remaining amount and increase repaid period
//        if (fromAccount != null) {
//            fromAccount.debit(repayAmount); //reduce from account balance
//            accountDAO.update(fromAccount); //update from account balance in db
//        }
//        return accountDAO.update(loanAccount); //update loan account remaining amount in db
//    }
//
//    public boolean payBorrowing(BorrowingAccount borrowingAccount, BigDecimal amount, Account fromAccount, Ledger ledger) {
//        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
//            return false;
//        }
//        if (amount.compareTo(borrowingAccount.getRemainingAmount()) > 0) {
//            return false;
//        }
//        if(fromAccount != null && !fromAccount.getSelectable()){
//            return false;
//        }
//        Transaction tx = new Transfer(LocalDate.now(), "Repay Borrowing", fromAccount, borrowingAccount, amount, ledger);
//        transactionDAO.insert(tx); //insert transaction to db
//        borrowingAccount.repay(amount); //reduce remaining amount, update status and add incoming transaction
//        borrowingTxLinkDAO.insert(borrowingAccount, tx); //insert borrowing payment record to db
//        if (fromAccount != null) {
//            fromAccount.debit(amount);
//            accountDAO.update(fromAccount); //update to account balance in db
//        }
//        return accountDAO.update(borrowingAccount); //update borrowing account borrowing amount in db
//    }
//
//    public boolean receiveLending(LendingAccount lendingAccount, BigDecimal amount, Account toAccount, Ledger ledger) {
//        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
//            return false;
//        }
//        if (amount.compareTo(lendingAccount.getBalance()) > 0) {
//            return false;
//        }
//        if(toAccount != null && !toAccount.getSelectable()){
//            return false;
//        }
//        Transaction tx = new Transfer(LocalDate.now(), "Receive Lending", lendingAccount, toAccount, amount, ledger);
//        transactionDAO.insert(tx); //insert transaction to db
//        lendingAccount.debit(amount);
//        lendingAccount.checkAndUpdateStatus();
//        lendingTxLinkDAO.insert(lendingAccount, tx); //insert lending receiving record to db
//        if (toAccount != null) {
//            toAccount.credit(amount);
//            accountDAO.update(toAccount); //update from account balance in db
//        }
//        return accountDAO.update(lendingAccount); //update lending account lending amount in db
//    }

}



