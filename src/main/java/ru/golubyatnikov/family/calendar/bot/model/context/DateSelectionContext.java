package ru.golubyatnikov.family.calendar.bot.model.context;

import lombok.Builder;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;

import java.time.LocalDate;
import java.util.List;

/**
 * Контекст для обработки выбора даты в календаре.
 * Инкапсулирует всю информацию, необходимую для принятия решений о навигации.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Builder
public record DateSelectionContext(User user,
                                   LocalDate selectedDate,
                                   LocalDate today,
                                   List<Event> events,
                                   boolean isCreatingEvent,
                                   boolean isEditingEvent) {

    /**
     * Проверяет, является ли выбранная дата прошедшей.
     *
     * @return true, если дата в прошлом
     */
    public boolean isPastDate() {
        return selectedDate.isBefore(today);
    }

    /**
     * Проверяет, является ли выбранная дата сегодняшним днем.
     *
     * @return true, если дата сегодня
     */
    public boolean isToday() {
        return selectedDate.equals(today);
    }

    /**
     * Проверяет, является ли выбранная дата будущей.
     *
     * @return true, если дата в будущем
     */
    public boolean isFutureDate() {
        return selectedDate.isAfter(today);
    }

    /**
     * Проверяет отсутствие событий на выбранную дату.
     *
     * @return true, если событий нет (список пустой или null)
     */
    public boolean isEmpty() {
        return events == null || events.isEmpty();
    }
}
