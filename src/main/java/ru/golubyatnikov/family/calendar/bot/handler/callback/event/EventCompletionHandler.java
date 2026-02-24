package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.context.CompletionNoteContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventNotificationService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.DESCRIPTION;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Обработчик завершения события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCompletionHandler implements CallbackHandler {
    
    private final EventService eventService;
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final EventNotificationService eventNotificationService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final CallbackQueryService callbackQueryService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.COMPLETE_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        return CallbackPrefix.COMPLETE_EVENT.matches(callbackData) ||
               CallbackPrefix.ADD_COMPLETION_NOTE.matches(callbackData) ||
               CallbackPrefix.SKIP_COMPLETION_NOTE.matches(callbackData);
    }
    
    @Override
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        if (CallbackPrefix.COMPLETE_EVENT.matches(context.callbackData())) {
            handleCompleteEvent(context);

        } else if (CallbackPrefix.ADD_COMPLETION_NOTE.matches(context.callbackData())) {
            handleAddCompletionNote(context);

        } else if (CallbackPrefix.SKIP_COMPLETION_NOTE.matches(context.callbackData())) {
            handleSkipCompletionNote(context);
        }
    }
    
    /**
     * Обрабатывает завершение события с переупорядочиванием списка.
     */
    private void handleCompleteEvent(@NonNull CallbackQueryContext context) {
        Long eventId = extractEventId(context.callbackData(), CallbackPrefix.COMPLETE_EVENT);
        
        try {
            Event completedEvent = eventService.completeEventWithReordering(eventId, context.getUserId());
            
            log.info("Событие ID={} успешно завершено с переупорядочиванием пользователем ID={}", 
                    eventId, context.getUserId());

            Integer updatedMessageId = completedEvent.getMessageId() != null 
                ? completedEvent.getMessageId().intValue() 
                : context.messageId();
            
            conversationStateService.setAwaitingCompletionNote(
                context.getUserId(), 
                eventId, 
                context.chatId(), 
                updatedMessageId
            );
            
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
        } catch (EventNotFoundException e) {
            log.error("Событие не найдено: eventId={}, userId={}", eventId, context.getUserId(), e);

        } catch (UnauthorizedAccessException e) {
            log.error("Нет прав на завершение события: eventId={}, userId={}", eventId, context.getUserId(), e);

        } catch (IllegalStateException e) {
            log.error("Неверное состояние события: eventId={}, userId={}", eventId, context.getUserId(), e);

        } catch (Exception e) {
            log.error("Ошибка при завершении события с переупорядочиванием: eventId={}, userId={}, error={}", 
                     eventId, context.getUserId(), e.getMessage(), e);
        }
    }
    
    /**
     * Обрабатывает нажатие кнопки "Добавить заметку" к завершенному событию.
     */
    private void handleAddCompletionNote(@NonNull CallbackQueryContext context) throws TelegramApiException {
        Long eventId = extractEventId(context.callbackData(), CallbackPrefix.ADD_COMPLETION_NOTE);
        
        String message = formatMessage(
                DESCRIPTION + " Напишите заметку о том, как прошло событие.\n\n" +
                "Например, что было сделано, какие были результаты или впечатления."
        );
        
        try {
            messageService.editMessageText(context.chatId(), context.messageId(), message, null);
            conversationStateService.setAwaitingCompletionNote(context.getUserId(), eventId,
                    context.chatId(), context.messageId());

        } catch (TelegramApiException e) {
            log.warn("Не удалось отредактировать сообщение, отправка нового: eventId={}, error={}",
                    eventId, e.getMessage());

            messageService.sendMessage(context.chatId(), message);
            conversationStateService.setAwaitingCompletionNote(context.getUserId(), eventId, context.chatId(), null);
        }

        callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
    }

    /**
     * Обрабатывает нажатие кнопки "Пропустить" при добавлении заметки.
     */
    private void handleSkipCompletionNote(@NonNull CallbackQueryContext context) throws TelegramApiException {
        CompletionNoteContext completionContext = conversationStateService.getCompletionNoteContext(context.getUserId());

        if (completionContext == null) {
            handleExpiredContext(context);
            return;
        }

        Long eventId = completionContext.getEventId();
        Event event = eventService.getEventById(eventId);
        boolean wasPartOfMyEventsList = (event.getMessageId() != null);

        sendCompletedEventMessage(context, completionContext, event);
        conversationStateService.clearAwaitingCompletionNote(context.getUserId());

        if (wasPartOfMyEventsList) {
            eventNotificationService.updateMyEventsHeaderAfterRemoval(context.getUserId());
        }

        callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
    }
    
    /**
     * Извлекает ID события из callback data.
     * 
     * @param callbackData строка callback data
     * @param prefix префикс для извлечения payload
     *
     * @return ID события
     */
    private @NonNull Long extractEventId(String callbackData, @NonNull CallbackPrefix prefix) {
        String payload = prefix.extractPayload(callbackData);
        return Long.parseLong(payload);
    }


    /**
     * Обрабатывает случай истекшего контекста заметки.
     */
    private void handleExpiredContext(@NonNull CallbackQueryContext context) throws TelegramApiException {
        conversationStateService.clearAwaitingCompletionNote(context.getUserId());
        messageService.sendMessage(context.chatId(), formatMessage(ERROR + " Время ожидания истекло. Попробуйте снова."));
        callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
    }

    /**
     * Отправляет сообщение о завершенном событии, пытаясь отредактировать существующее или создать новое.
     */
    private void sendCompletedEventMessage(@NonNull CallbackQueryContext context,
                                           @NonNull CompletionNoteContext completionContext,
                                           @NonNull Event event) throws TelegramApiException {

        String eventMessage = botMessageFormattingService.buildCompletedEventMessage(event);
        Integer targetMessageId = completionContext.getMessageId() != null
            ? completionContext.getMessageId()
            : context.messageId();

        editOrSendMessage(context.chatId(), targetMessageId, eventMessage, event.getId());
    }

    /**
     * Пытается отредактировать существующее сообщение, при неудаче отправляет новое.
     */
    private void editOrSendMessage(Long chatId, Integer messageId, String text, Long eventId) throws TelegramApiException {
        if (messageId != null) {
            try {
                messageService.editMessageText(chatId, messageId, text, null);

            } catch (TelegramApiException e) {
                log.warn("Не удалось отредактировать сообщение {}, отправка нового: eventId={}, error={}",
                         messageId, eventId, e.getMessage());

                messageService.sendMessage(chatId, text);
            }
        } else {
            messageService.sendMessage(chatId, text);
        }
    }

}
