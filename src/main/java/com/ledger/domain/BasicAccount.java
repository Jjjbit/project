package com.ledger.domain;

import java.math.BigDecimal;

public class BasicAccount extends Account {
    public BasicAccount() {}
    public BasicAccount(
            String name,
            BigDecimal balance,
            String note,
            boolean includedInNetWorth,
            boolean selectable,
            AccountType type,
            AccountCategory category,
            User owner
    ) {
        super(name, balance, type,category, owner, note, includedInNetWorth, selectable);
    }


}



