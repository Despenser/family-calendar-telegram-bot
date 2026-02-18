package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.context.EditingContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.EventEditKeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;

/**
 * Обработчик редактирования события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventEditHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final KeyboardService keyboardService;
    private final EventService eventService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final EventEditKeyboardFactory keyboardFactory;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final CallbackQueryService callbackQueryService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.EDIT_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        return CallbackPrefix.EDIT_EVENT.matches(callbackData) ||
               CallbackPrefix.EDIT_BACK.matches(callbackData) ||
               CallbackPrefix.EDIT_CANCEL.matches(callbackData);
    }
    
    @Override
    @Transactional
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        if (CallbackPrefix.EDIT_EVENT.matches(context.callbackData())) {
            handleEditEvent(context);

        } else if (CallbackPrefix.EDIT_BACK.matches(context.callbackData())) {
            handleEditBack(context);

        } else if (CallbackPrefix.EDIT_CANCEL.matches(context.callbackData())) {
            handleEditCancel(context);
        }
    }
    
    /**
     * Обрабатывает редактирование события.
     */
    private void handleEditEvent(@NonNull CallbackQueryContext context) {
        Long eventId = extractEventId(context.callbackData());
        
        log.info("Редактирование события ID={} пользователем ID={}", eventId, context.getUserId());
        
        try {
            Event event = eventService.getEventById(eventId);
            
            if (!event.getUser().getId().equals(context.getUserId())) {
                callbackQueryService.answerCallback(context, CallbackMessages.NO_ACCESS);
                return;
            }
            
            conversationStateService.startEventEditing(context.getUserId(), eventId, context.chatId(), context.messageId());
            
            String message = buildEditFieldSelectionMessage(event);
            InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId, context.getUserId());
            
            messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
        } catch (TelegramApiException e) {
            log.error("Ошибка при редактировании события: eventId={}, userId={}, error={}", 
                     eventId, context.getUserId(), e.getMessage(), e);

            throw new RuntimeException("Ошибка при редактировании события", e);
        }
    }
    
    /**
     * Обрабатывает возврат к меню выбора поля редактирования.
     */
    private void handleEditBack(@NonNull CallbackQueryContext context) {
        String eventIdStr = CallbackPrefix.EDIT_BACK.extractPayload(context.callbackData());
        Long eventId = Long.parseLong(eventIdStr);
        
        try {
            Event event = eventService.getEventById(eventId);
            
            String message = buildEditFieldSelectionMessage(event);
            InlineKeyboardMarkup keyboard = keyboardService.createEditFieldSelectionKeyboard(eventId, context.getUserId());
            
            messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API при возврате к меню выбора поля: eventId={}, userId={}, error={}", 
                     eventId, context.getUserId(), e.getMessage(), e);

            throw new RuntimeException("Ошибка при возврате к меню выбора поля", e);
        }
    }
    
    /**
     * Обрабатывает отмену редактирования события.
     */
    private void handleEditCancel(@NonNull CallbackQueryContext context) {
        String eventIdStr = CallbackPrefix.EDIT_CANCEL.extractPayload(context.callbackData());
        Long eventId = Long.parseLong(eventIdStr);
        
        try {
            EditingContext editingContext = conversationStateService.getEditingContext(context.getUserId());
            Integer messageId = editingContext != null ? editingContext.getMessageId() : null;
            LocalDate sourceDate = editingContext != null ? editingContext.getSourceDate() : null;
            
            conversationStateService.clearEventEditing(context.getUserId());
            
            Event event = eventService.getEventById(eventId);
            
            if (sourceDate != null) {
                handleCancelFromCalendar(event, context, messageId, sourceDate);
            } else {
                handleCancelFromEvent(event, context, messageId);
            }
            
        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API при отмене редактирования: eventId={}, userId={}, error={}", 
                     eventId, context.getUserId(), e.getMessage(), e);

            throw new RuntimeException("Ошибка при отмене редактирования события", e);
        }
    }
    
    /**
     * Обрабатывает отмену редактирования из календаря.
     */
    private void handleCancelFromCalendar(@NonNull Event event,
                                          CallbackQueryContext context,
                                          Integer messageId,
                                          LocalDate sourceDate) throws TelegramApiException {
        if (messageId != null) {
            List<Event> allEvents = eventService.getEventsByDate(event.getUser().getFamily().getId(), sourceDate);
            List<Event> myEvents = allEvents.stream()
                    .filter(e -> e.getUser().getId().equals(context.getUserId()))
                    .collect(Collectors.toList());
            
            String message = "✏️ Выберите событие для редактирования:";
            InlineKeyboardMarkup keyboard = keyboardFactory.createEventListKeyboard(myEvents, sourceDate);
            
            messageService.editMessageText(context.chatId(), messageId, message, keyboard);
            
        } else {
            log.warn("MessageId не найден в контексте редактирования из календаря: eventId={}, userId={}", 
                    event.getId(), context.getUserId());
        }
        
        callbackQueryService.answerCallback(context, CallbackMessageFormatter.actionCancelled("Редактирование"));
    }
    
    /**
     * Обрабатывает отмену редактирования из карточки события.
     */
    private void handleCancelFromEvent(Event event,
                                       CallbackQueryContext context,
                                       Integer messageId) throws TelegramApiException {

        if (messageId != null) {
            int eventCount = eventService.getActiveEventsCount(event.getUser().getId());
            String eventMessage = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, context.getUserId());
            messageService.editMessageText(context.chatId(), messageId, eventMessage, keyboard);
            
        } else {
            log.warn("MessageId не найден в контексте редактирования, используем sendOrUpdateEventMessage: eventId={}, userId={}",
                    event.getId(), context.getUserId());
                    
            eventService.sendOrUpdateEventMessage(event, context.chatId());
        }
        
        callbackQueryService.answerCallback(context, CallbackMessageFormatter.actionCancelled("Редактирование"));
    }
    
    /**
     * Формирует сообщение с текущими данными события для выбора поля редактирования.
     */
    private @NonNull String buildEditFieldSelectionMessage(@NonNull Event event) {
        return "📝 " + bold("Редактирование события") + "\n\n" +
                botMessageFormattingService.buildEventMessage(event) +
                "\n\n" + "Выберите поле для редактирования:";
    }
    
    /**
     * Извлекает ID события из callback data.
     */
    private @NonNull Long extractEventId(String callbackData) {
        String payload = CallbackPrefix.EDIT_EVENT.extractPayload(callbackData);
        return Long.parseLong(payload);
    }
}
