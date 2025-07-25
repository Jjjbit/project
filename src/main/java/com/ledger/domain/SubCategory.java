package com.ledger.domain;

import java.util.Collections;
import java.util.List;

public class SubCategory extends CategoryComponent {
    private CategoryComponent parent;

    public SubCategory(String name, String type) {
        super(name, type);
    }
    @Override
    public void remove(CategoryComponent child) {
        throw new UnsupportedOperationException("SubCategory does not support remove operation");
    }
    @Override
    public void add(CategoryComponent child) {
        throw new UnsupportedOperationException("SubCategory does not support add operation");
    }
    @Override
    public List<CategoryComponent> getChildren() {
        return Collections.emptyList(); //ritorna una lista vuota immutabile
        //throw new UnsupportedOperationException("SubCategory does not support getChildren operation");
    }
    public void setParent(CategoryComponent parent) {
        this.parent = parent;
    }
    public CategoryComponent getParent() {
        return this.parent;
    }
    @Override
    public void display(String indent) {
        System.out.println(indent + "- " + name + " (" + type + ")");
    }

}


