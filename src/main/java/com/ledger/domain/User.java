package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
public class User {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Ledger> ledgers;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Account> accounts;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Budget> budgets= new ArrayList<>();

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<BorrowingAndLending> borrowings;

    @Column(name = "total_assets", columnDefinition = "DECIMAL(15,2) DEFAULT 0.00", nullable = false)
    public BigDecimal totalAssets;

    @Column(name = "total_liabilities", columnDefinition = "DECIMAL(15,2) DEFAULT 0.00", nullable = false)
    public BigDecimal totalLiabilities;

    @Column(name = "net_assets", columnDefinition = "DECIMAL(15,2) DEFAULT 0.00", nullable = false)
    public BigDecimal netAssets;

    public User (){}
    public User(String username, String password){
        this.username = username;
        this.password = password;
        ledgers= new ArrayList<>();
        createLedger("Default Ledger");
        accounts = new ArrayList<>();
        borrowings = new ArrayList<>();
        this.totalAssets = getTotalAssets();
        this.totalLiabilities = getTotalLiabilities();
        this.netAssets = getNetAssets();
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public void createLedger(String name) {
        Ledger ledger = new Ledger(name, this);
        ledgers.add(ledger);
    }
    public List<Budget> getBudgets() {
        return budgets;
    }
    public void setBudget(BigDecimal amount, Budget.BudgetPeriod p, CategoryComponent c) {
        budgets.add(new Budget(amount, p, c,this));
    }
    public void hideAccount(Account account) {
        account.hide();
    }
    public void deleteAccount(Account account) {
        accounts.remove(account);
        account.getTransactions().forEach(transaction -> {
            transaction.getLedger().removeTransaction(transaction);
        });

        this.totalAssets = getTotalAssets();
        this.totalLiabilities = getTotalLiabilities();
        this.netAssets = getNetAssets();

    }

    public void createAccount(String name, BigDecimal balance, AccountType type, AccountCategory category, Currency currency, String notes, boolean includedInNetWorth, boolean selectable, Map<String, Object> strategyFields) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", name);
        fields.put("balance", balance);
        fields.put("owner", this);
        fields.put("currency", currency);
        fields.put("note", notes);
        fields.put("includedInNetWorth", includedInNetWorth);
        fields.put("selectable", selectable);
        fields.putAll(strategyFields);
        Account account = AccountFactory.createAccount(type, category, fields);
        accounts.add(account);
    }

    public BigDecimal getTotalAssets() {
        return accounts.stream()
                .filter(account -> !account.getType().equals(AccountType.LOAN))
                .filter(account -> account.includedInNetAsset && !account.hidden)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getNetAssets() {
        return getTotalAssets().subtract(getTotalLiabilities());
    }
    public BigDecimal getTotalLiabilities() {
        BigDecimal totalCreditDebt = accounts.stream()
                .filter(account -> account.getCategory() == AccountCategory.CREDIT)
                .filter(account -> account instanceof CreditAccount)
                .filter(account-> account.includedInNetAsset && !account.hidden)
                .map(account -> ((CreditAccount) account).getCurrentDebt())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBorrowing = borrowings.stream()
                .filter(BorrowingAndLending::isIncoming)
                .filter(record -> record.includedInNetWorth)
                .filter(record -> !record.isEnded)
                .map(record -> record.getTotalAmount().subtract(record.getRepaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnpaidLoan = accounts.stream()
                .filter(account -> account.getCategory() == AccountCategory.CREDIT)
                .filter(account -> account instanceof LoanAccount)
                .filter(account -> account.includedInNetAsset)
                .map(account -> ((LoanAccount) account).getRemainingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalCreditDebt.add(totalBorrowing).add(totalUnpaidLoan);
    }
    public void updateNetAssetsAndLiabilities(BigDecimal amount) {
        this.totalLiabilities = getTotalLiabilities().subtract(amount);
        //this.netAssets = this.netAssets.add(amount);
        this.netAssets = getTotalAssets().subtract(this.totalLiabilities);
    }
    public void updateNetAsset(){
        this.netAssets= getNetAssets();
    }
    public void updateTotalAssets(){
        this.totalAssets = getTotalAssets();
    }
    public void updateTotalLiabilities(){
        this.totalLiabilities = getTotalLiabilities();
    }
}



