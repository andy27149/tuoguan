package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

// 允许 offCampusMealRatePerDay 为 null（清空配置），所以不加 @NotNull。
public record UpdateOffCampusMealRateRequest(BigDecimal offCampusMealRatePerDay) {
}
