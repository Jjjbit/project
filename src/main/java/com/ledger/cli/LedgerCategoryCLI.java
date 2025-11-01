package com.ledger.cli;

import com.ledger.business.LedgerCategoryController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.CategoryType;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;
import com.ledger.domain.User;

import java.util.List;
import java.util.Scanner;

public class LedgerCategoryCLI {
    private final LedgerCategoryController ledgerCategoryController;
    private final ReportController reportController;
    private final UserController userController;
    private final Scanner scanner = new Scanner(System.in);

    public LedgerCategoryCLI(LedgerCategoryController ledgerCategoryController,
                             ReportController reportController,
                             UserController userController) {
        this.userController = userController;
        this.reportController = reportController;
        this.ledgerCategoryController = ledgerCategoryController;
    }

    public void addCategory() {
        System.out.println("\n === Adding a new category of first level ===");


        //select ledger
        System.out.println("\nSelect a ledger to add the category to:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());

        //enter category name
        System.out.print("Enter the name of the new category: ");
        String categoryName = scanner.nextLine().trim();
        if(categoryName.isEmpty()){
            System.out.println("Category name cannot be empty.");
            return;
        }

        //enter category type
        System.out.print("Enter the type of the new category (INCOME/EXPENSE): ");
        String categoryTypeInput = enterCategoryType();

        //create category
        LedgerCategory category = ledgerCategoryController.createCategory(categoryName, selectedLedger,
                Enum.valueOf(CategoryType.class, categoryTypeInput.toUpperCase()));

        if(category == null) {
            System.out.println("Failed to create category. It may already exist or the input was invalid.");
            return;
        }
        System.out.println("Category '" + category.getName() + "' added successfully to ledger '" + selectedLedger.getName() + "'.");
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
        System.out.print("Enter the type of the parent category (INCOME/EXPENSE): ");
        String categoryTypeInput = enterCategoryType();
        CategoryType categoryType = categoryTypeInput.equalsIgnoreCase("INCOME") ?
                CategoryType.INCOME : CategoryType.EXPENSE;

        //select parent category
        List<LedgerCategory> allCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger);
        List<LedgerCategory> parentCategories = allCategories.stream()
                .filter(cat -> cat.getType() == (categoryType)
                        && cat.getParent() == null)
                .toList();
        System.out.println("Select a parent category for the new sub-category:");
        LedgerCategory parentCategory = selectCategory(parentCategories);

        //enter sub-category name
        System.out.print("Enter the name of the new sub-category: ");
        String subCategoryName = scanner.nextLine().trim();

        //create sub-category
        LedgerCategory subCategory = ledgerCategoryController.createSubCategory(subCategoryName, parentCategory);
        if (subCategory == null) {
            System.out.println("Failed to create sub-category. It may already exist or the input was invalid.");
            return;
        }
        System.out.println("Sub-category '" + subCategory.getName() + "' added successfully under category '"
                + parentCategory.getName() + "' in ledger '" + selectedLedger.getName() + "'.");

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

        //select category type
        System.out.print("Enter the type of the category to rename (INCOME/EXPENSE): ");
        String categoryTypeInput = enterCategoryType();
        CategoryType categoryType = categoryTypeInput.equalsIgnoreCase("INCOME") ?
                CategoryType.INCOME : CategoryType.EXPENSE;

        //select category
        List<LedgerCategory> allCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                .filter(cat -> cat.getType() == categoryType)
                .toList();
        System.out.println("\nSelect a category to rename:");
        LedgerCategory categoryToRename = selectCategoryWithTree(allCategories);

        //enter new name
        System.out.print("Enter the new name for the category '" + categoryToRename.getName() + "': ");
        String newName = scanner.nextLine().trim();

        //rename category
        boolean success = ledgerCategoryController.renameCategory(categoryToRename, newName);
        if (!success) {
            System.out.println("Failed to rename category. The new name may already exist or the input was invalid.");
            return;
        }
        System.out.println("Category renamed successfully to '" + newName + "'.");


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

        //select category type
        System.out.print("Enter the type of the category to delete (INCOME/EXPENSE): ");
        String categoryTypeInput = enterCategoryType();
        CategoryType categoryType = categoryTypeInput.equalsIgnoreCase("INCOME") ?
                CategoryType.INCOME : CategoryType.EXPENSE;

        //select category
        List<LedgerCategory> allCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                .filter(cat -> cat.getType() == categoryType)
                .toList();
        System.out.println("Select a category to delete:");
        LedgerCategory categoryToDelete = selectCategoryWithTree(allCategories);

        //select if delete transactions
        System.out.print("Are you sure you want to delete the category '" + categoryToDelete.getName() + "'? (yes/no): ");
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
            List<LedgerCategory> categories = reportController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                    .filter(cat -> !cat.getId().equals(categoryToDelete.getId())
                            && cat.getType() == categoryToDelete.getType())
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

    }

