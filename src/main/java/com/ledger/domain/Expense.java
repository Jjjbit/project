package com.ledger.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@DiscriminatorValue("EXPENSE")
public class Expense extends Transaction {

    public Expense() {}
    public Expense(LocalDate date,
                   BigDecimal amount,
                   String description,
                   Account account,
                   Ledger ledger,
                   CategoryComponent category) {
        super(date, amount, description, account, ledger, category, TransactionType.EXPENSE);
    }
    @Override
    public void execute() {
        if (!account.hidden && account.selectable){
            if (!account.getCategory().equals(AccountCategory.CREDIT) && account.balance.compareTo(amount) < 0) {
                throw new IllegalArgumentException("Insufficient funds in the account to execute this transaction.");
            }
            account.debit(amount);
            account.addTransaction(this);
            category.addTransaction(this);
            ledger.addTransaction(this);
        }

    }

}


