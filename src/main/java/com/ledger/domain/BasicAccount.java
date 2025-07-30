package com.ledger.domain;

import java.math.BigDecimal;

public class BasicAccount extends Account {
    public BasicAccount(
            String name,
            BigDecimal balance,
            Currency currency,
            String note,
            boolean includedInNetWorth,
            boolean selectable,
            AccountType type,
            AccountCategory category,
            User owner
    ) {
        super(name, balance,type,category, owner, currency, note, includedInNetWorth, selectable);
    }

    /*@Override
    public Map<String, Object> getMetadata() {
        return Collections.emptyMap();
    }*/
}



