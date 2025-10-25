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
    private User owner; // User ID or name
    private LocalDate startDate;
    private LocalDate endDate;

    public Budget(){}
    public Budget(BigDecimal amount, Period period, LedgerCategory category, User owner) {
        this.amount = amount;
        this.period = period;
        this.category = category;
        this.owner = owner;
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


    public void setCategory(LedgerCategory category) {
        this.category = category;
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
    public LedgerCategory getCategory() {
        return category;
    }
    public Period getPeriod() {
        return period;
    }
    public User getOwner() {
        return owner;
    }
    public void setOwner(User owner) {
        this.owner = owner;
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


    public boolean isActive(LocalDate date) {
        return switch (period) {
            case MONTHLY -> date.isBefore(startDate.plusMonths(1));
            case YEARLY -> date.isBefore(startDate.plusYears(1));
        };
    }

}


