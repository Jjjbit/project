package com.ledger.CLI;

import com.ledger.BusinessLogic.*;
import com.ledger.DomainModel.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class BudgetCLI {
    private final BudgetController budgetController;
    private final ReportController reportController;
    private final UserController userController;
    private final LedgerController ledgerController;
    private final LedgerCategoryController ledgerCategoryController;
    private final Scanner scanner = new Scanner(System.in);

    public BudgetCLI(BudgetController budgetController,
                     ReportController reportController, UserController userController,
                     LedgerController ledgerController, LedgerCategoryController ledgerCategoryController) {
        this.ledgerCategoryController = ledgerCategoryController;
        this.ledgerController = ledgerController;
        this.userController = userController;
        this.reportController = reportController;
        this.budgetController = budgetController;
    }

    public void mergeBudgets() {
        System.out.println("\n === Merging budgets ===");


        //select ledger
        System.out.println("Select ledger:");
        Ledger selectedLedger = selectLedger();

        //select period
        System.out.println("Select budget period: ");
        Period period = selectBudgetPeriod();

        //show budgets of the period
        Map<Integer, Budget> budgetMap = new LinkedHashMap<>();
        int[] counter = {1};

        Budget uncategorizedBudget = budgetController.getActiveBudgetByLedger(selectedLedger, period);
        if(uncategorizedBudget==null){
            System.out.println("No ledger budget found for the selected period.");
            return;
        }
        System.out.println("\n=== Total Budget ===");
        System.out.printf("%d. Amount: %s, Period: %s%s\n",
                counter[0],
                uncategorizedBudget.getAmount(),
                uncategorizedBudget.getPeriod(),
                reportController.isOverBudget(uncategorizedBudget) ? " [OVER BUDGET]" : " (within budget)");
        budgetMap.put(counter[0]++, uncategorizedBudget);

        System.out.print("\n=== Available Budgets by Category ===");
        List<LedgerCategory> expenseCategories = ledgerCategoryController.getCategoryTreeByLedger(selectedLedger).stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .toList();

        printCategoryBudgets(expenseCategories, period, counter, budgetMap);

       //select target budget
        System.out.print("\nSelect a target budget number to merge into: ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline
        Budget targetBudget = budgetMap.get(choice);
        if (targetBudget == null) {
            System.out.println("Invalid budget selection.");
            return;
        }

        boolean success = budgetController.mergeBudgets(targetBudget);
        if (!success) {
            System.out.println("Failed to merge budgets.");
        }
        System.out.println("Budgets merged successfully.");

    }

    public void showAllBudgets() {
        System.out.println("\n === Showing all budgets ===");

        //select ledger
        System.out.println("Select ledger to show budgets from:");
        Ledger selectedLedger = selectLedger();

        //select period
        System.out.println("Select budget period:");
        Period period = selectBudgetPeriod();

        //show budgets
        Budget ledgerBudget = budgetController.getActiveBudgetByLedger(selectedLedger, period);
        if(ledgerBudget==null){
            System.out.println("No ledger budget found for the selected period.");
            return;
        }

        System.out.println("\n=== Ledger's Budget ===");
        System.out.println("Ledger: "+ selectedLedger.getName() + ", Amount: " + ledgerBudget.getAmount() +
                ", Period: " + ledgerBudget.getPeriod() +
                (reportController.isOverBudget(ledgerBudget) ? ", [OVER BUDGET]" : ", within budget"));


        List<LedgerCategory> categories = ledgerCategoryController.getCategoryTreeByLedger(selectedLedger);
        List<LedgerCategory> topCategories = categories.stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .filter(c -> c.getParent() == null)
                .toList();

        System.out.println("\n=== Categories' Budgets ===");
        for(LedgerCategory category : topCategories){
            Budget categoryBudget = budgetController.getActiveBudgetByCategory(category, period);
            if(categoryBudget!=null){
                System.out.println("Category: " + category.getName() + ", Amount: " + categoryBudget.getAmount() +
                        ", Period: " + categoryBudget.getPeriod() +
                        (reportController.isOverBudget(categoryBudget) ? ", [OVER BUDGET]" : ", within budget"));
            }
            List<LedgerCategory> children = categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == category.getId())
                    .toList();
            if(children.isEmpty()){
                continue;
            }
            for(LedgerCategory subcategory : children){
                Budget subcategoryBudget = budgetController.getActiveBudgetByCategory(subcategory, period);
                if(subcategoryBudget!=null){
                    System.out.println(" SubCategory: " + subcategory.getName() + ", Amount: " + subcategoryBudget.getAmount() +
                            ", Period: " + subcategoryBudget.getPeriod() +
                            (reportController.isOverBudget(subcategoryBudget) ? ", [OVER BUDGET]" : ", within budget"));
                }
            }
        }
    }

    public void editBudget() {
        System.out.println("\n === Editing a budget ===");

        //select ledger
        System.out.println("Select ledger:");
        Ledger selectedLedger = selectLedger();

        //select period
        System.out.println("Select budget period: ");
        Period period = selectBudgetPeriod();

        //show budgets
        Map<Integer, Budget> budgetMap = new LinkedHashMap<>();
        int[] counter = {1};

        System.out.println("\n=== Total Budget ===");
        Budget ledgerBudget = budgetController.getActiveBudgetByLedger(selectedLedger, period);
        if(ledgerBudget==null){
            System.out.println("No ledger budget found for the selected period.");
            return;
        }

        System.out.printf("%d." + "Ledger: " + selectedLedger.getName() +  " - Amount: %s, Period: %s%s\n",
                counter[0],
                ledgerBudget.getAmount(),
                ledgerBudget.getPeriod(),
                reportController.isOverBudget(ledgerBudget) ? ", [OVER BUDGET]" : " (within budget)");
        budgetMap.put(counter[0]++, ledgerBudget);

        System.out.println("\n=== Available Budgets by Category ===");
        List<LedgerCategory> expenseCategories = ledgerCategoryController.getCategoryTreeByLedger(selectedLedger).stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .toList();
        printAllCategoryBudgets(expenseCategories, period, counter, budgetMap);

        //select budget
        System.out.print("Select a budget by number: ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline
        Budget budgetToEdit = budgetMap.get(choice);
        if (budgetToEdit == null) {
            System.out.println("Invalid budget selection.");
            return;
        }

        //enter new amount
        System.out.print("Enter new budget amount: ");
        BigDecimal newAmount = scanner.nextBigDecimal();
        scanner.nextLine(); // consume newline

        boolean success = budgetController.editBudget(budgetToEdit, newAmount);
        if (!success) {
            System.out.println("Failed to edit budget.");
        }
        System.out.println("Budget edited successfully.");
    }

    //private helper methods
    private Period selectBudgetPeriod() {
        Period[] periods = Period.values();

        for (int i = 0; i < periods.length; i++) {
            System.out.println((i + 1) + ". " + periods[i]);
        }
        System.out.print("Enter number: ");

        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if (input.isEmpty()) {
            System.out.println("Input cannot be empty. Please try again.");
            return selectBudgetPeriod();
        }
        if (!input.matches("\\d+")) {
            System.out.println("Please enter a valid number!");
            return selectBudgetPeriod();
        }

        if (choice < 1 || choice > periods.length) {
            System.out.println("Invalid selection. Please try again.");
            return selectBudgetPeriod();
        }
        return periods[choice - 1];
    }

    private Ledger selectLedger() {
        List<Ledger> ledgers = ledgerController.getLedgersByUser(userController.getCurrentUser());

        for(int i=0;i<ledgers.size();i++){
            System.out.println((i+1) + ". " + ledgers.get(i).getName());
        }
        System.out.print("Select a ledger by number: ");

        int ledgerIndex = scanner.nextInt()-1;
        scanner.nextLine(); // consume newline

        if(ledgerIndex < 0 || ledgerIndex >= ledgers.size()) {
            System.out.println("Invalid ledger selection.");
            return selectLedger();
        }
        return ledgers.get(ledgerIndex);
    }

    //for edit
    private void printAllCategoryBudgets(List<LedgerCategory> categories, Period period,
                                         int[] counter, Map<Integer, Budget> budgetMap) {

        List<LedgerCategory> topCategories = categories.stream()
                .filter(c -> c.getParent() == null)
                .toList();

        for (LedgerCategory category : topCategories) {

            // print budgets for this category
            Budget budget = budgetController.getActiveBudgetByCategory(category, period);
            if (budget == null) {
                continue;
            }

            //print category budget with number
            String status = reportController.isOverBudget(budget) ? " [OVER BUDGET]" : " (within budget)";
            String line = counter[0] + ". "+ "Category: "+ category.getName() + " - Amount: " + budget.getAmount()
                    + ", Period: " + budget.getPeriod() + status;
            System.out.println(line);

            budgetMap.put(counter[0], budget);
            counter[0]++;

            // print subcategories recursively
            List<LedgerCategory> children = categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == category.getId())
                    .toList();
            if (!children.isEmpty()) {
                for(LedgerCategory subcategory : children){
                    Budget subcategoryBudget = budgetController.getActiveBudgetByCategory(subcategory, period);
                    if(subcategoryBudget==null){
                        continue;
                    }

                    //print subcategory budget with number
                    String subStatus = reportController.isOverBudget(subcategoryBudget) ? " [OVER BUDGET]" : " (within budget)";
                    String subLine = "   " + counter[0] + ". " + "SubCategory: " + subcategory.getName() + " - Amount: " + subcategoryBudget.getAmount()
                            + ", Period: " + subcategoryBudget.getPeriod() + subStatus;
                    System.out.println(subLine);

                    budgetMap.put(counter[0], subcategoryBudget);
                    counter[0]++;
                }
            }
        }
    }

    //select only top-level category budgets. subcategories are printed but not selectable. for merge
    private void printCategoryBudgets(List<LedgerCategory> categories, Period period,
                                      int[] counter, Map<Integer, Budget> budgetMap) {
        List<LedgerCategory> topCategories = categories.stream()
                .filter(c -> c.getParent() == null)
                .toList();

        for (LedgerCategory category : topCategories) {
            System.out.println();

            // print budgets for this category
            Budget budget = budgetController.getActiveBudgetByCategory(category, period);
            if(budget==null){
                continue;
            }

            // print top-level category budget with number
            String status = reportController.isOverBudget(budget) ? " [OVER BUDGET]" : " (within budget)";
            String line = counter[0] + ". Category: " + category.getName() + ", Amount: " + budget.getAmount()
                    + ", Period: " + budget.getPeriod() + status;
            System.out.print(line);

            //only top-level category budgets are selectable
            budgetMap.put(counter[0], budget);
            counter[0]++;

            // print subcategories budget without number
            List<LedgerCategory> children = categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == category.getId())
                    .toList();
            if(children.isEmpty()){
                continue;
            }
            for(LedgerCategory subcategory : children){
                Budget subcategoryBudget = budgetController.getActiveBudgetByCategory(subcategory, period);
                if(subcategoryBudget==null){
                    continue;
                }

                //print subcategory budget without number
                String subStatus = reportController.isOverBudget(subcategoryBudget) ? " [OVER BUDGET]" : " (within budget)";
                String subLine = "   SubCategory: " + subcategory.getName() + ", Amount: " + subcategoryBudget.getAmount()
                        + ", Period: " + subcategoryBudget.getPeriod() + subStatus;
                System.out.print("\n" + subLine);
            }

        }
    }

}
