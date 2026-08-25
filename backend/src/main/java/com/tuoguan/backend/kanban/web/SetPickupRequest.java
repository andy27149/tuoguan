package com.tuoguan.backend.kanban.web;

import java.time.LocalDate;

public record SetPickupRequest(LocalDate date, String pickedUpBy, String pickedUpAt) {
}
