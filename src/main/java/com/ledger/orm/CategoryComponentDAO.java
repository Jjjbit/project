package com.ledger.orm;

import com.ledger.domain.CategoryComponent;

public interface CategoryComponentDAO {
    void save(CategoryComponent categoryComponent);
    void update(CategoryComponent categoryComponent);
    void delete(Long id);
    CategoryComponent findById(Long id);
}
