package com.ledger.cli;
import java.util.Scanner;

public class MainCLI {
    private final UserCLI userCLI;
    private final AccountCLI accountCLI;
    private final LedgerCLI ledgerCLI;
    private final TransactionCLI transactionCLI;
    private final BudgetCLI budgetCLI;
    private final LedgerCategoryCLI ledgerCategoryCLI;
    private final Scanner scanner = new Scanner(System.in);
    private boolean running = true;

    public MainCLI(UserCLI userCLI, AccountCLI accountCLI, LedgerCLI ledgerCLI,
                   TransactionCLI transactionCLI,
                   BudgetCLI budgetCLI,
                   LedgerCategoryCLI ledgerCategoryCLI) {
        this.userCLI = userCLI;
        this.accountCLI =  accountCLI;
        this.ledgerCLI = ledgerCLI;
        this.transactionCLI = transactionCLI;
        this.budgetCLI = budgetCLI;
        this.ledgerCategoryCLI = ledgerCategoryCLI;
    }

    public void run() {
        System.out.println("=== Welcome to Personal Accounting System ===");

        // user not logged in, show welcome menu
        while (running && !userCLI.isUserLoggedIn()) {
            showWelcomeMenu();
        }

        //user logged in, show main menu
        while (running && userCLI.isUserLoggedIn()) {
            showMainMenu();
        }

        System.out.println("Thank you for using Personal Accounting System!");
    }

    //private methods for menus
    private void showWelcomeMenu() {
        System.out.println("\n=== Welcome Menu ===");
        System.out.println("1. Login");
        System.out.println("2. Register");
        System.out.println("3. Exit");
        System.out.print("Choose an option: ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                userCLI.login();
                break;
            case "2":
                userCLI.register();
                break;
            case "3":
                running = false;
                break;
            default:
                System.out.println("Invalid option! Please choose 1, 2, or 3.");
                showWelcomeMenu();
        }
    }

    private void showMainMenu() {
        System.out.println("\n=== Main Menu ===");
        System.out.println("1. Account Management");
        System.out.println("2. Ledger Management");
        System.out.println("3. Transaction Management");
        System.out.println("4. Budget Management");
        System.out.println("5. Lending Management");
        System.out.println("6. Borrowing Management");
        System.out.println("7. Category Management");
        System.out.println("8. User Profile");
        System.out.println("9. Logout");
        System.out.print("Choose an option: ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                // account management menu
                showAccountMenu();
                break;
            case "2":
                // ledger management menu
                showLedgerMenu();
                break;
            case "3":
                // transaction management menu
                showTransactionMenu();
                break;
            case "4":
                // budget management menu
                showBudgetMenu();
                break;
//            case"5":
//                // lending management menu
//                showLendingMenu();
//                break;
//            case"6":
//                // borrowing management menu
//                showBorrowingMenu();
//                break;
            case "7":
                // category management menu
                showCategoryMenu();
                break;
//            case "8":
//                // installment management menu
//                showInstallmentMenu();
//                break;
//            case "9":
//                // reimbursement management menu
//                showReimbursementMenu();
//                break;
            case "8" :
                // user profile menu
                showUserMenu();
                break;
            case "9":
                // logout
                userCLI.logout();
                showWelcomeMenu();
                break;
            default:
                System.out.println("Invalid option! Please choose 1-9.");
                showMainMenu();
        }
    }

//    private void showReimbursementMenu() {
//        System.out.println("\n=== Reimbursement Management ===");
//        System.out.println("1. Create Reimbursement");
//        System.out.println("2. Claim Reimbursement");
//        System.out.println("3. Delete Reimbursement");
//        System.out.println("4. Edit Pending Reimbursement");
//        System.out.println("5. Show All Pending Reimbursements");
//        System.out.println("6. Show All Claimed Reimbursements");
//        System.out.println("7. Show Reimbursement Details");
//        System.out.println("8. Back to Main Menu");
//        System.out.print("Choose an option: ");
//
//        String choice = scanner.nextLine().trim();
//
//        switch (choice) {
//            case "1":
//                reimbursementCLI.create();
//                break;
//            case "2":
//                reimbursementCLI.claim();
//                break;
//            case "3":
//                reimbursementCLI.delete();
//                break;
//            case "4":
//                reimbursementCLI.edit();
//                break;
//            case "5":
//                reimbursementCLI.showAllPendingReimbursements();
//                break;
//            case "6":
//                reimbursementCLI.showAllClaimedReimbursements();
//                break;
//            case "7":
//                reimbursementCLI.showReimbursementDetails();
//                break;
//            case "8":
//                // go back to main menu
//                showMainMenu();
//                break;
//            default:
//                System.out.println("Invalid option! Please choose 1-8.");
//                showReimbursementMenu();
//        }
//    }

