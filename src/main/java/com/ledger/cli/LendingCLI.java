package com.ledger.cli;

import com.ledger.business.AccountController;
import com.ledger.business.LedgerController;
import com.ledger.business.UserController;
import com.ledger.domain.Account;
import com.ledger.domain.Ledger;
import com.ledger.domain.LendingAccount;
import com.ledger.domain.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class LendingCLI {
    private final Scanner scanner = new java.util.Scanner(System.in);
    private final AccountController accountController;
    private final UserController userController;
    private final LedgerController ledgerController;

    public LendingCLI(AccountController accountController,
                      UserController userController, LedgerController ledgerController) {
        this.ledgerController = ledgerController;
        this.userController = userController;
        this.accountController = accountController;
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
        String amountStr = scanner.nextLine().trim();
        if(amountStr.isEmpty()) {
            System.out.println("Lending amount cannot be empty.");
            return;
        }
        BigDecimal amountInput = new BigDecimal(amountStr);

        boolean includedInNetWorth = inputIncludedInNetWorth();
        boolean selectable = inputSelectable();

        System.out.print("Enter borrowing date (YYYY-MM-DD) or leave blank for today: ");
        LocalDate lendingDate = inputLendingDate();

        System.out.print("Enter note (optional): ");
        String note = inputNote();

        //enter from account option can be added later
        System.out.println("Select an account to lend from: ");
        Account fromAccount;
        List<Account> accounts = accountController.getSelectableAccounts(userController.getCurrentUser());
        for (int i = 0; i < accounts.size(); i++) {
            System.out.println((i + 1) + ". " + accounts.get(i).getName());
        }
        System.out.println("0. From account is external");
        System.out.print("Enter the number of the account: ");

        String accountInput = scanner.nextLine().trim();
        int accountIndex = Integer.parseInt(accountInput);

        if (accountIndex == 0) {
            fromAccount = null;
        }else if (accountIndex < 0 || accountIndex > accounts.size()) {
            System.out.println("Invalid account selection.");
            return;
        }else{
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
        showAllLendings();
    }

    public void showAllLendings() {
        System.out.println("\n === Listing all lendings ===");

        List<LendingAccount> lendings = accountController.getVisibleLendingAccounts(userController.getCurrentUser());
        if (lendings.isEmpty()) {
            System.out.println("No active lendings found.");
            return;
        }
        for (LendingAccount lending : lendings) {
            System.out.println(" Name: " + lending.getName() +
                    " | Remaining Amount: " + lending.getBalance() +
                    " | Included in Net Worth: " + (lending.getIncludedInNetAsset() ? "Yes" : "No") +
                    " | Selectable: " + (lending.getSelectable() ? "Yes" : "No") +
                    " | Status: " + (lending.getIsEnded() ? "Ended" : "Active") +
                    " | Lending Date: " + lending.getDate() +
                    (lending.getNotes() != null ? " | Note: " + lending.getNotes() : " | No note"));
        }
    }

    public void editLending() {
        System.out.println("\n === Editing a lending ===");

        System.out.println("\nSelect the lending to edit:");
        List<LendingAccount> userLendings = accountController.getVisibleLendingAccounts(userController.getCurrentUser());

        for(int i=0;i<userLendings.size();i++){
            LendingAccount lending=userLendings.get(i);
            System.out.println((i+1) + ". " + "Name: " + lending.getName() +
                    " | Remaining Amount: " + lending.getBalance() +
                    " | Included in Net Worth: " + (lending.getIncludedInNetAsset() ? "Yes" : "No") +
                    " | Selectable: " + (lending.getSelectable() ? "Yes" : "No") +
                    " | Status: " + (lending.getIsEnded() ? "Ended" : "Active") +
                        " | Lending Date: " + lending.getDate() +
                    (lending.getNotes() != null ? " | Note: " + lending.getNotes() : " | No note"));
        }

        System.out.println("0. Cancel");
        System.out.print("Select a lending by number: ");

        String lendingInput = scanner.nextLine().trim();
        int lendingIndex = Integer.parseInt(lendingInput) - 1;

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
        System.out.println("Current name: " + lendingToEdit.getName() );
        System.out.print("Enter new name (press Enter to skip): ");
        String newNameInput = scanner.nextLine().trim();
        String newName = null;
        if (!newNameInput.isEmpty()) {
            newName = newNameInput;
        }

        //new balance
        System.out.println("Current amount lent: " + lendingToEdit.getBalance());
        System.out.print("Enter new amount (press Enter to skip): ");
        String newAmountInput = scanner.nextLine().trim();
        BigDecimal newBalance = null;
        if (!newAmountInput.isEmpty()) {
            newBalance = new BigDecimal(newAmountInput);
        }

        //new notes
        System.out.println("Current note: " + (lendingToEdit.getNotes() != null ? lendingToEdit.getNotes() : "No note"));
        System.out.print("Enter new note (press Enter to skip): ");
        String newNoteInput = scanner.nextLine().trim();
        String newNotes = null;
        if (!newNoteInput.isEmpty()) {
            newNotes = newNoteInput;
        }

        //included in net worth
        System.out.println("Currently included in net worth calculation: " + (lendingToEdit.getIncludedInNetAsset() ? "Yes" : "No"));
        System.out.print("(press Enter to skip) include in net worth? (y/n): ");
        String changeInclusionInput = scanner.nextLine().trim().toLowerCase();
        Boolean newIncludedInNetAsset = null;
        if (changeInclusionInput.equals("y") || changeInclusionInput.equals("yes")) {
            newIncludedInNetAsset = true;
        } else if (changeInclusionInput.equals("n") || changeInclusionInput.equals("no")) {
            newIncludedInNetAsset = false;
        }

        //selectable
        System.out.println("Currently selectable status: " + (lendingToEdit.getSelectable() ? "Yes" : "No"));
        System.out.print("(press Enter to skip) selectable? (y/n): ");
        String changeSelectableInput = scanner.nextLine().trim().toLowerCase();
        Boolean newSelectable = null;
        if (changeSelectableInput.equals("y") || changeSelectableInput.equals("yes")) {
            newSelectable = true;
        } else if (changeSelectableInput.equals("n") || changeSelectableInput.equals("no")) {
            newSelectable = false;
        }

        //is ended
        System.out.println("Currently ended status: " + lendingToEdit.getIsEnded());
        System.out.print("(press Enter to skip) is ended? (y/n): ");
        String changeIsEndedInput = scanner.nextLine().trim().toLowerCase();
        Boolean newIsEnded = null;
        if (changeIsEndedInput.equals("y") || changeIsEndedInput.equals("yes")) {
            newIsEnded = true;
        } else if (changeIsEndedInput.equals("n") || changeIsEndedInput.equals("no")) {
            newIsEnded = false;
        }

        boolean success = accountController.editLendingAccount(lendingToEdit, newName, newBalance,
                newNotes, newIncludedInNetAsset, newSelectable, newIsEnded);

        if(!success) {
            System.out.println("Failed to edit lending account.");
            return;
        }
        System.out.println("Lending account edited successfully.");
        showAllLendings();
    }

    public void deleteLending() {
        System.out.println("\n === Deleting a lending ===");

        System.out.println("Select the lending to delete:");
        List<LendingAccount> userLendings = accountController.getVisibleLendingAccounts(userController.getCurrentUser());
        System.out.println("0. Cancel");
        for(int i=0;i<userLendings.size();i++){
            LendingAccount lending=userLendings.get(i);
            System.out.println((i+1) + ". " + "Name: " + lending.getName() +
                    " | Remaining Amount: " + lending.getBalance() +
                    " | Included in Net Worth: " + (lending.getIncludedInNetAsset() ? "Yes" : "No") +
                    " | Selectable: " + (lending.getSelectable() ? "Yes" : "No") +
                    " | Status: " + (lending.getIsEnded() ? "Ended" : "Active") +
                    " | Lending Date: " + lending.getDate() +
                    (lending.getNotes() != null ? " | Note: " + lending.getNotes() : " | No note"));
        }

        System.out.print("Select a lending by number: ");

        int lendingIndex =scanner.nextInt() - 1;
        scanner.nextLine(); // Consume newline

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
        System.out.print("Do you want to delete all transactions associated with this lending account? (y/n): ");
        String deleteTransactionsChoice = scanner.nextLine().trim().toLowerCase();

        boolean deleteTransactions = deleteTransactionsChoice.equals("y") || deleteTransactionsChoice.equals("yes");

        boolean success = accountController.deleteAccount(lendingToDelete, deleteTransactions);

        if(!success) {
            System.out.println("Failed to delete lending account.");
            return;
        }
        System.out.println("Lending account deleted successfully.");
        showAllLendings();
    }

    public void receiveLendingPayment() {
        System.out.println("\n === Receiving a lending payment ===");

        System.out.println("Select the lending to receive payment for:");
        List<LendingAccount> userLendings = accountController.getVisibleLendingAccounts(userController.getCurrentUser());
        System.out.println("0. Cancel");
        for(int i=0;i<userLendings.size();i++){
            LendingAccount lending=userLendings.get(i);
            System.out.println((i+1) + ". " + "Name: " + lending.getName() +
                    " | Remaining Amount: " + lending.getBalance() +
                    " | Included in Net Worth: " + (lending.getIncludedInNetAsset() ? "Yes" : "No") +
                    " | Selectable: " + (lending.getSelectable() ? "Yes" : "No") +
                    " | Status: " + (lending.getIsEnded() ? "Ended" : "Active") +
                    " | Lending Date: " + lending.getDate() +
                    (lending.getNotes() != null ? " | Note: " + lending.getNotes() : " | No note"));
        }

        System.out.print("Select a lending by number: ");

        String lendingInput = scanner.nextLine().trim();
        int lendingIndex = Integer.parseInt(lendingInput) - 1;

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
        String paymentAmountStr = scanner.nextLine().trim();
        if(paymentAmountStr.isEmpty()) {
            System.out.println("Payment amount cannot be empty.");
            return;
        }
        BigDecimal paymentAmount = new BigDecimal(paymentAmountStr);

        //select to account option can be added later
        System.out.print("Do you want to  select an account to receive payment? (y/n):");
        String toAccountChoice = scanner.nextLine().trim().toLowerCase();
        Account toAccount = null;
        if(toAccountChoice.equals("y") || toAccountChoice.equals("yes")) {
            List<Account> accounts = accountController.getSelectableAccounts(userController.getCurrentUser());
            if (accounts.isEmpty()) {
                System.out.println("No available accounts to select.");
                return;
            }

            System.out.println("Select an account to receive payment into:");
            System.out.println("0. Cancel");
            for (int i = 0; i < accounts.size(); i++) {
                System.out.println((i + 1) + ". " + accounts.get(i).getName());
            }
            System.out.print("Enter the number of the account: ");

            String accountInput = scanner.nextLine().trim();
            int accountIndex = Integer.parseInt(accountInput);

            if(accountIndex == 0) {
                System.out.println("Lending payment cancelled.");
                return;
            }
            if (accountIndex < 1 || accountIndex > accounts.size()) {
                System.out.println("Invalid account selection.");
                return;
            }
            toAccount = accounts.get(accountIndex - 1);
        }

        //select ledger option can be added later
        System.out.println("Select a ledger to associate with this receiving:");
        List<Ledger> ledgers = ledgerController.getLedgersByUser(userController.getCurrentUser());
        if (ledgers.isEmpty()) {
            System.out.println("No available ledgers to select.");
            return;
        }

        for (int i = 0; i < ledgers.size(); i++) {
            System.out.println((i + 1) + ". " + ledgers.get(i).getName());
        }
        System.out.println("0. Cancel");
        System.out.print("Enter the number of the ledger: ");

        String ledgerInput = scanner.nextLine().trim();
        int ledgerIndex = Integer.parseInt(ledgerInput);

        if(ledgerIndex == 0) {
            System.out.println("Lending payment cancelled.");
            return;
        }
        if (ledgerIndex < 1 || ledgerIndex > ledgers.size()) {
            System.out.println("Invalid ledger selection.");
            return;
        }
        Ledger ledger = ledgers.get(ledgerIndex - 1);


        boolean success = accountController.receiveLending(lendingToReceivePayment, paymentAmount,
                 toAccount, ledger);

        if(!success) {
            System.out.println("Failed to receive lending payment.");
            return;
        }
        System.out.println("Lending payment received successfully.");
        showAllLendings();
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
        String name= scanner.nextLine().trim();
        if(name.isEmpty()) {
            System.out.println("Name cannot be empty. Please enter a valid name.");
            return inputName();
        }
        return name;
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
        List<Ledger> ledgers = ledgerController.getLedgersByUser(user);

        if(ledgers.isEmpty()) {
            System.out.println("No ledgers found for the user.");
            return null;
        }

        for (int i = 0; i < ledgers.size(); i++) {
            Ledger ledger = ledgers.get(i);
            System.out.println( (i + 1) + ". "+ "Name: " + ledger.getName());
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
