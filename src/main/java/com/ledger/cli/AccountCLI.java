package com.ledger.cli;

import com.ledger.business.*;
import com.ledger.domain.*;

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
//        if(type != AccountType.LOAN && type != AccountType.BORROWING){
//            System.out.print("Enter initial balance: ");
//            String balanceInput = scanner.nextLine().trim();
//            balance = balanceInput.isEmpty() ? BigDecimal.ZERO : new BigDecimal(balanceInput);
//        }

        boolean includedInNetWorth = inputIncludedInNetWorth();
        boolean selectable = inputSelectable();

//        System.out.print("Enter note (optional, press Enter to skip): ");
//        String note = inputNote();

        Account account = accountController.createAccount(name, balance, userController.getCurrentUser(), includedInNetWorth, selectable);
//        if (AccountCategory.CREDIT.equals(category)) {
//            if (type == AccountType.LOAN) {
//                account = createLoanAccount(name, includedInNetWorth, note);
//            } else if(type == AccountType.CREDIT_CARD || type == AccountType.OTHER_CREDIT) {
//                account = createCreditCardAccount(name, balance, type, includedInNetWorth, selectable, note);
//            }
//        }else if(AccountCategory.VIRTUAL_ACCOUNT.equals(category)){
//            if(type == AccountType.BORROWING){
//                account = createBorrowingAccount(name, includedInNetWorth, note, selectable);
//            }else{
//                account = createLendingAccount(name, includedInNetWorth, note, selectable, balance);
//            }
//        }
//        else{
//            account = createBasicAccount(name, balance, type, category, includedInNetWorth, selectable, note);
//        }
        if (account == null) {
            System.out.println(" Failed to create account.");
            return;
        }

        System.out.println(" Account created successfully: " + account.getName());
        showAllAccounts();
    }

    public void showAllAccounts() {
        System.out.println("\n=== Show All Accounts ===");

        List<Account> accounts = accountController.getAccounts(userController.getCurrentUser()); //select visible BasicAccount, LoanAccount, CreditAccount
        if (accounts.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }

        for (Account account : accounts) {
            System.out.print("- " + account.getName() + " |  Balance: " + account.getBalance() + " | Included in Net Worth: " + (account.getIncludedInNetAsset() ? "Yes" : "No") + " | Selectable: " + (account.getSelectable() ? "Yes" : "No"));
//            System.out.print("Category: "+ account.getCategory() + " | Type: " + account.getType() + " | Included in Net Worth: " + (account.getIncludedInNetAsset() ? "Yes" : "No") + " | Selectable: " + (account.getSelectable() ? "Yes" : "No"));
//            if(account instanceof BasicAccount){
//                System.out.print(" | Balance: " + account.getBalance());
//            }
//            if(account instanceof LoanAccount){
//                System.out.print(" | Remaining Loan Amount: " + ((LoanAccount) account).getRemainingAmount());
//                System.out.print(" | Loan Amount: " + ((LoanAccount) account).getLoanAmount());
//                System.out.print(" | Repaid Periods: " + ((LoanAccount) account).getRepaidPeriods() + "/" + ((LoanAccount) account).getTotalPeriods());
//            }
//            if(account instanceof CreditAccount){
//                System.out.print(" | Balance: " + account.getBalance());
//                System.out.print(" | Current Debt: " + ((CreditAccount) account).getCurrentDebt());
//                System.out.print(" | Credit Limit: " + ((CreditAccount) account).getCreditLimit());
//            }
//            if(account instanceof BorrowingAccount){
//                System.out.print(" | Remaining Amount: " + ((BorrowingAccount) account).getRemainingAmount());
//                System.out.print(" | Borrowing Amount: " + ((BorrowingAccount) account).getBorrowingAmount());
//                System.out.print(" | is Ended: " + (((BorrowingAccount) account).getIsEnded() ? "Yes" : "No"));
//            }
//            if(account instanceof LendingAccount){
//                System.out.print(" | Balance: " + account.getBalance());
//                System.out.print(" | is Ended: " + (((LendingAccount) account).getIsEnded() ? "Yes" : "No"));
//            }
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
            newName = null; //no change
        }

        //select included in net worth
        String includeInNetWorthProm = accountToUpdate.getIncludedInNetAsset() ? "Current: included in net worth. " : "Current: not included in net worth. ";
        System.out.print(includeInNetWorthProm + "(press Enter to skip). Include in net worth? (y/n): ");
        String input = scanner.nextLine().trim().toLowerCase();
        Boolean newIncludedInNetWorth = null;
        if (input.equals("y") || input.equals("yes")) {
            newIncludedInNetWorth = true;
        } else if (input.equals("n") || input.equals("no")) {
            newIncludedInNetWorth = false;
        }

        //update selectable and balance
        Boolean newSelectable = null;
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
//        if (!(accountToUpdate instanceof LoanAccount)) {
//            String selectableProm = accountToUpdate.getSelectable() ? "Current: selectable. " : "Current: not selectable. ";
//            System.out.print(selectableProm + "(press Enter to skip). Selectable? (y/n): ");
//            input = scanner.nextLine().trim().toLowerCase();
//            //update selectable
//            if(input.equals("y") || input.equals("yes")) {
//                newSelectable = true;
//            } else if (input.equals("n") || input.equals("no")) {
//                newSelectable = false;
//            }
//            if(!(accountToUpdate instanceof BorrowingAccount)){
//                //update balance
//                System.out.print("Current balance: " + accountToUpdate.getBalance() + ". (press Enter to skip) Enter new balance: ");
//                String balanceInput = scanner.nextLine().trim();
//                if (!balanceInput.isEmpty()) {
//                    newBalance = new BigDecimal(balanceInput);
//                    if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
//                        newBalance = null;
//                    }
//                }
//            }
        }else{
            newBalance = accountToUpdate.getBalance(); //no change
        }

        //update note
