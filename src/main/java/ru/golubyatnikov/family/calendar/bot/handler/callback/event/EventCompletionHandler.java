package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.domain.myevents.MyEventsPageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.myevents.MyEventsPageDisplayService;
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
    private final MyEventsPageService myEventsPageService;
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final MyEventsPageDisplayService pageDisplayService;
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
     * Поддерживает контекст постраничного списка /my_events.
     */
    private void handleCompleteEvent(@NonNull CallbackQueryContext context) {
        String payload = CallbackPrefix.COMPLETE_EVENT.extractPayload(context.callbackData());
        String[] parts = payload.split("_");
        Long eventId = Long.parseLong(parts[0]);
        Integer page = parts.length > 1 ? Integer.parseInt(parts[1]) : null;
        
        try {
            eventService.completeEventWithReordering(eventId, context.getUserId(), context.messageId());

            log.info("Событие ID={} успешно завершено пользователем ID={}, page={}", 
                    eventId, context.getUserId(), page);

            conversationStateService.setAwaitingCompletionNote(
                context.getUserId(), 
                eventId, 
                context.chatId(), 
                context.messageId(),
                page
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
        
        // Получаем текущий контекст, чтобы сохранить номер страницы
        CompletionNoteContext currentContext = conversationStateService.getCompletionNoteContext(context.getUserId());
        Integer myEventsPage = currentContext != null ? currentContext.getMyEventsPage() : null;
        
        String message = formatMessage(
                DESCRIPTION + " Напишите заметку о том, как прошло событие.\n\n" +
                "Например, что было сделано, какие были результаты или впечатления."
        );
        
        try {
            messageService.editMessageText(context.chatId(), context.messageId(), message, null);
            conversationStateService.setAwaitingCompletionNote(context.getUserId(), eventId,
                    context.chatId(), context.messageId(), myEventsPage);

        } catch (TelegramApiException e) {
            log.warn("Не удалось отредактировать сообщение, отправка нового: eventId={}, error={}",
                    eventId, e.getMessage());

            messageService.sendMessage(context.chatId(), message);
            conversationStateService.setAwaitingCompletionNote(context.getUserId(), eventId,
                    context.chatId(), null, myEventsPage);
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
        Integer myEventsPage = completionContext.getMyEventsPage();
        Event event = eventService.getEventById(eventId);
        
        conversationStateService.clearAwaitingCompletionNote(context.getUserId());

        // Показываем карточку завершенного события
        sendCompletedEventMessage(context, completionContext, event);
        
        // Если событие было из /my_events - отправляем список новым сообщением
        if (myEventsPage != null) {
            returnToMyEventsList(context, myEventsPage);
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
    
    /**
     * Возвращает пользователя к постраничному списку /my_events.
     * Отправляет список новым сообщением.
     */
    private void returnToMyEventsList(@NonNull CallbackQueryContext context, int page) {
        try {
            Page<Event> eventsPage = myEventsPageService.getEventsPage(context.getUserId(), page);
            pageDisplayService.sendEventsPage(context.chatId(), eventsPage);
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к списку /my_events после завершения события: {}", e.getMessage(), e);
        }
    }

}
