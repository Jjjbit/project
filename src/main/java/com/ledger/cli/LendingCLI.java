package com.ledger.cli;

import com.ledger.business.AccountController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class LendingCLI {
    private final Scanner scanner = new java.util.Scanner(System.in);
    private final ReportController reportController;
    private final AccountController accountController;
    private final UserController userController;

    public LendingCLI(ReportController reportController, AccountController accountController,
                      UserController userController) {
        this.userController = userController;
        this.accountController = accountController;
        this.reportController = reportController;
    }
    public void addLending() {
        System.out.println("\n ===Adding a new lending ===");

        //enter ledger
        System.out.println("Select a ledger for this lending:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());

        System.out.print("Enter lending account name: ");
        String name = inputName();
        if(name.isEmpty()) {
            System.out.println("Lending name cannot be empty.");
            return;
        }

        System.out.print("Enter lending amount: ");
        BigDecimal amountInput = scanner.nextBigDecimal();

        System.out.print("Enter borrowing date (YYYY-MM-DD) or leave blank for today: ");
        LocalDate lendingDate = inputLendingDate();

        boolean includedInNetWorth = inputIncludedInNetWorth();

        boolean selectable = inputSelectable();

        System.out.print("Enter note (optional): ");
        String note = inputNote();

        //enter from account option can be added later
        System.out.print("Do you want to specify a 'from account'? (y/n): ");
        String fromAccountChoice = scanner.nextLine().trim().toLowerCase();
        Account fromAccount = null;
        if(fromAccountChoice.equals("y") || fromAccountChoice.equals("yes")) {
            List<Account> accounts = reportController.getAccountsNotHidden(userController.getCurrentUser()).stream()
                    .filter(account -> !(account instanceof LoanAccount))
                    .toList();
            if (accounts.isEmpty()) {
                System.out.println("No available accounts to select.");
                return;
            }
            System.out.println("\n Select an account to lend from:");
            for (int i = 0; i < accounts.size(); i++) {
                System.out.println((i + 1) + ". " + accounts.get(i).getName());
            }
            System.out.print("Enter the number of the account: ");
            int accountIndex = scanner.nextInt();
            scanner.nextLine(); // Consume newline
            if (accountIndex < 1 || accountIndex > accounts.size()) {
                System.out.println("Invalid account selection.");
                return;
            }
            fromAccount = accounts.get(accountIndex - 1);
        }
        // Further implementation to create lending using the collected inputs
        LendingAccount lendingAccount = accountController.createLendingAccount(userController.getCurrentUser(),
                name, amountInput, note, includedInNetWorth, selectable, fromAccount, lendingDate, selectedLedger);
        if (lendingAccount == null) {
            System.out.println("Failed to create lending account.");
            return;
        }

        System.out.println("Lending account created successfully.");

    }

    public void showAllLendings() {
        System.out.println("\n === Listing all lendings ===");

        List<LendingAccount> lendings = reportController.getActiveLendingAccounts(userController.getCurrentUser());
        if (lendings.isEmpty()) {
            System.out.println("No active lendings found.");
            return;
        }
        for (LendingAccount lending : lendings) {
            System.out.println("Lending ID: " + lending.getId() +
                    ", Name: " + lending.getName() +
                    ", Amount Lent: " + lending.getBalance() +
                    ", Lending Date: " + lending.getDate() +
                    ", Included in Net Worth: " + lending.getIncludedInNetAsset() +
                    ", Selectable: " + !lending.getHidden() +
                    (lending.getNotes() != null ? ", Note: " + lending.getNotes() : ""));
        }
    }

    public void editLending() {
        System.out.println("\n === Editing a lending ===");

        System.out.println("Select the lending to edit:");
        List<LendingAccount> userLendings = reportController.getActiveLendingAccounts(userController.getCurrentUser());

        for(int i=0;i<userLendings.size();i++){
            LendingAccount lending=userLendings.get(i);
            System.out.println((i+1) + ". " + "Name: " + lending.getName() +
                    ", Amount Lent: " + lending.getBalance());
        }
        System.out.println("0. Cancel");
        System.out.print("Select a lending by number: ");
        int lendingIndex =scanner.nextInt() - 1;
        if(lendingIndex == -1) {
            System.out.println("Lending editing cancelled.");
            return;
        }
        if(lendingIndex < 0 || lendingIndex >= userLendings.size()) {
            System.out.println("Invalid lending selection.");
            return;
        }

        LendingAccount lendingToEdit = userLendings.get(lendingIndex);

        //new name
        System.out.print("Current name" + lendingToEdit.getName() );
        System.out.print("Do you want to change the name? (y/n): ");
        String changeNameChoice = scanner.nextLine().trim().toLowerCase();
        String newName = null;
        if(changeNameChoice.equals("y") || changeNameChoice.equals("yes")) {
            System.out.print("Enter new name: ");
            newName = inputName();
        }

        //new balance
        System.out.print("Current amount lent: " + lendingToEdit.getBalance());
        System.out.print("Do you want to change the amount lent? (y/n): ");
        String changeBalanceChoice = scanner.nextLine().trim().toLowerCase();
        BigDecimal newBalance = null;
        if(changeBalanceChoice.equals("y") || changeBalanceChoice.equals("yes")) {
            System.out.print("Enter new amount lent: ");
            newBalance = scanner.nextBigDecimal();
        }

        //new notes
        System.out.print("Current note: " + (lendingToEdit.getNotes() != null ? lendingToEdit.getNotes() : "No note"));
        System.out.print("Do you want to change the note? (y/n): ");
        String changeNoteChoice = scanner.nextLine().trim().toLowerCase();
        String newNotes = null;
        if(changeNoteChoice.equals("y") || changeNoteChoice.equals("yes")) {
            System.out.print("Enter new note (leave blank to remove): ");
            newNotes = inputNote();
        }

        //included in net worth
        System.out.print("Currently included in net worth calculation: " + lendingToEdit.getIncludedInNetAsset());
        System.out.print("Do you want to change this setting? (y/n): ");
        String changeIncludedChoice = scanner.nextLine().trim().toLowerCase();
        Boolean newIncludedInNetAsset = null;
        if(changeIncludedChoice.equals("y") || changeIncludedChoice.equals("yes")) {
            newIncludedInNetAsset = inputIncludedInNetWorth();
        }

        //selectable
        System.out.print("Currently selectable: " + !lendingToEdit.getHidden());
        System.out.print("Do you want to change this setting? (y/n): ");
        String changeSelectableChoice = scanner.nextLine().trim().toLowerCase();
        Boolean newSelectable = null;
        if(changeSelectableChoice.equals("y") || changeSelectableChoice.equals("yes")) {
            newSelectable = inputSelectable();
        }

        //is ended
        System.out.print("Currently ended: " + lendingToEdit.getIsEnded());
        System.out.print("Do you want to change this setting? (y/n): ");
        String changeIsEndedChoice = scanner.nextLine().trim().toLowerCase();
        Boolean newIsEnded = null;
        if(changeIsEndedChoice.equals("y") || changeIsEndedChoice.equals("yes")) {
            System.out.print("Is the lending ended? (y/n): ");
            String endedInput = scanner.nextLine().trim().toLowerCase();
            if(endedInput.equals("y") || endedInput.equals("yes")) {
                newIsEnded = true;
            } else if(endedInput.equals("n") || endedInput.equals("no")) {
                newIsEnded = false;
            } else {
                System.out.println("Invalid input for ended status. Skipping change.");
            }
        }

        boolean success = accountController.editLendingAccount(
                lendingToEdit,
                newName,
                newBalance,
                newNotes,
                newIncludedInNetAsset,
                newSelectable,
                newIsEnded
        );
        if(!success) {
            System.out.println("Failed to edit lending account.");
            return;
        }
        System.out.println("Lending account edited successfully.");
    }

    public void deleteLending() {
        System.out.println("\n === Deleting a lending ===");

        System.out.println("Select the lending to delete:");
        List<LendingAccount> userLendings = reportController.getActiveLendingAccounts(userController.getCurrentUser());
        System.out.println("0. Cancel");
        for(int i=0;i<userLendings.size();i++){
            LendingAccount lending=userLendings.get(i);
            System.out.println((i+1) + ". " + "Name: " + lending.getName() +
                    ", Amount Lent: " + lending.getBalance());
        }

        System.out.print("Select a lending by number: ");
        int lendingIndex =scanner.nextInt() - 1;
        if(lendingIndex == -1) {
            System.out.println("Lending deletion cancelled.");
            return;
        }
        if(lendingIndex < 0 || lendingIndex >= userLendings.size()) {
            System.out.println("Invalid lending selection.");
            return;
        }

        LendingAccount lendingToDelete = userLendings.get(lendingIndex);

        //delete transactions associated with the lending account first
        System.out.println("Do you want to delete all transactions associated with this lending account? (y/n): ");
        String deleteTransactionsChoice = scanner.nextLine().trim().toLowerCase();
        boolean deleteTransactions = false;
        if(deleteTransactionsChoice.equals("y") || deleteTransactionsChoice.equals("yes")) {
            deleteTransactions = true;
        }

        boolean success = accountController.deleteAccount(lendingToDelete, deleteTransactions);
        if(!success) {
            System.out.println("Failed to delete lending account.");
            return;
        }
        System.out.println("Lending account deleted successfully.");
    }

    public void receiveLendingPayment() {
        System.out.println("\n === Receiving a lending payment ===");

        System.out.println("Select the lending to receive payment for:");
        List<LendingAccount> userLendings = reportController.getActiveLendingAccounts(userController.getCurrentUser());
        System.out.println("0. Cancel");
        for(int i=0;i<userLendings.size();i++){
            LendingAccount lending=userLendings.get(i);
            System.out.println((i+1) + ". " + "Name: " + lending.getName() +
                    ", Amount Lent: " + lending.getBalance());
        }

        System.out.print("Select a lending by number: ");
        int lendingIndex =scanner.nextInt() - 1;
        if(lendingIndex == -1) {
            System.out.println("Lending payment cancelled.");
            return;
        }
        if(lendingIndex < 0 || lendingIndex >= userLendings.size()) {
            System.out.println("Invalid lending selection.");
            return;
        }

        LendingAccount lendingToReceivePayment = userLendings.get(lendingIndex);

        System.out.print("Enter payment amount: ");
        BigDecimal paymentAmount = scanner.nextBigDecimal();

        //select to account option can be added later
        System.out.print("Do you want to specify a 'to account'? (y/n):");
        String toAccountChoice = scanner.nextLine().trim().toLowerCase();
        Account toAccount = null;
        if(toAccountChoice.equals("y") || toAccountChoice.equals("yes")) {
            List<Account> accounts = reportController.getAccountsNotHidden(userController.getCurrentUser()).stream()
                    .filter(account -> !(account instanceof LoanAccount))
                    .toList();
            if (accounts.isEmpty()) {
                System.out.println("No available accounts to select.");
                return;
            }
            System.out.println("\n Select an account to receive payment into:");
            for (int i = 0; i < accounts.size(); i++) {
                System.out.println((i + 1) + ". " + accounts.get(i).getName());
            }
            System.out.print("Enter the number of the account: ");
            int accountIndex = scanner.nextInt();
            scanner.nextLine(); // Consume newline
            if (accountIndex < 1 || accountIndex > accounts.size()) {
                System.out.println("Invalid account selection.");
                return;
            }
            toAccount = accounts.get(accountIndex - 1);
        }

        //select ledger option can be added later
        System.out.print("Do you want to specify a 'ledger'? (y/n):");
        String ledgerChoice = scanner.nextLine().trim().toLowerCase();
        Ledger ledger = null;
        if(ledgerChoice.equals("y") || ledgerChoice.equals("yes")) {
            List<Ledger> ledgers = reportController.getLedgerByUser(userController.getCurrentUser());
            if (ledgers.isEmpty()) {
                System.out.println("No available ledgers to select.");
                return;
            }
            System.out.println("\n Select a ledger for the payment:");
            for (int i = 0; i < ledgers.size(); i++) {
                System.out.println((i + 1) + ". " + ledgers.get(i).getName());
            }
            System.out.print("Enter the number of the ledger: ");
            int ledgerIndex = scanner.nextInt();
            scanner.nextLine(); // Consume newline
            if (ledgerIndex < 1 || ledgerIndex > ledgers.size()) {
                System.out.println("Invalid ledger selection.");
                return;
            }
            ledger = ledgers.get(ledgerIndex - 1);
        }

        boolean success = accountController.receiveLending(lendingToReceivePayment, paymentAmount,
                 toAccount, ledger);

        if(!success) {
            System.out.println("Failed to receive lending payment.");
            return;
        }
        System.out.println("Lending payment received successfully.");
    }


    // Helper methods for input
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
    private String inputNote() {
        String note = scanner.nextLine().trim();
        return note.isEmpty() ? null : note;
    }

    private String inputName() {
        return scanner.nextLine().trim();
    }

    private LocalDate inputLendingDate() {
        String dateInput = scanner.nextLine().trim();
        if(dateInput.isEmpty()) {
            return LocalDate.now();
        }
        if(!dateInput.matches("\\d{4}-\\d{2}-\\d{2}")) {
            System.out.println("Invalid date format. Please use YYYY-MM-DD.");
            return inputLendingDate();
        }

        return LocalDate.parse(dateInput);
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
}
