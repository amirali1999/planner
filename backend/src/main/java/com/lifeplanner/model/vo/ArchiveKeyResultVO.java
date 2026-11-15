package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "Optional body of POST /key-results/{id}/archive.")
public record ArchiveKeyResultVO(@Schema(description = "Optional reason, stored in the KR history.", example = "هدف دیگر معنادار نیست") String note) {
}
