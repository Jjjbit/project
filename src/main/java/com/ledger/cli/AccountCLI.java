package com.ledger.cli;

import com.ledger.business.AccountController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class AccountCLI {
    private final AccountController accountController;
    private final UserController userController;
    private final ReportController reportController;
    private final Scanner scanner = new Scanner(System.in);

    public AccountCLI(AccountController accountController, UserController userController,
                      ReportController reportController) {
        this.reportController = reportController;
        this.accountController = accountController;
        this.userController = userController;
    }

    public void createAccount() {

        System.out.println("\n=== Create New Account ===");


        //choose account category
        AccountCategory category = selectAccountCategory();
        if (category == null) return;

        //choose account type
        AccountType type = selectAccountType(category);
        if (type == null) return;

        //input common account details
        String name = inputAccountName();

        BigDecimal balance = BigDecimal.ZERO;
        if(type != AccountType.LOAN){ //balance of LoanAccount  is 0
            balance = inputBalance();
        }

        boolean includedInNetWorth = inputIncludedInNetWorth();
        boolean selectable = inputSelectable();
        String note = inputNote();

        Account account = null;
        if (AccountCategory.CREDIT.equals(category)) {
            if (type == AccountType.LOAN) {
                account = createLoanAccount(name, includedInNetWorth, note);
            } else if(type == AccountType.CREDIT_CARD || type == AccountType.OTHER_CREDIT) {
                account = createCreditCardAccount(name, balance, type, includedInNetWorth, selectable, note);
            }else{
                account = createBasicAccount(name, balance, type, category, includedInNetWorth, selectable, note);
            }
        }

        if (account == null) {
            System.out.println(" Failed to create account.");
            return;
        }

        System.out.println(" Account created successfully: " + account.getName());

    }

    public void showAllAccounts() {
        System.out.println("\n=== Show All Accounts ===");

        List<Account> accounts = reportController.getAccountsNotHidden(userController.getCurrentUser());
        if (accounts.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }

        System.out.println("\n=== All Accounts ===");
        for (Account account : accounts) {
            System.out.println("- " + account.getName() );
            System.out.println("Type: " + account.getType());
            if(account instanceof LoanAccount){
                System.out.println("Remaining Loan Amount: " + ((LoanAccount) account).getRemainingAmount());
                System.out.println("Loan Amount: " + ((LoanAccount) account).getLoanAmount());
                System.out.println("Repaid Periods: " + ((LoanAccount) account).getRepaidPeriods() + "/" + ((LoanAccount) account).getTotalPeriods());
            }else if(account instanceof CreditAccount){
                System.out.println("Balance: " + account.getBalance());
                System.out.println("Current Debt: " + ((CreditAccount) account).getCurrentDebt());
                System.out.println("Credit Limit: " + ((CreditAccount) account).getCreditLimit());
            }else{
                System.out.println("Balance: " + account.getBalance());
            }
        }
    }

    public void updateAccount() {

        System.out.println("\n=== Update Account ===");

        System.out.println("Select the account to update:");
        Account accountToUpdate = selectAccount();
        if (accountToUpdate == null) {
            System.out.println("Account selection cancelled.");
            return;
        }

        //rename account
        System.out.println("Current name: " + accountToUpdate.getName() + ". Enter new name: (press Enter to skip) ");
        String newName = inputAccountName();

        //select included in net worth
        System.out.println("press Enter to skip ");
        Boolean newIncludedInNetWorth = inputIncludedInNetWorth();

        //update selectable and balance if not LoanAccount
        Boolean newSelectable = null;
        BigDecimal newBalance = null;
        if (!(accountToUpdate instanceof LoanAccount)) {
            System.out.println("press Enter to skip ");
            //update selectable
            newSelectable = inputSelectable();

            //update balance
            System.out.println("Current balance: " + accountToUpdate.getBalance() + ". press Enter to skip ");
            newBalance = inputBalance();
        }

        //update note
        System.out.println("Current note: " + accountToUpdate.getNotes() + "press Enter to skip ");
        String newNote = inputNote();

        //update based on account type
        if (accountToUpdate instanceof BasicAccount) {
            boolean success = accountController.editBasicAccount(
                    accountToUpdate,
                    newName,
                    newBalance,
                    newNote,
                    newIncludedInNetWorth,
                    newSelectable);
            if (!success) {
                System.out.println("✗ Failed to update account: " + accountToUpdate.getName());
                return;
            }
            System.out.println("✓ Account updated successfully: " + accountToUpdate.getName());
        } else if (accountToUpdate instanceof CreditAccount) {
            //update credit limit
            System.out.print("Current credit limit: " + ((CreditAccount) accountToUpdate).getCreditLimit());
            System.out.print("\nEnter new credit limit (or press Enter to skip): ");
            BigDecimal newCreditLimit = scanner.nextBigDecimal();
            scanner.nextLine();
            if (newCreditLimit.compareTo(BigDecimal.ZERO) < 0) {
                newCreditLimit = null;
            }
            //update current debt
            System.out.print("Current debt: " + ((CreditAccount) accountToUpdate).getCurrentDebt());
            System.out.print("\nEnter new current debt (or press Enter to skip: ");
            BigDecimal newCurrentDebt = scanner.nextBigDecimal();
            scanner.nextLine();
            if (newCurrentDebt.compareTo(BigDecimal.ZERO) < 0) {
                newCurrentDebt = null;
            }
            //update bill date
            System.out.print("Current bill day: " + ((CreditAccount) accountToUpdate).getBillDay());
            Integer newBillDate = inputOptionalInteger("Enter new bill day (1-31, optional, press Enter to skip): ", 1, 31);
            //update due date
            System.out.print("Current due day: " + ((CreditAccount) accountToUpdate).getDueDay());
            Integer newDueDate = inputOptionalInteger("Enter new due day (1-31, optional, press Enter to skip): ", 1, 31);

            boolean success = accountController.editCreditAccount(
                    accountToUpdate,
                    newName,
                    newBalance,
                    newNote,
                    newIncludedInNetWorth,
                    newSelectable,
                    newCreditLimit,
                    newCurrentDebt,
                    newBillDate,
                    newDueDate
            );
            if (!success) {
                System.out.println("✗ Failed to update account: " + accountToUpdate.getName());
                return;
            }
            System.out.println("✓ Account updated successfully: " + accountToUpdate.getName());

        } else if (accountToUpdate instanceof LoanAccount) {
            //update total periods
            System.out.print("Current total periods: " + ((LoanAccount) accountToUpdate).getTotalPeriods());
            System.out.print("\nEnter new total periods (or press Enter to skip): ");
            Integer newTotalPeriods = Integer.parseInt(scanner.nextLine().trim());

            //update repaid periods
            System.out.print("Current repaid periods: " + ((LoanAccount) accountToUpdate).getRepaidPeriods());
            System.out.print("\nEnter new repaid periods (or press Enter to skip): ");
            Integer newRepaidPeriods = Integer.parseInt(scanner.nextLine().trim());

            //update annual interest rate
            System.out.print("Current annual interest rate: " + ((LoanAccount) accountToUpdate).getAnnualInterestRate());
            System.out.print("\nEnter new annual interest rate (or press Enter to skip): ");
            BigDecimal newAnnualInterestRate = new BigDecimal(scanner.nextLine().trim());

            //update loan amount
            System.out.print("Current loan amount: " + ((LoanAccount) accountToUpdate).getLoanAmount());
            System.out.print("\nEnter new loan amount (or press Enter to skip): ");
            BigDecimal newLoanAmount = new BigDecimal(scanner.nextLine().trim());

            //update repayment date
            System.out.print("Current repayment date: " + ((LoanAccount) accountToUpdate).getRepaymentDay());
            LocalDate newRepaymentDate = inputRepaymentDate();

            //update repayment type
            System.out.print("Current repayment type: " + ((LoanAccount) accountToUpdate).getRepaymentType());
            LoanAccount.RepaymentType newRepaymentType = selectRepaymentType();

            boolean success = accountController.editLoanAccount(
                    accountToUpdate,
                    newName,
                    newNote,
                    newIncludedInNetWorth,
                    newTotalPeriods,
                    newRepaidPeriods,
                    newAnnualInterestRate,
                    newLoanAmount,
                    newRepaymentDate,
                    newRepaymentType
            );
            if (!success) {
                System.out.println("✗ Failed to update account: " + accountToUpdate.getName());
                return;
            }
            System.out.println("✓ Account updated successfully: " + accountToUpdate.getName());
        }
    }

    public void deleteAccount() {
        System.out.println("\n=== Delete Account ===");

        //select account to delete
        System.out.println("Select the account to delete:");
        Account accountToDelete = selectAccount();
        if( accountToDelete == null) {
            System.out.println("Account selection cancelled.");
            return;
        }

        //select if delete transactions linked to this account
        System.out.print("Also delete all transactions linked to this account? (y/n): ");
        String input = scanner.nextLine().trim().toLowerCase();
        boolean deleteTransactions;
        if (input.equals("y") || input.equals("yes")) {
            deleteTransactions = true;
        } else if (input.equals("n") || input.equals("no")) {
            deleteTransactions = false;
        } else {
            System.out.println("Please enter 'y' for yes or 'n' for no.");
            return;
        }
        boolean success = accountController.deleteAccount(accountToDelete, deleteTransactions);
        if (!success) {
            System.out.println("✗ Failed to delete account: " + accountToDelete.getName());
            return;
        }
        System.out.println("✓ Account deleted successfully: " + accountToDelete.getName());
    }

    public void hideAccount(){
        System.out.println("\n=== Hide Account ===");

        //select account to hide
        Account accountToHide = selectAccount();
        if( accountToHide == null) {
            System.out.println("Account selection cancelled.");
            return;
        }

        boolean success = accountController.hideAccount(accountToHide);
        if (!success) {
            System.out.println("✗ Failed to hide account: " + accountToHide.getName());
            return;
        }
        System.out.println("✓ Account hidden successfully: " + accountToHide.getName());

    }

    public void payDebt() {
        System.out.println("\n === Pay Debt ===");

        //select account to pay debt
        List<Account> creditAccounts = reportController.getAccountsNotHidden(userController.getCurrentUser()).stream()
                .filter(account -> account instanceof CreditAccount)
                .filter(account -> account.getType().equals(AccountType.CREDIT_CARD))
                .toList();
        if(creditAccounts.isEmpty()){
            System.out.println("No credit card accounts found.");
            return;
        }

        System.out.println("Select the account to pay debt from:");
        for (int i = 0; i < creditAccounts.size(); i++) {
            Account account = creditAccounts.get(i);
            System.out.println((i + 1) + ". " + account.getName() + " - Current Debt: " + ((CreditAccount) account).getCurrentDebt());
        }

        System.out.println("0. Cancel");
        System.out.print("Enter choice: ");

        int choice = scanner.nextInt();
        if(choice == 0){
            return;
        }
        if (choice < 1 || choice > creditAccounts.size()) {
            System.out.println("Invalid choice!");
            return ;
        }

        int accountIndex = choice - 1;
        Account accountToPayDebt = creditAccounts.get(accountIndex);
        System.out.println("Current debt: " + ((CreditAccount) accountToPayDebt).getCurrentDebt());

        //input payment amount
        System.out.print("Enter payment amount: ");
        BigDecimal paymentAmount = scanner.nextBigDecimal();
        scanner.nextLine(); // consume newline

        //select from account
        System.out.println("Do you want to select the from account? (y/n): ");
        String input = scanner.nextLine().trim().toLowerCase();
        Account fromAccount = null;
        if (input.equals("y") || input.equals("yes")) {
            fromAccount = selectAccount();
        }

        //select ledger
        System.out.println("Do you want to select the ledger for this payment? (y/n): ");
        input = scanner.nextLine().trim().toLowerCase();
        Ledger ledger = null;
        if (input.equals("y") || input.equals("yes")) {
            List<Ledger> userLedgers = reportController.getLedgerByUser(userController.getCurrentUser());
            if (userLedgers.isEmpty()) {
                System.out.println("No ledgers found. Please create a ledger first.");
                return;
            }
            System.out.println("Select the ledger:");
            for (int i = 0; i < userLedgers.size(); i++) {
                Ledger ledgerOption = userLedgers.get(i);
                System.out.println((i + 1) + ". " + ledgerOption.getName());
            }
            System.out.println("0. Cancel");
            System.out.print("Enter choice: ");
            int ledgerChoice = scanner.nextInt();
            scanner.nextLine(); // consume newline
            if(ledgerChoice == 0){
                return;
            }
            if (ledgerChoice < 1 || ledgerChoice > userLedgers.size()) {
                System.out.println("Invalid choice!");
                return;
            }
            ledger = userLedgers.get(ledgerChoice - 1);
        }

        boolean success = accountController.repayDebt(accountToPayDebt, paymentAmount, fromAccount, ledger);
        if (!success) {
            System.out.println("✗ Debt payment failed for account: " + accountToPayDebt.getName());
            return;
        }
        System.out.println("✓ Debt payment successful for account: " + accountToPayDebt.getName());

    }

    public void payLoan() {
        System.out.println("\n === Pay Loan ===");

        //select account to pay loan
        List<Account> loanAccounts = reportController.getAccountsNotHidden(userController.getCurrentUser()).stream()
                .filter(account -> account instanceof LoanAccount)
                .toList();
        if(loanAccounts.isEmpty()){
            System.out.println("No loan accounts found.");
            return;
        }
        System.out.println("Select the Loan:");
        for (int i = 0; i < loanAccounts.size(); i++) {
            Account account = loanAccounts.get(i);
            System.out.println((i + 1) + ". " + account.getName() + " - Remaining Loan Amount: " + ((LoanAccount) account).getRemainingAmount());
        }
        System.out.println("0. Cancel");
        System.out.print("Enter choice: ");
        int loanChoice= scanner.nextInt() -1;
        if(loanChoice == 0){
            return;
        }
        if( loanChoice < 0 || loanChoice > loanAccounts.size()) {
            System.out.println("Invalid choice!");
            return ;
        }
        LoanAccount accountToPayLoan = (LoanAccount) loanAccounts.get(loanChoice);

        System.out.println("Do you want to select the from account? (y/n): ");
        String input = scanner.nextLine().trim().toLowerCase();
        Account fromAccount = null;
        if (input.equals("y") || input.equals("yes")) {
            System.out.println("Select the account to pay loan from:");
            fromAccount = selectAccount();
        }

        //select ledger
        System.out.println("Do you want to select the ledger for this payment? (y/n): ");
        input = scanner.nextLine().trim().toLowerCase();
        Ledger ledger = null;
        if (input.equals("y") || input.equals("yes")) {
            List<Ledger> userLedgers = reportController.getLedgerByUser(userController.getCurrentUser());
            if (userLedgers.isEmpty()) {
                System.out.println("No ledgers found. Please create a ledger first.");
                return;
            }
            System.out.println("Select the ledger:");
            for (int i = 0; i < userLedgers.size(); i++) {
                Ledger ledgerOption = userLedgers.get(i);
                System.out.println((i + 1) + ". " + ledgerOption.getName());
            }
            System.out.println("0. Cancel");
            System.out.print("Enter choice: ");
            int ledgerChoice = scanner.nextInt()-1;
            scanner.nextLine(); // consume newline
            if(ledgerChoice == 0){
                return;
            }
            if (ledgerChoice < 0 || ledgerChoice > userLedgers.size()) {
                System.out.println("Invalid choice!");
                return;
            }
            ledger = userLedgers.get(ledgerChoice );
        }


        boolean success = accountController.repayLoan(accountToPayLoan, fromAccount, ledger);
        if (!success) {
            System.out.println("✗ Loan payment failed for account: " + accountToPayLoan.getName());
            return;
        }
        System.out.println("✓ Loan payment successful for account: " + accountToPayLoan.getName());
    }

    public void viewAccountSummary() {
        System.out.println("\n=== Account Summary ===");

        //select Account
        Account account = selectAccount(); //select BasicAccount, LoanAccount, CreditAccount
        if( account == null) {
            System.out.println("Account selection cancelled.");
            return;
        }

        //select date range
        System.out.println("Enter start date (YYYY-MM-DD): ");
        String startDateInput = scanner.nextLine().trim();
        LocalDate startDate;
        if(startDateInput.isEmpty()) {
            startDate = LocalDate.now().withDayOfMonth(1); //default to first day of current month
        }else{
            startDate = LocalDate.parse(startDateInput);
        }

        System.out.println("Enter end date (YYYY-MM-DD): ");
        String endDateInput = scanner.nextLine().trim();
        LocalDate endDate;
        if(endDateInput.isEmpty()) {
            endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        }else{
            endDate = LocalDate.parse(endDateInput);
        }

        List<Transaction> transactions = reportController.getTransactionsByAccountInRangeDate(account, startDate, endDate);
        BigDecimal totalIncome = reportController.getTotalIncomeByAccount(account, startDate, endDate);
        BigDecimal totalExpense = reportController.getTotalExpenseByAccount(account, startDate, endDate);
        System.out.println("\nAccount: " + account.getName());
        System.out.println("\nDate Range: " + startDate + " to " + endDate);
        System.out.println("\nTotal Income: " + totalIncome);
        System.out.println("Total Expense: " + totalExpense);


        if(account instanceof BasicAccount || account instanceof CreditAccount){
            BigDecimal totalBalance = account.getBalance();
            System.out.println("\nCurrent Balance: " + totalBalance);
        }

        if(account instanceof LoanAccount) {
            BigDecimal totalLoanAmount = ((LoanAccount) account).getLoanAmount();
            BigDecimal totalRepaymentAmount = ((LoanAccount) account).calculateTotalRepayment();
            BigDecimal totalInterest = totalRepaymentAmount.subtract(totalLoanAmount);
            BigDecimal totalLoanRemaining = ((LoanAccount) account).getRemainingAmount();
            boolean isEnded = ((LoanAccount) account).getIsEnded();
            System.out.println("\nTotal Loan Amount: " + totalLoanAmount);
            System.out.println("Total Repayment Amount: " + totalRepaymentAmount);
            System.out.println("Total Interest: " + totalInterest);
            System.out.println("Remaining Loan Amount: " + totalLoanRemaining);
            System.out.println("Loan Status: " + (isEnded ? "Ended" : "Active"));
        }

        if(account instanceof CreditAccount) {
            BigDecimal creditLimit = ((CreditAccount) account).getCreditLimit();
            BigDecimal currentDebt = ((CreditAccount) account).getCurrentDebt();
            System.out.println("\nCredit Limit: " + creditLimit);
            System.out.println("Current Debt: " + currentDebt);
        }

        System.out.println("\nTransactions:");
        for(Transaction tx : transactions) {
            System.out.println("Date: " + tx.getDate() + ", Type: " + tx.getType() + ", Amount: " + tx.getAmount() +
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

    }

    //private helper methods for input and selection
    private AccountCategory selectAccountCategory() {
        System.out.println("\nSelect account category:");

        AccountCategory[] categories = Arrays.stream(AccountCategory.values())
                .filter(c -> c != AccountCategory.VIRTUAL_ACCOUNT)
                .toArray(AccountCategory[]::new);

        for (int i = 0; i < categories.length; i++) {
            System.out.println((i + 1) + ". " + formatCategoryName(categories[i]));
        }
        System.out.println("0. Cancel");
        System.out.print("Enter choice: ");

        try {
            int choice = scanner.nextInt();
            scanner.nextLine(); // consume newline

            if (choice == 0) return null;
            if (choice < 1 || choice > categories.length) {
                System.out.println("Invalid choice!");
                return selectAccountCategory();
            }

            return categories[choice - 1];
        } catch (Exception e) {
            scanner.nextLine(); // clear invalid input
            System.out.println("Invalid input! Please enter a number.");
            return selectAccountCategory();
        }
    }

    private AccountType selectAccountType(AccountCategory category) {
        System.out.println("\nSelect account type for " + formatCategoryName(category) + ":");
        AccountType[] availableTypes = getAvailableAccountTypes(category);

        for (int i = 0; i < availableTypes.length; i++) {
            System.out.println((i + 1) + ". " + formatTypeName(availableTypes[i]));
        }
        System.out.println("0. Back");
        System.out.print("Enter choice: ");

        try {
            int choice = scanner.nextInt();
            scanner.nextLine(); // consume newline

            if (choice == 0) return null;
            if (choice < 1 || choice > availableTypes.length) {
                System.out.println("Invalid choice!");
                return selectAccountType(category);
            }

            return availableTypes[choice - 1];
        } catch (Exception e) {
            scanner.nextLine(); // clear invalid input
            System.out.println("Invalid input! Please enter a number.");
            return selectAccountType(category);
        }
    }

    private String inputAccountName() {
        System.out.print("\nEnter account name: ");
        String name = scanner.nextLine().trim();
        if( name.isEmpty()) {
            System.out.println("Account name cannot be empty!");
            return inputAccountName();
        }
        return name;
    }

    private BigDecimal inputBalance() {
        System.out.print("Enter balance: ");
        try {
            BigDecimal balance = scanner.nextBigDecimal();
            scanner.nextLine(); // consume newline
            return balance;
        } catch (Exception e) {
            scanner.nextLine(); // clear invalid input
            System.out.println("Invalid amount! Please enter a valid number.");
            return inputBalance();
        }
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

    private String inputNote() {
        System.out.print("Enter note (press Enter to skip): ");
        String note = scanner.nextLine().trim();
        return note.isEmpty() ? null : note;
    }
    private Integer inputOptionalInteger(String prompt, int min, int max) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return null;
        }

        try {
            int value = Integer.parseInt(input);
            if (value < min || value > max) {
                System.out.println("Value must be between " + min + " and " + max + ".");
                return inputOptionalInteger(prompt, min, max);
            }
            return value;
        } catch (Exception e) {
            System.out.println("Invalid number! Please enter a valid integer.");
            return inputOptionalInteger(prompt, min, max);
        }
    }
    private LocalDate inputRepaymentDate() {
        System.out.print("Enter repayment date (YYYY-MM-DD, e.g., 2024-12-31): ");
        String dateInput = scanner.nextLine().trim();
        return LocalDate.parse(dateInput);
    }

    private Account selectAccount() {
        List<Account> userAccounts = reportController.getAccountsNotHidden(userController.getCurrentUser());

        if (userAccounts.isEmpty()) {
            System.out.println("No accounts found. Please create an account first.");
            return null;
        }

        for (int i = 0; i < userAccounts.size(); i++) {
            Account account = userAccounts.get(i);
            System.out.println((i + 1) + ". " + account.getName() + " (" + account.getType() + ")");
        }
        System.out.println("0. Cancel");
        System.out.print("Enter choice: ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline

        if (choice == 0) return null;
        if (choice < 1 || choice > userAccounts.size()) {
            System.out.println("Invalid choice!");
            return selectAccount();
        }

        return userAccounts.get(choice - 1);
    }

    private LoanAccount.RepaymentType selectRepaymentType() {
        System.out.println("\nSelect repayment type:");
        LoanAccount.RepaymentType[] types = LoanAccount.RepaymentType.values();
        for (int i = 0; i < types.length; i++) {
            System.out.println((i + 1) + ". " + formatRepaymentType(types[i]));
        }
        System.out.println("0. Cancel");
        System.out.print("Enter choice: ");

        try {
            int choice = scanner.nextInt();
            scanner.nextLine(); // consume newline

            if (choice == 0) return null;
            if (choice < 1 || choice > types.length) {
                System.out.println("Invalid choice!");
                return selectRepaymentType();
            }

            return types[choice - 1];
        } catch (Exception e) {
            scanner.nextLine(); // clear invalid input
            System.out.println("Invalid input! Please enter a number.");
            return selectRepaymentType();
        }
    }

    private CreditAccount createCreditCardAccount(String name, BigDecimal balance,
                                                  AccountType type, boolean includedInNetWorth,
                                                  boolean selectable, String note) {

        System.out.print("Enter credit limit: ");
        BigDecimal creditLimit = scanner.nextBigDecimal();
        scanner.nextLine();

        System.out.print("Enter current debt (press Enter for 0): ");
        String debtInput = scanner.nextLine();
        BigDecimal currentDebt = debtInput.isEmpty() ? BigDecimal.ZERO : new BigDecimal(debtInput);

        Integer billDay = inputOptionalInteger("Enter bill day (1-31, optional, press Enter to skip): ", 1, 31);
        Integer dueDay = inputOptionalInteger("Enter due day (1-31, optional, press Enter to skip): ", 1, 31);

        return accountController.createCreditAccount(
                name,
                note,
                balance,
                includedInNetWorth,
                selectable,
                userController.getCurrentUser(),
                type,
                creditLimit,
                currentDebt,
                billDay,
                dueDay
        );
    }

    private LoanAccount createLoanAccount(String name, boolean includedInNetWorth, String note) {

        //enter ledger
        System.out.println("Enter ledger for this loan account:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger == null) {
            System.out.println("Ledger selection cancelled.");
            return null;
        }

        System.out.print("Enter loan amount: ");
        BigDecimal loanAmount = new BigDecimal(scanner.nextLine().trim());

        System.out.print("Enter annual interest rate (%, optional, press Enter for 0): ");
        String interestInput = scanner.nextLine().trim();
        BigDecimal interestRate = interestInput.isEmpty() ? BigDecimal.ZERO : new BigDecimal(interestInput);

        System.out.print("Enter total repayment periods: ");
        int totalPeriods;
        try {
            totalPeriods = Integer.parseInt(scanner.nextLine().trim());
        } catch (Exception e) {
            System.out.println("Invalid number of periods! Please enter a valid integer.");
            return createLoanAccount(name, includedInNetWorth, note);
        }

        System.out.print("Enter repaid periods (optional, press Enter for 0): ");
        String repaidInput = scanner.nextLine().trim();
        int repaidPeriods = repaidInput.isEmpty() ? 0 : Integer.parseInt(repaidInput);

        System.out.println("Select the account to receive the loan amount:");
        Account receivingAccount = selectAccount();

        LocalDate repaymentDay = inputRepaymentDate();

        LoanAccount.RepaymentType repaymentType = selectRepaymentType();
        if (repaymentType == null) {
            System.out.println("Repayment type selection cancelled.");
            return null;
        }

        return accountController.createLoanAccount(name, note, includedInNetWorth, userController.getCurrentUser(),
                totalPeriods, repaidPeriods, interestRate, loanAmount, receivingAccount, repaymentDay, repaymentType, selectedLedger);
    }

    private Account createBasicAccount(String name, BigDecimal balance, AccountType type,
                                       AccountCategory category, boolean includedInNetWorth, boolean selectable,
                                       String note) {
        return accountController.createBasicAccount(
                name,
                balance,
                type,
                category,
                userController.getCurrentUser(),
                note,
                includedInNetWorth,
                selectable

        );
    }


    private AccountType[] getAvailableAccountTypes(AccountCategory category) {
        switch (category) {
            case FUNDS:
                return new AccountType[]{
                        AccountType.CASH, AccountType.DEBIT_CARD, AccountType.PASSBOOK,
                        AccountType.PAYPAL, AccountType.PENSION, AccountType.OTHER_FUNDS
                };
            case CREDIT:
                return new AccountType[]{
                        AccountType.CREDIT_CARD, AccountType.LOAN, AccountType.OTHER_CREDIT
                };
            case RECHARGE:
                return new AccountType[]{
                        AccountType. MOBILE_RECHARGE, AccountType.FUND, AccountType.APPLE_ID, AccountType.OTHER_RECHARGE
                };
            case INVEST:
                return new AccountType[]{
                        AccountType.INVESTMENT, AccountType.STOCKS, AccountType.FUND,
                        AccountType.GOLD, AccountType.INSURANCE,
                        AccountType.FUTURES, AccountType.CRYPTO,
                        AccountType.FIXED_DEPOSIT, AccountType.OTHER_INVEST
                };
            default:
                return AccountType.values();
        }
    }

    private String formatCategoryName(AccountCategory category) {
        return category.name().charAt(0) + category.name().substring(1).toLowerCase().replace("_", " ");
    }

    private String formatTypeName(AccountType type) {
        return type.name().charAt(0) + type.name().substring(1).toLowerCase().replace("_", " ");
    }
    private String formatRepaymentType(LoanAccount.RepaymentType type) {
        switch (type) {
            case EQUAL_INTEREST:
                return "Equal Interest";
            case EQUAL_PRINCIPAL:
                return "Equal Principal";
            case EQUAL_PRINCIPAL_AND_INTEREST:
                return "Equal Principal and Interest";
            case INTEREST_BEFORE_PRINCIPAL:
                return "Interest Before Principal";
            default:
                return type.name().replace("_", " ");
        }
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
