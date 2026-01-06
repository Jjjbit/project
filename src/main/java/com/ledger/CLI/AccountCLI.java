package com.ledger.CLI;

import com.ledger.BusinessLogic.*;
import com.ledger.DomainModel.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class AccountCLI {
    private final AccountController accountController;
    private final UserController userController;
    private final ReportController reportController;
    private final TransactionController transactionController;
    private final Scanner scanner = new Scanner(System.in);

    public AccountCLI(AccountController accountController, UserController userController,
                      ReportController reportController, TransactionController transactionController) {
        this.transactionController = transactionController;
        this.reportController = reportController;
        this.accountController = accountController;
        this.userController = userController;
    }

    public void createAccount() {
        System.out.println("\n=== Create New Account ===");

        //choose account category
//        AccountCategory category = selectAccountCategory();
//        if (category == null) return;
//
//        //choose account type
//        AccountType type = selectAccountType(category);
//        if (type == null) return;

        //input common account details
        System.out.print("Enter account name: ");
        String name = inputAccountName();

        BigDecimal balance;
        System.out.print("Enter initial balance: ");
        String balanceInput = scanner.nextLine().trim();
        balance = balanceInput.isEmpty() ? BigDecimal.ZERO : new BigDecimal(balanceInput);

        boolean includedInNetWorth = inputIncludedInNetWorth();
        boolean selectable = inputSelectable();

        Account account = accountController.createAccount(name, balance, includedInNetWorth, selectable);
        if (account == null) {
            System.out.println(" Failed to create account.");
            return;
        }

        System.out.println(" Account created successfully: " + account.getName());
        showAllAccounts();
    }

    public void showAllAccounts() {
        System.out.println("\n=== Show All Accounts ===");

        List<Account> accounts = accountController.getAccounts(userController.getCurrentUser());
        if (accounts.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }

        for (Account account : accounts) {
            System.out.print("- " + account.getName() + " |  Balance: " + account.getBalance() + " | Included in Net Worth: " + (account.getIncludedInAsset() ? "Yes" : "No") + " | Selectable: " + (account.getSelectable() ? "Yes" : "No"));
            System.out.println();
        }
    }

    public void updateAccount() {
        System.out.println("\n=== Update Account ===");

        System.out.println("\nSelect the account to update:");
        List<Account> accounts = accountController.getAccounts(userController.getCurrentUser());
        if (accounts.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }
        for( int i=0; i< accounts.size(); i++) {
            Account account = accounts.get(i);
            System.out.println((i+1) + ". " + account.getName());
        }
        System.out.println("0. return");
        System.out.print("Enter number: ");
        String inputAccount = scanner.nextLine().trim();
        int choice = Integer.parseInt(inputAccount);
        if( choice ==0) {
            System.out.println("Account update cancelled.");
            return;
        }
        if( choice <1 || choice > accounts.size()) {
            System.out.println("Invalid choice!");
            return;
        }
        Account accountToUpdate = accounts.get(choice -1);

        //rename account
        System.out.print("Current name: " + accountToUpdate.getName() + ". Enter new name (press Enter to skip): ");
        String newName = scanner.nextLine();
        if (newName.isEmpty()) {
            newName = accountToUpdate.getName(); //no change
        }

        //select included in net worth
        String includeInNetWorthProm = accountToUpdate.getIncludedInAsset() ? "Current: included in net worth. " : "Current: not included in net worth. ";
        System.out.print(includeInNetWorthProm + "(press Enter to skip). Include in net worth? (y/n): ");
        String input = scanner.nextLine().trim().toLowerCase();
        boolean newIncludedInNetWorth = accountToUpdate.getIncludedInAsset();
        if (input.equals("y") || input.equals("yes")) {
            newIncludedInNetWorth = true;
        } else if (input.equals("n") || input.equals("no")) {
            newIncludedInNetWorth = false;
        }

        //update selectable and balance
        boolean newSelectable = accountToUpdate.getSelectable();
        BigDecimal newBalance;
        System.out.print("it is: " + (accountToUpdate.getSelectable() ? "selectable." : "not selectable.") +
                " Do you want to set as selectable? (y/n, press Enter to skip): ");
        input = scanner.nextLine().trim().toLowerCase();
        if(input.equals("y") || input.equals("yes")) {
            newSelectable = true;
        } else if (input.equals("n") || input.equals("no")) {
            newSelectable = false;
        }
        System.out.print("Current balance: " + accountToUpdate.getBalance() + ". (press Enter to skip) Enter new balance: ");
        String balanceInput = scanner.nextLine().trim();
        if (!balanceInput.isEmpty()) {
            newBalance = new BigDecimal(balanceInput);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0 ) {
                newBalance = accountToUpdate.getBalance();
            }
        }else{
            newBalance = accountToUpdate.getBalance(); //no change
        }

        boolean success = accountController.editAccount(accountToUpdate, newName, newBalance, newIncludedInNetWorth, newSelectable);
        if (!success) {
            System.out.println("Failed to update account: " + accountToUpdate.getName());
            return;
        }
        System.out.println("Account updated successfully: " + accountToUpdate.getName());

    }

    public void deleteAccount() {
        System.out.println("\n=== Delete Account ===");

        //select account to delete
        System.out.println("\nSelect the account to delete:");
        Account accountToDelete;
        List<Account> accounts = accountController.getAccounts(userController.getCurrentUser());
        if (accounts.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }
        for( int i=0; i< accounts.size(); i++) {
            Account account = accounts.get(i);
            System.out.println((i+1) + ". " + account.getName() );
        }
        System.out.println("0. Return");
        System.out.print("Enter number: ");

        String inputAccount = scanner.nextLine().trim();
        int choice = Integer.parseInt(inputAccount);
        if( choice ==0) {
            System.out.println("Account deletion cancelled.");
            return;
        }
        if( choice <1 || choice > accounts.size()) {
            System.out.println("Invalid choice!");
            return;
        }
        accountToDelete = accounts.get(choice -1);

        boolean success = accountController.deleteAccount(accountToDelete);

        if (!success) {
            System.out.println("Failed to delete account: " + accountToDelete.getName());
            return;
        }
        System.out.println("Account deleted successfully: " + accountToDelete.getName());
    }

    public void viewAccountSummary() {
        System.out.println("\n=== Account Summary ===");

        //select Account
        List<Account> accounts = accountController.getAccounts(userController.getCurrentUser());
        if (accounts.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }
        System.out.println("Select the account to view summary:");
        System.out.println("0. return");
        for( int i=0; i< accounts.size(); i++) {
            Account account = accounts.get(i);
            System.out.println((i+1) + ". " + account.getName());
        }
        System.out.print("Enter number: ");
        String inputAccount = scanner.nextLine().trim();
        int choice = Integer.parseInt(inputAccount);
        if( choice ==0) {
            System.out.println("Return to main menu.");
            return;
        }
        if( choice <1 || choice > accounts.size()) {
            System.out.println("Invalid choice!");
            return;
        }
        Account account = accounts.get(choice -1);

        //select date range
        LocalDate startDate;
        LocalDate endDate;
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
            int month;
            String monthInput = scanner.nextLine().trim();
            if(!monthInput.isEmpty()){

                month = Integer.parseInt(monthInput);
            if(month<1 || month>12){
                System.out.println("Invalid month. Please enter a value between 1 and 12.");
                return;
            }
            }else{
                month = LocalDate.now().getMonthValue();
            }
            startDate = LocalDate.of(LocalDate.now().getYear(), month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }

        List<Transaction> transactions = transactionController.getTransactionsByAccountInRangeDate(account, startDate, endDate);
        BigDecimal totalIncome = reportController.getTotalIncomeByAccount(account, startDate, endDate);
        BigDecimal totalExpense = reportController.getTotalExpenseByAccount(account, startDate, endDate);
        System.out.println("\nfrom " + startDate + " to " + endDate);
        System.out.print("Account: " + account.getName() + " | Total Income: " + totalIncome + " | Total Expense: " + totalExpense);
        System.out.print(" | Current Balance: " + account.getBalance() +"\n");

        if(transactions.isEmpty()) {
            System.out.println("No transactions found for this account in the selected date range.");
            return;
        }

        int i = 0;
        for (Transaction tx : transactions) {
            i++;
            StringBuilder info = new StringBuilder();

            info.append(String.format("%d. Amount: %s, Date: %s",
                    i,
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

    //private helper methods for input and selection
    private String inputAccountName() {
        String name = scanner.nextLine();
        if( name.isEmpty()) {
            System.out.println("Account name cannot be empty!");
            return inputAccountName();
        }
        return name;
    }

    private boolean inputIncludedInNetWorth() {
        System.out.print("Include in net worth calculation? (y/n): ");
        String input = scanner.nextLine().trim().toLowerCase();
        if (input.equals("y") || input.equals("yes")) {
            return true;
        } else if (input.equals("n") || input.equals("no")) {
            return false;
        } else {
            System.out.println("Please enter 'y' for yes or 'n' for no.");
            return inputIncludedInNetWorth();
        }
    }

    private boolean inputSelectable(){
        System.out.print("Selectable? (y/n): ");
        String input = scanner.nextLine().trim().toLowerCase();
        if (input.equals("y") || input.equals("yes")) {
            return true;
        } else if (input.equals("n") || input.equals("no")) {
            return false;
        } else {
            System.out.println("Please enter 'y' for yes or 'n' for no.");
            return inputSelectable();
        }
    }
}
