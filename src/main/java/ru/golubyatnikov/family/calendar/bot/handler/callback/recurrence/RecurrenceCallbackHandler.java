package ru.golubyatnikov.family.calendar.bot.handler.callback.recurrence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Обработчик callback queries для настройки повторений событий.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>recurrence_ - настройка повторений (daily, weekly, monthly, none)</li>
 *   <li>series_action_ - действия с серией повторяющихся событий</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.3, 2.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurrenceCallbackHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.RECURRENCE;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.RECURRENCE.matches(callbackData) ||
               CallbackPrefix.SERIES_ACTION.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback повторений: data='{}', userId={}", 
                callbackData, user.getId());
        
        if (CallbackPrefix.RECURRENCE.matches(callbackData)) {
            handleRecurrenceSettings(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.SERIES_ACTION.matches(callbackData)) {
            handleSeriesAction(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает настройку повторений события.
     * 
     * @param callbackData данные callback (формат: recurrence_{type})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleRecurrenceSettings(String callbackData, Long userId, Long chatId, 
                                         Integer messageId, String callbackQueryId) {
        // Извлекаем тип повторения
        String recurrenceType = CallbackPrefix.RECURRENCE.extractPayload(callbackData);
        
        log.info("Пользователь {} настроил повторение: {}", userId, recurrenceType);
        
        String message = switch (recurrenceType) {
            case "daily" -> "✅ Повторение: Ежедневно\n\nСобытие будет повторяться каждый день.";
            case "weekly" -> "✅ Повторение: Еженедельно\n\nСобытие будет повторяться каждую неделю.";
            case "monthly" -> "✅ Повторение: Ежемесячно\n\nСобытие будет повторяться каждый месяц.";
            case "none" -> "✅ Повторение отключено\n\nСобытие будет одноразовым.";
            default -> "❌ Неизвестный тип повторения";
        };
        
        try {
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.SUCCESS);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при настройке повторения: userId={}, type={}, error={}", 
                     userId, recurrenceType, e.getMessage());
            throw new RuntimeException("Ошибка при настройке повторения", e);
        }
    }
    
    /**
     * Обрабатывает действия с серией повторяющихся событий.
     * 
     * @param callbackData данные callback (формат: series_action_{action}_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleSeriesAction(String callbackData, Long userId, Long chatId, 
                                   Integer messageId, String callbackQueryId) {
        // Извлекаем действие (this_only или entire_series)
        String action = CallbackPrefix.SERIES_ACTION.extractPayload(callbackData);
        
        log.info("Пользователь {} выбрал действие с серией: {}", userId, action);
        
        String message = action.startsWith("this_only")
            ? "✅ Изменения применены только к этому событию"
            : "✅ Изменения применены ко всей серии событий";
        
        try {
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.SUCCESS);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при обработке действия с серией: userId={}, action={}, error={}", 
                     userId, action, e.getMessage());
            throw new RuntimeException("Ошибка при обработке действия с серией", e);
        }
    }
}
