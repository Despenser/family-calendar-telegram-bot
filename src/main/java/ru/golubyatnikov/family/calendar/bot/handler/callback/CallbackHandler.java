package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;

/**
 * Интерфейс для обработчиков callback queries.
 * 
 * <p>Каждый обработчик отвечает за определённую функциональную область
 * и обрабатывает callback queries с соответствующим префиксом.</p>
 * 
 * <p>Реализации этого интерфейса автоматически регистрируются в
 * {@link ru.golubyatnikov.family.calendar.bot.service.CallbackQueryDispatcher}
 * благодаря Spring DI.</p>
 * 
 * <p><b>Пример реализации:</b></p>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class DateTimeCallbackHandler implements CallbackHandler {
 *     
 *     @Override
 *     public CallbackPrefix getPrefix() {
 *         return CallbackPrefix.DATE;
 *     }
 *     
 *     @Override
 *     public boolean canHandle(String callbackData) {
 *         return CallbackPrefix.DATE.matches(callbackData) ||
 *                CallbackPrefix.HOUR.matches(callbackData) ||
 *                CallbackPrefix.TIME.matches(callbackData);
 *     }
 *     
 *     @Override
 *     @HandleCallbackErrors
 *     public void handle(CallbackQuery callbackQuery, User user) {
 *         // Обработка callback query
 *     }
 * }
 * }</pre>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 * @see CallbackPrefix
 * @see ru.golubyatnikov.family.calendar.bot.service.CallbackQueryDispatcher
 */
public interface CallbackHandler {
    
    /**
     * Возвращает основной префикс callback data, который обрабатывает данный handler.
     * 
     * <p>Этот метод используется для идентификации handler'а и может быть
     * переопределён методом {@link #canHandle(String)} для обработки
     * нескольких префиксов одним handler'ом.</p>
     * 
     * @return префикс из enum {@link CallbackPrefix}
     */
    CallbackPrefix getPrefix();
    
    /**
     * Обрабатывает callback query.
     * 
     * <p>Метод вызывается после проверки авторизации пользователя.
     * Рекомендуется использовать аннотацию {@code @HandleCallbackErrors}
     * для централизованной обработки ошибок.</p>
     * 
     * @param callbackQuery объект callback query от Telegram
     * @param user авторизованный пользователь, выполняющий действие
     * @throws Exception если произошла ошибка при обработке callback
     */
    void handle(CallbackQuery callbackQuery, User user) throws Exception;
    
    /**
     * Проверяет, может ли handler обработать данный callback.
     * 
     * <p>По умолчанию проверяет соответствие основному префиксу через
     * {@link #getPrefix()}. Переопределите этот метод, если handler
     * должен обрабатывать несколько типов callback data.</p>
     * 
     * @param callbackData строка callback data для проверки
     * @return true если handler может обработать данный callback
     */
    default boolean canHandle(String callbackData) {
        return getPrefix().matches(callbackData);
    }
}
