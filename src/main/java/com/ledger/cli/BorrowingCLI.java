package com.ledger.cli;

import com.ledger.business.AccountController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.Account;
import com.ledger.domain.BorrowingAccount;
import com.ledger.domain.Ledger;
import com.ledger.domain.LoanAccount;

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

        //enter amount
        System.out.println("Enter borrowing amount: ");
        BigDecimal amountInput =scanner.nextBigDecimal();

        //enter name
        System.out.println("Enter borrowing name: ");
        String nameInput = inputName();

        //enter note
        String noteInput = inputNote();

        //enter
        boolean includedInNetWorth = inputIncludedInNetWorth();
        boolean selectable = inputSelectable();

        //enter date
        System.out.print("Enter borrowing date (YYYY-MM-DD, e.g., 2024-12-31): ");
        LocalDate dateInput = inputBorrowingDate();

        //select to account
        System.out.println("Select an account that receives this borrowing:");
        List<Account> accounts = reportController.getAccountsNotHidden(userController.getCurrentUser()).stream()
                .filter(a -> !(a instanceof LoanAccount))
                .toList();
        for(int i=0;i<accounts.size();i++) {
            System.out.println((i+1)+". "+accounts.get(i).getName());
        }
        System.out.print("Enter the number corresponding to the account: ");
        System.out.println("0. Cancel");
        int accountIndex = scanner.nextInt() - 1;
        scanner.nextLine(); // Consume newline
        if(accountIndex == -1) {
            System.out.println("Borrowing addition cancelled.");
            return;
        }
        if(accountIndex < 0 || accountIndex >= accounts.size()) {
            System.out.println("Invalid account selection.");
            return;
        }
        Account selectedAccount = accounts.get(accountIndex);

        BorrowingAccount borrowingAccount = accountController.createBorrowingAccount(userController.getCurrentUser(), nameInput, amountInput, noteInput,
                includedInNetWorth, selectable, selectedAccount, dateInput);

        if(borrowingAccount == null) {
            System.out.println("Failed to add borrowing.");
            return;
        }
        System.out.println("Borrowing added successfully.");

    }

    public void showAllBorrowings() {
        System.out.println("\n === Showing all borrowings ===");

        List<BorrowingAccount> userBorrowings = reportController.getActiveBorrowingAccounts(userController.getCurrentUser());
        if(userBorrowings.isEmpty()){
            System.out.println("No active borrowings found.");
        }
        for(BorrowingAccount borrowing:userBorrowings){
            System.out.println("Name: " + borrowing.getName() +
                    ", Remaining Amount: " + borrowing.getRemainingAmount() +
                    ", Total Borrowing: " + borrowing.getBorrowingAmount() +
                    (borrowing.getNotes()!=null ? ", Note: " + borrowing.getNotes() : ", No Note"));
        }

    }

    public void editBorrowing() {
        System.out.println("\n === Editing a borrowing ===");

        //select borrowing to edit
        System.out.println("Select the borrowing to edit:");
        List<BorrowingAccount> userBorrowings = reportController.getActiveBorrowingAccounts(userController.getCurrentUser());
        for(int i=0;i<userBorrowings.size();i++){
            BorrowingAccount borrowing=userBorrowings.get(i);
            System.out.println((i+1) + ". " + "Name: " + borrowing.getName() +
                    ", Remaining Amount: " + borrowing.getRemainingAmount());
        }
        System.out.print("Select a borrowing by number: ");
        int borrowingIndex =scanner.nextInt() - 1;
        scanner.nextLine(); // Consume newline
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
        System.out.println("\n Do you want to change the borrowing name? (y/n): ");
        String changeNameInput = scanner.nextLine().trim().toLowerCase();
        String newName = null;
        if (changeNameInput.equals("y") || changeNameInput.equals("yes")) {
            System.out.print("Enter new borrowing name (current: " + borrowingToEdit.getName() + "): ");
            newName = inputName();
        }
        //
        System.out.println("Current amount: " + borrowingToEdit.getBorrowingAmount());
        System.out.println("\n Do you want to change amount? (y/n): ");
        String changeAmountInput = scanner.nextLine().trim().toLowerCase();
        BigDecimal newAmount = null;
        if (changeAmountInput.equals("y") || changeAmountInput.equals("yes")) {
            System.out.print("Enter new borrowing amount (current: " + borrowingToEdit.getBorrowingAmount() + "): ");
            newAmount = scanner.nextBigDecimal();
            scanner.nextLine(); // Consume newline
        }

        //
        System.out.println("Current note: " + (borrowingToEdit.getNotes() == null ? "No note" : borrowingToEdit.getNotes()));
        System.out.println("\n Do you want to change note? (y/n): ");
        String changeNoteInput = scanner.nextLine().trim().toLowerCase();
        String newNote = null;
        if (changeNoteInput.equals("y") || changeNoteInput.equals("yes")) {
            newNote = inputNote();
        }

        //
        System.out.println("Current inclusion in net worth: " + (borrowingToEdit.getIncludedInNetAsset() ? "Included" : "Not Included"));
        System.out.println("\n Do you want to change inclusion in net worth? (y/n): ");
        String changeIncludedInput = scanner.nextLine().trim().toLowerCase();
        Boolean newIncludedInNetWorth = null;
        if (changeIncludedInput.equals("y") || changeIncludedInput.equals("yes")) {
            newIncludedInNetWorth = inputIncludedInNetWorth();
        }
        //
        System.out.println("Current selectable status: " + (borrowingToEdit.getSelectable() ? "Selectable" : "Not Selectable"));
        System.out.println("\n Do you want to change selectable status? (y/n): ");
        String changeSelectableInput = scanner.nextLine().trim().toLowerCase();
        Boolean newSelectable = null;
        if (changeSelectableInput.equals("y") || changeSelectableInput.equals("yes")) {
            newSelectable = inputSelectable();
        }

        //
        System.out.println("Current ended status: " + (borrowingToEdit.getIsEnded() ? "Ended" : "Active"));
        System.out.println("\n Do you want to change ended status? (y/n): ");
        String changeEndedInput = scanner.nextLine().trim().toLowerCase();
        Boolean newIsEnded = null;
        if (changeEndedInput.equals("y") || changeEndedInput.equals("yes")) {
            System.out.print("Set as ended? (y/n): ");
            String endedInput = scanner.nextLine().trim().toLowerCase();
            if (endedInput.equals("y") || endedInput.equals("yes")) {
                newIsEnded = true;
            } else if (endedInput.equals("n") || endedInput.equals("no")) {
                newIsEnded = false;
            } else {
                System.out.println("Please enter 'y' for yes or 'n' for no.");
                return;
            }
        }

        boolean success = accountController.editBorrowingAccount(borrowingToEdit, newName, newAmount,
                newNote, newIncludedInNetWorth, newSelectable, newIsEnded);

        if (!success) {
            System.out.println("Failed to edit borrowing.");
            return;
        }
        System.out.println("Borrowing edited successfully.");

    }

    public void deleteBorrowing() {
       System.out.println("\n === Deleting a borrowing ===");

       //select borrowing to delete
        System.out.println("Select the borrowing to delete:");
        List<BorrowingAccount> userBorrowings = reportController.getActiveBorrowingAccounts(userController.getCurrentUser());
        for(int i=0;i<userBorrowings.size();i++){
            BorrowingAccount borrowing=userBorrowings.get(i);
            System.out.println((i+1) + ". " + "Name: " + borrowing.getName() +
                    ", Remaining Amount: " + borrowing.getRemainingAmount());
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
        boolean deleteTransactions = false;
        if (confirmInput.equals("y") || confirmInput.equals("yes")) {
            deleteTransactions = true;
        }

        boolean success = accountController.deleteAccount(borrowingToDelete, deleteTransactions);

        if (!success) {
            System.out.println("Failed to delete borrowing.");
            return;
        }
        System.out.println("Borrowing deleted successfully.");
    }

    public void makeBorrowingPayment() {
        System.out.println("\n === Making a borrowing payment ===");

        //select borrowing to make payment
        System.out.println("Select the borrowing to make a payment to:");
        List<BorrowingAccount> userBorrowings = reportController.getActiveBorrowingAccounts(userController.getCurrentUser());
        for(int i=0;i<userBorrowings.size();i++){
            BorrowingAccount borrowing=userBorrowings.get(i);
            System.out.println((i+1) + ". " + "Name: " + borrowing.getName() +
                    ", Remaining Amount: " + borrowing.getRemainingAmount());
        }
        System.out.print("Select a borrowing by number: ");
        int borrowingIndex =scanner.nextInt() - 1;
        scanner.nextLine(); // Consume newline
        if(borrowingIndex < 0 || borrowingIndex >= userBorrowings.size()) {
            System.out.println("Invalid borrowing selection.");
            return;
        }

        BorrowingAccount borrowingToPay = userBorrowings.get(borrowingIndex);

        //enter payment amount
        System.out.print("Enter payment amount: ");
        BigDecimal paymentAmount = scanner.nextBigDecimal();
        scanner.nextLine(); // Consume newline

        //select from account
        System.out.println("Do you want to select an account to make the payment from?");
        String selectAccountInput = scanner.nextLine().trim().toLowerCase();
        Account fromAccount = null;
        if (selectAccountInput.equals("y") || selectAccountInput.equals("yes")) {
            System.out.println("Select an account to make the payment from:");
            List<Account> accounts = reportController.getAccountsNotHidden(userController.getCurrentUser()).stream()
                    .filter(a -> !(a instanceof LoanAccount))
                    .toList();
            for (int i = 0; i < accounts.size(); i++) {
                System.out.println((i + 1) + ". " + accounts.get(i).getName());
            }
            System.out.println("0. Cancel");
            System.out.print("Enter the number corresponding to the account: ");
            int accountIndex = scanner.nextInt() - 1;
            if (accountIndex == -1) {
                System.out.println("Borrowing payment cancelled.");
                return;
            }
            scanner.nextLine(); // Consume newline
            if (accountIndex < 0 || accountIndex >= accounts.size()) {
                System.out.println("Invalid account selection.");
                return;
            }
            fromAccount = accounts.get(accountIndex);
        }

        System.out.print("DO you want to add this payment to a specific ledger? (y/n): ");
        String ledgerInput = scanner.nextLine().trim().toLowerCase();
        Ledger ledger = null;
        if (ledgerInput.equals("y") || ledgerInput.equals("yes")) {
            System.out.println("Select a ledger to associate with this payment:");
            List<Ledger> ledgers = reportController.getLedgerByUser(userController.getCurrentUser());
            for (int i = 0; i < ledgers.size(); i++) {
                System.out.println((i + 1) + ". " + ledgers.get(i).getName());
            }
            System.out.println("0. Cancel");
            System.out.print("Enter the number corresponding to the ledger: ");
            int ledgerIndex = scanner.nextInt() - 1;
            if (ledgerIndex == -1) {
                System.out.println("Borrowing payment cancelled.");
                return;
            }
            scanner.nextLine(); // Consume newline
            if (ledgerIndex < 0 || ledgerIndex >= ledgers.size()) {
                System.out.println("Invalid ledger selection.");
                return;
            }
            ledger = ledgers.get(ledgerIndex);
        }


        boolean success = accountController.payBorrowing(borrowingToPay, paymentAmount, fromAccount, ledger);

        if (!success) {
            System.out.println("Failed to make borrowing payment.");
            return;
        }
        System.out.println("Borrowing payment made successfully.");

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
        String name = scanner.nextLine().trim();
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
        LocalDate date = LocalDate.parse(dateInput);
        return date;

    }
}