//        String notePrompt = accountToUpdate.getNotes() != null ? "Current note: " + accountToUpdate.getNotes() : "No current note.";
//        System.out.print(notePrompt + " Do you want to update the note? (y/n): ");
//        String newNoteInput = scanner.nextLine().trim().toLowerCase();
//        String newNote;
//        if( newNoteInput.equals("y") || newNoteInput.equals("yes")) {
//            System.out.print("Enter new note (or press Enter to clear): ");
//            String noteContent = scanner.nextLine().trim();
//            if (noteContent.isEmpty()) {
//                newNote = null;
//            } else {
//                newNote = noteContent;
//            }
//        } else {
//            newNote = accountToUpdate.getNotes();
//        }

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

        //select if delete transactions linked to this account
//        System.out.print("Also delete all transactions linked to this account? (y/n): ");
//        String input = scanner.nextLine().trim().toLowerCase();
//        boolean deleteTransactions;
//        if (input.equals("y") || input.equals("yes")) {
//            deleteTransactions = true;
//        } else if (input.equals("n") || input.equals("no")) {
//            deleteTransactions = false;
//        } else {
//            System.out.println("Please enter 'y' for yes or 'n' for no.");
//            return;
//        }

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

//        if(account instanceof BasicAccount || account instanceof CreditAccount || account instanceof LendingAccount) {
//            BigDecimal totalBalance = account.getBalance();
//            System.out.print(" | Current Balance: " + totalBalance +"\n");
//        }
//
//        if(account instanceof LoanAccount) {
//            BigDecimal totalLoanAmount = ((LoanAccount) account).getLoanAmount();
//            BigDecimal totalRepaymentAmount = ((LoanAccount) account).calculateTotalRepayment();
//            BigDecimal totalInterest = totalRepaymentAmount.subtract(totalLoanAmount);
//            BigDecimal totalLoanRemaining = ((LoanAccount) account).getRemainingAmount();
//            boolean isEnded = ((LoanAccount) account).getIsEnded();
//            System.out.print(" | Total Loan Amount: " + totalLoanAmount + "| Total Repayment Amount: " + totalRepaymentAmount +
//                    " | Total Interest: " + totalInterest + " | Remaining Loan Amount: " + totalLoanRemaining +
//                    " | Loan Status: " + (isEnded ? "Ended" : "Active") +"\n");
//        }
//
//        if(account instanceof CreditAccount) {
//            BigDecimal creditLimit = ((CreditAccount) account).getCreditLimit();
//            BigDecimal currentDebt = ((CreditAccount) account).getCurrentDebt();
//            System.out.print(" | Credit Limit: " + creditLimit + " | Current Debt: " + currentDebt+"\n");
//        }
//
//        if(account instanceof BorrowingAccount) {
//            BigDecimal borrowingAmount = ((BorrowingAccount) account).getBorrowingAmount();
//            BigDecimal remainingAmount = ((BorrowingAccount) account).getRemainingAmount();
//            boolean isEnded = ((BorrowingAccount) account).getIsEnded();
//            System.out.print(" | Borrowing Amount: " + borrowingAmount + " | Remaining Amount: " + remainingAmount +
//                    " | Borrowing Status: " + (isEnded ? "Ended" : "Active") +"\n");
//        }
//        if(account instanceof LendingAccount) {
//            System.out.print(" | Lending Status: " + ( ((LendingAccount) account).getIsEnded() ? "Ended" : "Active") +"\n");
//        }

        int i = 0;
        System.out.println("Transactions for Account " + account.getName() + ":");
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
//    private LocalDate inputDate() {
//        String inputDate = scanner.nextLine().trim();
//        if (inputDate.isEmpty()) {
//            return LocalDate.now();
//        }
//        if(!inputDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
//            System.out.println("Invalid date format. Please use YYYY-MM-DD.");
//            return inputDate();
//        }
//        return LocalDate.parse(inputDate);
//    }

