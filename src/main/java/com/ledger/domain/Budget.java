package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

    public Budget(){}
    public Budget(BigDecimal amount, BudgetPeriod period, CategoryComponent category, User owner) {
        this.amount = amount;
        this.period = period;
        this.category = category;
        this.owner = owner;
        this.startDate = getStartDateForPeriod(LocalDate.now(), this.period);
    }
    public static LocalDate getStartDateForPeriod(LocalDate today, BudgetPeriod period) {
        return switch (period) {
            case YEARLY -> LocalDate.of(today.getYear(), 1, 1);
            case MONTHLY -> LocalDate.of(today.getYear(), today.getMonth(), 1);
            case WEEKLY -> today.with(DayOfWeek.SUNDAY);
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
    public boolean isForCategory() {
        return category != null;
    }

    public boolean isTransactionInPeriod(Transaction t, BudgetPeriod p) {
        LocalDate txDate = t.getDate();
        return switch (p) {
            case MONTHLY -> txDate.getYear() == startDate.getYear()
                    && txDate.getMonth() == startDate.getMonth();
            case WEEKLY -> ChronoUnit.WEEKS.between(startDate, txDate) == 0;
            case YEARLY -> txDate.getYear() == startDate.getYear();
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
    
}


