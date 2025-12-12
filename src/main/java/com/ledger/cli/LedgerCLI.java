package com.ledger.cli;

import com.ledger.business.*;
import com.ledger.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class LedgerCLI {
    private final UserController userController;
    private final ReportController reportController;
    private final LedgerController ledgerController;
    private final TransactionController transactionController;
    private final LedgerCategoryController ledgerCategoryController;
    private final BudgetController budgetController;
    private final Scanner scanner = new Scanner(System.in);

    public LedgerCLI(UserController userController, ReportController reportController,
                     LedgerController ledgerController, TransactionController transactionController,
                     LedgerCategoryController ledgerCategoryController,
                     BudgetController budgetController) {
        this.budgetController = budgetController;
        this.ledgerCategoryController = ledgerCategoryController;
        this.transactionController = transactionController;
        this.userController = userController;
        this.reportController = reportController;
        this.ledgerController = ledgerController;
    }

    public void createLedger() {
        System.out.println("\n === Creating a new ledger ===");

        System.out.print("Enter ledger name: ");
        String name = inputName();

        Ledger ledger=ledgerController.createLedger(name, userController.getCurrentUser());
        if(ledger==null){
            System.out.println("Failed to create ledger. Please try again.");
            return;
        }
        System.out.println("Ledger created successfully!");
        viewLedgers();
    }

    public void viewLedgers() {

        System.out.println("\n === Your Ledgers ===");

        List<Ledger> ledgers = ledgerController.getLedgersByUser(userController.getCurrentUser());
        if(ledgers.isEmpty()) {
            System.out.println("No ledgers found.");
            return;
        }

        for (int i = 0; i < ledgers.size(); i++) {
            Ledger ledger = ledgers.get(i);
            System.out.println((i + 1) + ". "+ "Ledger Name: " + ledger.getName());
        }
    }

    public void viewLedgerSummary() {
        System.out.println("\n === Ledger's Summary ===");

        //select ledger
        System.out.println("Select a ledger to view its summary:");
        Ledger selectedLedger = selectLedger();
        if(selectedLedger == null) {
            return;
        }

        LocalDate startDate;
        LocalDate endDate;
        //select monthly or yearly summary
        System.out.print("Do you want a monthly summary or yearly summary? (m/y):");
        String summaryType = scanner.nextLine().trim().toLowerCase();
        if(summaryType.equals("y") || summaryType.equals("yearly")){
            //select year
            System.out.print("Enter year (e.g., 2025) for yearly summary: ");
            int year= scanner.nextInt();
            startDate = LocalDate.of(year, 1, 1);
            endDate = LocalDate.of(year, 12, 31);
        } else if(summaryType.equals("m") || summaryType.equals("monthly")){
            //select month
            System.out.print("Enter month (1-12) for monthly summary: ");
            int month= scanner.nextInt();
            if(month<1 || month>12){
                System.out.println("Invalid month. Please enter a value between 1 and 12.");
                return;
            }
            startDate = LocalDate.of(LocalDate.now().getYear(), month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }

        //get budget
        Budget budget;
        if(summaryType.equals("y") || summaryType.equals("yearly")){
            budget = budgetController.getActiveBudgetByLedger(selectedLedger, Budget.Period.YEARLY);
        } else {
            budget = budgetController.getActiveBudgetByLedger(selectedLedger, Budget.Period.MONTHLY);
        }

        BigDecimal totalIncome = reportController.getTotalIncomeByLedger(selectedLedger, startDate, endDate);
        BigDecimal totalExpense = reportController.getTotalExpenseByLedger(selectedLedger, startDate, endDate);
        BigDecimal restAmount = totalIncome.subtract(totalExpense);
        //display income and expense
        System.out.println("Ledger Name: " + selectedLedger.getName() + ", from " + startDate + " to " + endDate);
        System.out.print("Total Income: " + totalIncome
                + ", Total Expense: " + totalExpense
                + ", Remaining Amount: " + restAmount
                + (reportController.isOverBudget(budget) ? ", [OVER BUDGET]" : ", (within budget)"));

        //show transaction
        List<Transaction> transactions = transactionController.getTransactionsByLedgerInRangeDate(
                selectedLedger, startDate, endDate);
        if(transactions.isEmpty()) {
            System.out.println("No transactions found for the selected period.");
            return;
        }
        System.out.println("\nTransactions for Ledger: " + selectedLedger.getName());
        int count = 0;
        for (Transaction tx : transactions) {
            count++;
            StringBuilder info = new StringBuilder();

            info.append(String.format("%d. Amount: %s, Date: %s",
                    count,
                    tx.getType() == TransactionType.EXPENSE
                            ? tx.getAmount().negate()
                            : tx.getAmount(),
                    tx.getDate()));

            if (tx.getCategory() != null) {
                info.append(", Category: ").append(tx.getCategory().getName());
            }

            if (tx instanceof Transfer) {
                if (tx.getFromAccount() != null) {
                    info.append(", FROM: ").append(tx.getFromAccount().getName());
                }
                if (tx.getToAccount() != null) {
                    info.append(", TO: ").append(tx.getToAccount().getName());
                }
            } else {
                if (tx.getFromAccount() != null) {
                    info.append(", From: ").append(tx.getFromAccount().getName());
                } else if (tx.getToAccount() != null) {
                    info.append(", To: ").append(tx.getToAccount().getName());
                }
            }

            if (tx.getNote() != null && !tx.getNote().isEmpty()) {
                info.append(", Note: ").append(tx.getNote());
            }

            System.out.println(info);
        }
    }

    public void renameLedger() {
        System.out.println("\n === Update a ledger ===");

        //select ledger
        System.out.println("Select a ledger to update:");
        Ledger selectedLedger = selectLedger();
        if(selectedLedger == null) {
            return;
        }

        //prompt for new name
        System.out.println("\nCurrent name: " + selectedLedger.getName());
        System.out.print("Enter new name for the ledger (press Enter to keep current): ");
        String newName = scanner.nextLine();
        if(newName.isEmpty()) {
            newName= selectedLedger.getName();
        }

        boolean updated = ledgerController.renameLedger(selectedLedger, newName, userController.getCurrentUser());
        if(!updated) {
            System.out.println("Failed to update ledger.");
            return;
        }
        System.out.println("Ledger updated successfully.  New ledger name: " + selectedLedger.getName());
    }

    public void copyLedger(){
        System.out.println("\n === Copying a ledger ===");

        //select ledger
        System.out.println("Select a ledger to copy:");
        Ledger selectedLedger = selectLedger();
        if(selectedLedger == null) {
            return;
        }

        Ledger copiedLedger = ledgerController.copyLedger(selectedLedger, userController.getCurrentUser());
        if(copiedLedger != null) {
            System.out.println("Ledger copied successfully. New ledger name: " + copiedLedger.getName());
        } else {
            System.out.println("Failed to copy ledger.");
        }
    }

    public void deleteLedger() {
        System.out.println("\n === Deleting a ledger ===");

        //select ledger
        System.out.println("Select a ledger to delete:");
        Ledger selectedLedger = selectLedger();
        if(selectedLedger == null) {
            return;
        }

        // Confirm deletion
        System.out.print("Are you sure you want to delete the ledger '" + selectedLedger.getName() + "'? (y/n): ");
        String confirmation = scanner.nextLine().trim().toLowerCase();

        if(confirmation.equals("n") || confirmation.equals("no") ) {
            System.out.println("Ledger deletion cancelled.");
            return;
        }

        boolean deleted = ledgerController.deleteLedger(selectedLedger);
        if(!deleted) {
            System.out.println("Ledger deletion failed.");
            return;
        }
        System.out.println("Ledger deleted successfully.");
        viewLedgers();
    }

    public void showCategoryTree(){
        System.out.println("\n === Ledger Category Tree ===");

        //select ledger
        System.out.println("Select a ledger to view its category tree:");
        Ledger selectedLedger = selectLedger();
        if(selectedLedger == null) {
            return;
        }

        List<LedgerCategory> categories = ledgerCategoryController.getLedgerCategoryTreeByLedger(selectedLedger);
        if(categories.isEmpty()) {
            System.out.println("No categories found.");
            return;
        }
        System.out.println("Category Tree for Ledger: " + selectedLedger.getName());

        List<LedgerCategory> expenseRoot = categories.stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE)
                .filter(cat -> cat.getParent() == null)
                .toList();
        List<LedgerCategory> incomeRoot = categories.stream()
                .filter(cat -> cat.getType() == CategoryType.INCOME)
                .filter(cat -> cat.getParent() == null)
                .filter(cat -> !cat.getName().equalsIgnoreCase("Claim Income")) // Exclude "Claim Income" category
                .toList();

        System.out.println("Expense Categories:");
        for(LedgerCategory root : expenseRoot) {
            System.out.println(" Category Name: " + root.getName());
            List<LedgerCategory> children = categories.stream()
                    .filter(cat -> cat.getParent() != null && cat.getParent().getId() == root.getId())
                    .toList();
            for(LedgerCategory child : children) {
                System.out.println("  SubCategory Name: " + child.getName());
            }
        }

        System.out.println("Income Categories:");
        for(LedgerCategory root : incomeRoot) {
            System.out.println(" Category Name: " + root.getName());
            List<LedgerCategory> children = categories.stream()
                    .filter(cat -> cat.getParent() != null && cat.getParent().getId() == root.getId())
                    .toList();
            for(LedgerCategory child : children) {
                System.out.println("  SubCategory Name: " + child.getName());
            }
        }
    }


    //private helper method
    private String inputName(){
        String name = scanner.nextLine();
        if(name.isEmpty()){
            System.out.println("Name cannot be empty. Please try again.");
            return inputName();
        }
        return name;
    }
    private Ledger selectLedger() {
        List<Ledger> ledgers = ledgerController.getLedgersByUser(userController.getCurrentUser());
        if(ledgers.isEmpty()) {
            System.out.println("No ledgers found.");
            return null;
        }

        for (int i = 0; i < ledgers.size(); i++) {
            Ledger ledger = ledgers.get(i);
            System.out.println((i + 1) + ". "+ "Name: " + ledger.getName());
        }
        System.out.print("Enter the number of the ledger: ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline

        if(choice < 1 || choice > ledgers.size()) {
            System.out.println("Invalid choice.");
            return selectLedger();
        }
        return ledgers.get(choice - 1);
    }
}
