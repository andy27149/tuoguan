package com.tuoguan.backend.platform.web;

import com.tuoguan.backend.platform.domain.RenewalPlan;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateRenewalRequest(@NotNull RenewalPlan plan, BigDecimal amount,
                                    @NotNull LocalDate validUntil, String note) {
}
