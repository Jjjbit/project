package com.ledger.cli;

import com.ledger.business.BudgetController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.Budget;
import com.ledger.domain.CategoryType;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class BudgetCLI {
    private final BudgetController budgetController;
    private final ReportController reportController;
    private final UserController userController;
    private final Scanner scanner = new Scanner(System.in);

    public BudgetCLI(BudgetController budgetController,
                     ReportController reportController, UserController userController) {
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
        System.out.print("Enter budget period to merge (MONTHLY, YEARLY): ");
        String periodInput = scanner.nextLine().trim().toUpperCase();
        Budget.Period period = Budget.Period.valueOf(periodInput);

        //show budgets of the period
        Map<Integer, Budget> budgetMap = new LinkedHashMap<>();
        int[] counter = {1};

        System.out.println("\n=== Available Budgets by Category ===");
        List<LedgerCategory> expenseCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .toList();

        printCategoryBudgets(expenseCategories, period, counter, budgetMap);

        Budget uncategorizedBudget = reportController.getActiveBudgetByLedger(selectedLedger, period);
        if(uncategorizedBudget==null){
            System.out.println("No ledger budget found for the selected period.");
            return;
        }
        System.out.println("\n=== Uncategorized Budgets ===");
        System.out.printf("%d. Amount: %s, Period: %s%s\n",
                counter[0],
                uncategorizedBudget.getAmount(),
                uncategorizedBudget.getPeriod(),
                budgetController.isOverBudget(uncategorizedBudget) ? " [OVER BUDGET]" : "within budget");
        budgetMap.put(counter[0]++, uncategorizedBudget);

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
        System.out.print("Enter budget period to show (MONTHLY, YEARLY): ");
        String periodInput = scanner.nextLine().trim().toUpperCase();
        Budget.Period period = Budget.Period.valueOf(periodInput);

        //show budgets
        Budget ledgerBudget = reportController.getActiveBudgetByLedger(selectedLedger, period);
        if(ledgerBudget==null){
            System.out.println("No ledger budget found for the selected period.");
            return;
        }

        System.out.println("\n=== Ledger's Budget ===");
        System.out.println("Amount: " + ledgerBudget.getAmount() +
                ", Period: " + ledgerBudget.getPeriod() +
                (ledgerBudget.getCategory()!=null ? ", Category: " + ledgerBudget.getCategory() : ", No Category") +
                (budgetController.isOverBudget(ledgerBudget) ? ", [OVER BUDGET]" : ", within budget"));

        List<LedgerCategory> categories = reportController.getLedgerCategoryTreeByLedger(selectedLedger);
        List<LedgerCategory> topCategories = categories.stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .filter(c -> c.getParent() == null)
                .toList();

        System.out.println("\n=== Categories' Budgets ===");
        for(LedgerCategory category : topCategories){
            Budget categoryBudget = reportController.getActiveBudgetByCategory(category, period);
            if(categoryBudget!=null){
                System.out.println("Category: " + category.getName() + "Amount: " + categoryBudget.getAmount() +
                        ", Period: " + categoryBudget.getPeriod() +
                        (budgetController.isOverBudget(categoryBudget) ? ", [OVER BUDGET]" : ", within budget"));
            }
            List<LedgerCategory> children = categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == category.getId())
                    .toList();
            if(children.isEmpty()){
                continue;
            }
            for(LedgerCategory subcategory : children){
                Budget subcategoryBudget = reportController.getActiveBudgetByCategory(subcategory, period);
                if(subcategoryBudget!=null){
                    System.out.println(" " + "Amount: " + subcategoryBudget.getAmount() +
                            ", Period: " + subcategoryBudget.getPeriod() +
                            ", Category: " + subcategory.getName() +
                            (budgetController.isOverBudget(subcategoryBudget) ? ", [OVER BUDGET]" : ", within budget"));
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
        System.out.print("Enter budget period to edit (MONTHLY, YEARLY): ");
        String periodInput = scanner.nextLine().trim().toUpperCase();
        Budget.Period period = Budget.Period.valueOf(periodInput);

        //show budgets
        Map<Integer, Budget> budgetMap = new LinkedHashMap<>();
        int[] counter = {1};

        System.out.println("\n=== Available Budgets by Category ===");
        List<LedgerCategory> expenseCategories = reportController.getLedgerCategoryTreeByLedger(selectedLedger).stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .toList();
        printAllCategoryBudgets(expenseCategories, period, counter, budgetMap);

        System.out.println("\n=== Uncategorized Budgets ===");
        Budget ledgerBudget = reportController.getActiveBudgetByLedger(selectedLedger, period);
        if(ledgerBudget==null){
            System.out.println("No ledger budget found for the selected period.");
            return;
        }

        System.out.printf("%d. Amount: %s, Period: %s%s\n",
                counter[0],
                ledgerBudget.getAmount(),
                ledgerBudget.getPeriod(),
                budgetController.isOverBudget(ledgerBudget) ? " [OVER BUDGET]" : "within budget");
        budgetMap.put(counter[0]++, ledgerBudget);

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
    private Ledger selectLedger() {
        List<Ledger> ledgers=reportController.getLedgerByUser(userController.getCurrentUser());

        for(int i=0;i<ledgers.size();i++){
            System.out.println((i+1) + ". " + ledgers.get(i).getName());
        }
        System.out.print("Select a ledger by number: ");
        int ledgerIndex = scanner.nextInt()-1;
        if(ledgerIndex < 0 || ledgerIndex >= ledgers.size()) {
            System.out.println("Invalid ledger selection.");
            return selectLedger();
        }
        return ledgers.get(ledgerIndex);
    }

    //for edit
    private void printAllCategoryBudgets(List<LedgerCategory> categories, Budget.Period period,
                                         int[] counter, Map<Integer, Budget> budgetMap) {

        List<LedgerCategory> topCategories = categories.stream()
                .filter(c -> c.getParent() == null)
                .toList();

        for (LedgerCategory category : topCategories) {
            System.out.println(category.getName());

            // print budgets for this category
            Budget budget = reportController.getActiveBudgetByCategory(category, period);
            if (budget == null) {
                continue;
            }

            //print category budget with number
            String status = budgetController.isOverBudget(budget) ? " [OVER BUDGET]" : " (within budget)";
            String line = counter[0] + ". Amount: " + budget.getAmount()
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
                    System.out.println("   " + subcategory.getName());

                    Budget subcategoryBudget = reportController.getActiveBudgetByCategory(subcategory, period);
                    if(subcategoryBudget==null){
                        continue;
                    }

                    //print subcategory budget with number
                    String subStatus = budgetController.isOverBudget(subcategoryBudget) ? " [OVER BUDGET]" : " (within budget)";
                    String subLine = "   " + counter[0] + ". Amount: " + subcategoryBudget.getAmount()
                            + ", Period: " + subcategoryBudget.getPeriod() + subStatus;
                    System.out.println(subLine);

                    budgetMap.put(counter[0], subcategoryBudget);
                    counter[0]++;
                }
            }
        }
    }

    //select only top-level category budgets. subcategories are printed but not selectable. for merge
    private void printCategoryBudgets(List<LedgerCategory> categories, Budget.Period period,
                                      int[] counter, Map<Integer, Budget> budgetMap) {
        List<LedgerCategory> topCategories = categories.stream()
                .filter(c -> c.getParent() == null)
                .toList();

        for (LedgerCategory category : topCategories) {
            System.out.println(category.getName());

            // print budgets for this category
            Budget budget = reportController.getActiveBudgetByCategory(category, period);
            if(budget==null){
                continue;
            }

            // print top-level category budget with number
            String status = budgetController.isOverBudget(budget) ? " [OVER BUDGET]" : " (within budget)";
            String line = counter[0] + ". Amount: " + budget.getAmount()
                    + ", Period: " + budget.getPeriod() + status;
            System.out.println(line);

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
                System.out.println("   " + subcategory.getName());

                Budget subcategoryBudget = reportController.getActiveBudgetByCategory(subcategory, period);
                if(subcategoryBudget==null){
                    continue;
                }

                //print subcategory budget without number
                String subStatus = budgetController.isOverBudget(subcategoryBudget) ? " [OVER BUDGET]" : " (within budget)";
                String subLine = "   " + "Amount: " + subcategoryBudget.getAmount()
                        + ", Period: " + subcategoryBudget.getPeriod() + subStatus;
                System.out.println(subLine);
            }

        }
    }




}
