package com.ledger.domain;

public class PromoteToTopLevelStrategy implements CategoryLevelStrategy {

    @Override
    public void changeLevel(CategoryComponent subCategory, CategoryComponent root) {
        if (subCategory.getParent() != null) {
            subCategory.getParent().remove(subCategory);
            root.add(subCategory);
        }
    }
}
