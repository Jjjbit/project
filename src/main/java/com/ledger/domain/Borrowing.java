package com.ledger.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Borrowing extends BorrowingandLending {
    private List<Account> depositAccounts; //può essere null

    public Borrowing(String lender, Currency currency, BigDecimal amount, Account depositAccount, String notes) {
        super(lender, amount, notes, currency);
        depositAccounts = new ArrayList<>();
        depositAccounts.add(depositAccount);
        depositAccount.credit(amount);
    }

    @Override
    public boolean isIncoming() {
        return true;
    }

    public void repay(BigDecimal amount, Account account) {
        repaidAmount = repaidAmount.add(amount);
        account.debit(amount);
    }

    public void addIncoming(BigDecimal additionalAmount, Account toAccount) {
        totalAmount = totalAmount.add(additionalAmount);
        if (toAccount != null) {
            toAccount.credit(additionalAmount);
            depositAccounts.add(toAccount);
        }
    }

}
