package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;


public class User {
    private Long id;
    private String username;
    private String password;
    private List<Ledger> ledgers= new ArrayList<>();
    private List<Account> accounts= new ArrayList<>();

    public User (){}
    public User(String username, String password){
        this.username = username;
        this.password = password;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getId() {
        return id;
    }
    public List<Ledger> getLedgers() {
        return ledgers;
    }
    public List<Account> getAccounts() {
        return accounts;
    }
    public String getPassword(){return password;}
    public String getUsername(){return username;}
    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }
    public void setLedgers(List<Ledger> ledgers) {
        this.ledgers = ledgers;
    }


    public BigDecimal getTotalLending(){
        return accounts.stream()
                .filter(account -> account instanceof LendingAccount)
                .filter(account -> account.includedInNetAsset && !account.hidden)
                .filter(account -> account.getBalance().compareTo(BigDecimal.ZERO) > 0)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalBorrowing() {
        return accounts.stream()
                .filter(account -> account instanceof BorrowingAccount)
                .filter(account -> account.includedInNetAsset && !account.hidden)
                .filter(account -> ((BorrowingAccount) account).getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(account -> ((BorrowingAccount) account).getRemainingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalAssets() {
        BigDecimal totalBalance = accounts.stream()
                .filter(account -> !(account instanceof LoanAccount))
                .filter(account -> !(account instanceof BorrowingAccount))
                .filter(account -> !(account instanceof LendingAccount))
                .filter(account -> account.includedInNetAsset && !account.hidden)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalBalance.add(getTotalLending()).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getNetAssets() {
        return getTotalAssets().subtract(getTotalLiabilities()).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalLiabilities() {
        BigDecimal totalCreditDebt = accounts.stream()
                .filter(account -> account.getCategory() == AccountCategory.CREDIT)
                .filter(account -> account instanceof CreditAccount)
                .filter(account-> account.includedInNetAsset && !account.hidden)
                .map(account -> ((CreditAccount) account).getCurrentDebt())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnpaidLoan = accounts.stream()
                .filter(account -> account.getCategory() == AccountCategory.CREDIT)
                .filter(account -> account instanceof LoanAccount)
                .filter(account -> account.includedInNetAsset && !account.hidden)
                .filter(account -> !((LoanAccount) account).isEnded)
                .map(account -> ((LoanAccount) account).getRemainingAmount()) //get this.remainingAmount
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalCreditDebt.add(getTotalBorrowing()).add(totalUnpaidLoan).setScale(2, RoundingMode.HALF_UP);
    }
}