    public void promoteSubCategory() {
        System.out.println("\n === Promoting a sub-category ===");

        //select ledger
        System.out.println("\nSelect a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger == null){
            System.out.println("No ledger selected.");
            return;
        }

        //select category type
        System.out.print("Enter the type of the sub-category to promote (INCOME/EXPENSE): ");
        String categoryTypeInput = enterCategoryType();
        CategoryType categoryType = categoryTypeInput.equalsIgnoreCase("INCOME") ?
                CategoryType.INCOME : CategoryType.EXPENSE;

        //select sub-category
        List<LedgerCategory> allCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                .filter(cat -> cat.getType() == categoryType
                        && cat.getParent() != null)
                .toList();
        System.out.println("Select a sub-category to promote:");
        LedgerCategory subCategoryToPromote = selectCategory(allCategories);

        //promote sub-category
        boolean success = ledgerCategoryController.promoteSubCategory(subCategoryToPromote);
        if (!success) {
            System.out.println("Failed to promote sub-category.");
            return;
        }
        System.out.println("Sub-category '" + subCategoryToPromote.getName() + "' promoted successfully to first-level category in ledger '" + selectedLedger.getName() + "'.");
        //print updated category tree
        List<LedgerCategory> updatedCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger);
        List<LedgerCategory> firstLevelCategories = updatedCategories.stream()
                .filter(cat -> cat.getParent() == null)
                .toList();
        System.out.println("Updated Category Tree:");
        for (LedgerCategory category : firstLevelCategories) {
            printCategoryTree(category, "");
        }
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
        System.out.print("Enter the type of the category to demote (INCOME/EXPENSE): ");
        String categoryTypeInput = enterCategoryType();
        CategoryType categoryType = categoryTypeInput.equalsIgnoreCase("INCOME") ?
                CategoryType.INCOME : CategoryType.EXPENSE;

        //select category
        List<LedgerCategory> allCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                .filter(cat -> cat.getType() == categoryType
                        && cat.getParent() == null)
                .toList();
        System.out.println("Select a first-level category to demote:");
        LedgerCategory categoryToDemote = selectCategory(allCategories);

        //select new parent category
        List<LedgerCategory> potentialParents = allCategories.stream()
                .filter(cat -> !cat.getId().equals(categoryToDemote.getId()))
                .toList();
        System.out.println("Select a new parent category for the category to demote:");
        LedgerCategory newParentCategory = selectCategory(potentialParents);

        //demote category
        boolean success = ledgerCategoryController.demoteCategory(categoryToDemote, newParentCategory);
        if (!success) {
            System.out.println("Failed to demote category.");
            return;
        }
        System.out.println("Category '" + categoryToDemote.getName() + "' demoted successfully under category '"
                + newParentCategory.getName() + "' in ledger '" + selectedLedger.getName() + "'.");

