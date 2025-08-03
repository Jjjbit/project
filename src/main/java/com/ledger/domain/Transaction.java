package com.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public abstract class Transaction {
    protected LocalDate date;
    protected BigDecimal amount;
    protected String description;
    protected Account account; //relaizone tra Transaction e Account è associazione
    protected Ledger ledger;
    protected CategoryComponent category;

    public Transaction(LocalDate date, BigDecimal amount, String description, Account account, Ledger ledger, CategoryComponent category) {
        if(date == null){
            this.date= LocalDate.now();
        }else{
            this.date = date;
        }
        this.amount = amount;
        this.description = description;
        this.account = account;
        this.ledger = ledger;
        this.category = category;
    }
    public abstract void execute();
    public LocalDate getDate() {
        return date;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public String getDescription() {
        return description;
    }
    public Account getAccount() {
        return account;
    }
}




