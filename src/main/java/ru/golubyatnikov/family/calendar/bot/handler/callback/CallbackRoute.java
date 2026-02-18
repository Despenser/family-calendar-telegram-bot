package ru.golubyatnikov.family.calendar.bot.handler.callback;

import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;

/**
 * Функциональный интерфейс для обработки callback query.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-16
 */
@FunctionalInterface
public interface CallbackRoute {
    
    /**
     * Обрабатывает callback query.
     * 
     * @param callbackData данные callback query
     * @param context контекст обработки
     */
    void handle(String callbackData, CallbackQueryContext context);
}
