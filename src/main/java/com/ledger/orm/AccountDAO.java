package com.ledger.orm;

import com.ledger.domain.Account;

import java.util.List;

public interface AccountDAO {
    void save(Account account);
    Account findById(Long id);
    List<Account> findByUserId(Long userId);
    void update(Account account);
    void delete(Long id);
}
