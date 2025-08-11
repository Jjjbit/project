package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

@Entity
public class Budget {

    public enum BudgetPeriod {
        WEEKLY, MONTHLY, YEARLY
    }

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount=BigDecimal.ZERO; // Budget amount

    @Column(name = "period", nullable = false)
    @Enumerated(EnumType.STRING)
    private BudgetPeriod period; // e.g., "monthly", "yearly", "weekly"

    @ManyToOne
    @JoinColumn(name = "category_id")
    private CategoryComponent category; // Category or subcategory

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner; // User ID or name

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    public Budget(){}
    public Budget(BigDecimal amount, BudgetPeriod period, CategoryComponent category, User owner) {
        this.amount = amount;
        this.period = period;
        this.category = category;
        this.owner = owner;
        this.startDate = getStartDateForPeriod(this.period);
        this.endDate= getEndDateForPeriod(this.period);
    }
    public static LocalDate getStartDateForPeriod(BudgetPeriod period) {
        return switch (period) {
            case YEARLY -> LocalDate.of(LocalDate.now().getYear(), 1, 1);
            case MONTHLY -> LocalDate.of(LocalDate.now().getYear(), LocalDate.now().getMonthValue(), 1);
            case WEEKLY -> LocalDate.now().with(DayOfWeek.MONDAY); //primo giorno è lunedì
        };
    }

    public BigDecimal getAmount() {
        return amount;
    }
    public CategoryComponent getCategory() {
        return category;
    }
    public BudgetPeriod getPeriod() {
        return period;
    }
    public User getOwner() {
        return owner;
    }
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isForCategory() {
        return category != null;
    }

    public static boolean isTransactionInPeriod( Transaction t, BudgetPeriod p) {
        LocalDate txDate = t.getDate();
        return switch (p) {
            case MONTHLY -> txDate.getYear() == getStartDateForPeriod(p).getYear()
                    && txDate.getMonth() == getStartDateForPeriod(p).getMonth();
            case WEEKLY -> ChronoUnit.WEEKS.between(getStartDateForPeriod(p), txDate) == 0;
            case YEARLY -> txDate.getYear() == getStartDateForPeriod(p).getYear();
        };
    }
    public boolean belongsTo(CategoryComponent cc) {
        return category != null && category.equals(cc);
    }

    public boolean isInPeriod(LocalDate date) {
        return switch (period) {
            case MONTHLY -> date.isBefore(startDate.plusMonths(1));
            case WEEKLY -> date.isBefore(startDate.plusWeeks(1));
            case YEARLY -> date.isBefore(startDate.plusYears(1));
        };
    }

    public LocalDate getEndDateForPeriod(BudgetPeriod p){
        if (startDate == null) {
            return null;
        }
        return switch (p){
            case YEARLY -> startDate.with(TemporalAdjusters.lastDayOfYear());
            case MONTHLY -> startDate.with(TemporalAdjusters.lastDayOfMonth());
            case WEEKLY -> startDate.with(DayOfWeek.SUNDAY);
        };
    }
    
}


