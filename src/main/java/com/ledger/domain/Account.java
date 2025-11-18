package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class Account {
    protected long id;
    protected String name;
    protected BigDecimal balance;
    protected AccountType type;
    protected AccountCategory category;
    protected User owner;
    protected String notes;
    protected boolean hidden;
    //protected List<Transaction> transactions = new ArrayList<>();
    protected boolean includedInNetAsset;
    protected boolean selectable;

    public Account() {}
    public Account(
            String name,
            BigDecimal balance,
            AccountType type,
            AccountCategory category,
            User owner,
            String notes,
            boolean includedInNetAsset,
            boolean selectable) {
        this.name = name;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.type = type;
        this.category = category;
        this.owner = owner;
        this.notes = notes;
        this.includedInNetAsset = includedInNetAsset;
        this.selectable = selectable;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount).setScale(2, RoundingMode.HALF_UP);
    }
    public void debit(BigDecimal amount){
        if(balance.compareTo(amount) < 0){
            throw new IllegalArgumentException("Insufficient funds in the account to execute this transaction.");
        }
        balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }
    public void hide() {
        this.hidden = true;
    }

    public void setIncludedInNetAsset(boolean includedInNetAsset) {
        this.includedInNetAsset =includedInNetAsset;
    }
    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }
    public void setOwner(User owner) {
        this.owner = owner;
    }
    public void setId(long id) {
        this.id = id;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public AccountType getType() { return type; }
    public AccountCategory getCategory() {
        return category;
    }
    public String getName() {
        return name;
    }
    public User getOwner() {
        return owner;
    }
    //public List<Transaction> getTransactions() {return transactions;}
    public BigDecimal getBalance() {
        return this.balance;
    }
    public long getId() {
        return id;
    }
    public String getNotes() {
        return notes;
    }
    public Boolean getSelectable() {
        return selectable;
    }
    public Boolean getHidden() {
        return hidden;
    }
    public Boolean getIncludedInNetAsset() {
        return includedInNetAsset;
    }
    public void setCategory(AccountCategory category) {
        this.category = category;
    }
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
    public void setType(AccountType type) {
        this.type = type;
    }
}
