package com.ledger.cli;

import com.ledger.business.LedgerCategoryController;
import com.ledger.business.LedgerController;
import com.ledger.business.UserController;
import com.ledger.domain.CategoryType;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;
import com.ledger.domain.User;

import java.util.List;
import java.util.Scanner;

public class LedgerCategoryCLI {
    private final LedgerCategoryController ledgerCategoryController;
    private final UserController userController;
    private final LedgerController ledgerController;
    private final Scanner scanner = new Scanner(System.in);

    public LedgerCategoryCLI(LedgerCategoryController ledgerCategoryController,
                             UserController userController, LedgerController ledgerController) {
        this.ledgerController = ledgerController;
        this.userController = userController;
        this.ledgerCategoryController = ledgerCategoryController;
    }

    public void addCategory() {
        System.out.println("\n === Adding a new category of first level ===");


        //select ledger
        System.out.println("\nSelect a ledger to add the category to:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger == null){
            System.out.println("No ledger selected.");
            return;
        }

        //enter category name
        System.out.print("Enter the name of the new category: ");
        String categoryName = inputName();

        //enter category type
        System.out.println("Enter the type of the new category: ");
        String categoryTypeInput = selectCategoryType();

        //create category
        LedgerCategory category = ledgerCategoryController.createCategory(categoryName, selectedLedger,
                Enum.valueOf(CategoryType.class, categoryTypeInput.toUpperCase()));

        if(category == null) {
            System.out.println("Failed to create category. It may already exist or the input was invalid.");
            return;
        }
        System.out.println("Category '" + category.getName() + "' added successfully to ledger '" + selectedLedger.getName() + "'.");
        printCategoryTree(selectedLedger);
    }

    public void addSubCategory() {
        System.out.println("\n === Adding a new sub-category ===");

        //select ledger
        System.out.println("\nSelect a ledger to add the sub-category to:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger == null){
            System.out.println("No ledger selected.");
            return;
        }

        //select category type
        System.out.println("Enter the type of the parent category: ");
        String categoryTypeInput = selectCategoryType();
        CategoryType categoryType = categoryTypeInput.equalsIgnoreCase("INCOME") ?
                CategoryType.INCOME : CategoryType.EXPENSE;

        //select parent category
        List<LedgerCategory> allCategories = ledgerCategoryController.getLedgerCategoryTreeByLedger(selectedLedger);
        List<LedgerCategory> parentCategories = allCategories.stream()
                .filter(cat -> cat.getType() == (categoryType) && cat.getParent() == null)
                .filter(cat -> !cat.getName().equals("Claim Income"))
                .toList();
        System.out.println("Select a parent category for the new sub-category:");
        LedgerCategory parentCategory = selectCategory(parentCategories);
        if(parentCategory == null){
            return;
        }

        //enter sub-category name
        System.out.print("Enter the name of the new sub-category: ");
        String subCategoryName = inputName();

        //create sub-category
        LedgerCategory subCategory = ledgerCategoryController.createSubCategory(subCategoryName, parentCategory, selectedLedger);
        if (subCategory == null) {
            System.out.println("Failed to create sub-category.");
            return;
        }
        System.out.println("Sub-category '" + subCategory.getName() + "' added successfully under category '"
                + parentCategory.getName() + "' in ledger '" + selectedLedger.getName() + "'.");
        printCategoryTree(selectedLedger);

    }

    public void renameCategory() {
        System.out.println("\n === Renaming a category ===");

        //select ledger
        System.out.println("\nSelect a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger == null){
            System.out.println("No ledger selected.");
            return;
        }

        //select category
        List<LedgerCategory> allCategories = ledgerCategoryController.getLedgerCategoryTreeByLedger(selectedLedger);
        System.out.println("\nSelect a category to rename:");
        LedgerCategory categoryToRename = selectCategoryWithTree(allCategories);
        if(categoryToRename == null){
            return;
        }

        //enter new name
        System.out.print("Enter the new name for the category '" + categoryToRename.getName() + "': ");
        String newName = scanner.nextLine();
        if(newName.isEmpty()){
            newName = categoryToRename.getName();
        }

        //rename category
        boolean success = ledgerCategoryController.renameCategory(categoryToRename, newName, selectedLedger);
        if (!success) {
            System.out.println("Failed to rename category. The new name may already exist or the input was invalid.");
            return;
        }
        System.out.println("Category renamed successfully to '" + newName + "'.");
        printCategoryTree(selectedLedger);
    }

