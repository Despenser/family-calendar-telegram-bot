package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.List;
import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Диспетчер для маршрутизации callback queries к соответствующим обработчикам.
 * 
 * <p>CallbackQueryDispatcher является центральным компонентом для обработки callback queries
 * от inline кнопок Telegram. Он выполняет следующие функции:</p>
 * <ul>
 *   <li>Маршрутизация callback queries к соответствующим {@link CallbackHandler}</li>
 *   <li>Проверка авторизации пользователя перед обработкой</li>
 *   <li>Обработка игнорируемых callback (calendar_ignore, time_ignore)</li>
 *   <li>Обработка неизвестных callback data</li>
 * </ul>
 * 
 * <p>Использует паттерн Chain of Responsibility для поиска подходящего обработчика.
 * Все зарегистрированные {@link CallbackHandler} автоматически внедряются через Spring DI.</p>
 * 
 * <p><b>Архитектурный паттерн:</b> Chain of Responsibility + Delegation</p>
 * <p><b>Требования:</b> 1.1, 1.2</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * @Service
 * public class UpdateProcessor {
 *     private final CallbackQueryDispatcher callbackQueryDispatcher;
 *     
 *     public void processUpdate(Update update) {
 *         if (update.hasCallbackQuery()) {
 *             callbackQueryDispatcher.dispatch(update.getCallbackQuery());
 *         }
 *     }
 * }
 * }</pre>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 * @see CallbackHandler
 * @see CallbackPrefix
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackQueryDispatcher {
    
    private final List<CallbackHandler> handlers;
    private final TelegramMessageService messageService;
    private final UserService userService;
    private final ru.golubyatnikov.family.calendar.bot.handler.callback.FilterCallbackHandler filterCallbackHandler;
    
    /**
     * Обрабатывает callback query, маршрутизируя его к соответствующему handler.
     * 
     * <p>Процесс обработки:</p>
     * <ol>
     *   <li>Проверка на null callback query</li>
     *   <li>Проверка на игнорируемые callback (calendar_ignore, time_ignore)</li>
     *   <li>Проверка авторизации пользователя</li>
     *   <li>Поиск подходящего handler через {@link #findHandler(String)}</li>
     *   <li>Делегирование обработки найденному handler</li>
     *   <li>Обработка неизвестных callback data</li>
     * </ol>
     * 
     * @param callbackQuery объект CallbackQuery от Telegram
     */
    public void dispatch(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            log.warn("Получен null callback query");
            return;
        }
        
        String callbackData = callbackQuery.getData();
        Long telegramId = callbackQuery.getFrom().getId();
        
        log.info("Диспетчеризация callback query: data='{}', telegramId={}", 
                callbackData, telegramId);
        
        // Игнорируем неактивные кнопки
        if (isIgnoredCallback(callbackData)) {
            log.debug("Игнорируемый callback: data='{}'", callbackData);
            answerCallbackQuerySafely(callbackQuery.getId(), "");
            return;
        }
        
        // Проверяем авторизацию пользователя
        Optional<User> userOpt = userService.findByTelegramId(telegramId);
        if (userOpt.isEmpty()) {
            log.warn("Неавторизованный пользователь при обработке callback: telegramId={}", telegramId);
            handleUnauthorizedUser(callbackQuery);
            return;
        }
        
        User user = userOpt.get();
        
        // Находим подходящий handler
        Optional<CallbackHandler> handler = findHandler(callbackData);
        
        if (handler.isPresent()) {
            log.debug("Найден handler для callback: data='{}', handler={}", 
                    callbackData, handler.get().getClass().getSimpleName());
            
            try {
                handler.get().handle(callbackQuery, user);
                log.info("Callback query успешно обработан: data='{}', handler={}", 
                        callbackData, handler.get().getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Ошибка при обработке callback query: data='{}', telegramId={}, handler={}, error={}, stackTrace={}", 
                        callbackData, telegramId, handler.get().getClass().getSimpleName(), 
                        e.getMessage(), getStackTraceString(e), e);
                // Ошибка уже обработана аспектом @HandleCallbackErrors, если он применен
            }
        } else {
            handleUnknownCallback(callbackQuery, callbackData);
        }
    }
    
    /**
     * Находит подходящий handler для данного callback data.
     * 
     * <p>Перебирает все зарегистрированные handlers и возвращает первый,
     * который может обработать данный callback (метод {@link CallbackHandler#canHandle(String)}
     * возвращает true).</p>
     * 
     * <p><b>Приоритет обработки:</b></p>
     * <ol>
     *   <li>FilterCallbackHandler проверяется первым для callback с префиксом "filter_"</li>
     *   <li>Остальные handlers проверяются в порядке регистрации</li>
     * </ol>
     * 
     * @param callbackData строка callback data для поиска handler
     * @return Optional с найденным handler или пустой Optional
     */
    public Optional<CallbackHandler> findHandler(String callbackData) {
        if (callbackData == null) {
            return Optional.empty();
        }
        
        // Проверяем FilterCallbackHandler первым для callback с префиксом "filter_"
        if (callbackData.startsWith("filter_") && filterCallbackHandler.canHandle(callbackData)) {
            log.debug("Callback с префиксом 'filter_' направлен в FilterCallbackHandler: data='{}'", callbackData);
            return Optional.of(filterCallbackHandler);
        }
        
        return handlers.stream()
            .filter(h -> h.canHandle(callbackData))
            .findFirst();
    }
    
    /**
     * Проверяет, является ли callback data игнорируемым.
     * 
     * <p>Игнорируемые callback используются для неактивных кнопок,
     * таких как заголовки дней недели в календаре.</p>
     * 
     * @param callbackData строка callback data
     * @return true если callback должен быть проигнорирован
     */
    private boolean isIgnoredCallback(String callbackData) {
        return CallbackPrefix.isIgnored(callbackData);
    }
    
    /**
     * Обрабатывает callback от неавторизованного пользователя.
     * 
     * <p>Отправляет пользователю сообщение о необходимости регистрации
     * через команду /start.</p>
     * 
     * @param callbackQuery callback query от неавторизованного пользователя
     */
    private void handleUnauthorizedUser(CallbackQuery callbackQuery) {
        String message = "❌ Пользователь не найден. Используйте 🚀 " + escape("/start") + " для регистрации.";
        answerCallbackQuerySafely(callbackQuery.getId(), message);
    }
    
    /**
     * Обрабатывает неизвестный callback data.
     * 
     * <p>Логирует предупреждение с включением telegramId пользователя
     * и отправляет пользователю сообщение об ошибке.</p>
     * 
     * <p><b>Требования:</b> 2.4, 2.5, 5.2</p>
     * 
     * @param callbackQuery callback query с неизвестным data
     * @param callbackData неизвестная строка callback data
     */
    private void handleUnknownCallback(CallbackQuery callbackQuery, String callbackData) {
        Long telegramId = callbackQuery.getFrom().getId();
        log.warn("Неизвестный callback data: '{}', telegramId={}, userId={}, userName='{}'", 
                callbackData, 
                telegramId,
                callbackQuery.getFrom().getId(),
                callbackQuery.getFrom().getUserName());
        answerCallbackQuerySafely(callbackQuery.getId(), "❌ Неизвестная команда");
    }
    
    /**
     * Безопасно отвечает на callback query с обработкой исключений.
     * 
     * @param callbackQueryId идентификатор callback query
     * @param text текст ответа
     */
    private void answerCallbackQuerySafely(String callbackQueryId, String text) {
        try {
            messageService.answerCallbackQuery(callbackQueryId, text);
        } catch (Exception e) {
            log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}, stackTrace={}", 
                    callbackQueryId, e.getMessage(), getStackTraceString(e), e);
        }
    }
    
    /**
     * Возвращает количество зарегистрированных handlers.
     * 
     * <p>Используется для тестирования и мониторинга.</p>
     * 
     * @return количество зарегистрированных handlers
     */
    public int getHandlersCount() {
        return handlers.size();
    }
    
    /**
     * Проверяет, есть ли handler для данного callback data.
     * 
     * @param callbackData строка callback data
     * @return true если есть handler, который может обработать данный callback
     */
    public boolean hasHandler(String callbackData) {
        return findHandler(callbackData).isPresent();
    }
    
    /**
     * Получает строковое представление стека вызовов исключения.
     * 
     * <p>Используется для детального логирования критических ошибок.</p>
     * 
     * @param e исключение
     * @return строка со стеком вызовов (первые 5 элементов)
     */
    private String getStackTraceString(Exception e) {
        if (e == null || e.getStackTrace() == null || e.getStackTrace().length == 0) {
            return "no stack trace";
        }
        
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = e.getStackTrace();
        int limit = Math.min(5, elements.length);
        
        for (int i = 0; i < limit; i++) {
            sb.append(elements[i].toString());
            if (i < limit - 1) {
                sb.append(" -> ");
            }
        }
        
        return sb.toString();
    }
}
