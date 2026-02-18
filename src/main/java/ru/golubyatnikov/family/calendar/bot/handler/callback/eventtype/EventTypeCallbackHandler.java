package ru.golubyatnikov.family.calendar.bot.handler.callback.eventtype;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.formatMessage;

/**
 * Обработчик callback queries для выбора типа события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventTypeCallbackHandler implements CallbackHandler {
    
    private final ConversationService conversationService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final EventService eventService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final CallbackQueryService callbackQueryService;
    
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
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        if (CallbackPrefix.EVENT_TYPE.matches(context.callbackData())) {
            handleEventTypeSelection(context);

        } else if (CallbackPrefix.SKIP_DESCRIPTION.matches(context.callbackData())) {
            handleSkipDescription(context);
        }
    }
    
    /**
     * Обрабатывает выбор типа события (семейное/персональное).
     * 
     * @param context контекст callback query
     */
    private void handleEventTypeSelection(@NonNull CallbackQueryContext context) {
        String eventType = CallbackPrefix.EVENT_TYPE.extractPayload(context.callbackData());
        boolean isPersonal = eventType.equals("personal");

        conversationService.updateEventType(context.getUserId(), isPersonal);

        String message = botMessageFormattingService.buildEventTypeSelectedMessage(isPersonal) + 
                        "\n\n" + bold("Теперь отправьте название события:");
        
        callbackQueryService.editMessageAndAnswer(context, message, null, CallbackMessages.SELECTED);
        
        }
    
    /**
     * Обрабатывает пропуск описания события.
     * Завершает создание события без описания.
     * 
     * @param context контекст callback query
     */
    private void handleSkipDescription(@NonNull CallbackQueryContext context) {
        Event completedEvent = conversationService.completeEventCreation(context.getUserId(), null);
        
        sendEventCreatedNotification(completedEvent, context.chatId());
        callbackQueryService.answerCallback(context.callbackQueryId(), CallbackMessages.CREATED);
        
        }
    
    /**
     * Отправляет уведомление о созданном событии.
     * В случае ошибки отправляет упрощённое сообщение.
     * 
     * @param event созданное событие
     * @param chatId идентификатор чата
     */
    private void sendEventCreatedNotification(Event event, Long chatId) {
        try {
            eventService.sendOrUpdateEventMessage(event, chatId);

        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения о созданном событии: eventId={}, error={}", 
                    event.getId(), e.getMessage());

            sendFallbackEventMessage(event, chatId);
        }
    }
    
    /**
     * Отправляет упрощённое сообщение о созданном событии в случае ошибки основного метода.
     * 
     * @param event созданное событие
     * @param chatId идентификатор чата
     */
    private void sendFallbackEventMessage(@NonNull Event event, Long chatId) {
        try {
            String response = formatMessage(
                    """
                            ✅ *Событие успешно создано!*
                            
                            📅 Дата: %s
                            🕐 Время: %s
                            📝 Название: %s""",
                event.getFormattedDate(),
                event.getFormattedTime(),
                event.getTitle()
            );

            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);

        } catch (TelegramApiException e) {
            log.error("Критическая ошибка при отправке fallback сообщения: eventId={}, error={}", 
                    event.getId(), e.getMessage());
        }
    }
}
