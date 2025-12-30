import com.ledger.domain.Budget;
import com.ledger.domain.Ledger;
import com.ledger.domain.Period;
import com.ledger.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BudgetUnitTest {
    private Ledger testLedger;

    @BeforeEach
    public void setUp() {
        User testUser = new User("test user", "password123");
        testLedger = new Ledger("Test Ledger", testUser);
    }

    //refresh
    @Test
    public void testRefreshBudget_Monthly() {
        Budget budget = new Budget(BigDecimal.valueOf(500), Period.MONTHLY, null, testLedger);
        budget.setStartDate(LocalDate.of(2025, 9, 1));
        budget.setEndDate(LocalDate.of(2025, 9, 30));

        budget.refreshIfExpired();

        assertEquals(BigDecimal.ZERO, budget.getAmount());
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), budget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), budget.getEndDate());
    }

    @Test
    public void testRefreshBudget_Yearly() {
        Budget budget = new Budget(BigDecimal.valueOf(2000), Period.YEARLY, null, testLedger);
        budget.setStartDate(LocalDate.of(2024, 1, 1));
        budget.setEndDate(LocalDate.of(2024, 12, 31));

        budget.refreshIfExpired();

        assertEquals(BigDecimal.ZERO, budget.getAmount());
        assertEquals(LocalDate.of(LocalDate.now().getYear(),1,1), budget.getStartDate());
        assertEquals(LocalDate.of(LocalDate.now().getYear(),12,31), budget.getEndDate());
    }

    @Test
    public void testRefreshBudget_NotExpired() {
        Budget budget = new Budget(BigDecimal.valueOf(800), Period.MONTHLY, null, testLedger);
        budget.setStartDate(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()));
        budget.setEndDate(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()));

        budget.refreshIfExpired();

        assertEquals(BigDecimal.valueOf(800), budget.getAmount());
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), budget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), budget.getEndDate());
    }


}