//    private Account createBorrowingAccount(String name, boolean includedInNetWorth, String note, boolean selectable) {
//        System.out.print("Enter borrowing amount: ");
//        BigDecimal amount = new BigDecimal(scanner.nextLine().trim());
//        if(amount.compareTo(BigDecimal.ZERO) <= 0){
//            return null;
//        }
//
//        System.out.println("Select to account for this borrowing: ");
//        Account toAccount = getSelectableAccount();
//
//        System.out.println("Select ledger for this borrowing account: ");
//        Ledger ledger = selectLedger(userController.getCurrentUser());
//
//        System.out.print("Enter borrowing date (YYYY-MM-DD): ");
//        LocalDate date = inputDate();
//
//        return accountController.createBorrowingAccount(userController.getCurrentUser(), name, amount, note,
//                includedInNetWorth, selectable, toAccount, date, ledger);
//    }
//
//    private Account createLendingAccount(String name, boolean includedInNetWorth, String note, boolean selectable, BigDecimal balance) {
//        System.out.println("Select from account for this lending: ");
//        Account fromAccount = getSelectableAccount();
//
//        System.out.println("Select ledger for this lending account: ");
//        Ledger ledger = selectLedger(userController.getCurrentUser());
//
//        System.out.print("Enter lending date (YYYY-MM-DD): ");
//        LocalDate date = inputDate();
//
//        return accountController.createLendingAccount(userController.getCurrentUser(), name, balance,
//                note, includedInNetWorth, selectable, fromAccount, date, ledger);
//    }
//    private AccountCategory selectAccountCategory() {
//        System.out.println("\nSelect account category:");
//
//        AccountCategory[] categories = Arrays.stream(AccountCategory.values())
//                //.filter(c -> c != AccountCategory.VIRTUAL_ACCOUNT)
//                .toArray(AccountCategory[]::new);
//
//        for (int i = 0; i < categories.length; i++) {
//            System.out.println((i + 1) + ". " + formatCategoryName(categories[i]));
//        }
//        System.out.println("0. Cancel");
//        System.out.print("Enter number: ");
//
//        int choice = scanner.nextInt();
//        scanner.nextLine(); // consume newline
//
//        if (choice == 0) return null;
//        if (choice < 1 || choice > categories.length) {
//            System.out.println("Invalid choice!");
//            return selectAccountCategory();
//        }
//
//        return categories[choice - 1];
//    }
//
//    private AccountType selectAccountType(AccountCategory category) {
//        System.out.println("\nSelect account type for " + formatCategoryName(category) + ":");
//        AccountType[] availableTypes = getAvailableAccountTypes(category);
//
//        for (int i = 0; i < availableTypes.length; i++) {
//            System.out.println((i + 1) + ". " + formatTypeName(availableTypes[i]));
//        }
//        System.out.println("0. Back");
//        System.out.print("Enter number: ");
//
//        int choice = scanner.nextInt();
//        scanner.nextLine(); // consume newline
//
//        if (choice == 0) return null;
//        if (choice < 1 || choice > availableTypes.length) {
//            System.out.println("Invalid choice!");
//            return selectAccountType(category);
//        }
//
//        return availableTypes[choice - 1];
//    }

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