    public void deleteCategory() {
        System.out.println("\n === Deleting a category ===");

        //select ledger
        System.out.println("\nSelect a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger == null){
            System.out.println("No ledger selected.");
            return;
        }

        //select category
        List<LedgerCategory> allCategories = ledgerCategoryController.getLedgerCategoryTreeByLedger(selectedLedger);
        System.out.println("Select a category to delete:");
        LedgerCategory categoryToDelete = selectCategoryWithTree(allCategories);
        if(categoryToDelete == null){
            return;
        }

        //select if delete transactions
        System.out.print("Are you sure you want to delete this category and all its associated transactions '" + categoryToDelete.getName() + "'? (y/n): ");
        String confirmation = scanner.nextLine().trim();
        if (confirmation.equalsIgnoreCase("yes") || confirmation.equalsIgnoreCase("y")) {
            //delete category and delete transactions
            boolean success = ledgerCategoryController.deleteCategory(categoryToDelete, true, null);
            if (!success) {
                System.out.println("Failed to delete category.");
                return;
            }
        }else {
            //delete category and migrate transactions
            List<LedgerCategory> categories = ledgerCategoryController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                    .filter(cat -> cat.getId() != categoryToDelete.getId() && cat.getType() == categoryToDelete.getType())
                    .toList();
            System.out.println("Select a category to migrate transactions to:");
            LedgerCategory migrateCategory = selectCategoryWithTree(categories);
            boolean success = ledgerCategoryController.deleteCategory(categoryToDelete, false, migrateCategory);
            if (!success) {
                System.out.println("Failed to delete category.");
                return;
            }
        }
        System.out.println("Category '" + categoryToDelete.getName() + "' deleted successfully.");
        printCategoryTree(selectedLedger);
    }

    public void promoteSubCategory() {
        System.out.println("\n === Promoting a subcategory ===");

        //select ledger
        System.out.println("\nSelect a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger == null){
            return;
        }

        //select category type
        System.out.println("Enter the type of the sub-category to promote: ");
        String categoryTypeInput = selectCategoryType();
        CategoryType categoryType = categoryTypeInput.equalsIgnoreCase("INCOME") ?
                CategoryType.INCOME : CategoryType.EXPENSE;

        //select sub-category
        List<LedgerCategory> allCategories = ledgerCategoryController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                .filter(cat -> cat.getType() == categoryType && cat.getParent() != null)
                .toList();
        System.out.println("Select a sub-category to promote:");
        LedgerCategory subCategoryToPromote = selectCategory(allCategories);
        if(subCategoryToPromote == null){
            return;
        }

        //promote sub-category
        boolean success = ledgerCategoryController.promoteSubCategory(subCategoryToPromote);
        if (!success) {
            System.out.println("Failed to promote sub-category.");
            return;
        }
        System.out.println("Sub-category '" + subCategoryToPromote.getName() + "' promoted successfully to first-level category in ledger '" + selectedLedger.getName() + "'.");

        //print updated category tree
        printCategoryTree(selectedLedger);
    }

