package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AccountController {
    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;

    public AccountController(AccountDAO accountDAO, TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
    }

    public List<Account> getVisibleAccounts(User user) {
        return accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> !account.getHidden())
                .toList();
    }
    public List<Account> getSelectableAccounts(User user) {
        return getVisibleAccounts(user).stream()
                .filter(Account::getSelectable)
                .toList();
    }

    public List<BorrowingAccount> getVisibleBorrowingAccounts(User user) {
        return getVisibleAccounts(user).stream()
                .filter(account -> account instanceof BorrowingAccount)
                .map(account -> (BorrowingAccount) account)
                .toList();
    }

    public List<LendingAccount> getVisibleLendingAccounts(User user) {
        return getVisibleAccounts(user).stream()
                .filter(account -> account instanceof LendingAccount)
                .map(account -> (LendingAccount) account)
                .toList();
    }

    public List<CreditAccount> getCreditCardAccounts(User user) {
        return getVisibleAccounts(user).stream()
                .filter(account -> account instanceof CreditAccount)
                .filter(account -> account.getType() == AccountType.CREDIT_CARD)
                .map(account -> (CreditAccount) account)
                .toList();
    }
    public List<LoanAccount> getVisibleLoanAccounts(User user) {
        return getVisibleAccounts(user).stream()
                .filter(account -> account instanceof LoanAccount)
                .map(account -> (LoanAccount) account)
                .toList();
    }

    public BasicAccount createBasicAccount(String name, BigDecimal balance,
                                           AccountType type, AccountCategory category,
                                           User owner, String notes, boolean includedInNetWorth,
                                           boolean selectable) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        if (balance == null) {
            balance = BigDecimal.ZERO;
        }

        if (category == null) {
            return null;
        }
        if(category.equals(AccountCategory.CREDIT) || category.equals(AccountCategory.VIRTUAL_ACCOUNT)){
            return null;
        }

        if (type == null) {
            return null;
        }
        if(type.equals(AccountType.CREDIT_CARD) || type.equals(AccountType.LOAN) || type.equals(AccountType.BORROWING)
                || type.equals(AccountType.LENDING) || type.equals(AccountType.OTHER_CREDIT)){
            return null;
        }
        BasicAccount account = new BasicAccount(name,
                balance,
                notes,
                includedInNetWorth,
                selectable,
                type,
                category,
                owner);
        accountDAO.createBasicAccount(account);
        return account;
    }

    public CreditAccount createCreditAccount(String name, String notes, BigDecimal balance,
                                             boolean includedInNetAsset, boolean selectable, User user,
                                             AccountType type, BigDecimal creditLimit, BigDecimal currentDebt,
                                             Integer billDate, Integer dueDate) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (type == null) {
            return null;
        }
        if(creditLimit == null){
            creditLimit = BigDecimal.ZERO;
        }
        if (creditLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (currentDebt == null) {
            currentDebt = BigDecimal.ZERO;
        }
        if (currentDebt.compareTo(creditLimit) > 0) {
            return null;
        }
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }

        CreditAccount account = new CreditAccount(name, balance, user, notes, includedInNetAsset, selectable,
                creditLimit, currentDebt, billDate, dueDate, type);
        accountDAO.createCreditAccount(account);
        return account;
    }

    public LoanAccount createLoanAccount(String name, String notes, boolean includedInNetAsset, User user,
                                         int totalPeriods, int repaidPeriods, BigDecimal annualInterestRate,
                                         BigDecimal loanAmount, Account receivingAccount, LocalDate repaymentDate,
                                         LoanAccount.RepaymentType repaymentType, Ledger ledger) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if(totalPeriods <=0 || totalPeriods > 480){ //max 40 years
            return null;
        }
        if(repaidPeriods > totalPeriods){
            return null;
        }
        if(ledger == null){
            return null;
        }
        if (repaymentDate == null) {
            return null;
        }
        if (repaymentType == null) {
            repaymentType = LoanAccount.RepaymentType.EQUAL_INTEREST;
        }
        if(receivingAccount != null){
            if(!receivingAccount.getSelectable()){
                return null;
            }
        }
        LoanAccount account = new LoanAccount(name, user, notes, includedInNetAsset, totalPeriods, repaidPeriods,
                annualInterestRate, loanAmount, repaymentDate, repaymentType);
        accountDAO.createLoanAccount(account); //insert loan account to db

        Transaction tx = new Transfer(LocalDate.now(),
                "Loan disbursement",
                account,
                receivingAccount,
                loanAmount,
                ledger);
        transactionDAO.insert(tx); //insert transaction to db

        if (receivingAccount != null) {
            receivingAccount.credit(loanAmount);
            accountDAO.update(receivingAccount); //update balance in db
        }

        return account;
    }

    public BorrowingAccount createBorrowingAccount(User user, String name, BigDecimal amount, String note,
                                                   boolean includeInAssets, boolean selectable, Account toAccount,
                                                   LocalDate date, Ledger ledger) {

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

        if (toAccount != null) {
            toAccount.credit(amount);
            accountDAO.update(toAccount); //update balance in db
        }

        return borrowingAccount;

    }

    public LendingAccount createLendingAccount(User user, String name, BigDecimal amount,
                                               String note, boolean includeInAssets, boolean selectable,
                                               Account fromAccount, LocalDate date, Ledger ledger) {
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

        if (fromAccount != null) {
            fromAccount.debit(amount);
            accountDAO.update(fromAccount); //update balance in db
        }

        return lendingAccount;
    }

    public boolean deleteAccount(Account account, boolean deleteTransactions) {
        List<Transaction> transactions = transactionDAO.getByAccountId(account.getId());

        if (deleteTransactions) {
            for (Transaction tx : transactions) {
                transactionDAO.delete(tx);
            }
        }
        return accountDAO.deleteAccount(account);
    }


    //name, balance, includedInNetAsset, selectable are null means no change
    public boolean editBasicAccount(BasicAccount account, String name, BigDecimal balance, String notes,
                                    Boolean includedInNetAsset, Boolean selectable) {

        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);

        return accountDAO.update(account);
    }

    public boolean editCreditAccount(CreditAccount account, String name, BigDecimal balance, String notes,
                                     Boolean includedInNetAsset, Boolean selectable,
                                     BigDecimal creditLimit, BigDecimal currentDebt,
                                     Integer billDate, Integer dueDate) {
        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);
        if (creditLimit != null) account.setCreditLimit(creditLimit);
        if (currentDebt != null) account.setCurrentDebt(currentDebt);
        if (billDate != null) account.setBillDay(billDate);
        if (dueDate != null) account.setDueDay(dueDate);

        return accountDAO.update(account);
    }

    public boolean editLoanAccount(LoanAccount account, String name, String notes, Boolean includedInNetAsset,
                                   Integer totalPeriods, Integer repaidPeriods,
                                   BigDecimal annualInterestRate, BigDecimal loanAmount,
                                   LocalDate repaymentDate, LoanAccount.RepaymentType repaymentType) {
        if (name != null) account.setName(name);
        account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (totalPeriods != null){
            if(totalPeriods<=0 || totalPeriods > 480){ //max 40 years
                return false;
            }
            account.setTotalPeriods(totalPeriods);
        }
        if (repaidPeriods != null) {
            account.setRepaidPeriods(repaidPeriods);
        }
        if(account.getTotalPeriods() < account.getRepaidPeriods()){
            return false;
        }
        if (annualInterestRate != null) account.setAnnualInterestRate(annualInterestRate);
        if (loanAmount != null) account.setLoanAmount(loanAmount);
        if (repaymentDate != null) account.setRepaymentDate(repaymentDate);
        if (repaymentType != null) account.setRepaymentType(repaymentType);

        account.setRemainingAmount(account.calculateRemainingAmount());
        account.checkAndUpdateStatus();
        return accountDAO.update(account);
    }

    public boolean editBorrowingAccount(BorrowingAccount account, String name, BigDecimal amount, String notes,
                                        Boolean includedInNetAsset, Boolean selectable, Boolean isEnded) {

        if (name != null) account.setName(name);
        if (amount != null) {
            account.setBorrowingAmount(amount);
            account.setRemainingAmount(amount);
        }
        account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);
        if (isEnded != null) {
            account.setIsEnded(isEnded);
        }

        return accountDAO.update(account);
    }

    public boolean editLendingAccount(LendingAccount account, String name,
                                      BigDecimal balance, String notes,
                                      Boolean includedInNetAsset, Boolean selectable, Boolean isEnded) {

        if (name != null) account.setName(name);
        if (balance != null) account.setBalance(balance);
        account.setNotes(notes);
        if (includedInNetAsset != null) account.setIncludedInNetAsset(includedInNetAsset);
        if (selectable != null) account.setSelectable(selectable);
        if (isEnded != null) account.setIsEnded(isEnded);

        return accountDAO.update(account);
    }


    public boolean hideAccount(Account account) {
        account.hide();
        return accountDAO.update(account);
    }

    public boolean repayDebt(CreditAccount creditAccount, BigDecimal amount, Account fromAccount, Ledger ledger) {
        if (amount == null) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        if (amount.compareTo(creditAccount.getCurrentDebt()) > 0) {
            return false;
        }

        Transaction tx = new Transfer(LocalDate.now(),
                "Repay credit account debt",
                fromAccount,
                creditAccount,
                amount,
                ledger);
        transactionDAO.insert(tx);
        creditAccount.repayDebt(amount);

        if (fromAccount != null) {
            fromAccount.debit(amount);
            accountDAO.update(fromAccount); //update from account balance in db
        }
        return accountDAO.update(creditAccount); //update credit account balance and current debt in db
    }

    public boolean repayLoan(LoanAccount loanAccount, Account fromAccount, Ledger ledger) {
        if (loanAccount.getRepaidPeriods() >= loanAccount.getTotalPeriods()) {
            return false;
        }

        BigDecimal repayAmount = loanAccount.getMonthlyRepayment(loanAccount.getRepaidPeriods() + 1);
        Transaction tx = new Transfer(LocalDate.now(),
                "Loan Repayment",
                fromAccount,
                loanAccount,
                repayAmount,
                ledger);
        transactionDAO.insert(tx); //insert transaction to db

        loanAccount.repayLoan(); //reduce remaining amount and increase repaid period

        if (fromAccount != null) {
            fromAccount.debit(repayAmount); //reduce from account balance
            accountDAO.update(fromAccount); //update from account balance in db
        }

        return accountDAO.update(loanAccount); //update loan account remaining amount in db
    }

    public boolean payBorrowing(BorrowingAccount borrowingAccount, BigDecimal amount,
                              Account fromAccount, Ledger ledger) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        if (amount.compareTo(borrowingAccount.getRemainingAmount()) > 0) {
            return false;
        }

        Transaction tx = new Transfer(LocalDate.now(),
                "Repay Borrowing",
                borrowingAccount,
                fromAccount,
                amount,
                ledger);
        transactionDAO.insert(tx); //insert transaction to db
        borrowingAccount.repay(amount); //reduce remaining amount, update status and add incoming transaction

        if (fromAccount != null) {
            fromAccount.debit(amount);
            accountDAO.update(fromAccount); //update to account balance in db
        }

        return accountDAO.update(borrowingAccount); //update borrowing account borrowing amount in db
    }

    public boolean receiveLending(LendingAccount lendingAccount, BigDecimal amount,
                                 Account toAccount, Ledger ledger) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        Transaction tx = new Transfer(LocalDate.now(),
                "Receive Lending",
                toAccount,
                lendingAccount,
                amount,
                ledger);
        transactionDAO.insert(tx); //insert transaction to db
        lendingAccount.receiveRepayment(amount); //increase lending amount, update status and add outgoing transaction

        if (toAccount != null) {
            toAccount.credit(amount);
            accountDAO.update(toAccount); //update from account balance in db
        }

        return accountDAO.update(lendingAccount); //update lending account lending amount in db
    }

}



