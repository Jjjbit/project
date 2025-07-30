package com.ledger.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Account {
    protected String name;
    protected BigDecimal balance;
    protected AccountType type;
    protected AccountCategory category;
    protected User owner;
    protected com.ledger.domain.Currency currency;
    protected String notes;
    protected boolean hidden=false;
    protected List<Transaction> transactions = new ArrayList<>();
    protected boolean includedInNetWorth = true;
    protected boolean selectable = true;

    public Account(String name,
                   BigDecimal balance,
                   AccountType type,
                   AccountCategory category,
                   User owner,
                   Currency currency,
                   String notes,
                   boolean includedInNetWorth,
                   boolean selectable) {
        this.name = name;
        if (balance == null) {
            this.balance = BigDecimal.ZERO;
        }else{
            this.balance = balance;
        }
        this.type = type;
        this.category = category;
        this.owner = owner;
        this.currency = currency;
        this.notes = notes;
        this.includedInNetWorth = includedInNetWorth;
        this.selectable = selectable;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }
    public void debit(BigDecimal amount) {
        balance = balance.subtract(amount);
    }
    public void hide() {
        this.hidden = true;
    }
    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
    }
    //public abstract Map<String, Object> getMetadata();

    public void setIncludedInNetWorth(){
        this.includedInNetWorth =false;
    }
    public void setSelectable(){
        this.selectable = false;
     }
    public AccountType getType() { return type; }
    public AccountCategory getCategory() {
        return category;
    }
    public String getName() {
        return name;
    }


    public List<Transaction> getTransactionsForMonth(YearMonth month) {
        return transactions.stream()
                .filter(tx -> YearMonth.from(tx.getDate()).equals(month))
                .collect(Collectors.toList());
    }

    public BigDecimal getBalance() {
        return this.balance;
    }

}



