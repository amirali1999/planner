package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;


/** The only field. Goal, deadline, priority etc. are deliberately not accepted (unknown field → 400). */
@Schema(description = "A raw note for the inbox. Only rawText is accepted; nothing is inferred from it.")
public record CaptureInboxItemVO(@Schema(description = "Note text, 1–4000 Unicode code points after trimming outer whitespace; stored exactly as typed (plain text, never rendered as HTML).", example = "کتاب RAG را بخرم", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 4000) String rawText) {
}
