package ru.golubyatnikov.family.calendar.bot.handler.callback.textevent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

//TODO подключить разбор от GigaChat и формировать данные для команды
/**
 * Обработчик callback queries для создания событий из текста.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TextEventCallbackHandler implements CallbackHandler {
    
    private final ConversationService conversationService;
    private final TelegramMessageService messageService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final KeyboardService keyboardService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final DateTimeFormattingService dateTimeFormattingService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.CONFIRM_TEXT_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.CONFIRM_TEXT_EVENT.matches(callbackData) ||
               CallbackPrefix.CANCEL_TEXT_EVENT.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        if (CallbackPrefix.CONFIRM_TEXT_EVENT.matches(context.callbackData())) {
            handleConfirmTextEvent(context);

        } else if (CallbackPrefix.CANCEL_TEXT_EVENT.matches(context.callbackData())) {
            handleCancelTextEvent(context);
        }
    }
    
    /**
     * Обрабатывает подтверждение создания события из текста.
     */
    private void handleConfirmTextEvent(CallbackQueryContext context) {
        Event createdEvent = null;
        String errorMessage = null;
        
        try {
            String encodedData = CallbackPrefix.CONFIRM_TEXT_EVENT.extractPayload(context.callbackData());
            String decodedData = new String(Base64.getDecoder().decode(encodedData));
            
            String[] parts = decodedData.split("\\|");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Неверный формат данных события");
            }
            
            String title = parts[0];
            LocalDate date = LocalDate.parse(parts[1]);
            LocalTime time = LocalTime.parse(parts[2]);
            
            createdEvent = createEventInTransaction(context.getUserId(), title, date, time);
            
        } catch (Exception e) {
            log.error("Ошибка при подтверждении создания события из текста: userId={}, " +
                     "errorType={}, errorMessage={}", 
                     context.getUserId(), e.getClass().getSimpleName(), e.getMessage(), e);
            
            cleanupDraftOnError(context.getUserId());
            errorMessage = e.getMessage() != null ? e.getMessage() : "Неизвестная ошибка";
        }

        sendTelegramResponse(createdEvent, errorMessage, context);
    }
    
    /**
     * Создает событие в транзакции.
     *
     * @param userId идентификатор пользователя
     * @param title название события
     * @param date дата события
     * @param time время события
     *
     * @return созданное событие
     */
    @Transactional
    public Event createEventInTransaction(Long userId, String title, LocalDate date, LocalTime time) {
        conversationService.startEventCreation(userId);
        conversationService.updateEventDate(userId, date);
        conversationService.updateEventTime(userId, time);
        conversationService.updateEventTitle(userId, title);

        return conversationService.completeEventCreation(userId, null);
    }
    
    /**
     * Очищает черновик при ошибке создания события.
     * 
     * @param userId идентификатор пользователя
     */
    private void cleanupDraftOnError(Long userId) {
        try {
            conversationService.cancelEventCreation(userId);

        } catch (Exception cleanupEx) {
            log.error("Ошибка при удалении черновика после ошибки создания события: userId={}, error={}", 
                     userId, cleanupEx.getMessage(), cleanupEx);
        }
    }
    
    /**
     * Отправляет ответ через Telegram API.
     */
    private void sendTelegramResponse(Event createdEvent, String errorMessage, CallbackQueryContext context) {
        try {
            if (createdEvent != null) {
                try {
                    String eventMessage = botMessageFormattingService.buildEventCreatedMessage(createdEvent);
                    InlineKeyboardMarkup eventKeyboard = keyboardService.createEventActionsKeyboard(createdEvent.getId());
                    messageService.safeEditMessageAndAnswer(context.chatId(), context.messageId(), eventMessage, eventKeyboard, context.callbackQueryId(), CallbackMessages.CREATED);

                } catch (TelegramApiException e) {
                    log.error("Ошибка при отправке сообщения о созданном событии: eventId={}, error={}", 
                            createdEvent.getId(), e.getMessage());

                    String response = formatMessage(
                            """
                                    ✅ *Событие успешно создано!*
                                    
                                    📅 Дата: %s
                                    🕐 Время: %s
                                    📝 Название: %s""",
                        dateTimeFormattingService.formatDate(createdEvent.getEventDate()),
                        dateTimeFormattingService.formatTime(createdEvent.getEventTime()),
                        createdEvent.getTitle()
                    );
                    messageService.safeEditMessageAndAnswer(context.chatId(), context.messageId(),
                            response, null, context.callbackQueryId(), CallbackMessages.CREATED);
                }
            } else {
                String response = "❌ " + bold("Произошла ошибка при создании события") + "\\.\n\n" +
                                italic("Попробуйте использовать команду /add_event для пошагового создания.") + "\n\n" +
                                "Детали ошибки: " + escape(errorMessage);
                
                messageService.safeEditMessageAndAnswer(context.chatId(), context.messageId(), response, null, context.callbackQueryId(), CallbackMessages.ERROR);
            }
        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения через Telegram API: chatId={}, error={}", 
                     context.chatId(), ex.getMessage(), ex);
        }
    }
    
    /**
     * Обрабатывает отмену создания события из текста.
     */
    private void handleCancelTextEvent(@NonNull CallbackQueryContext context) {
        String message = botMessageFormattingService.buildEventCancelledMessage();
        try {
            messageService.safeEditMessageAndAnswer(context.chatId(), context.messageId(), message, null, context.callbackQueryId(), CallbackMessages.CANCELLED);

        } catch (TelegramApiException e) {
            log.error("Ошибка при отмене создания события из текста: chatId={}, error={}", 
                     context.chatId(), e.getMessage());
            throw new RuntimeException("Ошибка при отмене создания события", e);
        }
    }
}
