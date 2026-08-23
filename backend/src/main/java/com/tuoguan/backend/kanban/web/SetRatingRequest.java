package com.tuoguan.backend.kanban.web;

import java.time.LocalDate;

public record SetRatingRequest(LocalDate date, int rating) {
}
