package com.ledger.domain;

public interface CategoryLevelStrategy {
    void changeLevel(CategoryComponent category, CategoryComponent root); //root è di tipo expense/income
}
