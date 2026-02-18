package ru.golubyatnikov.family.calendar.bot.service.presentation.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.context.EditingContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Сервис для обработки редактирования даты и времени событий.
 * Централизует логику обновления событий и возврата к карточке события.
 * 
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventEditingService {
    
    private final EventService eventService;
    private final ConversationStateService conversationStateService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final TelegramMessageService messageService;
    
    /**
     * Обновляет дату события и возвращает к карточке события.
     * 
     * @param userId идентификатор пользователя
     * @param date новая дата события
     * @param chatId идентификатор чата Telegram
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при обновлении даты
     */
    public void updateEventDate(Long userId, LocalDate date, Long chatId, String callbackQueryId) {
        EditingContext context = getEditingContextOrWarn(userId);
        if (context == null) {
            return;
        }
        
        try {
            Event updatedEvent = eventService.updateEventDate(context.getEventId(), userId, date);
            completeEditingAndReturnToCard(updatedEvent, userId, chatId, context.getMessageId(), 
                    callbackQueryId, CallbackMessages.UPDATED);

        } catch (Exception e) {
            log.error("Ошибка при обновлении даты события: userId={}, date={}, error={}", 
                     userId, date, e.getMessage());

            throw new RuntimeException("Ошибка при обновлении даты", e);
        }
    }
    
    /**
     * Обновляет время события и возвращает к карточке события.
     * 
     * @param userId идентификатор пользователя
     * @param time новое время события
     * @param chatId идентификатор чата Telegram
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при обновлении времени
     */
    public void updateEventTime(Long userId, LocalTime time, Long chatId, String callbackQueryId) {
        EditingContext context = getEditingContextOrWarn(userId);
        if (context == null) {
            return;
        }
        
        try {
            Event updatedEvent = eventService.updateEventTime(context.getEventId(), userId, time);
            completeEditingAndReturnToCard(updatedEvent, userId, chatId, context.getMessageId(), 
                    callbackQueryId, CallbackMessages.UPDATED);

        } catch (Exception e) {
            log.error("Ошибка при обновлении времени события: userId={}, time={}, error={}", 
                     userId, time, e.getMessage());

            throw new RuntimeException("Ошибка при обновлении времени", e);
        }
    }
    
    /**
     * Отменяет редактирование и возвращает к карточке события.
     * 
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата Telegram
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при отмене редактирования
     */
    public void cancelEditing(Long userId, Long chatId, String callbackQueryId) {
        EditingContext context = conversationStateService.getEditingContext(userId);
        if (context == null || context.getEventId() == null) {
            conversationStateService.clearEventEditing(userId);
            return;
        }
        
        try {
            Event event = eventService.getEventById(context.getEventId());
            completeEditingAndReturnToCard(event, userId, chatId, context.getMessageId(), 
                    callbackQueryId, "Редактирование отменено");

        } catch (Exception e) {
            log.error("Ошибка при отмене редактирования: userId={}, error={}", userId, e.getMessage());
            conversationStateService.clearEventEditing(userId);
            throw new RuntimeException("Ошибка при отмене редактирования", e);
        }
    }
    
    /**
     * Получает контекст редактирования или логирует предупреждение.
     * 
     * @param userId идентификатор пользователя
     * @return контекст редактирования или null, если не найден
     */
    private @Nullable EditingContext getEditingContextOrWarn(Long userId) {
        EditingContext context = conversationStateService.getEditingContext(userId);
        if (context == null || context.getEventId() == null) {
            return null;
        }
        return context;
    }
    
    /**
     * Завершает редактирование: возвращает к карточке события, очищает контекст и отвечает на callback.
     * 
     * @param event событие для отображения
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     * @param callbackMessage сообщение для ответа на callback
     */
    private void completeEditingAndReturnToCard(Event event,
                                                Long userId,
                                                Long chatId,
                                                Integer messageId,
                                                String callbackQueryId,
                                                String callbackMessage) {

        returnToEventCard(event, userId, chatId, messageId);
        conversationStateService.clearEventEditing(userId);
        answerCallbackQuery(callbackQueryId, callbackMessage);
    }
    
    /**
     * Отвечает на callback query с обработкой ошибок.
     * 
     * @param callbackQueryId идентификатор callback query
     * @param message сообщение для ответа
     */
    private void answerCallbackQuery(String callbackQueryId, String message) {
        try {
            messageService.answerCallbackQuery(callbackQueryId, message);

        } catch (TelegramApiException e) {
            log.warn("Не удалось ответить на callback query: {}", e.getMessage());
        }
    }
    
    /**
     * Возвращает пользователя к карточке события.
     * 
     * @param event событие для отображения
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования (может быть null)
     */
    private void returnToEventCard(@NonNull Event event,
                                   Long userId,
                                   Long chatId,
                                   Integer messageId) {

        int eventCount = eventService.getActiveEventsCount(event.getUser().getId());
        String eventMessage = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
        InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
        
        if (messageId != null) {
            tryEditMessage(event, chatId, messageId, eventMessage, keyboard);

        } else {
            sendEventMessage(event, chatId);
        }
    }
    
    /**
     * Пытается отредактировать существующее сообщение, при неудаче отправляет новое.
     * 
     * @param event событие
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для редактирования
     * @param eventMessage текст сообщения
     * @param keyboard клавиатура
     */
    private void tryEditMessage(@NonNull Event event,
                                Long chatId,
                                Integer messageId,
                                String eventMessage,
                                InlineKeyboardMarkup keyboard) {
        try {
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);

        } catch (TelegramApiException e) {
            log.warn("Не удалось обновить сообщение о событии: eventId={}, messageId={}, error={}", 
                    event.getId(), messageId, e.getMessage());

            sendEventMessage(event, chatId);
        }
    }
    
    /**
     * Отправляет сообщение о событии с обработкой ошибок.
     * 
     * @param event событие
     * @param chatId идентификатор чата
     */
    private void sendEventMessage(Event event, Long chatId) {
        try {
            eventService.sendOrUpdateEventMessage(event, chatId);

        } catch (TelegramApiException e) {
            log.error("Не удалось отправить сообщение о событии: eventId={}, error={}", event.getId(), e.getMessage());
        }
    }
}
