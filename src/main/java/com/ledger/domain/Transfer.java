package com.ledger.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@DiscriminatorValue("TRANSFER")
public class Transfer extends Transaction{

    @ManyToOne
    @JoinColumn(name = "toAccount_id")
    private Account toAccount;

    public Transfer() {}
    public Transfer(LocalDate date,
                    String description,
                    Account from,
                    Account to,
                    BigDecimal amount,
                    Ledger ledger) {
        super(date, amount, description, from, ledger, null, TransactionType.TRANSFER);
        this.account = from;
        this.toAccount = to;
    }

    public void execute() {
        if (!account.hidden && !toAccount.hidden) {
            if (account.selectable && toAccount.selectable) {
                account.debit(amount);
                account.addTransaction(this);
                toAccount.credit(amount);
                toAccount.addTransaction(this);
                ledger.addTransaction(this);
            }
        }
    }
}


