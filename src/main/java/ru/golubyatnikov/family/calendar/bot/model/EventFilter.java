package ru.golubyatnikov.family.calendar.bot.model;

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
     * Фильтрует события, помеченные как семейные.
     */
    FAMILY("Семейные"),
    
    /**
     * Фильтрует события, помеченные как личные.
     */
    PERSONAL("Личные");
    
    /**
     * Отображаемое имя фильтра для пользовательского интерфейса.
     */
    private final String displayName;
}
