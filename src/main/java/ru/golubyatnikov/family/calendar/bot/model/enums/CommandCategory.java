package ru.golubyatnikov.family.calendar.bot.model.enums;

import lombok.Getter;

/**
 * Категории команд для группировки в справке.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Getter
public enum CommandCategory {

    VIEW_EVENTS("Просмотр событий"),
    MANAGE_EVENTS("Управление событиями"),
    SEARCH_FILTER("Поиск и фильтрация"),
    STATS_TRASH("Статистика и корзина"),
    HELP("Справка");

    private final String displayName;

    CommandCategory(String displayName) {
        this.displayName = displayName;
    }
}
