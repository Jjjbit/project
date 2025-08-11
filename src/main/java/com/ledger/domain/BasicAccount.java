package com.ledger.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;

@Entity
@DiscriminatorValue("BasicAccount")
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

    @Override
    public void debit(BigDecimal amount) {
        balance = balance.subtract(amount);
        owner.updateTotalAssets();
        owner.updateNetAsset();
    }

}



