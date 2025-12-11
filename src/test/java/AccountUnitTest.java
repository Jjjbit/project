import com.ledger.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class AccountUnitTest {
    private User testUser;

    @BeforeEach
    public void setUp() {
        testUser= new User("test user", "password123");
    }

    //test debit
    @Test
    public void testDebit() {
        CreditAccount creditAccount = new CreditAccount("Test Credit Card",
                BigDecimal.valueOf(1000.00), //balance
                testUser, "Test Note", true,
                true,
                BigDecimal.valueOf(5000.00), // Credit limit
                BigDecimal.valueOf(2000.00), // Current debt
                15, // billDay
                25, // dueDay
                AccountType.CREDIT_CARD);

        creditAccount.debit(BigDecimal.valueOf(1000.01));
        assertEquals(0, creditAccount.getBalance().compareTo(BigDecimal.valueOf(-0.01)));
    }


    //test repay debt
    @Test
    public void testRepayDebt() {
        CreditAccount creditAccount = new CreditAccount("Test Credit Card",
                BigDecimal.valueOf(1000.00), //balance
                testUser, "Test Note", true,
                true,
                BigDecimal.valueOf(5000.00), // Credit limit
                BigDecimal.valueOf(2000.00), // Current debt
                15, // billDay
                25, // dueDay
                AccountType.CREDIT_CARD);

        creditAccount.repayDebt(BigDecimal.valueOf(500.00));

        assertEquals(0, creditAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(1500.00)));
    }

    @Test
    public void testRepayDebt_ExceedingAmount() {
        CreditAccount creditAccount = new CreditAccount("Test Credit Card",
                BigDecimal.valueOf(1000.00), //balance
                testUser, "Test Note", true,
                true,
                BigDecimal.valueOf(5000.00), // Credit limit
                BigDecimal.valueOf(2000.00), // Current debt
                15, // billDay
                25, // dueDay
                AccountType.CREDIT_CARD);

        creditAccount.repayDebt(BigDecimal.valueOf(2500.00));

        assertEquals(0, creditAccount.getCurrentDebt().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testRepayDebt_FullAmount() {
        CreditAccount creditAccount = new CreditAccount("Test Credit Card",
                BigDecimal.valueOf(1000.00), //balance
                testUser, "Test Note", true,
                true,
                BigDecimal.valueOf(5000.00), // Credit limit
                BigDecimal.valueOf(2000.00), // Current debt
                15, // billDay
                25, // dueDay
                AccountType.CREDIT_CARD);

        creditAccount.repayDebt(BigDecimal.valueOf(2000.00));

        assertEquals(0, creditAccount.getCurrentDebt().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testRepayDebt_ZeroAmount() {
        CreditAccount creditAccount = new CreditAccount("Test Credit Card",
                BigDecimal.valueOf(1000.00), //balance
                testUser, "Test Note", true,
                true,
                BigDecimal.valueOf(5000.00), // Credit limit
                BigDecimal.valueOf(2000.00), // Current debt
                15, // billDay
                25, // dueDay
                AccountType.CREDIT_CARD);

        creditAccount.repayDebt(BigDecimal.ZERO);

        assertEquals(0, creditAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(2000.00)));
    }

    //test receive lending
    @Test
    public void testReceiveRepayment() {
        LendingAccount lendingAccount = new LendingAccount("Alice",
                BigDecimal.valueOf(3000.00), //balance to be received from Alice
                "Test Note", true, true, testUser, LocalDate.now());

        lendingAccount.debit(BigDecimal.valueOf(1000.00));

        assertEquals(0, lendingAccount.getBalance().compareTo(BigDecimal.valueOf(2000.00)));
        assertFalse(lendingAccount.getIsEnded());
    }

    @Test
    public void testReceiveRepayment_FullAmount() {
        LendingAccount lendingAccount = new LendingAccount("Alice",
                BigDecimal.valueOf(3000.00), //balance to be received from Alice
                "Test Note", true, true, testUser, LocalDate.now());

        lendingAccount.debit(BigDecimal.valueOf(3000.00));

        assertEquals(0, lendingAccount.getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(lendingAccount.getIsEnded());
    }

    @Test
    public void testReceiveRepayment_ExceedingAmount() {
        LendingAccount lendingAccount = new LendingAccount("Alice",
                BigDecimal.valueOf(3000.00), //balance to be received from Alice
                "Test Note", true, true, testUser, LocalDate.now());

        lendingAccount.debit(BigDecimal.valueOf(3500.00));

        assertEquals(0, lendingAccount.getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(lendingAccount.getIsEnded());
    }

    //test repay borrowing
    @Test
    public void testRepayBorrowing() {
        BorrowingAccount borrowingAccount = new BorrowingAccount("Bob",
                BigDecimal.valueOf(4000.00), //borrowing amount
                "Test Note", true, true, testUser, LocalDate.now());

        borrowingAccount.repay(BigDecimal.valueOf(1500.00)); //4000-1500=2500

        assertEquals(0, borrowingAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(2500.00)));
        assertFalse(borrowingAccount.getIsEnded());
    }

    @Test
    public void testRepayBorrowing_FullAmount() {
        BorrowingAccount borrowingAccount = new BorrowingAccount("Bob",
                BigDecimal.valueOf(4000.00), //borrowing amount
                "Test Note", true, true, testUser, LocalDate.now());

        borrowingAccount.repay(BigDecimal.valueOf(4000.00));

        assertEquals(0, borrowingAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(borrowingAccount.getIsEnded());
    }

    @Test
    public void testRepayBorrowing_ExceedingAmount() {
        BorrowingAccount borrowingAccount = new BorrowingAccount("Bob",
                BigDecimal.valueOf(4000.00), //borrowing amount
                "Test Note", true, true, testUser, LocalDate.now());

        borrowingAccount.repay(BigDecimal.valueOf(4500.00));

        assertEquals(0, borrowingAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(borrowingAccount.getIsEnded());
    }

    //test repayLoan
    @Test
    public void testFullFlow_EQUAL_INTEREST() {
        LoanAccount loanAccount= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 0,
                BigDecimal.valueOf(1.00), // 1% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST);

        for (int i = 1; i <= loanAccount.getTotalPeriods(); i++) {
            System.out.println("Month " + i + " repayment: " + loanAccount.getMonthlyRepayment(i));
            loanAccount.repayLoan();

            System.out.println("Remaining Amount after month " + i + ": " + loanAccount.getRemainingAmount());
            assertEquals(i, loanAccount.getRepaidPeriods());
            assertEquals(0, loanAccount.getMonthlyRepayment(i).compareTo(BigDecimal.valueOf(141.04))); //monthly repayment should be constant
        }

        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(loanAccount.getIsEnded());
    }

    @Test
    public void testFullFlow_EQUAL_PRINCIPAL() {
        LoanAccount loanAccount= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 0,
                BigDecimal.valueOf(1.00), // 1% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_PRINCIPAL);
        assertEquals(0, loanAccount.getLoanAmount().compareTo(BigDecimal.valueOf(5000.00)));
        System.out.println("Loan Amount: " + loanAccount.getLoanAmount());
        System.out.println("Total Repayment: " + loanAccount.calculateTotalRepayment());
        System.out.println("Remaining Amount at start: " + loanAccount.getRemainingAmount());
        assertEquals(0, loanAccount.calculateTotalRepayment().compareTo(BigDecimal.valueOf(5077.08)));
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5077.08)));

        BigDecimal paidAmount = BigDecimal.ZERO;
        for (int i = 1; i <= loanAccount.getTotalPeriods(); i++) {

            System.out.println("Month " + i + " repayment: " + loanAccount.getMonthlyRepayment(i));
            loanAccount.repayLoan();
            paidAmount = paidAmount.add(loanAccount.getMonthlyRepayment(i));

            assertEquals(i, loanAccount.getRepaidPeriods());
            System.out.println("Remaining Amount after month " + i + ": " + loanAccount.getRemainingAmount());
        }
        assertEquals(0, paidAmount.compareTo(loanAccount.calculateTotalRepayment()));
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(loanAccount.getIsEnded());
    }

    @Test
    public void testFullFlow_EQUAL_PRINCIPAL_AND_INTEREST() {
        LoanAccount loanAccount= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 0,
                BigDecimal.valueOf(1.00), // 1% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_PRINCIPAL_AND_INTEREST);
        assertEquals(0, loanAccount.calculateTotalRepayment().compareTo(BigDecimal.valueOf(5150.16)));
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5150.16)));

        BigDecimal paidAmount = BigDecimal.ZERO;
        for (int i = 1; i <= loanAccount.getTotalPeriods(); i++) {
            BigDecimal repayAmount = loanAccount.getMonthlyRepayment(i);

            assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(143.06))); //monthly repayment should be constant
            System.out.println("Month " + i + " repayment: " + loanAccount.getMonthlyRepayment(i));
            loanAccount.repayLoan();
            paidAmount = paidAmount.add(repayAmount);

            assertEquals(i, loanAccount.getRepaidPeriods());
            System.out.println("Remaining Amount after month " + i + ": " + loanAccount.getRemainingAmount());
        }
        assertEquals(0, paidAmount.compareTo(loanAccount.calculateTotalRepayment()));
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(loanAccount.getIsEnded());
    }

    @Test
    public void testFullFlow_INTEREST_BEFORE_PRINCIPAL() {
        LoanAccount loanAccount= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 0,
                BigDecimal.valueOf(1.00), // 1% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(),
                LoanAccount.RepaymentType.INTEREST_BEFORE_PRINCIPAL);
        System.out.println("Loan Amount: " + loanAccount.getLoanAmount());
        System.out.println("Total Repayment: " + loanAccount.calculateTotalRepayment());
        assertEquals(0, loanAccount.calculateTotalRepayment().compareTo(BigDecimal.valueOf(5150.12)));
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5150.12)));

        for (int i = 1; i <= loanAccount.getTotalPeriods(); i++) {
            BigDecimal repayAmount = loanAccount.getMonthlyRepayment(i);

            if(i <= 35){
                assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(4.17))); //first 35 months repayment should be interest only
            } else {
                assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(5004.17))); //last month repayment should be remaining principal + last interest
            }
            System.out.println("Month " + i + " repayment: " + loanAccount.getMonthlyRepayment(i));
            loanAccount.repayLoan();

            assertEquals(i, loanAccount.getRepaidPeriods());
            System.out.println("Remaining Amount after month " + i + ": " + loanAccount.getRemainingAmount());
        }

        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(loanAccount.getIsEnded());
    }

    //test full flow with zero interest rate
    @Test
    public void testFullFlow_ZeroInterestRate_EUQAL_INTEREST() {
        LoanAccount loanAccount= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 0,
                BigDecimal.ZERO, // 0% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST);
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(0, loanAccount.calculateTotalRepayment().compareTo(BigDecimal.valueOf(5000.00)));

        for(int i = 1; i < loanAccount.getTotalPeriods(); i++) {
            BigDecimal repayAmount = loanAccount.getMonthlyRepayment(i);
            assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(138.89))); //monthly repayment should be constant

            loanAccount.repayLoan();

            assertEquals(i, loanAccount.getRepaidPeriods());
            assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5000.00)
                                   .subtract(BigDecimal.valueOf(138.89).multiply(BigDecimal.valueOf(i)))));
        }
        //last month
        BigDecimal repayAmount = loanAccount.getMonthlyRepayment(36);
        assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(138.85))); //last month repayment
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(138.85)));
        loanAccount.repayLoan();
        assertEquals(36, loanAccount.getRepaidPeriods());
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(loanAccount.getIsEnded());
    }

    @Test
    public void testFullFlow_ZeroInterestRate_EQUAL_PRINCIPAL() {
        LoanAccount loanAccount= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 0,
                BigDecimal.ZERO, // 0% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_PRINCIPAL);
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(0, loanAccount.calculateTotalRepayment().compareTo(BigDecimal.valueOf(5000.00)));

        for(int i = 1; i < loanAccount.getTotalPeriods(); i++) {
            BigDecimal repayAmount = loanAccount.getMonthlyRepayment(i);
            System.out.println("Month " + i + " repayment: " + repayAmount);
            assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(138.89))); //monthly repayment should be constant

            loanAccount.repayLoan();

            assertEquals(i, loanAccount.getRepaidPeriods());
            System.out.println("Remaining Amount after month " + i + ": " + loanAccount.getRemainingAmount());
            assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5000.00).subtract(BigDecimal.valueOf(138.89).multiply(BigDecimal.valueOf(i)))));
        }

        //last month
        BigDecimal repayAmount = loanAccount.getMonthlyRepayment(36);
        assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(138.85)));
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(138.85)));
        loanAccount.repayLoan();
        assertEquals(36, loanAccount.getRepaidPeriods());
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(loanAccount.getIsEnded());
    }

    @Test
    public void testFullFlow_ZeroInterestRate_EQUAL_PRINCIPAL_AND_INTEREST() {
        LoanAccount loanAccount= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 0,
                BigDecimal.ZERO, // 0% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_PRINCIPAL_AND_INTEREST);
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(0, loanAccount.calculateTotalRepayment().compareTo(BigDecimal.valueOf(5000.00)));

        for(int i = 1; i < loanAccount.getTotalPeriods(); i++) {
            BigDecimal repayAmount = loanAccount.getMonthlyRepayment(i);
            assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(138.89))); //monthly repayment should be constant

            loanAccount.repayLoan();

            assertEquals(i, loanAccount.getRepaidPeriods());
            assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5000.00).subtract(BigDecimal.valueOf(138.89).multiply(BigDecimal.valueOf(i)))));
        }
        //last month
        BigDecimal repayAmount = loanAccount.getMonthlyRepayment(36);
        assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(138.85)));
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(138.85)));
        loanAccount.repayLoan();
        assertEquals(36, loanAccount.getRepaidPeriods());
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(loanAccount.getIsEnded());
    }

    @Test
    public void testFullFlow_ZeroInterestRate_INTEREST_BEFORE_PRINCIPAL() {
        LoanAccount loanAccount= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 0,
                BigDecimal.ZERO, // 0% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(),
                LoanAccount.RepaymentType.INTEREST_BEFORE_PRINCIPAL);
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(0, loanAccount.calculateTotalRepayment().compareTo(BigDecimal.valueOf(5000.00)));

        for(int i = 1; i < loanAccount.getTotalPeriods(); i++) {
            BigDecimal repayAmount = loanAccount.getMonthlyRepayment(i);
            assertEquals(0, repayAmount.compareTo(BigDecimal.ZERO)); //first 35 months repayment should be 0

            loanAccount.repayLoan();

            assertEquals(i, loanAccount.getRepaidPeriods());
            assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5000.00)));
        }

        //last month
        BigDecimal repayAmount = loanAccount.getMonthlyRepayment(36);
        assertEquals(0, repayAmount.compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(5000.00)));
        loanAccount.repayLoan();
        assertEquals(36, loanAccount.getRepaidPeriods());
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(loanAccount.getIsEnded());
    }

    //test remaining amount calculation
    @Test
    public void testCalculateRemainingAmountWithRepaidPeriods() {
        LoanAccount loanAccount= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 2,
                BigDecimal.valueOf(1.00), // 1% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST);
        assertEquals(0, loanAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(4795.36)));

        LoanAccount loanAccount1= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 2,
                BigDecimal.valueOf(1.00), // 1% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_PRINCIPAL);
        assertEquals(0, loanAccount1.getRemainingAmount().compareTo(BigDecimal.valueOf(4791.08)));

        LoanAccount loanAccount2= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 2,
                BigDecimal.valueOf(1.00), // 1% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_PRINCIPAL_AND_INTEREST);
        assertEquals(0, loanAccount2.getRemainingAmount().compareTo(BigDecimal.valueOf(4864.04)));

        LoanAccount loanAccount3= new LoanAccount("Test Loan", testUser, "Test Note", true,
                36, 2,
                BigDecimal.valueOf(1.00), // 1% interest rate
                BigDecimal.valueOf(5000.00), // Loan amount
                LocalDate.now(), LoanAccount.RepaymentType.INTEREST_BEFORE_PRINCIPAL);
        assertEquals(0, loanAccount3.getRemainingAmount().compareTo(BigDecimal.valueOf(5141.78)));
    }
}
