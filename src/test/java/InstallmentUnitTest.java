import com.ledger.domain.Installment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InstallmentUnitTest {

    //full repayment flow with EVENLY_SPLIT - repayOnePeriod called multiple times until full repayment
    @Test
    public void testFullRepaymentFlow_EvenlySplit() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5, BigDecimal.valueOf(5.00), 0,
                Installment.Strategy.EVENLY_SPLIT,
                null,
                LocalDate.now(),
                null, true);


        for (int i = 0; i < 5; i++) {
            assertEquals(0, plan.getMonthlyPayment(plan.getPaidPeriods() + 1).compareTo(BigDecimal.valueOf(210.00)));
            plan.repayOnePeriod();
        }

        assertEquals(5, plan.getPaidPeriods());
        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.ZERO));
    }

    //full repayment flow with UPFRONT strategy
    @Test
    public void testFullRepaymentFlow_UpfrontStrategy() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.valueOf(10.00), // 10%
                0,
                Installment.Strategy.UPFRONT,
                null,
                LocalDate.now(),
                null, true);

        assertEquals(0, plan.getTotalPayment().compareTo(BigDecimal.valueOf(1100.00)));

        // First payment should include the upfront fee
        BigDecimal firstPayment = plan.getMonthlyPayment(plan.getPaidPeriods() + 1);
        assertEquals(0, firstPayment.compareTo(BigDecimal.valueOf(300.00))); // 200 + 100 upfront fee
        //repay first period
        plan.repayOnePeriod();

        //from second to fifth period
        for(int i = 0; i < 5; i++) {
            BigDecimal payment= plan.getMonthlyPayment(plan.getPaidPeriods() + 1);
            assertEquals(0, payment.compareTo(BigDecimal.valueOf(200.00)));
            plan.repayOnePeriod();
        }

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();
        assertEquals(0, remaining.compareTo(BigDecimal.ZERO));
    }

    //full repayment flow with FINAL strategy
    @Test
    public void testFullRepaymentFlow_FinalStrategy() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                new BigDecimal("10.00"), // 10%
                0,
                Installment.Strategy.FINAL,
                null,
                LocalDate.now(),
                null, true);

        for (int i = 0; i < 4; i++) {
            BigDecimal payment = plan.getMonthlyPayment(plan.getPaidPeriods() + 1);
            assertEquals(0, payment.compareTo(BigDecimal.valueOf(200.00)));
            plan.repayOnePeriod();
        }

        //last period should include the final fee
        BigDecimal lastPayment = plan.getMonthlyPayment(plan.getPaidPeriods() + 1);
        assertEquals(plan.getRemainingAmount(), lastPayment); // 200 + 100 final fee

        plan.repayOnePeriod();
        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.ZERO));
    }


    // Boundary test for feeRate = 0
    @Test
    public void testFullRepaymentFlow_ZeroFeeRate_EvenlySplit() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.ZERO,
                0,
                Installment.Strategy.EVENLY_SPLIT,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal totalPayment = plan.getTotalPayment();
        assertEquals(0, totalPayment.compareTo(BigDecimal.valueOf(1000.00)));

        for (int i = 0; i < 5; i++) {
            assertEquals(0, plan.getMonthlyPayment(plan.getPaidPeriods() + 1).compareTo(BigDecimal.valueOf(200.00)));
            plan.repayOnePeriod();
        }
        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(5, plan.getPaidPeriods());
    }
    @Test
    public void FullRepaymentFlow_ZeroFeeRate_Upfront() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.ZERO,
                0,
                Installment.Strategy.UPFRONT,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal totalPayment = plan.getTotalPayment();
        assertEquals(0, totalPayment.compareTo(BigDecimal.valueOf(1000.00)));

        for ( int i = 0; i < 5; i++) {
            assertEquals(0, plan.getMonthlyPayment(plan.getPaidPeriods() + 1).compareTo(BigDecimal.valueOf(200.00)));
            plan.repayOnePeriod();
        }

        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(5, plan.getPaidPeriods());
    }

    @Test
    public void FullRepaymentFlow_ZeroFeeRate_Final() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.ZERO,
                0,
                Installment.Strategy.FINAL,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal totalPayment = plan.getTotalPayment();
        assertEquals(0, totalPayment.compareTo(BigDecimal.valueOf(1000.00)));

        for( int i = 0; i < 5; i++) {
            assertEquals(0, plan.getMonthlyPayment(plan.getPaidPeriods() + 1).compareTo(BigDecimal.valueOf(200.00)));
            plan.repayOnePeriod();
        }

        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(5, plan.getPaidPeriods());

    }

    // Boundary test for high feeRate
    @Test
    public void testFullRepaymentFlow_HighFeeRate_EvenlySplit() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.valueOf(50.00), // 50%
                0,
                Installment.Strategy.EVENLY_SPLIT,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal totalPayment = plan.getTotalPayment();
        assertEquals(0, totalPayment.compareTo(BigDecimal.valueOf(1500.00)));

        for( int i = 0; i < 5; i++) {
            assertEquals(0, plan.getMonthlyPayment(plan.getPaidPeriods() + 1).compareTo(BigDecimal.valueOf(300.00)));
            plan.repayOnePeriod();
        }
        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(5, plan.getPaidPeriods());
    }

    @Test
    public void testFullRepaymentFlow_HighFeeRate_Upfront() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.valueOf(50.00), // 50%
                0,
                Installment.Strategy.UPFRONT,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal totalPayment = plan.getTotalPayment();
        assertEquals(0, totalPayment.compareTo(BigDecimal.valueOf(1500.00)));

        assertEquals(0, plan.getMonthlyPayment(plan.getPaidPeriods() + 1).compareTo(BigDecimal.valueOf(700.00))); // first payment
        plan.repayOnePeriod();

        for ( int i = 0; i < 4; i++) {
            assertEquals(0, plan.getMonthlyPayment(plan.getPaidPeriods() + 1).compareTo(BigDecimal.valueOf(200.00)));
            plan.repayOnePeriod();
        }

        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(5, plan.getPaidPeriods());
    }

    @Test
    public void testFullRepaymentFlow_HighFeeRate_Final() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.valueOf(50.00), // 50%
                0,
                Installment.Strategy.FINAL,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal totalPayment = plan.getTotalPayment();
        assertEquals(0, totalPayment.compareTo(BigDecimal.valueOf(1500.00)));

        for ( int i = 0; i < 4; i++) {
            assertEquals(0, plan.getMonthlyPayment(plan.getPaidPeriods() + 1).compareTo(BigDecimal.valueOf(200.00)));
            plan.repayOnePeriod();
        }

        assertEquals(0, plan.getMonthlyPayment(plan.getPaidPeriods() + 1).compareTo(BigDecimal.valueOf(700.00))); // last payment
        plan.repayOnePeriod();

        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(5, plan.getPaidPeriods());
    }

    // Boundary test for totalPeriods = 1
    @Test
    public void testGetMonthlyRepayment_EvenlySplit() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.valueOf(5.00),
                0,
                Installment.Strategy.EVENLY_SPLIT,
                null,
                LocalDate.now(),
                null, true);
        //1000*0.05=50 interest
        //total=1050
        //monthly=1050/5=210

        BigDecimal payment = plan.getMonthlyPayment(1);
        assertEquals(0, payment.compareTo(BigDecimal.valueOf(210.00)));
    }

    @Test
    public void testGetMonthlyRepayment_Upfront() {
        Installment plan = new Installment(null, BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.valueOf(5.00),
                0,
                Installment.Strategy.UPFRONT,
                null,
                LocalDate.now(),
                null, true);
        //1000*0.05=50 upfront interest
        //total=1050
        //first payment=50+1000/5=50+200=250
        //subsequent=1000/5=200

        BigDecimal firstPayment = plan.getMonthlyPayment(1);
        BigDecimal subsequentPayment = plan.getMonthlyPayment(2);

        assertEquals(0, firstPayment.compareTo(BigDecimal.valueOf(250.00)));
        assertEquals(0, subsequentPayment.compareTo(BigDecimal.valueOf(200.00)));
    }

    @Test
    public void testGetMonthlyRepayment_Final() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                5,
                BigDecimal.valueOf(5.00),
                0,
                Installment.Strategy.FINAL,
                null,
                LocalDate.now(),
                null, true);
        //1000*0.05=50 final interest
        //total=1050
        //last payment=50+1000/5=50+200=250
        //prior=1000/5=200

        BigDecimal lastPayment = plan.getMonthlyPayment(5);
        BigDecimal priorPayment = plan.getMonthlyPayment(4);

        assertEquals(0, lastPayment.compareTo(BigDecimal.valueOf(250.00)));
        assertEquals(0, priorPayment.compareTo(BigDecimal.valueOf(200.00)));
    }

    // Test for decimal precision and rounding
    @Test
    public void testGetMonthlyRepayment_DecimalPrecision_EvenlySplit() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1234.56),
                5,
                BigDecimal.valueOf(3.00),
                0,
                Installment.Strategy.EVENLY_SPLIT,
                null,
                LocalDate.now(),
                null, true);


        BigDecimal payment = plan.getMonthlyPayment(1);
        BigDecimal totalPayment = plan.getTotalPayment();

        //total fee= 1234.56 * 0.03 = 37.0368 -> 37.04
        assertEquals(0, payment.compareTo(BigDecimal.valueOf(254.32))); // (1234.56 + 37.04) / 5 = 254.32
        assertEquals(0, totalPayment.compareTo(BigDecimal.valueOf(1271.60))); // 1234.56 + 37.04 = 1271.60
    }

    @Test
    public void testGetMonthlyRepayment_DecimalPrecision_Upfront() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1234.56),
                5,
                BigDecimal.valueOf(3.00),
                0,
                Installment.Strategy.UPFRONT,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal firstPayment = plan.getMonthlyPayment(1);
        BigDecimal subsequentPayment = plan.getMonthlyPayment(2);
        BigDecimal totalPayment = plan.getTotalPayment();

        // total fee= 1234.56 * 0.03 = 37.0368 -> 37.04
        assertEquals(0, firstPayment.compareTo(BigDecimal.valueOf(283.95))); // 246.91 + 37.04 = 283.95
        assertEquals(0, subsequentPayment.compareTo(BigDecimal.valueOf(246.91))); // 1234.56 / 5 = 246.912 -> 246.91
        assertEquals(0, totalPayment.compareTo(BigDecimal.valueOf(1271.60))); // 1234.56 + 37.04 = 1271.60
    }
    @Test
    public void testGetMonthlyRepayment_DecimalPrecision_Final() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1234.56),
                5,
                BigDecimal.valueOf(3.00),
                0,
                Installment.Strategy.FINAL,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal lastPayment = plan.getMonthlyPayment(5);
        BigDecimal priorPayment = plan.getMonthlyPayment(4);
        BigDecimal totalPayment = plan.getTotalPayment();

        // total fee= 1234.56 * 0.03 = 37.0368 -> 37.04
        assertEquals(0, lastPayment.compareTo(BigDecimal.valueOf(283.95))); // 246.91 + 37.04 = 283.95
        assertEquals(0, priorPayment.compareTo(BigDecimal.valueOf(246.91))); // 1234.56 / 5 = 246.912 -> 246.91
        assertEquals(0, totalPayment.compareTo(BigDecimal.valueOf(1271.60))); // 1234.56 + 37.04 = 1271.60
    }

    //Boundary test for small totalAmount
    @Test
    public void testGetTotalPayment_SmallAmount() {
        Installment plan = new Installment();
        plan.setTotalAmount(BigDecimal.valueOf(10.00));
        plan.setInterest(BigDecimal.valueOf(2.50)); // 2.5%

        BigDecimal total = plan.getTotalPayment();

        // 10 + 0.25 = 10.25
        assertEquals(0, total.compareTo(BigDecimal.valueOf(10.25)));
    }

    //test for getRemainingAmountWithRepaidPeriods() method
    //test for getRemainingAmountWithRepaidPeriods with EVENLY_SPLIT - paidPeriods = 0 - loop from 1 to totalPeriods
    @Test
    void testGetRemainingAmount_NoPaidPeriods_LoopFullRange() {
        Installment plan = new Installment();
        plan.setTotalAmount(BigDecimal.valueOf(1000.00));
        plan.setTotalPeriods(10);
        plan.setInterest(BigDecimal.valueOf(5.00));
        plan.setPaidPeriods(0);
        plan.setStrategy(Installment.Strategy.EVENLY_SPLIT);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        assertEquals(0, remaining.compareTo(BigDecimal.valueOf(1050.00)));
    }

    //paidPeriods > 0 - loop from paidPeriods + 1 to totalPeriods
    @Test
    public void testGetRemainingAmount_SomePaidPeriods_LoopFromPaidPlus1() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                10,
                BigDecimal.valueOf(5.00),
                5,
                Installment.Strategy.EVENLY_SPLIT,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        assertEquals(0, remaining.compareTo(BigDecimal.valueOf(525.00)));
    }
    @Test
    public void testGetRemainingAmount_SomePaidPeriods_Upfront() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                10,
                BigDecimal.valueOf(5.00),
                5,
                Installment.Strategy.UPFRONT,
                null,
                LocalDate.now(),
                null, true);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        // first period = 150
        // subsequent periods = 100 each
        assertEquals(0, remaining.compareTo(BigDecimal.valueOf(500.00)));
    }
    @Test
    public void testGetRemainingAmount_SomePaidPeriods_Final() {
        Installment plan = new Installment(null,
                BigDecimal.valueOf(1000.00),
                10,
                BigDecimal.valueOf(5.00),
                5,
                Installment.Strategy.FINAL,
                null,
                LocalDate.now(),
                null, true);
        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();
        // last period = 150
        // prior periods = 100 each
        assertEquals(0, remaining.compareTo(BigDecimal.valueOf(550.00)));
    }

    //paidPeriods = totalPeriods-1 - loop executes once
    @Test
    void testGetRemainingAmount_OnePeriodLeft_LoopOnce() {
        Installment plan = new Installment();
        plan.setTotalAmount(new BigDecimal("1000.00"));
        plan.setTotalPeriods(10);
        plan.setInterest(new BigDecimal("5.00"));
        plan.setPaidPeriods(9);
        plan.setStrategy(Installment.Strategy.EVENLY_SPLIT);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        assertEquals(new BigDecimal("105.00"), remaining);
    }

    //boundary test - paidPeriods = totalPeriods - loop not executed
    @Test
    void testGetRemainingAmount_AllPaid_LoopNotExecuted() {
        Installment plan = new Installment();
        plan.setTotalAmount(new BigDecimal("1000.00"));
        plan.setTotalPeriods(10);
        plan.setInterest(new BigDecimal("5.00"));
        plan.setPaidPeriods(10);
        plan.setStrategy(Installment.Strategy.EVENLY_SPLIT);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        assertEquals(new BigDecimal("0.00"), remaining);
    }

    //getRemainingAmountWithRepaidPeriods with UPFRONT - paidPeriods = 0
    @Test
    void testGetRemainingAmount_UpfrontStrategy() {
        Installment plan = new Installment();
        plan.setTotalAmount(new BigDecimal("1000.00"));
        plan.setTotalPeriods(10);
        plan.setInterest(new BigDecimal("5.00"));
        plan.setPaidPeriods(0);
        plan.setStrategy(Installment.Strategy.UPFRONT);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        // first period = 150
        // subsequent periods = 100 each
        assertEquals(new BigDecimal("1050.00"), remaining);
    }

    //getRemainingAmountWithRepaidPeriods with UPFRONT - paidPeriods > 0
    @Test
    void testGetRemainingAmount_UpfrontStrategy_FirstPeriodPaid() {
        Installment plan = new Installment();
        plan.setTotalAmount(new BigDecimal("1000.00"));
        plan.setTotalPeriods(10);
        plan.setInterest(new BigDecimal("5.00"));
        plan.setPaidPeriods(1);
        plan.setStrategy(Installment.Strategy.UPFRONT);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        assertEquals(new BigDecimal("900.00"), remaining);
    }

    //get RemainingAmountWithRepaidPeriods with FINAL - paidPeriods = 0
    @Test
    void testGetRemainingAmount_FinalStrategy() {
        Installment plan = new Installment();
        plan.setTotalAmount(new BigDecimal("1000.00"));
        plan.setTotalPeriods(10);
        plan.setInterest(new BigDecimal("5.00"));
        plan.setPaidPeriods(0);
        plan.setStrategy(Installment.Strategy.FINAL);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        assertEquals(new BigDecimal("1050.00"), remaining);
    }

    //get RemainingAmountWithRepaidPeriods with FINAL - paidPeriods = totalPeriods-1
    @Test
    void testGetRemainingAmount_FinalStrategy_OnlyLastPeriod() {
        Installment plan = new Installment();
        plan.setTotalAmount(new BigDecimal("1000.00"));
        plan.setTotalPeriods(10);
        plan.setInterest(new BigDecimal("5.00"));
        plan.setPaidPeriods(9);
        plan.setStrategy(Installment.Strategy.FINAL);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        assertEquals(new BigDecimal("150.00"), remaining);
    }

    //boundary test - totalPeriods = 1
    @Test
    void testGetRemainingAmount_SinglePeriod() {
        Installment plan = new Installment();
        plan.setTotalAmount(new BigDecimal("1000.00"));
        plan.setTotalPeriods(1);
        plan.setInterest(new BigDecimal("5.00"));
        plan.setPaidPeriods(0);
        plan.setStrategy(Installment.Strategy.EVENLY_SPLIT);

        BigDecimal remaining = plan.getRemainingAmountWithRepaidPeriods();

        assertEquals(new BigDecimal("1050.00"), remaining);
    }

    //feeRate = 0 boundary test
    @Test
    void testGetRemainingAmount_ZeroFeeRate_AllStrategiesSame() {
        BigDecimal totalAmount = new BigDecimal("1000.00");
        int totalPeriods = 10;
        int paidPeriods = 3;

        Installment plan1 = new Installment();
        plan1.setTotalAmount(totalAmount);
        plan1.setTotalPeriods(totalPeriods);
        plan1.setInterest(BigDecimal.ZERO);
        plan1.setPaidPeriods(paidPeriods);
        plan1.setStrategy(Installment.Strategy.EVENLY_SPLIT);

        Installment plan2 = new Installment();
        plan2.setTotalAmount(totalAmount);
        plan2.setTotalPeriods(totalPeriods);
        plan2.setInterest(BigDecimal.ZERO);
        plan2.setPaidPeriods(paidPeriods);
        plan2.setStrategy(Installment.Strategy.UPFRONT);

        Installment plan3 = new Installment();
        plan3.setTotalAmount(totalAmount);
        plan3.setTotalPeriods(totalPeriods);
        plan3.setInterest(BigDecimal.ZERO);
        plan3.setPaidPeriods(paidPeriods);
        plan3.setStrategy(Installment.Strategy.FINAL);

        BigDecimal remaining1 = plan1.getRemainingAmountWithRepaidPeriods();
        BigDecimal remaining2 = plan2.getRemainingAmountWithRepaidPeriods();
        BigDecimal remaining3 = plan3.getRemainingAmountWithRepaidPeriods();

        assertEquals(remaining1, remaining2);
        assertEquals(remaining1, remaining3);
        assertEquals(new BigDecimal("700.00"), remaining1);
    }


}
