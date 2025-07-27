package com.ledger.domain;
import java.math.BigDecimal;
import java.time.LocalDate;

public class Income extends Transaction {
    public  Income (LocalDate date, BigDecimal amount, String description, Account account, Ledger ledger, CategoryComponent category) {
        super(date, amount, description, account, ledger, category);
    }
    @Override
    public void execute() {
        account.credit(amount);
        category.addTransaction(this);
    }
}


