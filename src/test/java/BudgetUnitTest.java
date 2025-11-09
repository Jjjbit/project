import com.ledger.domain.Budget;
import com.ledger.domain.Ledger;
import com.ledger.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetUnitTest {
    private User testUser;
    private Ledger testLedger;

    @BeforeEach
    public void setUp() {
        testUser = new User("testuser", "password123");
        testLedger = new Ledger("Test Ledger", testUser);
    }
    @Test
    public void testGetStartDateForPeriod_Monthly() {
        LocalDate today = LocalDate.of(2025, 10, 15);

        LocalDate startDate = Budget.calculateStartDateForPeriod(today, Budget.Period.MONTHLY);

        assertEquals(LocalDate.of(2025, 10, 1), startDate);
    }

    @Test
    public void testGetStartDateForPeriod_Yearly() {
        LocalDate today = LocalDate.of(2025, 10, 15);

        LocalDate startDate = Budget.calculateStartDateForPeriod(today, Budget.Period.YEARLY);

        assertEquals(LocalDate.of(2025, 1, 1), startDate);
    }

    @Test
    public void testGetEndDateForPeriod_Monthly() {
        LocalDate startDate = LocalDate.of(2025, 10, 1);

        LocalDate endDate = Budget.calculateEndDateForPeriod(startDate, Budget.Period.MONTHLY);

        assertEquals(LocalDate.of(2025, 10, 31), endDate);
    }

    @Test
    public void testGetEndDateForPeriod_Monthly_February() {
        LocalDate startDate = LocalDate.of(2025, 2, 1);

        LocalDate endDate = Budget.calculateEndDateForPeriod(startDate, Budget.Period.MONTHLY);

        assertEquals(LocalDate.of(2025, 2, 28), endDate);
    }

    @Test
    public void testGetEndDateForPeriod_Monthly_LeapYear() {
        LocalDate startDate = LocalDate.of(2024, 2, 1);

        LocalDate endDate = Budget.calculateEndDateForPeriod(startDate, Budget.Period.MONTHLY);

        assertEquals(LocalDate.of(2024, 2, 29), endDate);
    }

    @Test
    public void testGetEndDateForPeriod_Yearly() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);

        LocalDate endDate = Budget.calculateEndDateForPeriod(startDate, Budget.Period.YEARLY);

        assertEquals(LocalDate.of(2025, 12, 31), endDate);
    }

    //refresh
    @Test
    public void testRefreshBudget_Monthly() {
        Budget budget = new Budget(BigDecimal.valueOf(500),
                Budget.Period.MONTHLY,
                null,
                testLedger);
        budget.setStartDate(LocalDate.of(2025, 9, 1));
        budget.setEndDate(LocalDate.of(2025, 9, 30));

        budget.refreshIfExpired();

        assertEquals(BigDecimal.ZERO, budget.getAmount());
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), budget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), budget.getEndDate());
    }

    @Test
    public void testRefreshBudget_Yearly() {
        Budget budget = new Budget(BigDecimal.valueOf(2000),
                Budget.Period.YEARLY,
                null,
                testLedger);
        budget.setStartDate(LocalDate.of(2024, 1, 1));
        budget.setEndDate(LocalDate.of(2024, 12, 31));

        budget.refreshIfExpired();

        assertEquals(BigDecimal.ZERO, budget.getAmount());
        assertEquals(LocalDate.of(LocalDate.now().getYear(),1,1), budget.getStartDate());
        assertEquals(LocalDate.of(LocalDate.now().getYear(),12,31), budget.getEndDate());
    }

    @Test
    public void testRefreshBudget_NotExpired() {
        Budget budget = new Budget(BigDecimal.valueOf(800),
                Budget.Period.MONTHLY,
                null,
                testLedger);
        budget.setStartDate(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()));
        budget.setEndDate(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()));

        budget.refreshIfExpired();

        assertEquals(BigDecimal.valueOf(800), budget.getAmount());
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), budget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), budget.getEndDate());
    }


}
