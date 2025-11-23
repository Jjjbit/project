package com.ledger.cli;

import com.ledger.business.AccountController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class BorrowingCLI {
    private final ReportController reportController;
    private final AccountController accountController;
    private final UserController userController;
    private final Scanner scanner = new Scanner(System.in);

    public BorrowingCLI(ReportController reportController, AccountController accountController,
                        UserController userController) {
        this.userController = userController;
        this.accountController = accountController;
        this.reportController = reportController;
    }

    public void addBorrowing() {
        System.out.println("\n === Adding a new borrowing ===");

        //enter ledger
        System.out.println("Select a ledger for this borrowing:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());

        //enter amount
        System.out.print("Enter borrowing amount: ");
        String amountStr = scanner.nextLine().trim();
        if(amountStr.isEmpty()) {
            System.out.println("Borrowing amount cannot be empty.");
            return;
        }
        BigDecimal amountInput= new BigDecimal(amountStr);

        //enter name
        System.out.print("Enter borrowing name: ");
        String nameInput = inputName();

        //enter note
        System.out.print("Enter borrowing note (optional): ");
        String noteInput = inputNote();

        //enter
        boolean includedInNetWorth = inputIncludedInNetWorth();
        boolean selectable = inputSelectable();

        //enter date
        System.out.print("Enter borrowing date (YYYY-MM-DD): ");
        LocalDate dateInput = inputBorrowingDate();

        //select to account
        System.out.println("Select an account that receives this borrowing:");
        Account selectedAccount;
        List<Account> accounts = reportController.getAccountsNotHidden(userController.getCurrentUser()).stream()
                .filter(Account::getSelectable)
                .toList();
        for(int i=0;i<accounts.size();i++) {
            System.out.println((i+1)+". "+accounts.get(i).getName());
        }
        System.out.println("0. To account is external (no account)");
        System.out.print("Enter the number corresponding to the account: ");
        String accountInput = scanner.nextLine().trim();
        int accountIndex = Integer.parseInt(accountInput) ;

        if (accountIndex == 0) {
            selectedAccount = null;
        } else if (accountIndex > 0 && accountIndex <= accounts.size()) {
            selectedAccount = accounts.get(accountIndex - 1);
        } else {
            System.out.println("Invalid account selection.");
            return;
        }

        BorrowingAccount borrowingAccount = accountController.createBorrowingAccount(userController.getCurrentUser(), nameInput, amountInput, noteInput,
                includedInNetWorth, selectable, selectedAccount, dateInput, selectedLedger);

        if(borrowingAccount == null) {
            System.out.println("Failed to add borrowing.");
            return;
        }
        System.out.println("Borrowing added successfully.");
        showBorrowingDetails(borrowingAccount);
    }

    public void showAllBorrowings() {
        System.out.println("\n === Showing all borrowings ===");

        List<BorrowingAccount> userBorrowings = reportController.getActiveBorrowingAccounts(userController.getCurrentUser());
        if(userBorrowings.isEmpty()){
            System.out.println("No active borrowings found.");
        }
        for(BorrowingAccount borrowing:userBorrowings){
            System.out.println("Name: " + borrowing.getName() +
                    " | Remaining Amount: " + borrowing.getRemainingAmount() +
                    " | Total Borrowing: " + borrowing.getBorrowingAmount() +
                    " | Included in Net Worth: " + (borrowing.getIncludedInNetAsset() ? "Yes" : "No") +
                    " | Status: " + (borrowing.getIsEnded() ? "Ended" : "Active") +
                    (borrowing.getNotes()!=null ? " | Note: " + borrowing.getNotes() : " | No Note"));
        }

    }

    public void editBorrowing() {
        System.out.println("\n === Editing a borrowing ===");

        //select borrowing to edit
        System.out.println("\nSelect the borrowing to edit:");
        List<BorrowingAccount> userBorrowings = reportController.getActiveBorrowingAccounts(userController.getCurrentUser());
        for(int i=0;i<userBorrowings.size();i++){
            BorrowingAccount borrowing=userBorrowings.get(i);
            System.out.println((i+1) + ". " + "Name: " + borrowing.getName() +
                    " | Total Borrowing: " + borrowing.getBorrowingAmount() +
                    " | Remaining Amount: " + borrowing.getRemainingAmount() +
                    " | Included in Net Worth: " + (borrowing.getIncludedInNetAsset() ? "Yes" : "No") +
                    " | Status: " + (borrowing.getIsEnded() ? "Ended" : "Active") +
                    (borrowing.getNotes()!=null ? " | Note: " + borrowing.getNotes() : " | No Note"));
        }
        System.out.println("0. Cancel");
        System.out.print("Select a borrowing by number: ");
        String borrowingInput = scanner.nextLine().trim();
        int borrowingIndex = Integer.parseInt(borrowingInput) - 1;
        if(borrowingIndex == -1) {
            System.out.println("Borrowing editing cancelled.");
            return;
        }
        if(borrowingIndex < 0 || borrowingIndex >= userBorrowings.size()) {
            System.out.println("Invalid borrowing selection.");
            return;
        }

        BorrowingAccount borrowingToEdit = userBorrowings.get(borrowingIndex);

        //enter new name
        System.out.println("Current name: " + borrowingToEdit.getName());
        System.out.print("Enter new name (press Enter to skip): ");
        String newNameInput = scanner.nextLine().trim();
        String newName = null;
        if (!newNameInput.isEmpty()) {
            newName = newNameInput;
        }

        //
        System.out.println("Current amount: " + borrowingToEdit.getBorrowingAmount());
        System.out.print("Enter new amount (press Enter to skip): ");
        String newAmountInput = scanner.nextLine().trim();
        BigDecimal newAmount = null;
        if (!newAmountInput.isEmpty()) {
            newAmount = new BigDecimal(newAmountInput);
        }

        //
        System.out.println("Current note: " + (borrowingToEdit.getNotes() == null ? "No note" : borrowingToEdit.getNotes()));
        System.out.print("Enter new note (press Enter to skip): ");
        String newNoteInput = scanner.nextLine().trim();
        String newNote = null;
        if (!newNoteInput.isEmpty()) {
            newNote = newNoteInput;
        }

        //
        System.out.println("Current inclusion in net worth: " + (borrowingToEdit.getIncludedInNetAsset() ? "Included" : "Not Included"));
        System.out.print("(press Enter to skip) include in net worth? (y/n): ");
        String changeInclusionInput = scanner.nextLine().trim().toLowerCase();
        Boolean newIncludedInNetWorth = null;
        if (changeInclusionInput.equals("y") || changeInclusionInput.equals("yes")) {
            newIncludedInNetWorth = true;
        } else if (changeInclusionInput.equals("n") || changeInclusionInput.equals("no")) {
            newIncludedInNetWorth = false;
        }

        //
        System.out.println("Current selectable status: " + (borrowingToEdit.getSelectable() ? "Selectable" : "Not Selectable"));
        System.out.print("(press Enter to skip) selectable? (y/n): ");
        String changeSelectableInput = scanner.nextLine().trim().toLowerCase();
        Boolean newSelectable = null;
        if (changeSelectableInput.equals("y") || changeSelectableInput.equals("yes")) {
            newSelectable = true;
        } else if (changeSelectableInput.equals("n") || changeSelectableInput.equals("no")) {
            newSelectable = false;
        }

        //
        System.out.println("Current ended status: " + (borrowingToEdit.getIsEnded() ? "Ended" : "Active"));
        System.out.print("(press Enter to skip) is ended? (y/n): ");
        String changeIsEndedInput = scanner.nextLine().trim().toLowerCase();
        Boolean newIsEnded = null;
        if (changeIsEndedInput.equals("y") || changeIsEndedInput.equals("yes")) {
            newIsEnded = true;
        } else if (changeIsEndedInput.equals("n") || changeIsEndedInput.equals("no")) {
            newIsEnded = false;
        }

        boolean success = accountController.editBorrowingAccount(borrowingToEdit, newName, newAmount,
                newNote, newIncludedInNetWorth, newSelectable, newIsEnded);

        if (!success) {
            System.out.println("Failed to edit borrowing.");
            return;
        }
        System.out.println("Borrowing edited successfully.");
        showBorrowingDetails(borrowingToEdit);
    }

    public void deleteBorrowing() {
       System.out.println("\n === Deleting a borrowing ===");

       //select borrowing to delete
        System.out.println("Select the borrowing to delete:");
        List<BorrowingAccount> userBorrowings = reportController.getActiveBorrowingAccounts(userController.getCurrentUser());
        for(int i=0;i<userBorrowings.size();i++){
            BorrowingAccount borrowing=userBorrowings.get(i);
            System.out.println((i+1) + ". " + "Name: " + borrowing.getName() +
                    " | Total Borrowing: " + borrowing.getBorrowingAmount() +
                    " | Remaining Amount: " + borrowing.getRemainingAmount() +
                    " | Included in Net Worth: " + (borrowing.getIncludedInNetAsset() ? "Yes" : "No") +
                    " | Status: " + (borrowing.getIsEnded() ? "Ended" : "Active") +
                    (borrowing.getNotes()!=null ? " | Note: " + borrowing.getNotes() : " | No Note"));
        }
        System.out.println("0. Cancel");
        System.out.print("Select a borrowing by number: ");
        int borrowingIndex =scanner.nextInt() - 1;
        scanner.nextLine(); // Consume newline
        if(borrowingIndex == -1) {
            System.out.println("Borrowing deletion cancelled.");
            return;
        }
        if(borrowingIndex < 0 || borrowingIndex >= userBorrowings.size()) {
            System.out.println("Invalid borrowing selection.");
            return;
        }

        BorrowingAccount borrowingToDelete = userBorrowings.get(borrowingIndex);
        //confirm delete transactions
        System.out.print("Are you sure you want to delete this borrowing and all its associated transactions? (y/n): ");
        String confirmInput = scanner.nextLine().trim().toLowerCase();

        boolean deleteTransactions = confirmInput.equals("y") || confirmInput.equals("yes");

        boolean success = accountController.deleteAccount(borrowingToDelete, deleteTransactions);

        if (!success) {
            System.out.println("Failed to delete borrowing.");
            return;
        }
        System.out.println("Borrowing deleted successfully.");
        showAllBorrowings();
    }

    public void makeBorrowingPayment() {
        System.out.println("\n === Making a borrowing payment ===");

        //select borrowing to make payment
        System.out.println("Select the borrowing to make a payment to:");
        List<BorrowingAccount> userBorrowings = reportController.getActiveBorrowingAccounts(userController.getCurrentUser());
        for(int i=0;i<userBorrowings.size();i++){
            BorrowingAccount borrowing=userBorrowings.get(i);
            System.out.println((i+1) + ". " + "Name: " + borrowing.getName() +
                    " | Total Borrowing: " + borrowing.getBorrowingAmount() +
                    ", Remaining Amount: " + borrowing.getRemainingAmount() +
                    " | Included in Net Worth: " + (borrowing.getIncludedInNetAsset() ? "Yes" : "No") +
                    " | Status: " + (borrowing.getIsEnded() ? "Ended" : "Active") +
                    (borrowing.getNotes()!=null ? " | Note: " + borrowing.getNotes() : " | No Note"));
        }

        System.out.println("0. Cancel");
        System.out.print("Select a borrowing by number: ");
        String borrowingInput = scanner.nextLine().trim();
        int borrowingIndex = Integer.parseInt(borrowingInput) - 1;
        if(borrowingIndex == -1) {
            System.out.println("Borrowing payment cancelled.");
            return;
        }
        if(borrowingIndex < 0 || borrowingIndex >= userBorrowings.size()) {
            System.out.println("Invalid borrowing selection.");
            return;
        }

        BorrowingAccount borrowingToPay = userBorrowings.get(borrowingIndex);

        //enter payment amount
        System.out.print("Enter payment amount: ");
        String paymentAmountStr = scanner.nextLine().trim();
        if(paymentAmountStr.isEmpty()) {
            System.out.println("Payment amount cannot be empty.");
            return;
        }
        BigDecimal paymentAmount= new BigDecimal(paymentAmountStr);

        //select from account
        System.out.print("Do you want to select an account to make the payment from? (y/n): ");
        String selectAccountInput = scanner.nextLine().trim().toLowerCase();
        Account fromAccount = null;
        if (selectAccountInput.equals("y") || selectAccountInput.equals("yes")) {
            System.out.println("Select an account to make the payment from:");
            List<Account> accounts = reportController.getAccountsNotHidden(userController.getCurrentUser()).stream()
                    .filter(Account::getSelectable)
                    .toList();
            for (int i = 0; i < accounts.size(); i++) {
                System.out.println((i + 1) + ". " + accounts.get(i).getName());
            }
            System.out.println("0. Cancel");
            System.out.print("Enter the number corresponding to the account: ");
            String accountInput = scanner.nextLine().trim();
            int accountIndex = Integer.parseInt(accountInput) - 1;

            if(accountIndex == -1) {
                System.out.println("Borrowing payment cancelled.");
                return;
            }
            if (accountIndex < 0 || accountIndex >= accounts.size()) {
                System.out.println("Invalid account selection.");
                return;
            }
            fromAccount = accounts.get(accountIndex);
        }

        System.out.println("Select a ledger to associate with this payment:");
        List<Ledger> ledgers = reportController.getLedgerByUser(userController.getCurrentUser());
        for (int i = 0; i < ledgers.size(); i++) {
            System.out.println((i + 1) + ". " + ledgers.get(i).getName());
        }
        System.out.println("0. Cancel");
        System.out.print("Enter the number corresponding to the ledger: ");
        String ledgerInput = scanner.nextLine().trim();
        int ledgerIndex = Integer.parseInt(ledgerInput);
        if (ledgerIndex == 0) {
            System.out.println("Borrowing payment cancelled.");
            return;
        }

        if (ledgerIndex < 1 || ledgerIndex > ledgers.size()) {
            System.out.println("Invalid ledger selection.");
            return;
        }
        Ledger ledger = ledgers.get(ledgerIndex-1);

        boolean success = accountController.payBorrowing(borrowingToPay, paymentAmount, fromAccount, ledger);

        if (!success) {
            System.out.println("Failed to make borrowing payment.");
            return;
        }
        System.out.println("Borrowing payment made successfully.");
        showBorrowingDetails(borrowingToPay);
    }

    private void showBorrowingDetails(BorrowingAccount borrowing) {
        System.out.print("Name: " + borrowing.getName() + " | Total Borrowing Amount: " + borrowing.getBorrowingAmount()
        + " | Remaining Amount: " + borrowing.getRemainingAmount()
        + " | Notes: " + (borrowing.getNotes() == null ? "No note" : borrowing.getNotes())
        + " | Included in Net Worth: " + (borrowing.getIncludedInNetAsset() ? "Yes" : "No")
        + " | Selectable: " + (borrowing.getSelectable() ? "Yes" : "No")
        + " | Status: " + (borrowing.getIsEnded() ? "Ended" : "Active"));
    }

    //input helpers
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

    private LocalDate inputBorrowingDate() {
        String dateInput = scanner.nextLine().trim();
        if(dateInput.isEmpty()) {
            return LocalDate.now();
        }
        if(!dateInput.matches("\\d{4}-\\d{2}-\\d{2}")) {
            System.out.println("Invalid date format. Please use YYYY-MM-DD.");
            return inputBorrowingDate();
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
}