        //print updated category tree
        List<LedgerCategory> updatedCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger);
        List<LedgerCategory> updatedFirstLevelCategories = updatedCategories.stream()
                .filter(cat -> cat.getParent() == null)
                .toList();
        System.out.println("Updated Category Tree:");
        for (LedgerCategory category : updatedFirstLevelCategories) {
            printCategoryTree(category, "");
        }
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
        List<LedgerCategory> allCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger);
        List<LedgerCategory> subCategories = allCategories.stream()
                .filter(cat -> cat.getParent() != null)
                .toList();
        LedgerCategory subCategoryToChange = selectCategory(subCategories);

        //select new parent category
        List<LedgerCategory> potentialParents = allCategories.stream()
                .filter(cat -> !cat.getId().equals(subCategoryToChange.getId())
                        && cat.getType() == subCategoryToChange.getType()
                        && cat.getParent() == null)
                .toList();
        LedgerCategory newParentCategory = selectCategory(potentialParents);

        //change parent category
        boolean success = ledgerCategoryController.changeParent(subCategoryToChange, newParentCategory);
        if (!success) {
            System.out.println("Failed to change parent category.");
            return;
        }
        System.out.println("Sub-category '" + subCategoryToChange.getName() + "' now has new parent category '"
                + newParentCategory.getName() + "' in ledger '" + selectedLedger.getName() + "'.");
        //print updated category tree
        List<LedgerCategory> updatedCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger);
        List<LedgerCategory> updatedFirstLevelCategories = updatedCategories.stream()
                .filter(cat -> cat.getParent() == null)
                .toList();
        System.out.println("Updated Category Tree:");
        for (LedgerCategory category : updatedFirstLevelCategories) {
            printCategoryTree(category, "");
        }
    }

    //private helper methods
    private String enterCategoryType() {
        String categoryTypeInput = scanner.nextLine().trim();
       if(!categoryTypeInput.equalsIgnoreCase("INCOME") &&
               !categoryTypeInput.equalsIgnoreCase("EXPENSE")) {
           System.out.println("Invalid category type. Please enter INCOME or EXPENSE.");
           return enterCategoryType();
       }
         return categoryTypeInput;
    }

    private void printCategoryTree(LedgerCategory category, String indent) {
        System.out.println(indent + "- " + category.getName());
        for(LedgerCategory child : category.getChildren()) {
            printCategoryTree(child, indent + "  ");
        }
    }

    private Ledger selectLedger(User user) {
        List<Ledger> ledgers = reportController.getLedgerByUser(user);

        if(ledgers.isEmpty()) {
            System.out.println("No ledgers found for the user.");
            return null;
        }
        for (int i = 0; i < ledgers.size(); i++) {
            Ledger ledger = ledgers.get(i);
            System.out.println("Ledger " + (i + 1) + ". "+ "Name: " + ledger.getName());
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
        for (int i = 0; i < categories.size(); i++) {
            LedgerCategory parent = categories.get(i);
            System.out.println( (i + 1) + ". "+ "Name: " + parent.getName());

            List<LedgerCategory> subCategories = parent.getChildren();
            if (subCategories != null && !subCategories.isEmpty()) {
                for (int j = 0; j < subCategories.size(); j++) {
                    LedgerCategory child = subCategories.get(j);
                    System.out.println("   " + (i + 1) + "." + (j + 1) + " " + child.getName());
                }
            }
        }
        System.out.print("Enter choice (e.g. 1 or 1.2): ");
        String input = scanner.nextLine().trim();
        if(input.contains(".")) {
            //sub-category selected
            String[] parts = input.split("\\.");
            int parentIndex = Integer.parseInt(parts[0]) - 1;
            int childIndex = Integer.parseInt(parts[1]) - 1;
            if(parentIndex < 0 || parentIndex >= categories.size()) {
                System.out.println("Invalid choice.");
                return selectCategoryWithTree(categories);
            }
            LedgerCategory parentCategory = categories.get(parentIndex);
            List<LedgerCategory> subCategories = parentCategory.getChildren();
            if(childIndex < 0 || childIndex >= subCategories.size()) {
                System.out.println("Invalid choice.");
                return selectCategoryWithTree(categories);
            }
            return subCategories.get(childIndex);
        } else {
            //first-level category selected
            int choice = Integer.parseInt(input);
            if(choice < 1 || choice > categories.size()) {
                System.out.println("Invalid choice.");
                return selectCategoryWithTree(categories);
            }
            return categories.get(choice - 1);
        }
    }

    private LedgerCategory selectCategory(List<LedgerCategory> categories){
        for (int i = 0; i < categories.size(); i++) {
            LedgerCategory category = categories.get(i);
            System.out.println( (i + 1) + ". "+ "Name: " + category.getName());
        }

        System.out.print("Enter the number of the category: ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if(choice < 1 || choice > categories.size()) {
            System.out.println("Invalid choice.");
            return selectCategory(categories);
        }
        return categories.get(choice - 1);
    }
}
