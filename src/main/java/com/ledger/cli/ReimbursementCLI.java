package com.ledger.cli;

import com.ledger.business.*;
import com.ledger.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class ReimbursementCLI {
    private final UserController userController;
    private final ReimbursementController reimbursementController;
    private final TransactionController transactionController;
    private final LedgerController ledgerController;
    private final AccountController accountController;
    private final Scanner scanner = new Scanner(System.in);

    public ReimbursementCLI(UserController userController,
                            ReimbursementController reimbursementController,
                            TransactionController transactionController, LedgerController ledgerController,
                            AccountController accountController) {
        this.accountController = accountController;
        this.ledgerController = ledgerController;
        this.transactionController = transactionController;
        this.userController = userController;
        this.reimbursementController = reimbursementController;
    }

    public void claim(){
        System.out.println("\n === Claim Reimbursement ===");

        System.out.println("Select a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        System.out.print("Select a Pending reimbursement: ");
        List<Reimbursement> pendingReimbursements = reimbursementController.getReimbursementsByLedger(selectedLedger).stream()
                .filter(reimb -> reimb.getReimbursementStatus() == ReimbursableStatus.PENDING)
                .toList();
        if(pendingReimbursements.isEmpty()){
            System.out.println("No pending reimbursements found in the selected ledger.");
            return;
        }
        for(int i=0; i<pendingReimbursements.size(); i++){
            Reimbursement reimb = pendingReimbursements.get(i);
            Transaction tx = reimb.getOriginalTransaction();
            System.out.println(String.format("%d. %s, Remaining Amount: %s",
                    (i+1),
                    showTransactionInfo(tx),
                    reimb.getAmount()));
        }
        System.out.print("Enter the number of the reimbursement to claim (or 0 to cancel): ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if(choice == 0) {
            System.out.println("No reimbursement selected. Returning to main menu.");
            return;
        }
        if(choice < 1 || choice > pendingReimbursements.size()) {
            System.out.println("Invalid choice. Returning to main menu.");
            return;
        }
        Reimbursement selectedReimbursement = pendingReimbursements.get(choice - 1);
        //Transaction selectedExpense = selectedReimbursement.getOriginalTransaction();

        //select reimbursement account
        System.out.println("Select an account for reimbursement:");
        Account reimbursementAccount = selectAccount();

        //input reimbursed amount
        System.out.print("Enter the reimbursed amount: ");
        BigDecimal reimbursedAmount = scanner.nextBigDecimal();
        scanner.nextLine(); // consume newline
        if(reimbursedAmount == null || reimbursedAmount.compareTo(BigDecimal.ZERO) <= 0){
            reimbursedAmount = selectedReimbursement.getAmount();
        }

        //input date
        System.out.print("Enter the date for the reimbursement (YYYY-MM-DD): ");
        LocalDate date = inputDate();

        boolean moreToReimburse = false;
        if(reimbursedAmount.compareTo(selectedReimbursement.getAmount()) > 0) {
            //more to reimburse
            System.out.print("Is there more to reimburse for this expense? (y/n): ");
            String moreInput = scanner.nextLine().trim().toLowerCase();
            moreToReimburse = moreInput.equals("y") || moreInput.equals("yes");
        }

        boolean claimed = reimbursementController.claim(selectedReimbursement, reimbursedAmount, moreToReimburse, reimbursementAccount, date);
        if(!claimed){
            System.out.println("Failed to claim reimbursement.");
            return;
        }
        System.out.println("Reimbursement claimed successfully");
    }

    public void deleteRecord(){}

    public void showAllPendingReimbursements(){
        System.out.println("\n === Pending Reimbursements ===");

        System.out.println("Select a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        LocalDate startDate;
        LocalDate endDate;
        //select monthly or yearly summary
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
            int month= scanner.nextInt();
            if(month<1 || month>12){
                System.out.println("Invalid month. Please enter a value between 1 and 12.");
                return;
            }
            startDate = LocalDate.of(LocalDate.now().getYear(), month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }

        List<Transaction> expenses = transactionController.getTransactionsByLedgerInRangeDate(selectedLedger, startDate, endDate).stream()
                .filter(tx -> tx instanceof Expense)
                .filter(tx -> {
                    Reimbursement reimbursement = reimbursementController.getReimbursementByTransaction(tx);
                    return reimbursement == null || reimbursement.getReimbursementStatus() == ReimbursableStatus.PENDING;
                })
                .toList();
        if(expenses.isEmpty()){
            System.out.println("No reimbursable expenses found in the selected ledger.");
            return;
        }
        List<Reimbursement> pendingReimbursements = expenses.stream()
                .map(tx -> reimbursementController.getReimbursementByTransaction(tx))
                .filter(reimb -> reimb != null && reimb.getReimbursementStatus() == ReimbursableStatus.PENDING)
                .toList();

        for(Reimbursement reimbursement : pendingReimbursements){
            Transaction tx = reimbursement.getOriginalTransaction();
            System.out.println(String.format("Expense: %s, Remaining Amount: %s",
                    showTransactionInfo(tx),
                    reimbursement.getAmount()
            ));
        }

    }

    public void showAllClaimedReimbursements(){
        System.out.println("\n === Claimed Reimbursements ===");

        System.out.println("Select a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        List<Reimbursement> claimedReimbursements = reimbursementController.getReimbursementsByLedger(selectedLedger);
        //List<Transaction> claimedTransaction

        LocalDate startDate;
        LocalDate endDate;
        //select monthly or yearly summary
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
            int month= scanner.nextInt();
            if(month<1 || month>12){
                System.out.println("Invalid month. Please enter a value between 1 and 12.");
                return;
            }
            startDate = LocalDate.of(LocalDate.now().getYear(), month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else {
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }

        List<Transaction> expenses = transactionController.getTransactionsByLedgerInRangeDate(selectedLedger, startDate, endDate).stream()
                .filter(tx -> tx instanceof Expense)
                .filter(tx -> {
                    Reimbursement reimbursement = reimbursementController.getReimbursementByTransaction(tx);
                    return reimbursement != null;
                })
                .toList();
        if(expenses.isEmpty()){
            System.out.println("No fully claimed reimbursable expenses found in the selected ledger.");
            return;
        }

        for(Transaction tx : expenses){
            Reimbursement reimbursement = reimbursementController.getReimbursementByTransaction(tx);
            List<Transaction> txLinks = transactionController.getTransactionsByReimbursement(reimbursement);

        }
    }

    //private helper methods
    private String showTransactionInfo(Transaction tx){
        StringBuilder info = new StringBuilder();

        info.append(String.format("-Type: %s, Amount: %s, Date: %s",
                tx.getType(),
                tx.getAmount(),
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

            info.append(String.format("%d. Type: %s, Amount: %s, Date: %s",
                    (i + 1),
                    tx.getType(),
                    tx.getAmount(),
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
            Reimbursement reimbursement = reimbursementController.getReimbursementByTransaction(tx);
            if( reimbursement != null && reimbursement.getReimbursementStatus() == ReimbursableStatus.PENDING) {
                info.append(", Pending");
            }else if( reimbursement != null && reimbursement.getReimbursementStatus() == ReimbursableStatus.FULL) {
                info.append(", Fully Claimed");
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