    private void showAccountMenu()  {
        System.out.println("\n=== Account Management ===");
        System.out.println("1. Create Account");
        System.out.println("2. View All Accounts"); //show all accounts
        System.out.println("3. Edit Account");
        System.out.println("4. Delete Account");
        System.out.println("5. Hide Account");
        System.out.println("6. Pay Debt of Credit Card");
        System.out.println("7. Pay Loan");
        System.out.println("8. Repay Borrowing");
        System.out.println("9. Receive Lending Payment");
        System.out.println("10. Show Account's Summary");
        System.out.println("11. Back to Main Menu");
        System.out.print("Choose an option: ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                accountCLI.createAccount();
                break;
            case "2":
                accountCLI.showAllAccounts();
                break;
            case "3":
                accountCLI.updateAccount();
                break;
            case "4":
                accountCLI.deleteAccount();
                break;
//            case "5":
//                accountCLI.hideAccount();
//                break;
//            case "6":
//                accountCLI.payDebt();
//                break;
//            case "7":
//                accountCLI.payLoan();
//                break;
//            case "8":
//                accountCLI.makeBorrowingPayment();
//                break;
//            case "9":
//                accountCLI.receiveLendingPayment();
//                break;
            case "10":
                accountCLI.viewAccountSummary();
                break;
            case "11":
                // go back to main menu
                showMainMenu();
                break;
            default:
                System.out.println("Invalid option! Please choose 1-11.");
                showAccountMenu();
        }
    }

//    private void showInstallmentMenu() {
//        System.out.println("\n=== Installment Plan Management ===");
//        System.out.println("1. Create Installment Plan");
//        System.out.println("2. View Installment Plans");
//        System.out.println("3. Update Installment Plan");
//        System.out.println("4. Delete Installment Plan");
//        System.out.println("5. Pay one period");
//        System.out.println("6. Show Installment Plan Details");
//        System.out.println("7. Back to Account Menu");
//        System.out.print("Choose an option: ");
//
//        String choice = scanner.nextLine().trim();
//
//        switch (choice) {
//            case "1":
//                installmentCLI.createInstallment();
//                break;
//            case "2":
//                installmentCLI.viewInstallments();
//                break;
//            case "3":
//                installmentCLI.editInstallmentPlan();
//                break;
//            case "4":
//                installmentCLI.deleteInstallment();
//                break;
//            case "5":
//                installmentCLI.payInstallment();
//                break;
//            case "6":
//                installmentCLI.showInstallmentDetails();
//                break;
//            case "7":
//                // go back to account menu
//                showAccountMenu();
//                break;
//            default:
//                System.out.println("Invalid option! Please choose 1-7.");
//                showInstallmentMenu();
//        }
//    }

    private void showLedgerMenu()  {
        System.out.println("\n=== Ledger Management ===");
        System.out.println("1. Create Ledger");
        System.out.println("2. View All Ledgers"); //show all ledgers
        System.out.println("3. Edit Ledger");
        System.out.println("4. Show Ledger's Summary");
        System.out.println("5. Copy Ledger");
        System.out.println("6. Delete Ledger");
        System.out.println("7. Category Settings");
        System.out.println("8. Add Transaction");
        System.out.println("9. Back to Main Menu");
        System.out.print("Choose an option: ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                ledgerCLI.createLedger();
                break;
            case "2":
                ledgerCLI.viewLedgers();
                break;
            case "3":
                ledgerCLI.renameLedger();
                break;
            case "4":
                ledgerCLI.viewLedgerSummary();
                break;
            case "5":
                ledgerCLI.copyLedger();
                break;
            case "6":
                ledgerCLI.deleteLedger();
                break;
            case "7":
                showCategoryMenu();
                break;
            case "8":
                transactionCLI.addTransaction();
                break;
            case "9":
                // go back to main menu
                showMainMenu();
                break;
            default:
                System.out.println("Invalid option! Please choose 1-8.");
                showLedgerMenu();
        }
    }

    private void showCategoryMenu()  {
        System.out.println("\n=== Category Management ===");
        System.out.println("1. Create");
        System.out.println("2. Rename");
        System.out.println("3. Promote category of secondo level to first level");
        System.out.println("4. Demote category of first level to second level");
        System.out.println("5. Change Parent Category of second level category");
        System.out.println("6. Delete");
        System.out.println("7. Back to Main Menu");
        System.out.print("Choose an option: ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                ledgerCategoryCLI.createCategory();
                break;
            case "2":
                ledgerCategoryCLI.renameCategory();
                break;
            case "3":
                ledgerCategoryCLI.promoteSubCategory();
                break;
            case "4":
                ledgerCategoryCLI.demoteCategory();
                break;
            case "5":
                ledgerCategoryCLI.changeParent();
                break;
            case "6":
                ledgerCategoryCLI.deleteCategory();
                break;
            case "7":
                // go back to main menu
                showMainMenu();
                break;
            default:
                System.out.println("Invalid option! Please choose 1-7.");
                showCategoryMenu();
        }
    }