//    private String inputNote() {
//        String note = scanner.nextLine().trim();
//        return note.isEmpty() ? null : note;
//    }
//
//    private Integer inputOptionalInteger(String prompt, int min, int max) {
//        System.out.print(prompt);
//        String input = scanner.nextLine().trim();
//        if (input.isEmpty()) {
//            return null;
//        }
//
//        int value = Integer.parseInt(input);
//        if (value < min || value > max) {
//            System.out.println("Value must be between " + min + " and " + max + ".");
//            return inputOptionalInteger(prompt, min, max);
//        }
//        return value;
//    }

//    private LocalDate inputRepaymentDate() {
//        String dateInput = scanner.nextLine().trim();
//        if( dateInput.isEmpty()) {
//            return LocalDate.now();
//        }
//        return LocalDate.parse(dateInput);
//    }

//    private Account getSelectableAccount(){
//        List<Account> userAccounts = accountController.getSelectableAccounts(userController.getCurrentUser()).stream()
//                .filter(Account::getSelectable)
//                .toList();
//
//        if (userAccounts.isEmpty()) {
//            System.out.println("No accounts found. Please create an account first.");
//            return null;
//        }
//
//        for (int i = 0; i < userAccounts.size(); i++) {
//            Account account = userAccounts.get(i);
//            System.out.println((i + 1) + ". " + account.getName());
//        }
//        System.out.println("0. From Account is external");
//        System.out.print("Enter choice: ");
//
//        String input = scanner.nextLine().trim();
//        int choice= Integer.parseInt(input);
//
//        if (choice == 0) return null;
//        if (choice < 1 || choice > userAccounts.size()) {
//            System.out.println("Invalid choice!");
//            return getSelectableAccount();
//        }
//
//        return userAccounts.get(choice - 1);
//    }

//    private LoanAccount.RepaymentType selectRepaymentType() {
//        LoanAccount.RepaymentType[] types = LoanAccount.RepaymentType.values();
//        for (int i = 0; i < types.length; i++) {
//            System.out.println((i + 1) + ". " + formatRepaymentType(types[i]));
//        }
//        System.out.println("0. skip");
//        System.out.print("Enter number: ");
//
//        int choice = scanner.nextInt();
//        scanner.nextLine(); // consume newline
//
//        if (choice == 0) return null;
//        if (choice < 0 || choice > types.length) {
//            System.out.println("Invalid choice!");
//            return selectRepaymentType();
//        }
//
//        return types[choice - 1];
//    }

