package com.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Income extends Transaction {
    public  Income (LocalDate date,
                    BigDecimal amount,
                    String description,
                    Account account,
                    Ledger ledger,
                    LedgerCategory category) {
        super(date, amount, description, null, account, ledger, category, TransactionType.INCOME, false);
    }

    public Income() {}
}


