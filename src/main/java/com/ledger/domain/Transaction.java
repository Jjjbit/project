package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    protected LocalDate date;
    protected BigDecimal amount;
    protected String note;

    @ManyToOne
    @JoinColumn(name = "account_id")
    protected Account account; //relaizone tra Transaction e Account è associazione. più transazioni->un account

    @ManyToOne
    @JoinColumn(name = "ledger_id")
    protected Ledger ledger; //relazione tra Transaction e Ledger è aggregazione. più transazioni -> un ledger

    @ManyToOne
    @JoinColumn(name = "category_id")
    protected CategoryComponent category;

    @Enumerated(EnumType.STRING)
    protected TransactionType type;

    public Transaction() {}
    public Transaction(LocalDate date,
                       BigDecimal amount,
                       String description,
                       Account account,
                       Ledger ledger,
                       CategoryComponent category,
                       TransactionType type) {
        this.date = date != null ? date : LocalDate.now();
        this.amount = amount;
        this.note = description;
        this.account = account;
        this.ledger = ledger;
        this.category = category;
        this.type = type;
        this.id++;
    }
    public abstract void execute();
    public LocalDate getDate() {
        return date;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public String getNote() {
        return note;
    }
    public Account getAccount() {
        return account;
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
    public void setCategory(CategoryComponent category) {
        this.category = category;
    }
    public void setAccount(Account account) {
        this.account = account;
    }
    public void setLedger(Ledger ledger){
        this.ledger = ledger;
    }
}




