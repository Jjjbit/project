package com.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Transfer extends Transaction{
    private Account toAccount;

    public Transfer(LocalDate date, String description, Account from, Account to, BigDecimal amount, Ledger ledger) {
        super(date, amount, description, from, ledger, null);
        this.account = from;
        this.toAccount = to;
    }

    public void execute() {
        if (!account.hidden && !toAccount.hidden) {
            if (account.selectable) {
                account.debit(amount);
                toAccount.credit(amount);
                ledger.addTransaction(this);
            }
        }
    }
}


