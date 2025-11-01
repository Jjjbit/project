package com.ledger.cli;

import com.ledger.business.BudgetController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.Budget;
import com.ledger.domain.CategoryType;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;

import java.math.BigDecimal;
import java.util.List;
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

    public void addBudget() {
        System.out.println("\n ===Adding a new budget ===");
        //select ledger
        System.out.println("Select ledger:");
        Ledger selectedLedger = selectLedger();

        System.out.print("Enter budget period (MONTHLY, YEARLY): ");
        String periodInput = scanner.nextLine().trim().toUpperCase();
        Budget.Period period = Budget.Period.valueOf(periodInput);

        System.out.print("Enter budget amount: ");
        BigDecimal amountInput =scanner.nextBigDecimal();

        System.out.println("Do you want to set a category for this budget? (yes/no): ");
        String categoryChoice = scanner.nextLine().trim().toLowerCase();
        if(categoryChoice.equals("yes") || categoryChoice.equals("y")) {
            System.out.println("\n Select a category for the budget:");
            LedgerCategory category = selectCategory(selectedLedger);
            Budget budget = budgetController.createBudget(amountInput, category, period, selectedLedger);
            if (budget == null) {
                System.out.println("Failed to create budget.");
                return;
            }
            System.out.println("Budget created successfully.");
        }else{
            Budget budget = budgetController.createBudget(amountInput, null, period, selectedLedger);
            if (budget == null) {
                System.out.println("Failed to create budget.");
                return;
            }
            System.out.println("Budget created successfully.");
        }
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

        //select target budget
        System.out.println("Select the target budget to merge into:");
        List<Budget> userBudgets = reportController.getActiveBudgetsByLedger(selectedLedger, period);
        for(int i=0;i<userBudgets.size();i++){
            Budget budget=userBudgets.get(i);
            System.out.println((i+1) + ". " + "Amount: " + budget.getAmount() +
                    ", Period: " + budget.getPeriod() +
                    (budget.getCategory()!=null ? ", Category: " + budget.getCategory() : ", No Category") +
                    (budgetController.isOverBudget(budget, selectedLedger) ? ", [OVER BUDGET]" : ", within budget"));
        }

        System.out.print("Select a budget by number: ");
        int budgetIndex = scanner.nextInt() - 1;
        if(budgetIndex < 0 || budgetIndex >= userBudgets.size()) {
            System.out.println("Invalid budget selection.");
            return;
        }
        Budget targetBudget = userBudgets.get(budgetIndex);

        boolean success = budgetController.mergeBudgets(targetBudget);
        if (!success) {
            System.out.println("Failed to merge budgets.");
        }
        System.out.println("Budgets merged successfully.");

    }

    public void deleteBudget() {
        System.out.println("\n === Deleting a budget ===");

        //select ledger
        System.out.println("Select ledger:");
        Ledger selectedLedger = selectLedger();

        //select period
        System.out.print("Enter budget period to delete (MONTHLY, YEARLY): ");
        String periodInput = scanner.nextLine().trim().toUpperCase();
        Budget.Period period = Budget.Period.valueOf(periodInput);

        //select budget to delete
        System.out.println("Select the budget to delete:");
        List<Budget> userBudgets = reportController.getActiveBudgetsByLedger(selectedLedger, period);
        for(int i=0;i<userBudgets.size();i++){
            Budget budget=userBudgets.get(i);
            System.out.println((i+1) + ". " + "Amount: " + budget.getAmount() +
                    ", Period: " + budget.getPeriod() +
                    (budget.getCategory()!=null ? ", Category: " + budget.getCategory() : ", No Category") +
                    (budgetController.isOverBudget(budget, selectedLedger) ? ", [OVER BUDGET]" : ", within budget"));
        }
        System.out.print("Select a budget by number: ");
        //String input = scanner.nextLine().trim();
        int budgetIndex = scanner.nextInt() - 1;
        if(budgetIndex < 0 || budgetIndex >= userBudgets.size()) {
            System.out.println("Invalid budget selection.");
            return;
        }
        Budget budgetToDelete = userBudgets.get(budgetIndex);

        boolean success = budgetController.deleteBudget(budgetToDelete);
        if (!success) {
            System.out.println("Failed to delete budget.");
        }
        System.out.println("Budget deleted successfully.");


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

        //select and show budgets
        List<Budget> ledgerBudgets = reportController.getActiveBudgetsByLedger(selectedLedger, period);
        if(ledgerBudgets.isEmpty()){
            System.out.println("No active budgets found for the selected period.");
        }
        for(Budget budget:ledgerBudgets){
            System.out.println("Amount: " + budget.getAmount() +
                    ", Period: " + budget.getPeriod() +
                    (budget.getCategory()!=null ? ", Category: " + budget.getCategory() : ", No Category") +
                    (budgetController.isOverBudget( budget, selectedLedger) ? ", [OVER BUDGET]" : ", within budget"));
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

        //select budget to edit
        System.out.println("Select the budget to edit:");
        List<Budget> userBudgets = reportController.getActiveBudgetsByLedger(selectedLedger, period);
        for(int i=0;i<userBudgets.size();i++){
            Budget budget=userBudgets.get(i);
            System.out.println((i+1) + ". " + "Amount: " + budget.getAmount() +
                    ", Period: " + budget.getPeriod());
        }
        System.out.print("Select a budget by number: ");
        //String input = scanner.nextLine().trim();
        int budgetIndex =scanner.nextInt() - 1;
        if(budgetIndex < 0 || budgetIndex >= userBudgets.size()) {
            System.out.println("Invalid budget selection.");
            return;
        }

        Budget budgetToEdit = userBudgets.get(budgetIndex);

        //enter new amount
        System.out.print("Enter new budget amount: ");
        //String amountInput = scanner.nextLine().trim();
        BigDecimal newAmount = scanner.nextBigDecimal();
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

    private LedgerCategory selectCategory(Ledger ledger) {
        List<LedgerCategory> allCategories=reportController.getLedgerCategoryTreeByLedger(ledger).stream()
                .filter(c->c.getType().equals(CategoryType.EXPENSE))
                .toList();

        for(int i=0;i<allCategories.size();i++){ //show also subcategories
            System.out.println((i+1) + ". " + allCategories.get(i).getName());
        }
        System.out.print("Select a category by number: ");
        int categoryIndex = scanner.nextInt()-1;
        if(categoryIndex < 0 || categoryIndex >= allCategories.size()) {
            System.out.println("Invalid category selection.");
            return selectCategory(ledger);
        }
        return allCategories.get(categoryIndex);
    }


}
