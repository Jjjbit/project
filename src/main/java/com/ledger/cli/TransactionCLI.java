package com.ledger.cli;

import com.ledger.business.*;
import com.ledger.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class TransactionCLI {
    private final TransactionController transactionController;
    private final UserController userController;
    private final AccountController accountController;
    private final LedgerController ledgerController;
    private final LedgerCategoryController ledgerCategoryController;
    private final Scanner scanner = new Scanner(System.in);

    public TransactionCLI(TransactionController transactionController,
                          UserController userController, AccountController accountController,
                          LedgerController ledgerController, LedgerCategoryController ledgerCategoryController) {
        this.ledgerCategoryController = ledgerCategoryController;
        this.ledgerController = ledgerController;
        this.accountController = accountController;
        this.userController = userController;
        this.transactionController = transactionController;
    }

    public void addTransaction(){
        System.out.println("\n === Add Transaction ===");

        System.out.println("Select transaction type to add:");
        System.out.println("1. Income");
        System.out.println("2. Expense");
        System.out.println("3. Transfer");
        System.out.print("Enter your choice (1-3): ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        switch (choice) {
            case 1 -> addIncome();
            case 2 -> addExpense();
            case 3 -> addTransfer();
            default -> {
                System.out.println("Invalid choice. Please try again.");
                addTransaction();
            }
        }
    }
    private void addIncome() {
        System.out.println("\n === Add Income Transaction ===");

        //select ledger
        System.out.println("\nSelect a ledger to add income transaction:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());

        //select account
        System.out.println("\nSelect an account:");
        Account selectedAccount = selectAccount();

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
        Income incomeTransaction = transactionController.createIncome(selectedLedger, selectedAccount, selectedCategory, note, date, amount);
        if(incomeTransaction==null){
            System.out.println("Failed to create income transaction.");
            return;
        }
        System.out.println("Income transaction created successfully: " + "-Type: " + incomeTransaction.getType()
                + ", Amount" + incomeTransaction.getAmount() + ", Category: " + incomeTransaction.getCategory().getName() + ", To Account:" + incomeTransaction.getToAccount().getName()
                + ", Date: " + incomeTransaction.getDate()
                + ", Note: " + (incomeTransaction.getNote() != null ? incomeTransaction.getNote() : "No note"));
    }

    private void addExpense(){
        System.out.println("\n === Add Expense Transaction ===");

        //select ledger
        System.out.println("\nSelect a ledger to add expense transaction:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());

        //select account
        System.out.println("\nSelect an account:");
        Account selectedAccount = selectAccount();


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
        System.out.println("Expense transaction created successfully: " + "-Type: " + expenseTransaction.getType()
                + ", Amount: " + expenseTransaction.getAmount() + ", Category: " + expenseTransaction.getCategory().getName()
                + ", From Account: " + expenseTransaction.getFromAccount().getName()
                + ", Date: " + expenseTransaction.getDate()
                + ", Note: " + (expenseTransaction.getNote() != null ? expenseTransaction.getNote() : "No note"));
    }

    private void addTransfer(){
        System.out.println("\n === Add Transfer Transaction ===");

        //select ledger
        System.out.println("\nSelect a ledger to add transfer transaction:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());

        //get selectable accounts
        List<Account> accounts = accountController.getSelectableAccounts(userController.getCurrentUser());

        if(accounts.isEmpty()){
            System.out.println("At least two selectable accounts are required to perform a transfer.");
            return;
        }
        //select from account
        System.out.println("\nSelect the FROM account: ");
        Account fromAccount;
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            System.out.println((i + 1) + ". " + "Name: " + account.getName() + ", Balance: " + account.getBalance());
        }
        System.out.println("0. FROM account is external");
        System.out.print("Enter the number of the account: ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if(choice == 0) {
            fromAccount = null;
        } else if(choice < 0 || choice > accounts.size()) {
            System.out.println("Invalid choice.");
            return;
        }else{
            fromAccount = accounts.get(choice - 1);
        }

        //select to account
        System.out.println("\nSelect the TO account: ");
        Account toAccount;
        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            System.out.println((i + 1) + ". " + "Name: " + account.getName() + ", Balance: " + account.getBalance());
        }
        System.out.println("0. TO account is external");
        System.out.print("Enter the number of the account: ");
        input = scanner.nextLine().trim();
        choice = Integer.parseInt(input);
        if(choice == 0) {
            toAccount = null;
        } else if(choice < 0 || choice > accounts.size()) {
            System.out.println("Invalid choice.");
            return;
        }else{
            toAccount = accounts.get(choice - 1);
        }

        if (fromAccount == null && toAccount == null) {
            System.out.println("At least one account must be selected for a transfer.");
            return;
        }

        if (fromAccount != null && toAccount != null && fromAccount.getId() == (toAccount.getId())) {
            System.out.println("From and To accounts cannot be the same.");
            return;
        }

        //add note
        String note = inputNote();

        //add amount
        System.out.print("Enter the amount for the transfer: ");
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

        System.out.println("Transfer transaction created successfully: " + "-Type: "+transferTransaction.getType()
                + ", Amount: " + transferTransaction.getAmount() + ", From Account: " + (transferTransaction.getFromAccount()!=null ? transferTransaction.getFromAccount().getName() : "External")
                + ", To Account: " + (transferTransaction.getToAccount() != null ? transferTransaction.getToAccount().getName() : "External") + ", Date: " + transferTransaction.getDate()
                + ", Note: " + (transferTransaction.getNote() != null ? transferTransaction.getNote() : "No note"));
    }

    public void deleteTransaction(){
        System.out.println("\n === Delete Transaction ===");

        System.out.println("Select a ledger to delete transaction from:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        List<Transaction> transactions = transactionController.getTransactionsByLedgerInRangeDate(
                selectedLedger, LocalDate.MIN, LocalDate.MAX);
        if(transactions.isEmpty()){
            System.out.println("No transactions found in the selected ledger.");
            return;
        }
        System.out.println("Select a transaction to delete:");
        Transaction selectedTransaction = selectTransaction(transactions);
        if(selectedTransaction==null){
            System.out.println("No transaction selected. Returning to main menu.");
            return;
        }

        boolean deleted = transactionController.deleteTransaction(selectedTransaction);
        if(!deleted){
            System.out.println("Failed to delete transaction.");
            return;
        }
        System.out.println("Successfully deleted. Transaction info: " + showTransactionInfo(selectedTransaction));

    }

    public void editTransaction(){
        System.out.println("\n === Edit Transaction ===");

        System.out.println("Select a ledger to edit transaction from: ");
        //select ledger
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        List<Transaction> transactions = transactionController.getTransactionsByLedgerInRangeDate(
                selectedLedger, LocalDate.MIN, LocalDate.MAX);
        if(transactions.isEmpty()){
            System.out.println("No transactions found in the selected ledger.");
            return;
        }

        System.out.println("Select a transaction to edit:");
        Transaction selectedTransaction = selectTransaction(transactions);
        if(selectedTransaction==null){
            System.out.println("No transaction selected. Returning to main menu.");
            return;
        }

        //edit note
        System.out.println("Current note: " + (selectedTransaction.getNote() != null ? selectedTransaction.getNote() : "No note"));
        System.out.print("Do you want to edit the note ? (y/n): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        String note;
        if(confirm.equals("y") || confirm.equals("yes")){
            //edit note
            note = inputNote();
        }else{
            note = selectedTransaction.getNote(); //keep the same
        }


        //edit amount
        System.out.println("Current amount: " + selectedTransaction.getAmount());
        System.out.print("Do you want to edit the amount ? (y/n): ");
        confirm = scanner.nextLine().trim().toLowerCase();
        BigDecimal amount=null;
        if(confirm.equals("y") || confirm.equals("yes")){
            //edit amount
            System.out.print("Enter the amount for the transaction: ");
            amount = inputAmount();
        }

        //edit date
        System.out.println("Current transaction date: " + selectedTransaction.getDate());
        System.out.print("Do you want to edit the date ? (y/n): ");
        confirm = scanner.nextLine().trim().toLowerCase();
        LocalDate date=null;
        if(confirm.equals("y") || confirm.equals("yes")){
            //edit date
            System.out.print("Enter the date for the transaction (YYYY-MM-DD): ");
            date = inputDate();
        }

        //edit ledger
        System.out.println("Current ledger: " + selectedTransaction.getLedger().getName());
        System.out.print("Do you want to edit the ledger ? (y/n): ");
        confirm = scanner.nextLine().trim().toLowerCase();
        Ledger newLedger=null;
        if(confirm.equals("y") || confirm.equals("yes")){
           newLedger = selectLedger(userController.getCurrentUser());
        }

        LedgerCategory newCategory = null;
        Account newToAccount = null;
        Account newFromAccount = null;
        boolean updated = false;

        //edit from/to account or category based on transaction type
        switch(selectedTransaction.getType()){
            case INCOME -> {
                System.out.println("Current category: " + selectedTransaction.getCategory().getName());
                System.out.print("Do you want to edit the category ? (y/n): ");
                confirm = scanner.nextLine().trim().toLowerCase();
                if (confirm.equals("y") || confirm.equals("yes")) {
                    System.out.println("Select a new category:");
                    newCategory = selectCategory(selectedLedger, CategoryType.INCOME);
                }

                System.out.println("Current account: " + selectedTransaction.getToAccount().getName());
                System.out.print("Do you want to edit the account ? (y/n): ");
                confirm = scanner.nextLine().trim().toLowerCase();
                if (confirm.equals("y") || confirm.equals("yes")) {
                    System.out.println("Select a new account:");
                    newToAccount = selectAccount();
                }
                updated = transactionController.updateIncome((Income) selectedTransaction, newToAccount,
                        newCategory, note, date, amount, newLedger);
            }
            case EXPENSE -> {
                System.out.println("Current category: " + selectedTransaction.getCategory().getName());
                System.out.print("Do you want to edit the category ? (y/n): ");
                confirm = scanner.nextLine().trim().toLowerCase();
                if (confirm.equals("y") || confirm.equals("yes")) {
                    System.out.println("Select a new category:");
                    newCategory = selectCategory(selectedLedger, CategoryType.EXPENSE);
                }

                System.out.println("Current account: " + selectedTransaction.getFromAccount().getName());
                System.out.print("Do you want to edit the account ? (y/n): ");
                confirm = scanner.nextLine().trim().toLowerCase();
                if (confirm.equals("y") || confirm.equals("yes")) {
                    System.out.println("Select a new account:");
                    newFromAccount = selectAccount();
                }
                updated = transactionController.updateExpense((Expense) selectedTransaction, newFromAccount,
                        newCategory, note, date, amount, newLedger);
            }
            case TRANSFER -> {
                List<Account> accounts = accountController.getSelectableAccounts(userController.getCurrentUser());
                if (accounts.isEmpty()) {
                    System.out.println("At least two selectable accounts are required to perform a transfer.");
                    return;
                }

                System.out.println("Current FROM account: " + (selectedTransaction.getFromAccount() != null ? selectedTransaction.getFromAccount().getName() : "External"));
                System.out.print("Do you want to edit the FROM account ? (y/n): ");
                confirm = scanner.nextLine().trim().toLowerCase();
                if (confirm.equals("y") || confirm.equals("yes")) {
                    System.out.println("Select a new FROM account:");
                    for (int i = 0; i < accounts.size(); i++) {
                        Account account = accounts.get(i);
                        System.out.println((i + 1) + ". " + "Name: " + account.getName() + ", Balance: " + account.getBalance());
                    }
                    System.out.println("0. new FROM account is external");
                    System.out.print("Enter the number of the account: ");
                    String input = scanner.nextLine().trim();
                    int choice = Integer.parseInt(input);
                    if (choice > 0 && choice <= accounts.size()) {
                        newFromAccount = accounts.get(choice - 1);
                    } else if(choice < 0 || choice > accounts.size()) {
                        System.out.println("Invalid choice: number out of range.");
                        return;
                    }
                }else{
                    newFromAccount = selectedTransaction.getFromAccount(); //keep the same
                }

                System.out.println("Current TO account: " + (selectedTransaction.getToAccount() != null ? selectedTransaction.getToAccount().getName() : "External"));
                System.out.print("Do you want to edit the TO account ? (y/n): ");
                confirm = scanner.nextLine().trim().toLowerCase();
                if (confirm.equals("y") || confirm.equals("yes")) {
                    System.out.println("Select a new TO account:");
                    for (int i = 0; i < accounts.size(); i++) {
                        Account account = accounts.get(i);
                        System.out.println((i + 1) + ". " + "Name: " + account.getName() + ", Balance: " + account.getBalance());
                    }
                    System.out.println("0. new TO account is external");
                    System.out.print("Enter the number of the account: ");
                    String input = scanner.nextLine().trim();
                    int choice = Integer.parseInt(input);

                    if(choice > 0 && choice <= accounts.size()) {
                        newToAccount = accounts.get(choice - 1);
                    }else if(choice < 0 || choice > accounts.size()) {
                        System.out.println("Invalid choice: number out of range.");
                        return;
                    }
                }else{
                    newToAccount = selectedTransaction.getToAccount();
                }

                if (newFromAccount == null && newToAccount == null) {
                    System.out.println("At least one account must be selected for a transfer.");
                    return;
                }
                if (newFromAccount != null && newToAccount != null && newFromAccount.getId() == (newToAccount.getId())) {
                    System.out.println("From and To accounts cannot be the same.");
                    return;
                }

                updated = transactionController.updateTransfer((Transfer) selectedTransaction, newFromAccount,
                        newToAccount, note, date, amount, newLedger);
            }
        }

        //update transaction
        if(!updated){
            System.out.println("Failed to update transaction.");
            return;
        }
        System.out.println("Transaction updated successfully: " + showTransactionInfo(selectedTransaction));
    }

    //helper methods
    private String showTransactionInfo(Transaction tx){
        StringBuilder info = new StringBuilder();

        info.append(String.format("- Amount: %s, Date: %s",
                tx.getType() == TransactionType.EXPENSE
                        ? tx.getAmount().negate()
                        : tx.getAmount(),
                tx.getDate()));

        if (tx.getCategory() != null) {
            info.append(", Category: ").append(tx.getCategory().getName());
        }

        if (tx.getType().toString().equals("TRANSFER")) {
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

        return info.toString();
    }
    private Transaction selectTransaction(List<Transaction> transactions){
        for(int i=0; i<transactions.size(); i++){
            Transaction tx = transactions.get(i);
            StringBuilder info = new StringBuilder();

            info.append(String.format("%d. Amount: %s, Date: %s",
                    (i + 1),
                    tx.getType() == TransactionType.EXPENSE
                            ? tx.getAmount().negate()
                            : tx.getAmount(),
                    tx.getDate()));

            if (tx.getCategory() != null) {
                info.append(", Category: ").append(tx.getCategory().getName());
            }

            if (tx.getType().toString().equals("TRANSFER")) {
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

        System.out.println("0. Cancel");
        System.out.print("Enter the number of the transaction: ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if(choice == 0) {
            return null;
        }
        if(choice < 1 || choice > transactions.size()) {
            System.out.println("Invalid choice.");
            return selectTransaction(transactions);
        }
        return transactions.get(choice - 1);
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

    private Account selectAccount() {
        List<Account> accounts = accountController.getSelectableAccounts(userController.getCurrentUser());
        if (accounts.isEmpty()) {
            System.out.println("No accounts found for the user.");
            return null;
        }

        List<Account> accountsSelectable = accounts.stream()
                .filter(Account::getSelectable)
                .toList();

        if (accountsSelectable.isEmpty()) {
            System.out.println("No selectable accounts found for the user.");
            return null;
        }

        for (int i = 0; i < accountsSelectable.size(); i++) {
            Account account = accountsSelectable.get(i);
            System.out.println((i + 1) + ". " + "Name: " + account.getName() + ", Balance: " + account.getBalance());
        }
        System.out.print("Enter the number of the account: ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if (choice < 1 || choice > accountsSelectable.size()) {
            System.out.println("Invalid choice.");
            return selectAccount();
        }
        return accountsSelectable.get(choice - 1);
    }

    private LedgerCategory selectCategory(Ledger ledger, CategoryType type) {
        List<LedgerCategory> categories = ledgerCategoryController.getLedgerCategoryTreeByLedger(ledger);

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
                .filter(category -> !category.getName().equals("Claim Income")) //exclude "Claim Income" category
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
        String input = scanner.nextLine().trim();
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
