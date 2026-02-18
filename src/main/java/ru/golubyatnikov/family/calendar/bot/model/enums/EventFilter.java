package ru.golubyatnikov.family.calendar.bot.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum для типов фильтрации событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Getter
@RequiredArgsConstructor
public enum EventFilter {
    
    /**
     * Показывать все события (семейные и личные).
     * Используется по умолчанию для новых пользователей.
     */
    ALL("Все события"),
    
    /**
     * Фильтрует только семейные события (is_personal = false).
     */
    FAMILY("Семейные"),
    
    /**
     * Фильтрует только персональные события пользователя (is_personal = true).
     */
    PERSONAL("Личные"),
    
    /**
     * Завершенные события.
     */
    COMPLETED("Завершенные");
    
    /**
     * Отображаемое имя фильтра для пользовательского интерфейса.
     */
    private final String displayName;
}
