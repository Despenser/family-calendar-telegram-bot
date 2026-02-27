package ru.golubyatnikov.family.calendar.bot.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.util.List;

/**
 * Результат валидации извлеченных данных о событии агентом-судьей.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-27
 */
@Builder
public record ValidationResult(
        @JsonProperty("valid") boolean valid,
        @JsonProperty("title") String title,
        @JsonProperty("date") String date,
        @JsonProperty("time") String time,
        @JsonProperty("missingFields") List<String> missingFields,
        @JsonProperty("errors") List<String> errors,
        @JsonProperty("clarificationQuestion") String clarificationQuestion
) {
}
