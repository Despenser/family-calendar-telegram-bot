package ru.golubyatnikov.family.calendar.bot.model.enums;

/**
 * Тип напоминания о событии.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-10
 */
public enum ReminderType {
    /**
     * Вечером накануне (20:00)
     */
    EVENING_BEFORE,
    
    /**
     * За 1 час до события
     */
    ONE_HOUR_BEFORE,
    
    /**
     * За 15 минут до события
     */
    FIFTEEN_MINUTES_BEFORE
}
