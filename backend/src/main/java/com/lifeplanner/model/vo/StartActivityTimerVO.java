package com.lifeplanner.model.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
@Schema(description = "Start recording actual activity now.")
public record StartActivityTimerVO(
 @NotBlank @Size(max=200) @Schema(description = "What you are doing.", example = "مطالعه") String title,
 @NotBlank @Schema(description = "IANA timezone for splitting local days.", example = "Asia/Tehran") String timeZone) {}
