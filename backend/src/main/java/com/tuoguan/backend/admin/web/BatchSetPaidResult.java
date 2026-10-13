package com.tuoguan.backend.admin.web;

import java.util.List;

public record BatchSetPaidResult(int successCount, List<BatchSetPaidFailure> failures) {
}