    public void demoteCategory() {
        System.out.println("\n === Demoting a category ===");


        //select ledger
        System.out.println("\nSelect a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger == null){
            System.out.println("No ledger selected.");
            return;
        }

        //select category type
        System.out.println("Enter the type of the category to demote: ");
        String categoryTypeInput = selectCategoryType();
        CategoryType categoryType = categoryTypeInput.equalsIgnoreCase("INCOME") ?
                CategoryType.INCOME : CategoryType.EXPENSE;

        //select category
        List<LedgerCategory> allCategories = ledgerCategoryController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                .filter(cat -> cat.getType() == categoryType
                        && cat.getParent() == null)
                .filter(cat -> !cat.getName().equals("Claim Income"))
                .toList();
        System.out.println("Select a first-level category to demote:");
        LedgerCategory categoryToDemote = selectCategory(allCategories);
        if(categoryToDemote == null){
            return;
        }

        //select new parent category
        List<LedgerCategory> potentialParents = allCategories.stream()
                .filter(cat -> cat.getId() != categoryToDemote.getId())
                .toList();
        System.out.println("Select a new parent category for the category to demote:");
        LedgerCategory newParentCategory = selectCategory(potentialParents);
        if(newParentCategory == null){
            return;
        }

        //demote category
        boolean success = ledgerCategoryController.demoteCategory(categoryToDemote, newParentCategory);
        if (!success) {
            System.out.println("Failed to demote category.");
            return;
        }
        System.out.println("Category '" + categoryToDemote.getName() + "' demoted successfully under category '"
                + newParentCategory.getName() + "' in ledger '" + selectedLedger.getName() + "'.");

        //print updated category tree
        printCategoryTree(selectedLedger);
    }

    public void changeParent(){
        System.out.println("\n === Changing parent of a sub-category ===");

        //select ledger
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger == null){
            System.out.println("No ledger selected.");
            return;
        }

        //select sub-category
        List<LedgerCategory> allCategories = ledgerCategoryController.getLedgerCategoryTreeByLedger(selectedLedger);
        List<LedgerCategory> subCategories = allCategories.stream()
                .filter(cat -> cat.getParent() != null)
                .toList();
        LedgerCategory subCategoryToChange = selectCategory(subCategories);
        if(subCategoryToChange == null){
            return;
        }

        //select new parent category
        List<LedgerCategory> potentialParents = allCategories.stream()
                .filter(cat -> cat.getId() != subCategoryToChange.getId()
                        && cat.getType() == subCategoryToChange.getType()
                        && cat.getParent() == null)
                .filter(cat -> !cat.getName().equals("Claim Income"))
                .toList();
        LedgerCategory newParentCategory = selectCategory(potentialParents);
        if(newParentCategory == null){
            return;
        }

        //change parent category
        boolean success = ledgerCategoryController.changeParent(subCategoryToChange, newParentCategory);
        if (!success) {
            System.out.println("Failed to change parent category.");
            return;
        }
        System.out.println("Sub-category '" + subCategoryToChange.getName() + "' now has new parent category '"
                + newParentCategory.getName() + "' in ledger '" + selectedLedger.getName() + "'.");

