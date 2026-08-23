package com.tuoguan.backend.kanban.web;

import java.time.LocalDate;

public record SetCommentRequest(LocalDate date, String comment) {
}
