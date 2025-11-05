package com.ledger.cli;

import com.ledger.business.BudgetController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.Budget;
import com.ledger.domain.CategoryType;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

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
        List<LedgerCategory> topCategories = selectedLedger.getCategories().stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .filter(c -> c.getParent() == null)
                .toList();

        printCategoryBudgetsRecursive(topCategories, "", period, counter, budgetMap);

        Budget uncategorizedBudget = selectedLedger.getBudgets().stream()
                .filter(b -> b.getCategory() == null)
                .filter(b -> b.getPeriod() == period)
                .findFirst()
                .orElse(null);
        if(uncategorizedBudget==null){
            System.out.println("No ledger budget found for the selected period.");
            return;
        }
        if(!uncategorizedBudget.isActive(LocalDate.now())){
            uncategorizedBudget.refreshIfExpired();
            budgetController.editBudget(uncategorizedBudget, BigDecimal.ZERO);
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

        List<LedgerCategory> topCategories = selectedLedger.getCategories().stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .filter(c -> c.getParent() == null)
                .toList();

        System.out.println("\n=== Categories' Budgets ===");
        for(LedgerCategory category : topCategories){
            Budget categoryBudget = reportController.getActiveBudgetByCategory(category, period);
            if(categoryBudget!=null && categoryBudget.isActive(LocalDate.now())){
                System.out.println("Amount: " + categoryBudget.getAmount() +
                        ", Period: " + categoryBudget.getPeriod() +
                        ", Category: " + category.getName() +
                        (budgetController.isOverBudget(categoryBudget) ? ", [OVER BUDGET]" : ", within budget"));
            }
            if(category.getChildren().isEmpty()){
                continue;
            }
            for(LedgerCategory subcategory : category.getChildren()){
                Budget subcategoryBudget = reportController.getActiveBudgetByCategory(subcategory, period);
                if(subcategoryBudget!=null && subcategoryBudget.isActive(LocalDate.now())){
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
        List<LedgerCategory> topCategories = selectedLedger.getCategories().stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .filter(c -> c.getParent() == null)
                .toList();
        printAllCategoryBudgetsRecursive(topCategories, "", period, counter, budgetMap);

        System.out.println("\n=== Uncategorized Budgets ===");
        Budget ledgerBudget = selectedLedger.getBudgets().stream()
                .filter(b -> b.getCategory() == null)
                .filter(b -> b.getPeriod() == period)
                .findFirst()
                .orElse(null);
        if(ledgerBudget==null){
            System.out.println("No ledger budget found for the selected period.");
            return;
        }
        if(!ledgerBudget.isActive(LocalDate.now())){
            ledgerBudget.refreshIfExpired();
            budgetController.editBudget(ledgerBudget, BigDecimal.ZERO);
        }
        System.out.printf("%d. Period: %s%s\n",
                counter[0],
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

    //serve per edit
    private void printAllCategoryBudgetsRecursive(
            List<LedgerCategory> categories,
            String indent,
            Budget.Period period,
            int[] counter,
            Map<Integer, Budget> budgetMap) {

        for (LedgerCategory category : categories) {
            System.out.println(indent + category.getName());

            // print budgets for this category
            Budget budget = category.getBudgets().stream()
                    .filter(b -> b.getPeriod() == period)
                    .findFirst()
                    .orElse(null);
            if (budget == null) {
                continue;
            }
            if( !budget.isActive(LocalDate.now())){
                budget.refreshIfExpired();
                budgetController.editBudget(budget, BigDecimal.ZERO); //update budget's amount in DB
            }

            //print category budget with number
            String status = budgetController.isOverBudget(budget) ? " [OVER BUDGET]" : " (within budget)";
            String line = indent + counter[0] + ". Amount: " + budget.getAmount()
                    + ", Period: " + budget.getPeriod() + status;
            System.out.println(line);

            //budgetMap.put(counter[0]++, budget);
            budgetMap.put(counter[0], budget);
            counter[0]++;

            // print subcategories recursively
            if (!category.getChildren().isEmpty()) {
                printAllCategoryBudgetsRecursive(
                        category.getChildren(),
                        indent + "   ",
                        period,
                        counter,
                        budgetMap);
            }
        }
    }

    //può selezionare solo budget di categorie di primo livello. serve per merge
    private void printCategoryBudgetsRecursive(
            List<LedgerCategory> categories,
            String indent,
            Budget.Period period,
            int[] counter,
            Map<Integer, Budget> budgetMap) {

        for (LedgerCategory category : categories) {
            System.out.println(indent + category.getName());

            // print budgets for this category
            Budget budget= category.getBudgets().stream()
                    .filter(b -> b.getPeriod() == period)
                    .findFirst()
                    .orElse(null);
            if(budget==null){
                continue;
            }
            if( !budget.isActive(LocalDate.now())){
                budget.refreshIfExpired();
                budgetController.editBudget(budget, BigDecimal.ZERO); //update budget's amount in DB
            }

            boolean selectable = category.getParent() == null;
            if (selectable) {
                // print top-level category budget with number
                String status = budgetController.isOverBudget(budget) ? " [OVER BUDGET]" : " (within budget)";
                String line = indent + counter[0] + ". Amount: " + budget.getAmount()
                        + ", Period: " + budget.getPeriod() + status;
                System.out.println(line);

                //budgetMap.put(counter[0]++, budget); //only top-level category budgets are selectable
                budgetMap.put(counter[0], budget);
                counter[0]++;
            } else {
                //print subcategory budget without number
                String status = budgetController.isOverBudget(budget) ? " [OVER BUDGET]" : " (within budget)";
                String line = indent  + " - Amount: " + budget.getAmount()
                        + ", Period: " + budget.getPeriod() + status;
                System.out.println(line);
            }

            // print subcategories recursively
            if (!category.getChildren().isEmpty()) {
                printCategoryBudgetsRecursive(
                        category.getChildren(),
                        indent + "   ",
                        period,
                        counter,
                        budgetMap);
            }
        }
    }




}
