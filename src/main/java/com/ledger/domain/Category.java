package com.ledger.domain;

import java.util.ArrayList;
import java.util.List;

public class Category { // General category not tied to a specific ledger. global category
    private Long id;
    private String name;
    private Category parent;
    protected CategoryType type;
    private List<Category> children = new ArrayList<>();

    public Category() {}
    public Category(String name, CategoryType type) {
        this.type = type;
        this.name = name;
    }

    // --- Getter/Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getParentId() {
        return parent != null ? parent.getId() : 0;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public void setChildren(List<Category> children) { this.children = children; }
    public CategoryType getType() { return type; }
    public void setType(CategoryType type) { this.type = type; }
    public Category getParent() { return parent; }
    public void setParent(Category parent) { this.parent = parent; }
    public List<Category> getChildren() { return children; }

}


