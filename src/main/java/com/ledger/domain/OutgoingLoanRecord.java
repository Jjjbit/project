package com.ledger.domain;

import java.math.BigDecimal;
import java.util.List;


public class OutgoingLoanRecord extends LoanRecord {
    private List<Account> paymentAccounts;

    public OutgoingLoanRecord(String borrower, Currency currency, BigDecimal amount, Account paymentAccount, String notes, Ledger ledger) {
        super(borrower, amount, notes, ledger, currency);
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
