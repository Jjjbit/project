package com.ledger.orm;

import com.ledger.domain.Transaction;

import java.util.List;

public interface TransactionDAO {
    void save(Transaction transaction);
    Transaction findById(Long id);
    List<Transaction> findByAccountId(Long accountId);
    List<Transaction> findByCategory(Long categoryId);
    List<Transaction> findByUserId(Long userId);
    void update(Transaction transaction);
    void delete(Long id);
}
