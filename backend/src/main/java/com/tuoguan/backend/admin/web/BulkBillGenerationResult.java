package com.tuoguan.backend.admin.web;

import java.util.List;

public record BulkBillGenerationResult(int generatedCount, List<BulkBillGenerationFailure> failures) {
}
