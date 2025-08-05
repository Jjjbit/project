package com.ledger.domain;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@DiscriminatorValue("INCOME")
public class Income extends Transaction {
    public  Income (LocalDate date, BigDecimal amount, String description, Account account, Ledger ledger, CategoryComponent category) {
        super(date, amount, description, account, ledger, category, TransactionType.INCOME);
    }

    public Income() {}
    @Override
    public void execute() {
        if (!account.hidden && account.selectable) {
            account.credit(amount);
            account.addTransaction(this);
            category.addTransaction(this);
            ledger.addTransaction(this);
        }

    }
}


