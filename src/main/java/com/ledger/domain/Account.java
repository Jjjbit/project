package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Account {
    private long id;
    private String name;
    private BigDecimal balance;
    private User owner;
    private boolean includedInNetAsset;
    private boolean selectable;

    public Account() {}
    public Account(
            String name,
            BigDecimal balance,
            User owner,
            boolean includedInNetAsset,
            boolean selectable) {
        this.name = name;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.owner = owner;
        this.includedInNetAsset = includedInNetAsset;
        this.selectable = selectable;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount).setScale(2, RoundingMode.HALF_UP);
    }
    public void debit(BigDecimal amount){
        balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public void setIncludedInNetAsset(boolean includedInNetAsset) {
        this.includedInNetAsset =includedInNetAsset;
    }
    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }
    public void setId(long id) {
        this.id = id;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
    public String getName() {
        return name;
    }
    public User getOwner() {
        return owner;
    }
    public BigDecimal getBalance() {
        return this.balance;
    }
    public long getId() {
        return id;
    }
    public Boolean getSelectable() {
        return selectable;
    }
    public Boolean getIncludedInNetAsset() {
        return includedInNetAsset;
    }

}