    private void showTransactionMenu() {
        System.out.println("\n=== Transaction Management ===");
        System.out.println("1. Add Transaction");
        System.out.println("2. Delete Transaction");
        System.out.println("3. Edit Transaction");
        System.out.println("4. Back to Main Menu");
        System.out.print("Choose an option: ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                transactionCLI.addTransaction();
                break;
            case "2":
                transactionCLI.deleteTransaction();
                break;
            case "3":
                transactionCLI.editTransaction();
                break;
            case "4":
                // go back to main menu
                showMainMenu();
                break;
            default:
                System.out.println("Invalid option! Please choose 1-4.");
                showTransactionMenu();
        }
    }

    private void showUserMenu() {
        System.out.println("\n=== User Profile ===");
        System.out.println("1. View Profile");
        System.out.println("2. Change Password");
        System.out.println("3. Change Username");
        System.out.println("4. Back to Main Menu");
        System.out.print("Choose an option: ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                userCLI.showCurrentUser();
                //return;
                break;
            case "2":
                // change password
                userCLI.changePassword();
                //return;
                break;
            case "3":
                // change username
                userCLI.changeUsername();
                //return;
                break;
            case "4":
                // go back to main menu
                return;
            default:
                System.out.println("Invalid option! Please choose 1-3.");
                showUserMenu();
        }
    }

    private void showBudgetMenu()  {
        System.out.println("\n=== Budget Management ===");
        System.out.println("1. Show all Budgets");
        System.out.println("2. Edit Budget");
        System.out.println("3. Merge Budget");
        System.out.println("4. Back to Main Menu");
        System.out.print("Choose an option: ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                budgetCLI.showAllBudgets();
                break;
            case "2":
                budgetCLI.editBudget();
                break;
            case "3":
                budgetCLI.mergeBudgets();
                break;
            case "4":
                // go back to main menu
                showMainMenu();
                break;
            default:
                System.out.println("Invalid option! Please choose 1-4.");
                showBudgetMenu();
        }
    }

//    private void showLendingMenu() {
//        System.out.println("\n=== Lending Management ===");
//        System.out.println("1. Create Lending Record");
//        System.out.println("2. View All Lending Records");
//        System.out.println("3. Edit Lending Record");
//        System.out.println("4. Delete Lending Record");
//        System.out.println("5. Receive Lending Payment");
//        System.out.println("6. Back to Main Menu");
//        System.out.print("Choose an option: ");
//
//        String choice = scanner.nextLine().trim();
//
//        switch (choice) {
//            case "1":
//                lendingCLI.addLending();
//                break;
//            case "2":
//                lendingCLI.showAllLendings();
//                break;
//            case "3":
//                lendingCLI.editLending();
//                break;
//            case "4":
//                lendingCLI.deleteLending();
//                break;
//            case "5":
//                lendingCLI.receiveLendingPayment();
//                break;
//            case "6":
//                // go back to main menu
//                showMainMenu();
//                break;
//            default:
//                System.out.println("Invalid option! Please choose 1-6.");
//                showLendingMenu();
//        }
//    }
//
//    private void showBorrowingMenu()  {
//        System.out.println("\n=== Borrowing Management ===");
//        System.out.println("1. Create Borrowing Record");
//        System.out.println("2. View All Borrowing Records");
//        System.out.println("3. Edit Borrowing Record");
//        System.out.println("4. Delete Borrowing Record");
//        System.out.println("5. Repay Borrowing");
//        System.out.println("6. Back to Main Menu");
//        System.out.print("Choose an option: ");
//
//        String choice = scanner.nextLine().trim();
//
//        switch (choice) {
//            case "1":
//                borrowingCLI.addBorrowing();
//                break;
//            case "2":
//                borrowingCLI.showAllBorrowings();
//                break;
//            case "3":
//                borrowingCLI.editBorrowing();
//                break;
//            case "4":
//                borrowingCLI.deleteBorrowing();
//                break;
//            case "5":
//                borrowingCLI.makeBorrowingPayment();
//                break;
//            case "6":
//                // go back to main menu
//                showMainMenu();
//                break;
//            default:
//                System.out.println("Invalid option! Please choose 1-6.");
//                showBorrowingMenu();
//        }
//    }

}
