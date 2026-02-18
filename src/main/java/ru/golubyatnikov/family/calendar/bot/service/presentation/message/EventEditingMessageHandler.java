package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.context.EditingContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.EditField;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

/**
 * TODO сделать рефакторинг класса
 * Обработчик редактирования полей события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventEditingMessageHandler {

    private final ConversationStateService conversationStateService;
    private final EventService eventService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;

    /**
     * Обрабатывает ввод текста при редактировании поля события.
     * 
     * @param message сообщение от пользователя
     * @param user авторизованный пользователь
     */
    public void handle(@NonNull Message message, @NonNull User user) {
        Long userId = user.getId();
        Long chatId = message.getChatId();
        Integer userMessageId = message.getMessageId();
        String text = message.getText();
        handleEventFieldEdit(userId, chatId, userMessageId, text);
    }

    /**
     * Обрабатывает ввод текста при редактировании поля события.
     */
    private void handleEventFieldEdit(Long userId, Long chatId, Integer userMessageId, String text) {
        EditingContext context = conversationStateService.getEditingContext(userId);
        
        if (context == null || context.getCurrentField() == null) {
            return;
        }
        
        Long eventId = context.getEventId();
        EditField field = context.getCurrentField();
        Integer editingMessageId = context.getMessageId();
        
        try {
            switch (field) {
                case TITLE -> handleTitleEdit(userId, chatId, userMessageId, text, eventId, editingMessageId);
                case DESCRIPTION -> handleDescriptionEdit(userId, chatId, userMessageId, text, eventId, editingMessageId);
                case TIME, DATE -> handleNonTextEdit(userId, chatId, userMessageId, eventId, editingMessageId, field);
                default -> log.warn("Неподдерживаемое поле для текстового ввода: {}", field);
            }
        } catch (UnauthorizedAccessException e) {
            handleUnauthorizedError(userId, chatId, eventId, e);

        } catch (EventNotFoundException e) {
            handleEventNotFoundError(userId, chatId, eventId, e);

        } catch (Exception e) {
            handleGeneralError(userId, chatId, eventId, field, e);
        }
    }

    /**
     * Обрабатывает редактирование названия события.
     */
    private void handleTitleEdit(Long userId, Long chatId, Integer userMessageId, String text, 
                                 Long eventId, Integer editingMessageId) {

        Event updatedEvent = eventService.updateEventTitle(eventId, userId, text);
        updateEventMessage(userId, chatId, userMessageId, updatedEvent, editingMessageId);
        conversationStateService.clearEventEditing(userId);
    }

    /**
     * Обрабатывает редактирование описания события.
     */
    private void handleDescriptionEdit(Long userId, Long chatId, Integer userMessageId, String text, 
                                       Long eventId, Integer editingMessageId) {

        Event updatedEvent = eventService.updateEventDescription(eventId, userId, text);
        updateEventMessage(userId, chatId, userMessageId, updatedEvent, editingMessageId);
        conversationStateService.clearEventEditing(userId);
    }


    /**
     * Обрабатывает попытку текстового ввода для полей, редактируемых через inline-кнопки.
     */
    private void handleNonTextEdit(Long userId, Long chatId, Integer userMessageId, 
                                   Long eventId, Integer editingMessageId, 
                                   EditField field) {

        messageService.deleteMessageSilently(chatId, userMessageId);
        conversationStateService.clearEventEditing(userId);
        
        try {
            Event event = eventService.getEventById(eventId);
            
            if (editingMessageId != null) {
                int eventCount = eventService.getActiveEventsCount(event.getUser().getId());
                String eventMessage = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
                
                try {
                    messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
                    } catch (TelegramApiException e) {
                    log.warn("Не удалось обновить сообщение о событии: eventId={}, messageId={}, error={}", 
                            eventId, editingMessageId, e.getMessage());

                    eventService.sendOrUpdateEventMessage(event, chatId);
                }
            } else {
                eventService.sendOrUpdateEventMessage(event, chatId);
            }
        } catch (Exception e) {
            log.error("Ошибка при восстановлении карточки события: eventId={}, error={}", eventId, e.getMessage());
        }
    }

    /**
     * Обновляет сообщение о событии после редактирования.
     */
    private void updateEventMessage(Long userId, Long chatId, Integer userMessageId, 
                                    Event updatedEvent,
                                    Integer editingMessageId) {

        if (editingMessageId != null) {
            try {
                int eventCount = eventService.getActiveEventsCount(updatedEvent.getUser().getId());
                String eventMessage = botMessageFormattingService.buildEventMessageWithHeader(updatedEvent, eventCount);
                InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(updatedEvent, userId);
                messageService.editMessageText(chatId, editingMessageId, eventMessage, keyboard);
                
                } catch (TelegramApiException e) {
                log.error("Не удалось обновить сообщение о событии: eventId={}, messageId={}, error={}", 
                        updatedEvent.getId(), editingMessageId, e.getMessage());
            }
            
            messageService.deleteMessageSilently(chatId, userMessageId);
            }
    }

    /**
     * Обрабатывает ошибку отсутствия прав.
     */
    private void handleUnauthorizedError(Long userId, Long chatId, Long eventId, 
                                        @NonNull UnauthorizedAccessException e) {

        log.error("Нет прав для редактирования события: userId={}, eventId={}, error={}", 
                userId, eventId, e.getMessage());
        
        try {
            String errorMessage = "❌ У вас нет прав для редактирования этого события.";
            messageService.sendMessage(chatId, errorMessage);

        } catch (TelegramApiException ex) {
            log.error("Не удалось отправить сообщение об ошибке: {}", ex.getMessage());
        }
        
        conversationStateService.clearEventEditing(userId);
    }

    /**
     * Обрабатывает ошибку отсутствия события.
     */
    private void handleEventNotFoundError(Long userId, Long chatId, Long eventId, 
                                         @NonNull EventNotFoundException e) {
        log.error("Событие не найдено при редактировании: userId={}, eventId={}, error={}", 
                userId, eventId, e.getMessage());
        
        try {
            String errorMessage = "❌ Событие не найдено. Возможно, оно было удалено.";
            messageService.sendMessage(chatId, errorMessage);

        } catch (TelegramApiException ex) {
            log.error("Не удалось отправить сообщение об ошибке: {}", ex.getMessage());
        }
        
        conversationStateService.clearEventEditing(userId);
    }

    /**
     * Обрабатывает общую ошибку.
     */
    private void handleGeneralError(Long userId, Long chatId, Long eventId, 
                                   EditField field, Exception e) {

        log.error("Ошибка при обновлении поля события: userId={}, eventId={}, field={}, error={}", 
                userId, eventId, field, e.getMessage(), e);
        
        try {
            String errorMessage = "❌ Произошла ошибка при обновлении " + 
                                (field == EditField.TITLE ? "названия" : "описания") + 
                                " события. Попробуйте еще раз.";
            messageService.sendMessage(chatId, errorMessage);

        } catch (TelegramApiException ex) {
            log.error("Не удалось отправить сообщение об ошибке: {}", ex.getMessage());
        }
        
        conversationStateService.clearEventEditing(userId);
    }
}
