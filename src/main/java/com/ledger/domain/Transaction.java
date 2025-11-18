package com.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public abstract class Transaction {
    protected long id;
    protected LocalDate date;
    protected BigDecimal amount;
    protected String note;
    protected Account fromAccount;
    protected Account toAccount;
    protected Ledger ledger;
    protected LedgerCategory category;
    protected TransactionType type;

    public Transaction() {}
    public Transaction(LocalDate date,
                       BigDecimal amount,
                       String description,
                       Account fromAccount,
                       Account toAccount,
                       Ledger ledger,
                       LedgerCategory category,
                       TransactionType type) {
        this.date = date != null ? date : LocalDate.now();
        this.amount = amount;
        this.note = description;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.ledger = ledger;
        this.category = category;
        this.type = type;
    }
    public LedgerCategory getCategory() {
        return category;
    }
    public long getId() {
        return id;
    }
    public TransactionType getType() {
        return type;
    }
    public LocalDate getDate() {
        return date;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public String getNote() {
        return note;
    }
    public Account getFromAccount() {
        return fromAccount;
    }
    public Account getToAccount() {
        return toAccount;
    }
    public Ledger getLedger() {
        return ledger;
    }
    public void setAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        this.amount = amount;
    }
    public void setDate(LocalDate date) {
        if (date == null) {
            this.date = LocalDate.now();
        } else {
            this.date = date;
        }
    }
    public void setType(TransactionType type) {
        this.type = type;
    }
    public void setNote(String note) {
        this.note = note;
    }
    public void setCategory(LedgerCategory category) {
        this.category = category;
    }
    public void setFromAccount(Account account) {
        this.fromAccount = account;
    }
    public void setToAccount(Account toAccount) {
        this.toAccount = toAccount;
    }
    public void setLedger(Ledger ledger){
        this.ledger = ledger;
    }
    public void setId(long id) {
        this.id = id;
    }
}
