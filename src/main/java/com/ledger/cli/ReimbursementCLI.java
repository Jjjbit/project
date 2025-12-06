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
    private final LedgerCategoryController ledgerCategoryController;
    private final Scanner scanner = new Scanner(System.in);

    public ReimbursementCLI(UserController userController,
                            ReimbursementController reimbursementController,
                            TransactionController transactionController, LedgerController ledgerController,
                            AccountController accountController, LedgerCategoryController ledgerCategoryController) {
        this.ledgerCategoryController = ledgerCategoryController;
        this.accountController = accountController;
        this.ledgerController = ledgerController;
        this.transactionController = transactionController;
        this.userController = userController;
        this.reimbursementController = reimbursementController;
    }

    public void create(){
        System.out.println("\n === Create Reimbursement ===");

        System.out.println("Select a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        System.out.print("Enter the amount for the reimbursable expense: ");
        BigDecimal amount = inputAmount();

        System.out.println("Select an account for the expense:");
        Account expenseAccount = selectAccount();

        System.out.print("Enter a name/description for the reimbursable expense: ");
        LedgerCategory category = selectCategory(selectedLedger);
        if(category==null){
            System.out.println("No category selected. Returning to main menu.");
            return;
        }

        Reimbursement record = reimbursementController.create(amount, expenseAccount, selectedLedger, category);
        if(record == null){
            System.out.println("Failed to create reimbursable expense.");
            return;
        }
        System.out.println("Reimbursable expense created successfully");
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
                .filter(reimbursement -> !reimbursement.isEnded())
                .toList();
        if(pendingReimbursements.isEmpty()){
            System.out.println("No pending reimbursements found in the selected ledger.");
            return;
        }
        for(int i=0; i<pendingReimbursements.size(); i++){
            Reimbursement reimb = pendingReimbursements.get(i);
            System.out.printf("%d. Category: %s, Total Amount: %s, Remaining Amount: %s, is ended: %s%n",
                    (i + 1),
                    reimb.getLedgerCategory().getName(),
                    reimb.getAmount(),
                    reimb.getRemainingAmount(),
                    reimb.isEnded() ? "Yes" : "No"
            );
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

        //select reimbursement account
        System.out.println("Select an account for reimbursement:");
        Account reimbursementAccount = selectAccount();
        if(reimbursementAccount==null){
            reimbursementAccount = selectedReimbursement.getFromAccount();
        }

        //input reimbursed amount
        System.out.print("Enter the reimbursed amount: ");
        BigDecimal reimbursedAmount = scanner.nextBigDecimal();
        scanner.nextLine(); // consume newline
        if(reimbursedAmount == null || reimbursedAmount.compareTo(BigDecimal.ZERO) <= 0){
            reimbursedAmount = selectedReimbursement.getRemainingAmount();
        }

        //input date
        System.out.print("Enter the date for the reimbursement (YYYY-MM-DD): ");
        LocalDate date = inputDate();

        boolean moreToReimburse = false;
        if(reimbursedAmount.compareTo(selectedReimbursement.getRemainingAmount()) < 0) {
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

    public void delete(){
        System.out.println("\n === Delete Reimbursement ===");

        System.out.println("Select a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        List<Reimbursement> reimbursements = reimbursementController.getReimbursementsByLedger(selectedLedger);
        if(reimbursements.isEmpty()){
            System.out.println("No reimbursements found in the selected ledger.");
            return;
        }

        for(int i=0; i<reimbursements.size(); i++){
            Reimbursement reimb = reimbursements.get(i);
            System.out.printf("%d. Category: %s, Total Amount: %s, Remaining Amount: %s, is ended: %s%n",
                    (i + 1),
                    reimb.getLedgerCategory().getName(),
                    reimb.getAmount(),
                    reimb.getRemainingAmount(),
                    reimb.isEnded() ? "Yes" : "No"
            );
        }
        System.out.print("Enter the number of the reimbursement to delete (or 0 to cancel): ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if(choice == 0) {
            System.out.println("No reimbursement selected. Returning to main menu.");
            return;
        }
        if(choice < 1 || choice > reimbursements.size()) {
            System.out.println("Invalid choice. Returning to main menu.");
            return;
        }
        Reimbursement selectedReimbursement = reimbursements.get(choice - 1);

        boolean deleted = reimbursementController.delete(selectedReimbursement);
        if(!deleted){
            System.out.println("Failed to delete reimbursement.");
            return;
        }
        System.out.println("Reimbursement deleted successfully");
    }

    public void edit(){
        System.out.println("\n === Edit Pending Reimbursement ===");

        System.out.println("Select a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        List<Reimbursement> pendingReimbursements = reimbursementController.getReimbursementsByLedger(selectedLedger).stream()
                .filter(reimbursement -> !reimbursement.isEnded())
                .toList();
        if(pendingReimbursements.isEmpty()){
            System.out.println("No pending reimbursements found in the selected ledger.");
            return;
        }

        for(int i=0; i<pendingReimbursements.size(); i++){
            Reimbursement reimb = pendingReimbursements.get(i);
            System.out.printf("%d. Category: %s, Total Amount: %s, Remaining Amount: %s%n",
                    (i + 1),
                    reimb.getLedgerCategory().getName(),
                    reimb.getAmount(),
                    reimb.getRemainingAmount()
            );
        }
        System.out.print("Enter the number of the reimbursement to edit (or 0 to cancel): ");
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


        System.out.println("Current Total Amount: " + selectedReimbursement.getAmount());
        System.out.print("Enter new total amount for the reimbursable expense (or press Enter to keep current): ");
        String amountInput = scanner.nextLine().trim();
        BigDecimal newAmount;
        if(amountInput.isEmpty()){
            newAmount = selectedReimbursement.getAmount();
        } else {
            newAmount = new BigDecimal(amountInput);
            if(newAmount.compareTo(BigDecimal.ZERO) <= 0){
                newAmount = selectedReimbursement.getAmount();
            }
        }

        boolean edited = reimbursementController.editReimbursement(selectedReimbursement, newAmount);
        if(!edited){
            System.out.println("Failed to edit reimbursement.");
            return;
        }
        System.out.println("Reimbursement edited successfully");
    }

    public void showAllPendingReimbursements(){
        System.out.println("\n === Pending Reimbursements ===");

        System.out.println("Select a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        List<Reimbursement> pendingReimbursements = reimbursementController.getReimbursementsByLedger(selectedLedger).stream()
                .filter(reimbursement -> !reimbursement.isEnded())
                .toList();
        if(pendingReimbursements.isEmpty()){
            System.out.println("No pending reimbursements found in the selected ledger.");
            return;
        }

        System.out.println("\nPending Reimbursements:");
        System.out.println("Total Pending: " + reimbursementController.getTotalPending(selectedLedger));
        for(Reimbursement reimb : pendingReimbursements){
            System.out.printf("Category: %s, Total Amount: %s, Remaining Amount: %s%n",
                    reimb.getLedgerCategory().getName(),
                    reimb.getAmount(),
                    reimb.getRemainingAmount()
            );
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
        List<Reimbursement> claimedReimbursements = reimbursementController.getReimbursementsByLedger(selectedLedger).stream()
                .filter(Reimbursement::isEnded)
                .toList();
        if(claimedReimbursements.isEmpty()){
            System.out.println("No claimed reimbursements found in the selected ledger.");
            return;
        }

        System.out.println("\nClaimed Reimbursements:");
        System.out.println("Total Claimed: " + reimbursementController.getTotalReimbursed(selectedLedger));
        for(Reimbursement reimb : claimedReimbursements) {
            System.out.printf("Category: %s, Total Amount: %s%n",
                    reimb.getLedgerCategory().getName(),
                    reimb.getAmount()
            );
        }
    }

    public void showReimbursementDetails(){
        System.out.println("\n === Reimbursement Details ===");

        System.out.println("Select a ledger:");
        Ledger selectedLedger = selectLedger(userController.getCurrentUser());
        if(selectedLedger==null){
            System.out.println("No ledger selected. Returning to main menu.");
            return;
        }

        List<Reimbursement> reimbursements = reimbursementController.getReimbursementsByLedger(selectedLedger);
        if(reimbursements.isEmpty()){
            System.out.println("No reimbursements found in the selected ledger.");
            return;
        }

        for(int i=0; i<reimbursements.size(); i++){
            Reimbursement reimb = reimbursements.get(i);
            System.out.printf("%d. Category: %s, Total Amount: %s, Remaining Amount: %s, Account: %s, is ended: %s%n",
                    (i + 1),
                    reimb.getLedgerCategory().getName(),
                    reimb.getAmount(),
                    reimb.getRemainingAmount(),
                    reimb.getFromAccount() != null ? reimb.getFromAccount().getName() : "N/A",
                    reimb.isEnded() ? "Yes" : "No"
            );
        }

        System.out.print("Enter the number of the reimbursement to view details (or 0 to cancel): ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if(choice == 0) {
            return;
        }
        if(choice < 1 || choice > reimbursements.size()) {
            System.out.println("Invalid choice. Returning to main menu.");
            return;
        }
        Reimbursement selectedReimbursement = reimbursements.get(choice - 1);

        List<Transaction> relatedTransactions = transactionController.getTransactionsByReimbursement(selectedReimbursement);
        if(relatedTransactions.isEmpty()){
            System.out.println("No transactions found for the selected reimbursement.");
            return;
        }

        System.out.println("\nTransactions for the selected reimbursement:");
        int count = 0;
        for(Transaction tx : relatedTransactions){
            count++;
            StringBuilder info = new StringBuilder();

            info.append(String.format("%d. Amount: %s, Date: %s",
                    count,
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

    //private helper methods
    private LedgerCategory selectCategory(Ledger ledger) {
        List<LedgerCategory> categories = ledgerCategoryController.getLedgerCategoryTreeByLedger(ledger).stream()
                .filter(cat -> cat.getType().equals(CategoryType.EXPENSE))
                .toList();

        if(categories.isEmpty()) {
            System.out.println("No categories found for the ledger.");
            return null;
        }

        List<LedgerCategory> parentCategories = categories.stream()
                .filter(cat -> cat.getParent() == null)
                .toList();

        for(int i = 0; i < parentCategories.size(); i++) {
            //display parent category
            LedgerCategory parent = parentCategories.get(i);
            System.out.println((i + 1) + ". " + "Name: " + parent.getName());

            //display sub-categories
            List<LedgerCategory> subCategories = categories.stream()
                    .filter(cat -> cat.getParent()!= null && cat.getParent().getId() == parent.getId())
                    .toList();
            if(subCategories.isEmpty()) {
                continue;
            }
            for(int j = 0; j < subCategories.size(); j++) {
                LedgerCategory sub = subCategories.get(j);
                System.out.println("   " + (i + 1) + "." + (j + 1) + " Name: " + sub.getName());
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
                return selectCategory(ledger);
            }

            LedgerCategory parentCategory = parentCategories.get(parentIndex);
            List<LedgerCategory> subCategories = categories.stream()
                    .filter(category -> category.getParent() != null && category.getParent().getId() == parentCategory.getId())
                    .toList();
            if (childIndex < 0 || childIndex >= subCategories.size()) {
                System.out.println("Invalid subcategory choice!");
                return selectCategory(ledger);
            }

            return subCategories.get(childIndex);
        } else {
            //select parent category
            int parentIndex = Integer.parseInt(input) - 1;

            if (parentIndex < 0 || parentIndex >= parentCategories.size()) {
                System.out.println("Invalid choice!");
                return selectCategory(ledger);
            }
            return parentCategories.get(parentIndex);
        }
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

        for (int i = 0; i < accounts.size(); i++) {
            Account account = accounts.get(i);
            System.out.println((i + 1) + ". " + "Name: " + account.getName() + ", Balance: " + account.getBalance());
        }
        System.out.print("Enter the number of the account: ");
        String input = scanner.nextLine().trim();
        int choice = Integer.parseInt(input);
        if (choice < 1 || choice > accounts.size()) {
            System.out.println("Invalid choice.");
            return selectAccount();
        }
        return accounts.get(choice - 1);
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
