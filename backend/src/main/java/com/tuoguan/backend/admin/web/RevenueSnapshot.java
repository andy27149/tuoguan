package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

public record RevenueSnapshot(String tuitionMonth, BigDecimal tuitionBilled, BigDecimal tuitionCollected,
                               String consumptionMonth, int offCampusConsumptionCount) {
}
