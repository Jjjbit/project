package com.ledger.domain;

public class DemoteToSubCategoryStrategy implements CategoryLevelStrategy {
    private CategoryComponent newParent;

    public DemoteToSubCategoryStrategy(CategoryComponent newParent) {
        this.newParent = newParent;
    }

    @Override
    public void changeLevel(CategoryComponent category, CategoryComponent root) {
        if (category instanceof Category && ((Category) category).getChildren().isEmpty()) {
            SubCategory sub = new SubCategory(category.getName(), category.getType());
            newParent.add(sub);
            root.remove(category);
        } else {
            System.out.println("Cannot demote a category with subcategory.");
        }
    }
}
