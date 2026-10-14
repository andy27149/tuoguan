package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotNull;

public record UpdateBillTuitionInAdvanceRequest(@NotNull Boolean billTuitionInAdvance) {
}
