package com.ledger.cli;

import com.ledger.business.InstallmentController;
import com.ledger.business.ReportController;
import com.ledger.business.UserController;
import com.ledger.domain.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class InstallmentCLI {
    private final InstallmentController installmentController;
    private final UserController userController;
    private final ReportController reportController;


    private final Scanner scanner = new Scanner(System.in);

    public InstallmentCLI(InstallmentController installmentController,
                          UserController userController,
                          ReportController reportController) {

        this.reportController = reportController;
        this.userController = userController;
        this.installmentController = installmentController;
    }

    public void createInstallment() {
        System.out.println("\n=== Create Installment ===");

        //select credit account
        System.out.println("Select credit card account for the installment:");
        CreditAccount creditAccount = selectCreditCardAccount();
        if (creditAccount == null) return;

        // input installment details
        System.out.println("Enter name for your installment :");
        String name = scanner.nextLine().trim();

        // input total amount
        System.out.print("Enter total amount: ");
        BigDecimal totalAmount = new BigDecimal(scanner.nextLine().trim());

        // input total periods
        System.out.print("Enter total periods: ");
        int totalPeriods = Integer.parseInt(scanner.nextLine().trim());

        // input interest
        System.out.print("Enter interest (optional, press Enter for 0): ");
        String interestInput = scanner.nextLine().trim();
        BigDecimal interest = interestInput.isEmpty() ? BigDecimal.ZERO : new BigDecimal(interestInput);

        // select strategy
        Installment.Strategy strategy = selectStrategy();

        // input start repayment date
        LocalDate repaymentStartDate = inputRepaymentDate();

        //input if include in current debt
        System.out.print("Include in current debt? (y/n, default y): ");
        String includeInput = scanner.nextLine().trim().toLowerCase();
        boolean includeInCurrentDebt = includeInput.isEmpty() || includeInput.equals("y") || includeInput.equals("yes");

        //select ledger
        Ledger ledger= selectLedger();
        if(ledger==null) return;

        //select category
        System.out.println("Select category for the installment:");
        LedgerCategory category = inputCategory(ledger);
        if(category==null) return;

        Installment plan = installmentController.createInstallment(creditAccount, name, totalAmount, totalPeriods,
                interest, strategy, repaymentStartDate, category, includeInCurrentDebt, ledger);

        System.out.println("Installment created successfully!");
        System.out.println(formatInstallment(plan));
    }

    public void viewInstallments() {

        System.out.println("\n=== View Installments ===");

        //select credit account
        System.out.println("Select credit account to view installments:");
        CreditAccount creditAccount = selectCreditCardAccount();
        if (creditAccount == null) return;

        //get all plan with remaining amount >0
        List<Installment> plans = reportController.getActiveInstallments(creditAccount);

        if (plans.isEmpty()) {
            System.out.println("No installment plans found.");
            return;
        }

        System.out.println("\nInstallments for " + creditAccount.getName() + ":");
        for (int i = 0; i < plans.size(); i++) {
            Installment plan = plans.get(i);
            System.out.println((i + 1) + ". " + formatInstallment(plan));
        }

    }

    public void payInstallment() {
        System.out.println("\n=== Make Installment Payment ===");

        //select credit account
        System.out.println("Select credit account:");
        CreditAccount creditAccount = selectCreditCardAccount();
        if (creditAccount == null) return;

        //get remaining > 0 installments
        List<Installment> activePlans =reportController.getActiveInstallments(creditAccount);

        if (activePlans.isEmpty()) {
            System.out.println("No active installments found.");
            return;
        }

        //select installment to pay
        System.out.println("\nSelect installment to pay:");
        for (int i = 0; i < activePlans.size(); i++) {
            Installment plan = activePlans.get(i);
            System.out.println((i + 1) + ". " + formatInstallment(plan));
        }
        System.out.print("Enter choice: ");
        int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;

        if (choice < 0 || choice >= activePlans.size()) {
            System.out.println("Invalid choice!");
            return;
        }

        Installment selectedPlan = activePlans.get(choice);

        // confirm payment
        System.out.println("Payment amount: " + selectedPlan.getMonthlyPayment(selectedPlan.getPaidPeriods() + 1));
        System.out.print("Confirm payment? (y/n): ");
        String confirm = scanner.nextLine().trim().toLowerCase();

        if (confirm.equals("y") || confirm.equals("yes")) {
            boolean success= installmentController.payInstallment(selectedPlan, creditAccount, userController.getCurrentUser());
            if(!success){
                System.out.println("✗ Payment failed!");
                return;
            }
            System.out.println("✓ Payment completed successfully!");
            System.out.println(formatInstallment(selectedPlan));
        } else {
            System.out.println("Payment cancelled.");
        }
    }

    public void deleteInstallment() {

        System.out.println("\n=== Delete Installment ===");

        //select credit account
        System.out.println("Select credit card account:");
        CreditAccount creditAccount = selectCreditCardAccount();
        if (creditAccount == null) return;

        List<Installment> plans = reportController.getActiveInstallments(creditAccount);

        if (plans.isEmpty()) {
            System.out.println("No installments found.");
            return;
        }

        System.out.println("\nSelect installment to delete:");
        for (int i = 0; i < plans.size(); i++) {
            Installment plan = plans.get(i);
            System.out.println((i + 1) + ". " + formatInstallment(plan));
        }
        System.out.print("Enter choice: ");
        int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;

        if (choice < 0 || choice >= plans.size()) {
            System.out.println("Invalid choice!");
            return;
        }

        Installment selectedPlan = plans.get(choice);

        // confirm deletion
        System.out.print("Are you sure you want to delete this installment plan? Transactions related to this installment will be retained. (y/n): ");
        String confirm = scanner.nextLine().trim().toLowerCase();

        if (confirm.equals("y") || confirm.equals("yes")) {
            boolean success= installmentController.deleteInstallment(selectedPlan, creditAccount, userController.getCurrentUser());
            if(!success){
                System.out.println("✗ Deletion failed!");
                return;
            }
            System.out.println("✓ Installment plan deleted successfully!");
        } else {
            System.out.println("Deletion cancelled.");
        }


    }

    public void editInstallmentPlan() {
        System.out.println("\n=== Edit Installment===");

        System.out.println("\nSelect credit card account: ");
        CreditAccount creditAccount = selectCreditCardAccount();
        if (creditAccount == null) return;

        List<Installment> plans = reportController.getActiveInstallments(creditAccount);
        if (plans.isEmpty()) {
            System.out.println("No installments found.");
            return;
        }
        System.out.println("\nSelect installment to edit:");
        for (int i = 0; i < plans.size(); i++) {
            Installment plan = plans.get(i);
            System.out.println((i + 1) + ". " + formatInstallment(plan));
        }
        System.out.println("0. Cancel");
        System.out.print("Enter choice: ");
        int choice = Integer.parseInt(scanner.nextLine().trim()) ;
        if(choice==0) return;
        if (choice < 0 || choice >= plans.size()) {
            System.out.println("Invalid choice!");
            return;
        }
        Installment selectedPlan = plans.get(choice);

        if(selectedPlan.isIncludedInCurrentDebts()){
            System.out.print("The installment is currently included in current debts. Do you want to exclude it? (y/n): ");
        } else {
            System.out.print("The installment is currently excluded from current debts. Do you want to include it? (y/n): ");
        }
        String input = scanner.nextLine().trim().toLowerCase(); //input is null meaning no change
        Boolean includeInCurrentDebt = null;
        if (input.equals("y") || input.equals("yes")) {
            includeInCurrentDebt = !selectedPlan.isIncludedInCurrentDebts();
        }

        boolean success= installmentController.editInstallment(selectedPlan, includeInCurrentDebt, userController.getCurrentUser(), creditAccount);
        if(!success) {
            System.out.println("Edit failed!");
            return;
        }
        System.out.println("Installment edited successfully!");
        System.out.println(formatInstallment(selectedPlan));
        System.out.println("Update Credit Card current debt: " + creditAccount.getCurrentDebt());
    }


    //private helper methods
    private CreditAccount selectCreditCardAccount(){
        List<Account> userAccounts = reportController.getVisibleAccounts(userController.getCurrentUser());

        List<CreditAccount> creditAccounts = userAccounts.stream()
                .filter(account -> account instanceof CreditAccount)
                .filter(account -> account.getType().equals(AccountType.CREDIT_CARD))
                .map(account -> (CreditAccount) account)
                .toList();

        if (creditAccounts.isEmpty()) {
            System.out.println("No credit accounts found. Please create a credit account first.");
            return null;
        }

        for (int i = 0; i < creditAccounts.size(); i++) {
            CreditAccount account = creditAccounts.get(i);
            System.out.println((i + 1) + ". " + account.getName() + " - balance: " + account.getBalance() +
                    ", Credit Limit: " + account.getCreditLimit() + ", Current debt: " + account.getCurrentDebt() );
        }
        System.out.print("Enter number of credit card: ");


        int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;
        if (choice < 0 || choice >= creditAccounts.size()) {
            System.out.println("Invalid choice!");
            return selectCreditCardAccount();
        }
        return creditAccounts.get(choice);
    }

    private Installment.Strategy selectStrategy() {
        System.out.println("\nSelect strategy:");
        Installment.Strategy[] strategies = Installment.Strategy.values();
        for (int i = 0; i < strategies.length; i++) {
            System.out.println((i + 1) + ". " + formatStrategy(strategies[i]));
        }
        System.out.print("Enter choice: ");

        try {
            int choice = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (choice < 0 || choice >= strategies.length) {
                System.out.println("Invalid choice!");
                return selectStrategy();
            }
            return strategies[choice];
        } catch (Exception e) {
            System.out.println("Invalid input!");
            return selectStrategy();
        }
    }

    private LocalDate inputRepaymentDate() {
        System.out.print("Enter repayment start date (YYYY-MM-DD): ");
        String dateInput = scanner.nextLine().trim();
        LocalDate date;
        if(dateInput.isEmpty()){
            date=LocalDate.now();
            return date;
        }
        date = LocalDate.parse(dateInput);
        return date;
    }

    private Ledger selectLedger() {
        List<Ledger> ledgers = reportController.getLedgersByUser(userController.getCurrentUser());

        if (ledgers.isEmpty()) {
            System.out.println("No ledgers found. Please create a ledger first.");
            return null;
        }

        for (int i = 0; i < ledgers.size(); i++) {
            Ledger ledger = ledgers.get(i);
            System.out.println((i + 1) + ". " + ledger.getName());
        }
        System.out.print("Enter number of ledger: ");

        int choice = Integer.parseInt(scanner.nextLine().trim()) ;
        if (choice < 1 || choice >ledgers.size()) {
            System.out.println("Invalid choice!");
            return selectLedger();
        }
        return ledgers.get(choice -1 );
    }

    private LedgerCategory inputCategory(Ledger ledger) {
        List<LedgerCategory> categories = reportController.getLedgerCategoryTreeByLedger(ledger);

        if (categories.isEmpty()) {
            System.out.println("No categories found in the selected ledger. Please create a category first.");
            return null;
        }

        // filter only expense parent categories
        List<LedgerCategory> parentCategories = categories.stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE)
                .filter(cat -> cat.getParent() == null)
                .toList();

        // display parent categories
        for (int i = 0; i < parentCategories.size(); i++) {
            LedgerCategory parent = parentCategories.get(i);
            System.out.println((i + 1) + ". " + parent.getName());

            // display subcategories
            List<LedgerCategory> subCategories = categories.stream()
                    .filter(cat -> cat.getParent() != null && cat.getParent().getId() == parent.getId())
                    .toList();
            if (!subCategories.isEmpty()) {
                for (int j = 0; j < subCategories.size(); j++) {
                    LedgerCategory child = subCategories.get(j);
                    System.out.println("   " + (i + 1) + "." + (j + 1) + " " + child.getName());
                }
            }
        }

        System.out.print("Enter choice (e.g. 1 or 1.2): ");
        String choiceInput = scanner.nextLine().trim();

        if (choiceInput.contains(".")) {
            // select subcategory
            String[] parts = choiceInput.split("\\.");
            int parentIndex = Integer.parseInt(parts[0]) - 1;
            int childIndex = Integer.parseInt(parts[1]) - 1;

            if (parentIndex < 0 || parentIndex >= parentCategories.size()) {
                System.out.println("Invalid parent choice!");
                return inputCategory(ledger);
            }

            LedgerCategory parentCategory = parentCategories.get(parentIndex);
            List<LedgerCategory> subCategories = categories.stream()
                    .filter(cat -> parentCategory.getId() == cat.getParent().getId())
                    .toList();
            if (childIndex < 0 || childIndex >= subCategories.size()) {
                System.out.println("Invalid subcategory choice!");
                return inputCategory(ledger);
            }

            return subCategories.get(childIndex);
        } else {
            //select parent category
            int parentIndex = Integer.parseInt(choiceInput) - 1;
            if (parentIndex < 0 || parentIndex >= parentCategories.size()) {
                System.out.println("Invalid choice!");
                return inputCategory(ledger);
            }
            return parentCategories.get(parentIndex);
        }
    }

    private String formatInstallment(Installment plan) {
        return String.format(
                "Total to pay: %s | Paid amount: %s |Periods: %d/%d | Next month: %s | Remaining: %s | include in current debt: %s | Strategy: %s",
                plan.getTotalPayment(),
                plan.getTotalPayment().subtract(plan.getRemainingAmount()).setScale(2, RoundingMode.HALF_UP),
                plan.getPaidPeriods(),
                plan.getTotalPeriods(),
                plan.getMonthlyPayment(plan.getPaidPeriods() + 1),
                plan.getRemainingAmount(),
                plan.isIncludedInCurrentDebts() ? "Yes" : "No",
                formatStrategy(plan.getStrategy())
        );
    }

    private String formatStrategy(Installment.Strategy strategy) {
        return switch (strategy) {
            case EVENLY_SPLIT -> "Evenly Split";
            case UPFRONT      -> "Upfront";
            case FINAL        -> "Final";
        };
    }
}
