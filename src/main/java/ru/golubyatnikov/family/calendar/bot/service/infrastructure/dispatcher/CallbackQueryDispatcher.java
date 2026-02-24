package ru.golubyatnikov.family.calendar.bot.service.infrastructure.dispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.handler.callback.filter.FilterCallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.domain.user.UserService;
import ru.golubyatnikov.family.calendar.bot.util.TelegramExceptionUtil;
import java.util.List;
import java.util.Optional;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.START;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Диспетчер для маршрутизации callback queries к соответствующим обработчикам.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackQueryDispatcher {
    
    private final List<CallbackHandler> handlers;
    private final TelegramMessageService messageService;
    private final UserService userService;
    private final FilterCallbackHandler filterCallbackHandler;
    
    /**
     * Обрабатывает callback query, маршрутизируя его к соответствующему handler.
     *
     * @param callbackQuery объект CallbackQuery от Telegram
     */
    public void dispatch(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            return;
        }
        
        String callbackData = callbackQuery.getData();
        Long telegramId = callbackQuery.getFrom().getId();
        
        // Игнорируем неактивные кнопки
        if (isIgnoredCallback(callbackData)) {
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
            try {
                handler.get().handle(callbackQuery, user);

            } catch (Exception e) {
                log.error("Ошибка при обработке callback query: data='{}', telegramId={}, handler={}, error={}, stackTrace={}", 
                        callbackData, telegramId, handler.get().getClass().getSimpleName(), 
                        e.getMessage(), TelegramExceptionUtil.getStackTraceString(e), e);
            }
        } else {
            handleUnknownCallback(callbackQuery, callbackData);
        }
    }
    
    /**
     * Находит подходящий handler для данного callback data.
     *
     * @param callbackData строка callback data для поиска handler
     * @return Optional с найденным handler или пустой Optional
     */
    public Optional<CallbackHandler> findHandler(String callbackData) {
        if (callbackData == null) {
            return Optional.empty();
        }
        
        // Проверяем FilterCallbackHandler первым для callback с префиксом "filter_"
        if (CallbackPrefix.FILTER.matches(callbackData) && filterCallbackHandler.canHandle(callbackData)) {
            return Optional.of(filterCallbackHandler);
        }
        
        return handlers.stream()
            .filter(h -> h.canHandle(callbackData))
            .findFirst();
    }
    
    /**
     * Проверяет, является ли callback data игнорируемым.
     *
     * @param callbackData строка callback data
     * @return true, если callback должен быть проигнорирован
     */
    private boolean isIgnoredCallback(String callbackData) {
        return CallbackPrefix.isIgnored(callbackData);
    }
    
    /**
     * Обрабатывает callback от неавторизованного пользователя.
     *
     * @param callbackQuery callback query от неавторизованного пользователя
     */
    private void handleUnauthorizedUser(@NonNull CallbackQuery callbackQuery) {
        String message = ERROR + " Пользователь не найден. Используйте " + START + " " + escape("/start") + " для регистрации.";
        answerCallbackQuerySafely(callbackQuery.getId(), message);
    }
    
    /**
     * Обрабатывает неизвестный callback data.
     *
     * @param callbackQuery callback query с неизвестным data
     * @param callbackData неизвестная строка callback data
     */
    private void handleUnknownCallback(@NonNull CallbackQuery callbackQuery, String callbackData) {
        Long telegramId = callbackQuery.getFrom().getId();

        log.warn("Неизвестный callback data: '{}', telegramId={}, userId={}, userName='{}'", 
                callbackData, 
                telegramId,
                callbackQuery.getFrom().getId(),
                callbackQuery.getFrom().getUserName());

        answerCallbackQuerySafely(callbackQuery.getId(), ERROR + " Неизвестная команда");
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
                    callbackQueryId, e.getMessage(), TelegramExceptionUtil.getStackTraceString(e), e);
        }
    }
}
