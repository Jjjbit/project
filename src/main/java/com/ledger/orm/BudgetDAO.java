package com.ledger.orm;

import com.ledger.domain.Budget;

import java.util.List;

public interface BudgetDAO {
    void save(Budget budget);
    void update(Budget budget);
    void delete(Long id);
    Budget findById(Long id);
    List<Budget> findAll();
    Budget findByCategoryId(Long categoryId);
}
