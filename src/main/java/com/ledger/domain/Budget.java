package com.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public class Budget {
    public enum Period {
        MONTHLY, YEARLY
    }

    private Long id;
    private BigDecimal amount=BigDecimal.ZERO; // Budget amount
    private Period period; // e.g., "monthly", "yearly"
    private LedgerCategory category; // Category or subcategory
    private transient LocalDate startDate;
    private transient LocalDate endDate;
    private Ledger ledger;

    public Budget(){}
    public Budget(BigDecimal amount, Period period, LedgerCategory category, Ledger ledger) {
        this.ledger = ledger;
        this.amount = amount;
        this.period = period;
        this.category = category;
        this.startDate = calculateStartDateForPeriod(LocalDate.now(), this.period);
        this.endDate = calculateEndDateForPeriod(this.startDate, this.period);
    }
    public static LocalDate calculateStartDateForPeriod(LocalDate today, Period budgetPeriod) {
        return switch (budgetPeriod) {
            case YEARLY -> LocalDate.of(today.getYear(), 1, 1);
            case MONTHLY -> LocalDate.of(today.getYear(), today.getMonth(), 1);
        };
    }
    public static LocalDate calculateEndDateForPeriod(LocalDate startDate, Period period) {
        return switch (period) {
            case YEARLY -> LocalDate.of(startDate.getYear(), 12, 31);
            case MONTHLY -> YearMonth.from(startDate).atEndOfMonth();
        };
    }


    public Ledger getLedger() {
        return ledger;
    }
    public void setLedger(Ledger ledger) {
        this.ledger = ledger;
    }
    public void setCategory(LedgerCategory category) {
        this.category = category;
    }
    public LedgerCategory getCategory() {
        return category;
    }
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public Period getPeriod() {
        return period;
    }
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
    public void setPeriod(Period period) {
        this.period = period;
    }
    public LocalDate getStartDate() {
        return startDate;
    }
    public LocalDate getEndDate() {
        return endDate;
    }

    public void refreshIfExpired() { //updates start and end date if budget period has passed
        LocalDate today = LocalDate.now();
        if (today.isAfter(endDate)) {
            amount = BigDecimal.ZERO; //reset amount
            startDate = calculateStartDateForPeriod(today, period);
            endDate = calculateEndDateForPeriod(startDate, period);
        }
    }

}


