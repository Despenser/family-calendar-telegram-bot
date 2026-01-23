package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.UserService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDate;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик callback queries для выбора типа события.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>event_type_ - выбор типа события (семейное/персональное)</li>
 *   <li>skip_description - пропуск описания события</li>
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
public class EventTypeCallbackHandler implements CallbackHandler {
    
    private final ConversationService conversationService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final UserService userService;
    private final BotMessageBuilder messageBuilder;
    private final ru.golubyatnikov.family.calendar.bot.service.EventService eventService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.EVENT_TYPE;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.EVENT_TYPE.matches(callbackData) ||
               CallbackPrefix.SKIP_DESCRIPTION.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback типа события: data='{}', userId={}", 
                callbackData, user.getId());
        
        if (CallbackPrefix.EVENT_TYPE.matches(callbackData)) {
            handleEventTypeSelection(callbackData, user, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.SKIP_DESCRIPTION.matches(callbackData)) {
            handleSkipDescription(user.getId(), chatId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает выбор типа события (семейное/персональное).
     * 
     * @param callbackData данные callback (формат: event_type_{type})
     * @param user пользователь
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleEventTypeSelection(String callbackData, User user, Long chatId, 
                                          Integer messageId, String callbackQueryId) {
        // Извлекаем тип события (family или personal)
        String eventType = CallbackPrefix.EVENT_TYPE.extractPayload(callbackData);
        boolean isPersonal = eventType.equals("personal");
        
        // Сохраняем выбор типа события в черновике
        conversationService.updateEventType(user.getId(), isPersonal);
        log.info("Пользователь {} выбрал тип события: {}", user.getId(), eventType);
        
        // Показываем календарь для выбора даты
        LocalDate now = LocalDate.now();
        InlineKeyboardMarkup calendar = keyboardService.createCalendarKeyboard(
            now.getYear(), now.getMonthValue(), user.getFamily().getId());
        
        String message = messageBuilder.buildEventTypeSelectedMessage(isPersonal);
        
        try {
            // Обновляем сообщение создания через editMessageText
            messageService.editMessageText(chatId, messageId, message, calendar);
            log.debug("Сообщение создания обновлено после выбора типа: userId={}, messageId={}, type={}", 
                     user.getId(), messageId, eventType);
            messageService.answerCallbackQuery(callbackQueryId, "Тип события выбран");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при выборе типа события: userId={}, type={}, error={}", 
                     user.getId(), eventType, e.getMessage());
            throw new RuntimeException("Ошибка при выборе типа события", e);
        }
    }
    
    /**
     * Обрабатывает пропуск описания события.
     * Завершает создание события без описания.
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param callbackQueryId идентификатор callback query
     */
    private void handleSkipDescription(Long userId, Long chatId, String callbackQueryId) {
        Event completedEvent = conversationService.completeEventCreation(userId, null);
        
        try {
            // Отправляем сообщение о созданном событии и сохраняем messageId
            try {
                eventService.sendOrUpdateEventMessage(completedEvent, chatId);
                log.debug("Сообщение о созданном событии отправлено и messageId сохранён: eventId={}", 
                        completedEvent.getId());
            } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
                log.error("Ошибка при отправке сообщения о созданном событии: eventId={}, error={}", 
                        completedEvent.getId(), e.getMessage());
                // Отправляем простое подтверждающее сообщение как fallback
                String response = formatMessage(
                    "✅ *Событие успешно создано!*\n\n" +
                    "📅 Дата: %s\n" +
                    "🕐 Время: %s\n" +
                    "📝 Название: %s",
                    completedEvent.getFormattedDate(),
                    completedEvent.getFormattedTime(),
                    completedEvent.getTitle()
                );
                ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
                messageService.sendMessage(chatId, response, keyboard);
            }
            
            messageService.answerCallbackQuery(callbackQueryId, "Событие создано");
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при создании события: userId={}, eventId={}, error={}", 
                     userId, completedEvent.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при создании события", e);
        }
        
        log.info("Событие успешно создано без описания: eventId={}, userId={}", 
            completedEvent.getId(), userId);
    }
}
