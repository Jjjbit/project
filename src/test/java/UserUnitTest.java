import com.ledger.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserUnitTest {

    private User testUser;

    @BeforeEach
    public void setUp() {
        testUser = new User("Alice", PasswordUtils.hash("password"));
    }

    @Test
    public void testGetTotalLending() {
        LendingAccount lending1 = new LendingAccount(
                "Lend to Friend A",
                BigDecimal.valueOf(1000),
                null,
                true,  // includedInNetAsset
                true, //selected
                testUser,
                LocalDate.now()
        );

        LendingAccount lending2 = new LendingAccount(
                "Lend to Friend B",
                BigDecimal.valueOf(500),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );


        LendingAccount lending3 = new LendingAccount(
                "Excluded Lending",
                BigDecimal.valueOf(300),
                null,
                false, // not includedInNetAsset
                true,
                testUser,
                LocalDate.now()
        );

        // Edge case: hidden account
        LendingAccount lending4 = new LendingAccount(
                "Hidden Lending",
                BigDecimal.valueOf(200),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );
        lending4.hide(); // hide this account

        // Edge case: zero balance
        LendingAccount lending5 = new LendingAccount(
                "Zero Balance Lending",
                BigDecimal.ZERO,
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        testUser.getAccounts().add(lending1);
        testUser.getAccounts().add(lending2);
        testUser.getAccounts().add(lending3);
        testUser.getAccounts().add(lending4);
        testUser.getAccounts().add(lending5);



        BigDecimal totalLending = testUser.getTotalLending();
        assertEquals(0, BigDecimal.valueOf(1500).compareTo(totalLending));
    }

    @Test
    public void testGetTotalBorrowing() {
        BorrowingAccount borrowing1 = new BorrowingAccount(
                "Borrow from Friend A",
                BigDecimal.valueOf(2000), //borrowing amount
                null,
                true,  // includedInNetAsset
                true, // selectable
                testUser,
                LocalDate.now()
        );

        BorrowingAccount borrowing2 = new BorrowingAccount(
                "Borrow from Friend B",
                BigDecimal.valueOf(1000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        // Edge case: excluded from net worth
        BorrowingAccount borrowing3 = new BorrowingAccount(
                "Excluded Borrowing",
                BigDecimal.valueOf(500),
                null,
                false, // NOT includedInNetWorth
                true,
                testUser,
                LocalDate.now()
        );

        // Edge case: hidden account
        BorrowingAccount borrowing4 = new BorrowingAccount(
                "Hidden Borrowing",
                BigDecimal.valueOf(300),
                null,
                true,
                true, // hidden
                testUser,
                LocalDate.now()
        );
        borrowing4.hide(); // hide this account

        testUser.getAccounts().add(borrowing1);
        testUser.getAccounts().add(borrowing2);
        testUser.getAccounts().add(borrowing3);
        testUser.getAccounts().add(borrowing4);


        // only borrowing1 and borrowing2 should be counted
        BigDecimal totalBorrowing = testUser.getTotalBorrowing();
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(totalBorrowing));
    }

    @Test
    public void testGetTotalAssets() {
        BasicAccount cash = new BasicAccount(
                "Cash",
                BigDecimal.valueOf(5000),
                null,
                true,
                false,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );


        // create LendingAccount
        LendingAccount lending = new LendingAccount(
                "Lend to Friend",
                BigDecimal.valueOf(2000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        // create CreditAccount (with currentDebt, should not count as asset)
        CreditAccount creditCard = new CreditAccount(
                "Credit Card",
                BigDecimal.valueOf(500), // balance
                testUser,
                null,
                false,
                true,
                BigDecimal.valueOf(10000), // creditLimit
                BigDecimal.valueOf(9500), // currentDebt
                null,
                null,
                AccountType.CREDIT_CARD
        );

        // create LoanAccount (should not count as asset)
        LoanAccount loan = new LoanAccount(
                "Mortgage",
                testUser,
                null,
                false,
                36,
                0,
                BigDecimal.ZERO, //interestRate
                BigDecimal.valueOf(100000), //loanAmount
                creditCard, //receving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );

        // create BorrowingAccount (should not count as asset)
        BorrowingAccount borrowing = new BorrowingAccount(
                "Borrow from Friend",
                BigDecimal.valueOf(1000),
                null,
                false,
                true,
                testUser,
                LocalDate.now()
        );


        BasicAccount hiddenAccount = new BasicAccount(
                "Hidden Cash",
                BigDecimal.valueOf(3000),
                null,
                true,
                true, // hidden
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );
        hiddenAccount.hide(); // hide this account

        testUser.getAccounts().add(cash);
        testUser.getAccounts().add(lending);
        testUser.getAccounts().add(creditCard);
        testUser.getAccounts().add(loan);
        testUser.getAccounts().add(borrowing);
        testUser.getAccounts().add(hiddenAccount);

        // total assets should be cash + lending = 5000 + 2000 = 7000
        BigDecimal totalAssets = testUser.getTotalAssets();
        assertEquals(0, BigDecimal.valueOf(7000).compareTo(totalAssets));
    }

    @Test
    public void testGetTotalLiabilities() {
        // create CreditAccounts (with currentDebt)
        CreditAccount creditCard1 = new CreditAccount(
                "Credit Card 1",
                BigDecimal.valueOf(1000), // balance
                testUser,
                null,
                true,
                false,
                BigDecimal.valueOf(10000), // creditLimit
                BigDecimal.valueOf(3000), // currentDebt
                null,
                null,
                AccountType.CREDIT_CARD
        );

        CreditAccount creditCard2 = new CreditAccount(
                "Credit Card 2",
                BigDecimal.valueOf(500),
                testUser,
                null,
                true,
                false,
                BigDecimal.valueOf(5000), // creditLimit
                BigDecimal.valueOf(2000), // currentDebt
                null,
                null,
                AccountType.CREDIT_CARD
        );

        //create active LoanAccount
        LoanAccount activeLoan = new LoanAccount(
                "Car Loan",
                testUser,
                null,
                true,
                36,
                0,
                BigDecimal.ZERO, // interestRate
                BigDecimal.valueOf(50000), // loanAmount
                creditCard1, // receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );

        // create ended LoanAccount (should not count)
        LoanAccount endedLoan = new LoanAccount(
                "Ended Loan",
                testUser,
                null,
                true,
                36,
                36,
                BigDecimal.ZERO, // interestRate
                BigDecimal.valueOf(20000), // loanAmount
                creditCard2, // receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );

        // create BorrowingAccount (should count)
        BorrowingAccount borrowing = new BorrowingAccount(
                "Borrow from Friend",
                BigDecimal.valueOf(5000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()

        );

        // create hidden CreditAccount (should not count)
        CreditAccount hiddenCreditCard = new CreditAccount(
                "Hidden Credit Card",
                BigDecimal.valueOf(500),
                testUser,
                null,
                true,
                true,
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(1000),
                null,
                null,
                AccountType.CREDIT_CARD
        );
        hiddenCreditCard.hide(); // hide this account

        testUser.getAccounts().add(creditCard1);
        testUser.getAccounts().add(creditCard2);
        testUser.getAccounts().add(activeLoan);
        testUser.getAccounts().add(endedLoan);
        testUser.getAccounts().add(borrowing);
        testUser.getAccounts().add(hiddenCreditCard);


        // totalLiabilities = creditCard1Debt(3000) + creditCard2Debt(2000) + activeLoan(50000) + borrowing(5000) = 60000
        BigDecimal totalLiabilities = testUser.getTotalLiabilities();
        assertEquals(0, BigDecimal.valueOf(60000).compareTo(totalLiabilities));
    }

    @Test
    public void testGetNetAssets() {
        BasicAccount cash = new BasicAccount(
                "Cash",
                BigDecimal.valueOf(10000),
                null,
                true,
                true,
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser
        );

        LendingAccount lending = new LendingAccount(
                "Lending",
                BigDecimal.valueOf(5000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        // create CreditAccount with currentDebt
        CreditAccount creditCard = new CreditAccount(
                "Credit Card",
                BigDecimal.valueOf(500), // balance
                testUser,
                null,
                true,
                false,
                BigDecimal.valueOf(10000), // creditLimit
                BigDecimal.valueOf(3000), // currentDebt
                null,
                null,
                AccountType.CREDIT_CARD
        );

        BorrowingAccount borrowing = new BorrowingAccount(
                "Borrowing",
                BigDecimal.valueOf(2000),
                null,
                true,
                true,
                testUser,
                LocalDate.now()
        );

        testUser.getAccounts().add(cash);
        testUser.getAccounts().add(lending);
        testUser.getAccounts().add(creditCard);
        testUser.getAccounts().add(borrowing);



        BigDecimal netAssets = testUser.getNetAssets();
        assertEquals(0, BigDecimal.valueOf(10500).compareTo(netAssets));
    }

    @Test
    public void testNetAssetsWithAllAccountTypes() {
        BasicAccount cash = new BasicAccount("Cash", BigDecimal.valueOf(5000),
                null, true, false, AccountType.CASH, AccountCategory.FUNDS, testUser);
        BasicAccount savings = new BasicAccount("Savings", BigDecimal.valueOf(20000),
                null, true, false, AccountType.CASH, AccountCategory.FUNDS, testUser);
        LendingAccount lending = new LendingAccount("Lending", BigDecimal.valueOf(3000),
                null, true, true, testUser,LocalDate.now());

        //create account with debt
        CreditAccount creditCard = new CreditAccount("Credit Card", BigDecimal.valueOf(1000),
                testUser, null, true, false, BigDecimal.valueOf(10000),
                BigDecimal.valueOf(4000), null, null, AccountType.CREDIT_CARD);
        LoanAccount loan = new LoanAccount("Mortgage", testUser, null,
                true, 36, 0, BigDecimal.ZERO, BigDecimal.valueOf(80000), creditCard,
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST);
        BorrowingAccount borrowing = new BorrowingAccount("Borrowing", BigDecimal.valueOf(2000),
                null, true, true, testUser, LocalDate.now());

        testUser.getAccounts().addAll(List.of(cash, savings, lending, creditCard, loan, borrowing));


        assertEquals(0, BigDecimal.valueOf(29000).compareTo(testUser.getTotalAssets()));

        //  4000 + 80000 + 2000 = 86000
        assertEquals(0, BigDecimal.valueOf(86000).compareTo(testUser.getTotalLiabilities()));

        assertEquals(0, BigDecimal.valueOf(-57000).compareTo(testUser.getNetAssets()));
    }

    @Test
    public void testEmptyAccounts() {
        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalLending()));
        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalBorrowing()));
        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalAssets()));
        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getTotalLiabilities()));
        assertEquals(0, BigDecimal.ZERO.compareTo(testUser.getNetAssets()));
    }
}
