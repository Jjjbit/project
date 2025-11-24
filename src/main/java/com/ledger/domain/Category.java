package com.ledger.domain;

public class Category { // General category not tied to a specific ledger. global category
    private long id;
    private String name;
    private Category parent;
    protected CategoryType type;

    // --- Getter/Setter ---
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CategoryType getType() { return type; }
    public void setType(CategoryType type) { this.type = type; }
    public Category getParent() { return parent; }
    public void setParent(Category parent) { this.parent = parent; }
}


