package com.ledger.orm;

import com.ledger.domain.Ledger;

import java.util.List;

public interface LedgerDAO {
    void save(Ledger ledger);
    Ledger findById(Long id);
    List<Ledger> findLedgersByUserId(Long userId);

    void update(Ledger ledger);
    void delete(Long id);
}
