import com.ledger.domain.Budget;
import com.ledger.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetUnitTest {
    private User testUser;

    @BeforeEach
    public void setUp() {
        testUser = new User("testuser", "password123");
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

    @Test
    public void testIsActive_Monthly_Active() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 10, 1));
        budget.setEndDate(LocalDate.of(2025, 10, 31));
        LocalDate dateToCheck = LocalDate.of(2025, 10, 15);

        boolean isActive = budget.isActive(dateToCheck);

        assertTrue(isActive);
    }

    @Test
    public void testIsActive_Monthly_Inactive() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 11, 1);

        boolean isActive = budget.isActive(dateToCheck);

        assertFalse(isActive);
    }

    @Test
    public void testIsActive_Monthly_LastDayActive() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 10, 1));
        budget.setEndDate(LocalDate.of(2025, 10, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 10, 31);

        boolean isActive = budget.isActive(dateToCheck);

        assertTrue(isActive);
    }

    @Test
    public void testIsActive_Yearly_Active() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.YEARLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 12, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 6, 15);

        boolean isActive = budget.isActive(dateToCheck);

        assertTrue(isActive);
    }


    @Test
    public void testIsActive_Yearly_Inactive() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.MONTHLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2024, 1, 1));
        budget.setEndDate(LocalDate.of(2024, 12, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 1, 1);

        boolean isActive = budget.isActive(dateToCheck);

        assertFalse(isActive);
    }

    @Test
    public void testIsActive_Yearly_LastDayActive() {
        Budget budget = new Budget(BigDecimal.valueOf(1000),
                Budget.Period.YEARLY,
                null,
                testUser);
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 12, 31));

        LocalDate dateToCheck = LocalDate.of(2025, 12, 31);

        boolean isActive = budget.isActive(dateToCheck);

        assertTrue(isActive);
    }
}
