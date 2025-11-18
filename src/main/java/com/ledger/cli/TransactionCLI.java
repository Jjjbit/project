package com.ledger.cli;

import com.ledger.business.ReportController;
import com.ledger.business.TransactionController;
import com.ledger.business.UserController;
import com.ledger.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class TransactionCLI {
    private final TransactionController transactionController;
    private final ReportController reportController;
    private final UserController userController;
    private final Scanner scanner = new Scanner(System.in);

    public TransactionCLI(TransactionController transactionController,
                          ReportController reportController,
                          UserController userController) {
        this.reportController = reportController;
        this.userController = userController;
        this.transactionController = transactionController;
    }

    public void addIncome() {
        System.out.println("\n === Add Income Transaction ===");

        //select ledger
        System.out.println("\nSelect a ledger to add income transaction:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if (selectedLedger == null) {
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        //select account
        System.out.println("\nSelect an account:");
        Account selectedAccount = selectAccount();
        if (selectedAccount == null) {
            System.out.println("No account selected. Returning to main menu.");
            return;
        }

        //select category
        System.out.println("\nSelect a category:");
        LedgerCategory selectedCategory = selectCategory(selectedLedger, CategoryType.INCOME);

        //add note
        String note = inputNote();

        //add amount
        System.out.print("Enter the amount for the transaction: ");
        BigDecimal amount = inputAmount();

        //add date
        System.out.print("Enter the date for the transaction (YYYY-MM-DD): ");
        LocalDate date = inputDate();

        //create transaction
        Income incomeTransaction = transactionController.createIncome(selectedLedger, selectedAccount,
                selectedCategory, note, date, amount);
        if(incomeTransaction==null){
            System.out.println("Failed to create income transaction.");
            return;
        }
        System.out.println("Income transaction created successfully: " + incomeTransaction);

    }

    public void addExpense(){
        System.out.println("\n === Add Expense Transaction ===");

        //select ledger
        System.out.println("\nSelect a ledger to add expense transaction:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if (selectedLedger == null) {
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        //select account
        System.out.println("\nSelect an account:");
        Account selectedAccount = selectAccount();
        if (selectedAccount == null) {
            System.out.println("No account selected. Returning to main menu.");
            return;
        }

        //select category
        System.out.println("\nSelect a category:");
        LedgerCategory selectedCategory = selectCategory(selectedLedger, CategoryType.EXPENSE);

        //add note
        String note = inputNote();

        //add amount
        System.out.print("Enter the amount for the transaction: ");
        BigDecimal amount = inputAmount();

        //add date
        System.out.print("Enter the date for the transaction (YYYY-MM-DD): ");
        LocalDate date = inputDate();

        //create transaction
        Expense expenseTransaction = transactionController.createExpense(selectedLedger, selectedAccount,
                selectedCategory, note, date, amount);
        if(expenseTransaction==null){
            System.out.println("Failed to create expense transaction.");
            return;
        }
        System.out.println("Expense transaction created successfully: " + expenseTransaction);
    }

    public void addTransfer(){
        System.out.println("\n === Add Transfer Transaction ===");

        //select ledger
        System.out.println("\nSelect a ledger to add transfer transaction:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if (selectedLedger == null) {
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        //select from account
        System.out.println("\nSelect the FROM account:");
        Account fromAccount = selectAccount();

        //select to account
        System.out.println("\nSelect the TO account:");
        Account toAccount = selectAccount();

        //add note
        String note = inputNote();

        //add amount
        System.out.print("Enter the amount for the transaction: ");
        BigDecimal amount = inputAmount();

        //add date
        System.out.print("Enter the date for the transaction (YYYY-MM-DD): ");
        LocalDate date = inputDate();

        //create transaction
        Transfer transferTransaction = transactionController.createTransfer(selectedLedger, fromAccount,
                toAccount, note, date, amount);
        if(transferTransaction==null){
            System.out.println("Failed to create transfer transaction.");
            return;
        }
        System.out.println("Transfer transaction created successfully: " + transferTransaction);
    }

    public void deleteTransaction(){
        System.out.println("\n === Delete Transaction ===");

        System.out.println("\nSelect a ledger to delete transaction from:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        List<Transaction> transactions = reportController.getTransactionsByLedgerInRangeDate(
                selectedLedger, LocalDate.MIN, LocalDate.MAX);
        if(transactions.isEmpty()){
            System.out.println("No transactions found in the selected ledger.");
            return;
        }
        System.out.println("\nSelect a transaction to delete:");
        Transaction selectedTransaction = selectTransaction(transactions);

        boolean deleted = transactionController.deleteTransaction(selectedTransaction);
        if(!deleted){
            System.out.println("Failed to delete transaction.");
            return;
        }
        System.out.println("Failed to delete transaction.");

    }

    public void editTransaction(){
        System.out.println("\n === Edit Transaction ===");

        System.out.println("\nSelect a ledger to edit transaction from:");

        //select ledger
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        List<Transaction> transactions = reportController.getTransactionsByLedgerInRangeDate(
                selectedLedger, LocalDate.MIN, LocalDate.MAX);
        if(transactions.isEmpty()){
            System.out.println("No transactions found in the selected ledger.");
            return;
        }

        System.out.println("\nSelect a transaction to edit:");
        Transaction selectedTransaction = selectTransaction(transactions);

        //edit note
        System.out.println("Current note: " + selectedTransaction.getNote());
        System.out.println("Do you want to edit the note ? (y/n): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        String note=null;
        if(confirm.equals("y") || confirm.equals("yes")){
            //edit note
            note = inputNote();
        }


        //edit amount
        System.out.println("Current amount: " + selectedTransaction.getAmount());
        System.out.println("Do you want to edit the amount ? (y/n): ");
        confirm = scanner.nextLine().trim().toLowerCase();
        BigDecimal amount=null;
        if(confirm.equals("y") || confirm.equals("yes")){
            //edit amount
            System.out.print("Enter the amount for the transaction: ");
            amount = inputAmount();
        }

        //edit date
        System.out.println("Current transaction date: " + selectedTransaction.getDate());
        System.out.println("Do you want to edit the date ? (y/n): ");
        confirm = scanner.nextLine().trim().toLowerCase();
        LocalDate date=null;
        if(confirm.equals("y") || confirm.equals("yes")){
            //edit date
            System.out.print("Enter the date for the transaction (YYYY-MM-DD): ");
            date = inputDate();
        }

        //edit ledger
        System.out.println("Current ledger: " + selectedTransaction.getLedger().getName());
        System.out.println("Do you want to edit the ledger ? (y/n): ");
        confirm = scanner.nextLine().trim().toLowerCase();
        Ledger newLedger=null;
        if(confirm.equals("y") || confirm.equals("yes")){
           newLedger = selectLedger(userController.getCurrentUser());
        }

        LedgerCategory newCategory = null;
        Account newToAccount = null;
        Account newFromAccount = null;


        //edit from/to account or category based on transaction type
        if (selectedTransaction instanceof Income) {
            System.out.println("Current category: " +  selectedTransaction.getCategory().getName());
            System.out.println("Do you want to edit the category ? (y/n): ");
            confirm = scanner.nextLine().trim().toLowerCase();
            if(confirm.equals("y") || confirm.equals("yes")){
                System.out.println("\nSelect a new category:");
                newCategory = selectCategory(newLedger, CategoryType.INCOME);
            }

            System.out.println("Current account: " + selectedTransaction.getToAccount().getName());
            System.out.println("Do you want to edit the account ? (y/n): ");
            confirm = scanner.nextLine().trim().toLowerCase();
            if(confirm.equals("y") || confirm.equals("yes")){
                System.out.println("\nSelect a new account:");
                newToAccount = selectAccount();
            }

        } else if (selectedTransaction instanceof Expense) {
            System.out.println("Current category: " + selectedTransaction.getCategory().getName());
            System.out.println("Do you want to edit the category ? (y/n): ");
            confirm = scanner.nextLine().trim().toLowerCase();
            if(confirm.equals("y") || confirm.equals("yes")) {
                System.out.println("\nSelect a new category:");
                newCategory = selectCategory(selectedLedger, CategoryType.EXPENSE);
            }

            System.out.println("Current account: " + selectedTransaction.getFromAccount().getName());
            System.out.println("Do you want to edit the account ? (y/n): ");
            confirm = scanner.nextLine().trim().toLowerCase();
            if(confirm.equals("y") || confirm.equals("yes")){
                System.out.println("\nSelect a new account:");
                newFromAccount = selectAccount();
            }

        } else if (selectedTransaction instanceof Transfer) {
            System.out.println("Current FROM account: " + selectedTransaction.getFromAccount().getName());
            System.out.println("Do you want to edit the FROM account ? (y/n): ");
            confirm = scanner.nextLine().trim().toLowerCase();
            if (confirm.equals("y") || confirm.equals("yes")) {
                System.out.println("\nSelect a new FROM account:");
                newFromAccount = selectAccount();
            }

            System.out.println("Current TO account: " + selectedTransaction.getToAccount().getName());
            System.out.println("Do you want to edit the TO account ? (y/n): ");
            confirm = scanner.nextLine().trim().toLowerCase();
            if (confirm.equals("y") || confirm.equals("yes")){
                System.out.println("\nSelect a new TO account:");
                newToAccount = selectAccount();
            }
        }

        //update transaction
        boolean updated = transactionController.updateTransaction(selectedTransaction, newFromAccount, newToAccount,
                newCategory, note, date, amount, newLedger);

        if(!updated){
            System.out.println("Failed to update transaction.");
            return;
        }
        System.out.println("Transaction updated successfully: " + selectedTransaction);
    }

    //helper methods
    private Transaction selectTransaction(List<Transaction> transactions){
        for(int i=0; i<transactions.size(); i++){
            Transaction tx = transactions.get(i);
            System.out.println("Transaction " + (i + 1) + ". " + tx);
        }
        System.out.print("Enter the number of the transaction to delete: ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if(choice < 1 || choice > transactions.size()) {
            System.out.println("Invalid choice.");
            return selectTransaction(transactions);
        }
        return transactions.get(choice - 1);
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

    private Account selectAccount() {
        List<Account> accounts = reportController.getAccountsNotHidden(userController.getCurrentUser());
        if (accounts.isEmpty()) {
            System.out.println("No accounts found for the user.");
            return null;
        }

        List<Account> accountsSelectable = accounts.stream()
                .filter(Account::getSelectable)
                .filter(account -> account instanceof BasicAccount || account instanceof CreditAccount)
                .toList();

        if (accountsSelectable.isEmpty()) {
            System.out.println("No selectable accounts found for the user.");
            return null;
        }

        for (int i = 0; i < accountsSelectable.size(); i++) {
            Account account = accountsSelectable.get(i);
            System.out.println("Account " + (i + 1) + ". " + "Name: " + account.getName() + ", Balance: " + account.getBalance());
        }
        System.out.print("Enter the number of the account: ");
        //String input = scanner.nextLine().trim();
        int choice = scanner.nextInt();
        if (choice < 1 || choice > accountsSelectable.size()) {
            System.out.println("Invalid choice.");
            return selectAccount();
        }
        return accountsSelectable.get(choice - 1);
    }

    private LedgerCategory selectCategory(Ledger ledger, CategoryType type) {
        List<LedgerCategory> categories=reportController.getLedgerCategoryTreeByLedger(ledger);

        if(categories.isEmpty()) {
            System.out.println("No categories found in the selected ledger.");
            return null;
        }

        //filter by type
        List<LedgerCategory> filteredCategories = categories.stream()
                .filter(category -> category.getType() == type)
                .toList();
        if(filteredCategories.isEmpty()) {
            System.out.println("No categories found for the selected type.");
            return null;
        }

        //get only parent categories
        List<LedgerCategory> parentCategories = filteredCategories.stream()
                .filter(category -> category.getParent() == null)
                .toList();

        for (int i = 0; i < parentCategories.size(); i++) {
            //display parent category
            LedgerCategory parent = parentCategories.get(i);
            System.out.println( (i + 1) + ". "+ "Name: " + parent.getName());

            //display sub-categories
            List<LedgerCategory> subCategories = filteredCategories.stream()
                    .filter(category -> category.getParent() != null && category.getParent().getId() == parent.getId())
                    .toList();
            if (!subCategories.isEmpty()) {
                for (int j = 0; j < subCategories.size(); j++) {
                    LedgerCategory child = subCategories.get(j);
                    System.out.println("   " + (i + 1) + "." + (j + 1) + " " + child.getName());
                }
            }
        }

        System.out.print("Enter the number of the category: ");
        String input = scanner.nextLine().trim();
        if (input.contains(".")) {
            // select subcategory
            String[] parts = input.split("\\.");
            int parentIndex = Integer.parseInt(parts[0]) - 1;
            int childIndex = Integer.parseInt(parts[1]) - 1;

            if (parentIndex < 0 || parentIndex >= parentCategories.size()) {
                System.out.println("Invalid parent choice!");
                return selectCategory(ledger, type);
            }

            LedgerCategory parentCategory = parentCategories.get(parentIndex);
            List<LedgerCategory> subCategories = filteredCategories.stream()
                    .filter(category -> category.getParent() != null && category.getParent().getId() == parentCategory.getId())
                    .toList();
            if (childIndex < 0 || childIndex >= subCategories.size()) {
                System.out.println("Invalid subcategory choice!");
                return selectCategory(ledger, type);
            }

            return subCategories.get(childIndex);
        } else {
            //select parent category
            int parentIndex = Integer.parseInt(input) - 1;
            if (parentIndex < 0 || parentIndex >= parentCategories.size()) {
                System.out.println("Invalid choice!");
                return selectCategory(ledger, type);
            }
            return parentCategories.get(parentIndex);
        }
    }

    private String inputNote(){
        System.out.print("Enter a note for the transaction (optional): ");
        String note = scanner.nextLine().trim();
        return note.isEmpty() ? null : note;
    }
    private BigDecimal inputAmount(){
        String input = System.console().readLine();
        BigDecimal amount = new BigDecimal(input);
        if(amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("Amount must be greater than zero.");
            return inputAmount();
        }
        return amount;
    }
    private LocalDate inputDate(){
        String input = scanner.nextLine().trim();

        if(input.isEmpty()) {
            return LocalDate.now();
        }

        if(!input.matches("\\d{4}-\\d{2}-\\d{2}")) {
            System.out.println("Invalid date format.");
            return inputDate();
        }

        return LocalDate.parse(input);
    }
}
