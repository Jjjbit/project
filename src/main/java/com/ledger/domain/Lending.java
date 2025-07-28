package com.ledger.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class Lending extends BorrowingandLending {
    private List<Account> paymentAccounts;

    public Lending(String borrower, Currency currency, BigDecimal amount, Account paymentAccount, String notes) {
        super(borrower, amount, notes, currency);
        paymentAccounts=new ArrayList<>();
        paymentAccounts.add(paymentAccount);
        paymentAccount.debit(amount);
    }

    @Override
    public boolean isIncoming() {
        return false;
    }

    public void receiveRepayment(BigDecimal amount, Account account) {
        repaidAmount = repaidAmount.add(amount);
        account.credit(amount);
    }

    public void addOutgoing(BigDecimal additionalAmount, Account fromAccount) {
        totalAmount = totalAmount.add(additionalAmount);
        if (fromAccount != null) {
            fromAccount.debit(additionalAmount);
            paymentAccounts.add(fromAccount);
        }
    }
}
