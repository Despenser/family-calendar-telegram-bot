package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.context.EditingContext;
import ru.golubyatnikov.family.calendar.bot.model.dto.EventMessageData;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.EditField;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventNotificationService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;

/**
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
    private final EventNotificationService eventNotificationService;

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
                case TIME, DATE -> handleNonTextEdit(userId, chatId, userMessageId, eventId, editingMessageId);
                default -> log.warn("Неподдерживаемое поле для текстового ввода: {}", field);
            }

        } catch (UnauthorizedAccessException e) {
            handleUnauthorizedError(userId, chatId);

        } catch (EventNotFoundException e) {
            handleEventNotFoundError(userId, chatId);

        } catch (Exception e) {
            handleGeneralError(userId, chatId, field);
        }
    }

    /**
     * Обрабатывает редактирование названия события.
     */
    private void handleTitleEdit(Long userId,
                                 Long chatId,
                                 Integer userMessageId,
                                 String text,
                                 Long eventId,
                                 Integer editingMessageId) {

        Event updatedEvent = eventService.updateEventTitle(eventId, userId, text);
        updateEventMessage(userId, chatId, userMessageId, updatedEvent, editingMessageId);
        conversationStateService.clearEventEditing(userId);
    }

    /**
     * Обрабатывает редактирование описания события.
     */
    private void handleDescriptionEdit(Long userId,
                                       Long chatId,
                                       Integer userMessageId,
                                       String text,
                                       Long eventId,
                                       Integer editingMessageId) {

        Event updatedEvent = eventService.updateEventDescription(eventId, userId, text);
        updateEventMessage(userId, chatId, userMessageId, updatedEvent, editingMessageId);
        conversationStateService.clearEventEditing(userId);
    }


    /**
     * Обрабатывает попытку текстового ввода для полей, редактируемых через inline-кнопки.
     */
    private void handleNonTextEdit(Long userId,
                                   Long chatId,
                                   Integer userMessageId,
                                   Long eventId,
                                   Integer editingMessageId) {

        messageService.deleteMessageSilently(chatId, userMessageId);
        conversationStateService.clearEventEditing(userId);
        
        try {
            Event event = eventService.getEventById(eventId);
            restoreEventCard(userId, chatId, event, eventId, editingMessageId);

        } catch (Exception e) {
            log.error("Ошибка при восстановлении карточки события: eventId={}, error={}", eventId, e.getMessage());
        }
    }

    /**
     * Восстанавливает карточку события.
     */
    private void restoreEventCard(Long userId, Long chatId, Event event, Long eventId, Integer editingMessageId) {
        if (editingMessageId != null) {
            // Извлекаем контекст страницы из EditingContext
            EditingContext context = conversationStateService.getEditingContext(userId);
            Integer myEventsPage = context != null ? context.getMyEventsPage() : null;
            
            // Подготавливаем данные сообщения через EventNotificationService
            EventMessageData messageData = eventNotificationService.prepareEventMessageData(event, userId, myEventsPage);
            
            try {
                messageService.editMessageText(chatId, editingMessageId, messageData.messageText(), messageData.keyboard());

            } catch (TelegramApiException e) {
                log.warn("Не удалось обновить сообщение о событии: eventId={}, messageId={}, error={}", 
                        eventId, editingMessageId, e.getMessage());

                sendOrUpdateEventMessage(event, chatId);
            }
        } else {
            sendOrUpdateEventMessage(event, chatId);
        }
    }

    /**
     * Отправляет или обновляет сообщение о событии.
     */
    private void sendOrUpdateEventMessage(Event event, Long chatId) {
        try {
            eventService.sendOrUpdateEventMessage(event, chatId);

        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения о событии: eventId={}, error={}", 
                    event.getId(), e.getMessage());
        }
    }

    /**
     * Обновляет сообщение о событии после редактирования.
     */
    private void updateEventMessage(Long userId,
                                    Long chatId,
                                    Integer userMessageId,
                                    Event updatedEvent,
                                    Integer editingMessageId) {

        if (editingMessageId != null) {
            try {
                // Извлекаем контекст страницы из EditingContext
                EditingContext context = conversationStateService.getEditingContext(userId);
                Integer myEventsPage = context != null ? context.getMyEventsPage() : null;
                
                // Подготавливаем данные сообщения через EventNotificationService
                EventMessageData messageData = eventNotificationService.prepareEventMessageData(updatedEvent, userId, myEventsPage);
                    
                messageService.editMessageText(chatId, editingMessageId, messageData.messageText(), messageData.keyboard());
                
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
    private void handleUnauthorizedError(Long userId, Long chatId) {
        String errorMessage = ERROR + " У вас нет прав для редактирования этого события.";
        sendErrorMessageAndClearState(userId, chatId, errorMessage);
    }

    /**
     * Обрабатывает ошибку отсутствия события.
     */
    private void handleEventNotFoundError(Long userId, Long chatId) {
        String errorMessage = ERROR + " Событие не найдено. Возможно, оно было удалено.";
        sendErrorMessageAndClearState(userId, chatId, errorMessage);
    }

    /**
     * Обрабатывает общую ошибку.
     */
    private void handleGeneralError(Long userId, Long chatId, EditField field) {
        String errorMessage = ERROR + " Произошла ошибка при обновлении " + 
                            (field == EditField.TITLE ? "названия" : "описания") + 
                            " события. Попробуйте еще раз.";

        sendErrorMessageAndClearState(userId, chatId, errorMessage);
    }

    /**
     * Отправляет сообщение об ошибке и очищает состояние редактирования.
     */
    private void sendErrorMessageAndClearState(Long userId, Long chatId, String errorMessage) {
        try {
            messageService.sendMessage(chatId, errorMessage);

        } catch (TelegramApiException ex) {
            logErrorMessageSendFailure(ex);
        }
        
        conversationStateService.clearEventEditing(userId);
    }

    /**
     * Логирует ошибку при отправке сообщения об ошибке.
     */
    private void logErrorMessageSendFailure(@NonNull TelegramApiException ex) {
        log.error("Не удалось отправить сообщение об ошибке: {}", ex.getMessage());
    }
}
