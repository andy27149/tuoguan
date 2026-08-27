package com.tuoguan.backend.kanban.web;

import java.time.LocalDate;

public record SetArrivalRequest(LocalDate date, String arrivedAt) {
}
