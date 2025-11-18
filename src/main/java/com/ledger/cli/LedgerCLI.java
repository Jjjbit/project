package com.ledger.cli;

import com.ledger.business.LedgerController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class LedgerCLI {
    private final UserController userController;
    private final ReportController reportController;
    private final LedgerController ledgerController;
    private final Scanner scanner = new Scanner(System.in);

    public LedgerCLI(UserController userController, ReportController reportController,
                     LedgerController ledgerController) {
        this.userController = userController;
        this.reportController = reportController;
        this.ledgerController = ledgerController;
    }

    public void createLedger() {

        System.out.println("\n === Creating a new ledger ===");

        System.out.print("Enter ledger name: ");
        String name = scanner.nextLine().trim();

        Ledger ledger=ledgerController.createLedger(name, userController.getCurrentUser());
        if(ledger==null){
            System.out.println("Failed to create ledger. Please try again.");
            return;
        }
        System.out.println("Ledger created successfully!");
        System.out.println("Ledger Name: " + ledger.getName());
    }

    public void viewLedgers() {

        System.out.println("\n === Your Ledgers ===");

        List<Ledger> ledgers =reportController.getLedgerByUser(userController.getCurrentUser());
        if(ledgers.isEmpty()) {
            System.out.println("No ledgers found.");
            return;
        }
        for (int i = 0; i < ledgers.size(); i++) {
            Ledger ledger = ledgers.get(i);
            System.out.println("Ledger" + (i + 1) + ". "+ "Name: " + ledger.getName());
        }
    }

    public void viewLedgerSummary() {
        System.out.println("\n === Ledger Summary ===");

        //select ledger
        System.out.println("Select a ledger to view its summary:");
        Ledger selectedLedger = selectLedger();
        if(selectedLedger == null) {
            return;
        }

        // Display summary for selected ledger
        System.out.println("Ledger Name: " + selectedLedger.getName());

        LocalDate startDate;
        LocalDate endDate;
        //select monthly or yearly summary
        System.out.print("Do you want a monthly summary or yearly summary? (m/y):");
        String summaryType = scanner.nextLine().trim().toLowerCase();
        if(summaryType.equals("y") || summaryType.equals("yearly")){
            //select year
            System.out.print("Enter year (e.g., 2024) for yearly summary: ");
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
            System.out.println("Invalid choice. Please enter 'm' for monthly or 'y' for yearly.");
            return;
        }


        //display income and expense
        System.out.println("Ledger Name: " + selectedLedger.getName());
        System.out.println("\nTotal Income: " + reportController.getTotalIncomeByLedger(selectedLedger, startDate, endDate));
        System.out.println("\nTotal Expense: " + reportController.getTotalExpenseByLedger(selectedLedger, startDate, endDate));

        //show transaction
        List<Transaction> transactions = reportController.getTransactionsByLedgerInRangeDate(
                selectedLedger, startDate, endDate);
        if(transactions.isEmpty()) {
            System.out.println("No transactions found for the selected period.");
            return;
        }
        System.out.println("Transactions for Ledger: " + selectedLedger.getName());
        for (Transaction tx : transactions) {
            String info = "Transaction ID: " + tx.getId()
                    + ", Date: " + tx.getDate()
                    + ", Amount: " + tx.getAmount()
                    + ", Type: " + tx.getType();

            if(tx.getCategory() != null) {
                info += ", Category: " + tx.getCategory().getName();
            }
            if (tx.getToAccount() != null) {
                info += ", To Account: " + tx.getToAccount().getName();
            } else if (tx.getFromAccount() != null) {
                info += ", From Account: " + tx.getFromAccount().getName();
            }
            if(tx.getNote() != null && !tx.getNote().isEmpty()) {
                info += ", Note: " + tx.getNote();
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
        System.out.println("Current name: " + selectedLedger.getName());
        System.out.print("\nEnter new name for the ledger (leave blank to keep current): ");
        String newName = scanner.nextLine().trim();

        boolean updated = ledgerController.renameLedger(selectedLedger, newName);
        if(!updated) {
            System.out.println("Failed to update ledger.");
            return;
        }
        System.out.println("Ledger updated successfully.");

    }

    public void copyLedger(){
        System.out.println("\n === Copying a ledger ===");

        //select ledger
        System.out.println("Select a ledger to copy:");
        Ledger selectedLedger = selectLedger();
        if(selectedLedger == null) {
            return;
        }

        Ledger copiedLedger = ledgerController.copyLedger(selectedLedger);
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
        String confirmation = System.console().readLine();

        if(confirmation.equalsIgnoreCase("n") || confirmation.equalsIgnoreCase("no") ) {
            System.out.println("Ledger deletion cancelled.");
            return;
        }

        boolean deleted = ledgerController.deleteLedger(selectedLedger);
        if(!deleted) {
            System.out.println("Ledger deletion failed.");
            return;
        }
        System.out.println("Ledger deleted successfully.");
    }

    public void showCategoryTree(){
        System.out.println("\n === Ledger Category Tree ===");

        //select ledger
        System.out.println("Select a ledger to view its category tree:");
        Ledger selectedLedger = selectLedger();
        if(selectedLedger == null) {
            return;
        }

        List<LedgerCategory> categories = reportController.getLedgerCategoryTreeByLedger(selectedLedger);
        if(categories.isEmpty()) {
            System.out.println("No categories found.");
            return;
        }
        System.out.println("Category Tree for Ledger: " + selectedLedger.getName());

        List<LedgerCategory> expenseRootCategories = categories.stream()
                .filter(cat -> cat.getParent() == null && cat.getType() == CategoryType.EXPENSE)
                .toList();
        List<LedgerCategory> incomeRootCategories = categories.stream()
                .filter(cat -> cat.getParent() == null && cat.getType() == CategoryType.INCOME)
                .toList();
        System.out.println("Expense Categories:");
        for(LedgerCategory root : expenseRootCategories) {
            System.out.println(" Category Name: " + root.getName());
            List<LedgerCategory> children = categories.stream()
                    .filter(cat -> cat.getParent() != null && cat.getParent().getId() == root.getId())
                    .toList();
            for(LedgerCategory child : children) {
                System.out.println("  SubCategory Name: " + child.getName());
            }
        }

        System.out.println("Income Categories:");
        for(LedgerCategory root : incomeRootCategories) {
            System.out.println(" Category Name: " + root.getName());
            List<LedgerCategory> children = categories.stream()
                    .filter(cat -> cat.getParent() != null && cat.getParent().getId() == root.getId())
                    .toList();
            for(LedgerCategory child : children) {
                System.out.println("  SubCategory Name: " + child.getName());
            }
        }
    }

    /*public void showTransaction(){
        System.out.println("\n === Ledger Transactions ===");

        //select ledger
        System.out.println("Select a ledger to view its transactions:");
        Ledger selectedLedger = selectLedger();
        if(selectedLedger == null) {
            return;
        }

        System.out.print("Enter start date (YYYY-MM-DD): ");
        LocalDate startDate=inputStartDate();

        System.out.print("Enter end date (YYYY-MM-DD): ");
        LocalDate endDate=inputEndDate();

        List<Transaction> transactions = reportController.getTransactionsByLedgerInRangeDate(
                selectedLedger, startDate, endDate);
        if(transactions.isEmpty()) {
            System.out.println("No transactions found for the selected period.");
            return;
        }
        System.out.println("Transactions for Ledger: " + selectedLedger.getName());
        for(Transaction tx : transactions) {
            System.out.println("Date: " + tx.getDate() +
                    ", Type: " + tx.getType() +
                    ", Amount: " + tx.getAmount() +
                    ", Category: " + (tx.getCategory() != null ? tx.getCategory().getName() : "N/A"));
            if(tx instanceof Expense){
                System.out.print(", From Account: " + (tx.getFromAccount() != null ? tx.getFromAccount().getName() : "N/A"));
            }
            if(tx instanceof Income){
                System.out.print(", To Account: " + (tx.getToAccount() != null ? tx.getToAccount().getName() : "N/A") );
            }
            if(tx instanceof Transfer){
                System.out.print(", From Account: " + (tx.getFromAccount() != null ? tx.getFromAccount().getName() : "N/A"));
                System.out.print(", To Account: " + (tx.getToAccount() != null ? tx.getToAccount().getName() : "N/A"));
            }
            System.out.println(", Note: " + tx.getNote());
        }

    }*/


    //private helper method
    private LocalDate inputStartDate() {
        String dateStr = scanner.nextLine().trim();
        if(dateStr.isEmpty()){
            return LocalDate.now().withDayOfMonth(1);
        }
        return LocalDate.parse(dateStr);
    }
    private LocalDate inputEndDate() {
        String dateStr = scanner.nextLine().trim();
        if(dateStr.isEmpty()){
            return LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        }
        return LocalDate.parse(dateStr);
    }

    private Ledger selectLedger() {
        List<Ledger> ledgers =reportController.getLedgerByUser(userController.getCurrentUser());
        if(ledgers.isEmpty()) {
            System.out.println("No ledgers found.");
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
            return selectLedger();
        }
        return ledgers.get(choice - 1);
    }


}
