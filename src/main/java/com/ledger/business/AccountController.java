package com.ledger.business;

import com.ledger.domain.Account;
import com.ledger.domain.Transaction;
import com.ledger.domain.User;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.TransactionDAO;
import com.ledger.session.UserSession;
import com.ledger.transaction.DbTransactionManager;

import java.math.BigDecimal;
import java.util.List;

public class AccountController {
    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;

    public AccountController(AccountDAO accountDAO, TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
    }

    public List<Account> getAccounts(User user) {
        return accountDAO.getAccountsByOwner(user);
    }
    public List<Account> getSelectableAccounts(User user) {
        return getAccounts(user).stream()
                .filter(Account::getSelectable)
                .toList();
    }

    public Account createAccount(String name, BigDecimal balance, boolean includedInAsset, boolean selectable) {
        if(!UserSession.getInstance().isLoggedIn()) return null;
        if (name == null || name.isEmpty() || name.length() > 50) return null;
        if (balance == null ) balance = BigDecimal.ZERO;
        User owner = UserSession.getInstance().getCurrentUser();
        Account account = new Account(name, balance, owner, includedInAsset, selectable);
        if(accountDAO.insert(account)){
            return account;
        } else {
            return null;
        }
    }

    public boolean deleteAccount(Account account) {
        Boolean deleted = DbTransactionManager.getInstance().execute(() -> {
            List<Transaction> linkedTransactions = transactionDAO.getByAccountId(account.getId());
            for (Transaction tx : linkedTransactions) {
                Account fromAccount = tx.getFromAccount();
                Account toAccount = tx.getToAccount();
                    if(fromAccount == null && toAccount.getId() == account.getId()){
                        if(!transactionDAO.delete(tx)) throw new Exception("Failed to delete linked transaction");
                    }
                    if(toAccount == null && fromAccount.getId() == account.getId()){
                        if(!transactionDAO.delete(tx)) throw new Exception("Failed to delete linked transaction");
                    }
            }
            if(!accountDAO.delete(account)) throw new Exception("Failed to delete account");
            return true;
        });
        return deleted != null && deleted;
    }

    public boolean editAccount(Account account, String newName, BigDecimal newBalance, boolean newIncludedInAsset, boolean newSelectable) {
        if(newName == null || newBalance == null) return false;
        if(newName.isEmpty() || newName.length() > 50) return false;
        account.setName(newName);
        account.setBalance(newBalance);
        account.setIncludedInAsset(newIncludedInAsset);
        account.setSelectable(newSelectable);
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
