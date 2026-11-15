package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * How one active KR feeds Outcome: score (0..100), weight, its share of the total weight and the points it
 * contributes (weight × score / Σweight). All rounded to 2 decimals for display; Outcome itself is computed from
 * unrounded values, so displayed contributions may differ from it by rounding.
 */
@Schema(description = "How one own active KR feeds the goal's Outcome (all values rounded to 2 decimals; the sum of contributions may differ from Outcome by rounding).")
public record KeyResultContributionSrv(@Schema(description = "KR id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String keyResultId, @Schema(description = "KR name.", example = "Recall@5") String name, @Schema(description = "KR score 0..100.", example = "50") BigDecimal score, @Schema(description = "KR weight.", example = "1") BigDecimal weight,
                                    @Schema(description = "Share of the total weight, 0..100.", example = "50") BigDecimal weightPercent, @Schema(description = "Points contributed to Outcome = weight × score ÷ Σweight.", example = "25") BigDecimal contribution) {
}