        //print updated category tree
        printCategoryTree(selectedLedger);
    }

    //private helper methods
    private String inputName(){
        System.out.print("Enter the name: ");
        String name = scanner.nextLine();
        if(name.isEmpty()){
            System.out.println("Name cannot be empty.");
            return inputName();
        }
        return name;
    }
    private void printCategoryTree(Ledger selectedLedger) {
        List<LedgerCategory> updatedCategories = ledgerCategoryController.getLedgerCategoryTreeByLedger(selectedLedger);

        List<LedgerCategory> expenseRoots = updatedCategories.stream()
                .filter(cat -> cat.getParent() == null && cat.getType() == CategoryType.EXPENSE)
                .toList();
        List<LedgerCategory> incomeRoots = updatedCategories.stream()
                .filter(cat -> cat.getParent() == null && cat.getType() == CategoryType.INCOME)
                .filter(cat-> !cat.getName().equals("Claim Income"))
                .toList();
        System.out.println("Category Tree for Ledger: " + selectedLedger.getName());
        System.out.println("Expense categories: ");
        for (LedgerCategory category : expenseRoots) {
            System.out.println(" Category: " + category.getName());
            List<LedgerCategory> children = updatedCategories.stream()
                    .filter(cat -> cat.getParent() != null && cat.getParent().getId() == category.getId())
                    .toList();
            for (LedgerCategory child : children) {
                System.out.println("   Subcategory: " + child.getName());
            }
        }
        System.out.println("Income categories: ");
        for (LedgerCategory category : incomeRoots) {
            System.out.println(" Category: " + category.getName());
            List<LedgerCategory> children = updatedCategories.stream()
                    .filter(cat -> cat.getParent() != null && cat.getParent().getId() == category.getId())
                    .toList();
            for (LedgerCategory child : children) {
                System.out.println("   Subcategory: " + child.getName());
            }
        }
    }
    private String selectCategoryType() {
        System.out.println("1. INCOME");
        System.out.println("2. EXPENSE");
        System.out.print("Enter choice (1 or 2): ");
        String input = scanner.nextLine().trim();
        String categoryTypeInput;
        if (input.equals("1")) {
            categoryTypeInput = "INCOME";
        } else if (input.equals("2")) {
            categoryTypeInput = "EXPENSE";
        } else {
            System.out.println("Invalid choice.");
            return selectCategoryType();
        }
         return categoryTypeInput;
    }
    private Ledger selectLedger(User user) {
        List<Ledger> ledgers = ledgerController.getLedgersByUser(user);

        if(ledgers.isEmpty()) {
            System.out.println("No ledgers found for the user.");
            return null;
        }
        for (int i = 0; i < ledgers.size(); i++) {
            Ledger ledger = ledgers.get(i);
            System.out.println((i + 1) + ". "+ "Name: " + ledger.getName());
        }
        System.out.print("Enter the number of the ledger: ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if(choice < 1 || choice > ledgers.size()) {
            System.out.println("Invalid choice.");
            return selectLedger(user);
        }
        return ledgers.get(choice - 1);
    }

    //tree selection
    private LedgerCategory selectCategoryWithTree(List<LedgerCategory> categories) {
        List<LedgerCategory> rootCategories = categories.stream()
                .filter(cat -> cat.getParent() == null)
                .filter(cat -> !cat.getName().equals("Claim Income"))
                .toList();
        System.out.println("0. Cancel");
        for (int i = 0; i < rootCategories.size(); i++) {
            //display parent category
            LedgerCategory parent = rootCategories.get(i);
            System.out.println( (i + 1) + ". "+ "Name: " + parent.getName());

            //display sub-categories
            List<LedgerCategory> subCategories = categories.stream()
                    .filter(cat -> cat.getParent() != null && cat.getParent().getId() == parent.getId())
                    .toList();
            if (!subCategories.isEmpty()) {
                for (int j = 0; j < subCategories.size(); j++) {
                    LedgerCategory child = subCategories.get(j);
                    System.out.println("   " + (i + 1) + "." + (j + 1) + " " + child.getName());
                }
            }
        }
        System.out.print("Enter choice (e.g. 1 or 1.2): ");
        String input = scanner.nextLine().trim();

        if(input.equals("0")) {
            return null;
        }

        if(input.contains(".")) {
            //sub-category selected
            String[] parts = input.split("\\.");
            int parentIndex = Integer.parseInt(parts[0]) - 1;
            int childIndex = Integer.parseInt(parts[1]) - 1;
            if(parentIndex < 0 || parentIndex >= categories.size()) {
                System.out.println("Invalid choice.");
                return selectCategoryWithTree(categories);
            }

            LedgerCategory parentCategory = rootCategories.get(parentIndex);
            List<LedgerCategory> subCategories = categories.stream()
                    .filter(cat -> cat.getParent() != null && cat.getParent().getId() == parentCategory.getId())
                    .toList();
            if(childIndex < 0 || childIndex >= subCategories.size()) {
                System.out.println("Invalid choice.");
                return selectCategoryWithTree(categories);
            }
            return subCategories.get(childIndex);
        } else {
            //first-level category selected
            int choice = Integer.parseInt(input);

            if(choice < 1 || choice > rootCategories.size()) {
                System.out.println("Invalid choice.");
                return selectCategoryWithTree(categories);
            }
            return rootCategories.get(choice - 1);
        }
    }

    private LedgerCategory selectCategory(List<LedgerCategory> categories){
        if(categories.isEmpty()) {
            System.out.println("No categories available.");
            return null;
        }
        System.out.println("0. Cancel");
        for (int i = 0; i < categories.size(); i++) {
            LedgerCategory category = categories.get(i);
            System.out.println( (i + 1) + ". "+ "Name: " + category.getName());
        }

        System.out.print("Enter the number of the category: ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if(choice == 0) {
            return null;
        }
        if(choice < 1 || choice > categories.size()) {
            System.out.println("Invalid choice.");
            return selectCategory(categories);
        }
        return categories.get(choice - 1);
    }
}
