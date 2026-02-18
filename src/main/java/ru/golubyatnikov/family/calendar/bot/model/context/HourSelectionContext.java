package ru.golubyatnikov.family.calendar.bot.model.context;

import lombok.Builder;
import org.springframework.lang.NonNull;

import java.time.LocalDate;

/**
 * Контекст для обработки выбора часа при создании или редактировании события.
 * Инкапсулирует информацию о дате события и режиме работы (создание/редактирование).
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-16
 */
@Builder
public record HourSelectionContext(
    @NonNull LocalDate eventDate,
    Long editingEventId,
    boolean isEditingEvent
) {
}
