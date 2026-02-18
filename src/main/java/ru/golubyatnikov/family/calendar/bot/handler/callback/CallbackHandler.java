package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;

/**
 * Интерфейс для обработчиков callback queries.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
public interface CallbackHandler {
    
    /**
     * Возвращает основной префикс callback data, который обрабатывает данный handler.
     *
     * @return префикс из enum CallbackPrefix
     */
    CallbackPrefix getPrefix();
    
    /**
     * Обрабатывает callback query.
     * 
     * @param callbackQuery объект callback query от Telegram
     * @param user авторизованный пользователь, выполняющий действие
     *
     * @throws Exception если произошла ошибка при обработке callback
     */
    void handle(CallbackQuery callbackQuery, User user) throws Exception;
    
    /**
     * Проверяет, может ли handler обработать данный callback.
     *
     * @param callbackData строка callback data для проверки
     * @return true если handler может обработать данный callback
     */
    default boolean canHandle(String callbackData) {
        return getPrefix().matches(callbackData);
    }
}
