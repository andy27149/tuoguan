package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

public record StudentExtraFeeRow(Long id, String name, BigDecimal pricePerLesson, int lessonCount,
                                  BigDecimal amount) {
}
