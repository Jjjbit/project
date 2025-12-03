package com.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Transfer extends Transaction{

    public Transfer() {}
    public Transfer(LocalDate date,
                    String description,
                    Account from,
                    Account to,
                    BigDecimal amount,
                    Ledger ledger) {
        super(date, amount, description, from, to, ledger, null, TransactionType.TRANSFER);
    }

}



