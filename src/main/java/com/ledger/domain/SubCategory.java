package com.ledger.domain;

public class SubCategory {
    private String name;
    private Category parentCategory;

    public SubCategory(String name, Category parentCategory) {
        this.name = name;
        this.parentCategory = parentCategory;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Category getParentCategory() {
        return parentCategory;
    }

    public void setParentCategory(Category parentCategory) {
        this.parentCategory = parentCategory;
    }
}
