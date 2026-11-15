package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/** One audited KR change (created / changed / archived); before/after hold only changed fields. */
@Schema(description = "One audited change of a Key Result.")
public record KeyResultHistorySrv(@Schema(description = "History entry id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id, @Schema(description = "What happened.", example = "key_result_changed", allowableValues = {"key_result_created", "key_result_changed", "key_result_archived"}) String eventType, @Schema(description = "Changed fields before the change (API field names); empty for creation.") Map<String, Object> before, @Schema(description = "Changed fields after the change.") Map<String, Object> after,
                                       @Schema(description = "Reason given with the change; null when none.", example = "benchmark changed") String note, @Schema(description = "When the change happened (UTC).", example = "2026-11-09T08:30:00.000Z") Instant createdAt) {
}
