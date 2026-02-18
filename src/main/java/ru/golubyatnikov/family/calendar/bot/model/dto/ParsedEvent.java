package ru.golubyatnikov.family.calendar.bot.model.dto;

import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO для результата парсинга текстового сообщения с информацией о событии.
 *
 * @param title название события
 * @param date дата события
 * @param time время события
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
public record ParsedEvent(String title, LocalDate date, LocalTime time) {

    /**
     * Проверяет валидность распознанного события.
     *
     * @return true, если все поля заполнены и дата не в прошлом
     */
    public boolean isValid() {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        if (date == null || time == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return !date.isBefore(today);
    }

    @Override
    public @NonNull String toString() {
        return String.format("Событие: %s\nДата: %s\nВремя: %s",
                title,
                date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                time.format(DateTimeFormatter.ofPattern("HH:mm")));
    }
}