//    private CreditAccount createCreditCardAccount(String name, BigDecimal balance,
//                                                  AccountType type, boolean includedInNetWorth,
//                                                  boolean selectable, String note) {
//
//        System.out.print("Enter credit limit: ");
//        BigDecimal creditLimit = scanner.nextBigDecimal();
//        scanner.nextLine();
//
//        System.out.print("Enter current debt (press Enter for 0): ");
//        String debtInput = scanner.nextLine();
//        BigDecimal currentDebt = debtInput.isEmpty() ? BigDecimal.ZERO : new BigDecimal(debtInput);
//
//        Integer billDay = inputOptionalInteger("Enter bill day (1-31, optional, press Enter to skip): ", 1, 31);
//        Integer dueDay = inputOptionalInteger("Enter due day (1-31, optional, press Enter to skip): ", 1, 31);
//
//        return accountController.createCreditAccount(
//                name,
//                note,
//                balance,
//                includedInNetWorth,
//                selectable,
//                userController.getCurrentUser(),
//                type,
//                creditLimit,
//                currentDebt,
//                billDay,
//                dueDay
//        );
//    }
//
//    private LoanAccount createLoanAccount(String name, boolean includedInNetWorth, String note) {
//
//        //enter ledger
//        System.out.println("Enter ledger for this loan account:");
//        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
//        if(selectedLedger == null) {
//            System.out.println("Ledger selection cancelled.");
//            return null;
//        }
//
//        System.out.print("Enter loan amount: ");
//        BigDecimal loanAmount = new BigDecimal(scanner.nextLine().trim());
//
//        System.out.print("Enter annual interest rate (%, optional, press Enter for 0): ");
//        String interestInput = scanner.nextLine().trim();
//        BigDecimal interestRate = interestInput.isEmpty() ? BigDecimal.ZERO : new BigDecimal(interestInput);
//
//        System.out.print("Enter total repayment periods: ");
//        int totalPeriods;
//        try {
//            totalPeriods = Integer.parseInt(scanner.nextLine().trim());
//        } catch (Exception e) {
//            System.out.println("Invalid number of periods! Please enter a valid integer.");
//            return createLoanAccount(name, includedInNetWorth, note);
//        }
//
//        System.out.print("Enter repaid periods (optional, press Enter for 0): ");
//        String repaidInput = scanner.nextLine().trim();
//        int repaidPeriods = repaidInput.isEmpty() ? 0 : Integer.parseInt(repaidInput);
//
//        System.out.println("Select the account to receive the loan amount: (optional) ");
//        Account receivingAccount = getSelectableAccount(); //receiving account can be null
//
//        System.out.print("necessary enter repayment date (YYYY-MM-DD): ");
//        LocalDate repaymentDay = inputRepaymentDate(); //repayment date can not be null
//
//        LoanAccount.RepaymentType repaymentType = selectRepaymentType(); //repayment type can be null
//
//        return accountController.createLoanAccount(name, note, includedInNetWorth, userController.getCurrentUser(),
//                totalPeriods, repaidPeriods, interestRate, loanAmount, receivingAccount, repaymentDay, repaymentType, selectedLedger);
//    }
//
//    private Account createBasicAccount(String name, BigDecimal balance, AccountType type,
//                                       AccountCategory category, boolean includedInNetWorth, boolean selectable,
//                                       String note) {
//        return accountController.createAccount(
//                name,
//                balance,
//                type,
//                category,
//                userController.getCurrentUser(),
//                note,
//                includedInNetWorth,
//                selectable
//
//        );
//    }
//
//
//    private AccountType[] getAvailableAccountTypes(AccountCategory category) {
//        return switch (category) {
//            case FUNDS -> new AccountType[]{
//                    AccountType.CASH, AccountType.DEBIT_CARD, AccountType.PASSBOOK,
//                    AccountType.PAYPAL, AccountType.PENSION, AccountType.OTHER_FUNDS
//            };
//            case CREDIT -> new AccountType[]{
//                    AccountType.CREDIT_CARD, AccountType.LOAN, AccountType.OTHER_CREDIT
//            };
//            case RECHARGE -> new AccountType[]{
//                    AccountType.MOBILE_RECHARGE, AccountType.FUND, AccountType.APPLE_ID, AccountType.OTHER_RECHARGE
//            };
//            case INVEST -> new AccountType[]{
//                    AccountType.INVESTMENT, AccountType.STOCKS, AccountType.FUND,
//                    AccountType.GOLD, AccountType.INSURANCE,
//                    AccountType.FUTURES, AccountType.CRYPTO,
//                    AccountType.FIXED_DEPOSIT, AccountType.OTHER_INVEST
//            };
//            case VIRTUAL_ACCOUNT -> new AccountType[]{
//                    AccountType.BORROWING, AccountType.LENDING
//            };
//            //default -> AccountType.values();
//        };
//    }
//
//    private String formatCategoryName(AccountCategory category) {
//        return category.name().charAt(0) + category.name().substring(1).toLowerCase().replace("_", " ");
//    }
//
//    private String formatTypeName(AccountType type) {
//        return type.name().charAt(0) + type.name().substring(1).toLowerCase().replace("_", " ");
//    }
//    private String formatRepaymentType(LoanAccount.RepaymentType type) {
//        return switch (type) {
//            case EQUAL_INTEREST -> "Equal Interest";
//            case EQUAL_PRINCIPAL -> "Equal Principal";
//            case EQUAL_PRINCIPAL_AND_INTEREST -> "Equal Principal and Interest";
//            case INTEREST_BEFORE_PRINCIPAL -> "Interest Before Principal";
//        };
//    }

//    private Ledger selectLedger(User user) {
//        List<Ledger> ledgers = ledgerController.getLedgersByUser(user);
//
//        if(ledgers.isEmpty()) {
//            System.out.println("No ledgers found for the user.");
//            return null;
//        }
//
//        for (int i = 0; i < ledgers.size(); i++) {
//            Ledger ledger = ledgers.get(i);
//            System.out.println((i + 1) + ". "+ "Name: " + ledger.getName());
//        }
//        System.out.print("Enter the number of the ledger: ");
//        String input = scanner.nextLine().trim();
//        int choice = Integer.parseInt(input);
//
//        if(choice < 1 || choice > ledgers.size()) {
//            System.out.println("Invalid choice.");
//            return selectLedger(user);
//        }
//        return ledgers.get(choice - 1);
//    }
}
