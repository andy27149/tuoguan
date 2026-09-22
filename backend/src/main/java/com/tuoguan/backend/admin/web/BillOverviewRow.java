package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

public record BillOverviewRow(Long studentId, String studentName, Long classRoomId, String className,
                               String teacherName, Long billId, BigDecimal totalAmount, boolean isPaid,
                               String yearMonth) {
}
