package com.ledger.domain;

import java.util.List;

public abstract class CategoryComponent {
    protected String name;
    protected String type;


    public CategoryComponent(String name, String type) {
        this.name = name;
        this.type = type;
    }
    public abstract void remove(CategoryComponent child);
    public abstract void add(CategoryComponent child);
    public abstract List<CategoryComponent> getChildren();
    public abstract void display(String indent);
    public String getType() {
        return type;
    }
}
