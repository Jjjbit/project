package com.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Expense extends Transaction {
    public Expense(LocalDate date,  BigDecimal amount, String description, Account account, Ledger ledger, CategoryComponent category) {
        super(date, amount, description, account, ledger, category);
    }
    @Override
    public void execute() {
        if (!account.hidden && account.selectable){
            account.debit(amount);
            category.addTransaction(this);
            ledger.addTransaction(this);
        }

    }

}


