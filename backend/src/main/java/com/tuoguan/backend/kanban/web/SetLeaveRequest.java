package com.tuoguan.backend.kanban.web;

import java.time.LocalDate;

public record SetLeaveRequest(LocalDate date, String reason) {
}
