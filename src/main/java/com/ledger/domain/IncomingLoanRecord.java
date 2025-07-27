package com.ledger.domain;

import java.math.BigDecimal;
import java.util.List;

public class IncomingLoanRecord extends LoanRecord {
    private List<Account> depositAccounts; //può essere null

    public IncomingLoanRecord(String lender, Currency currency, BigDecimal amount, Account depositAccount, String notes, Ledger ledger) {
        super(lender, amount, notes, ledger, currency);
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
