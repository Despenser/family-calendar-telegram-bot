package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Обработчик просмотра деталей события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventViewHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final CallbackQueryService callbackQueryService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final KeyboardService keyboardService;
    private final EventService eventService;
    private final BotMessageFormattingService botMessageFormattingService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.VIEW_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return CallbackPrefix.VIEW_EVENT.matches(callbackData);
    }
    
    @Override
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        Long eventId = extractEventId(context.callbackData());
        
        log.debug("Просмотр деталей события: eventId={}, userId={}, messageId={}", 
                 eventId, context.getUserId(), context.messageId());
        
        try {
            Event event = eventService.getEventById(eventId);
            
            log.debug("Событие загружено: eventId={}, userId={}", eventId, context.getUserId());
            
            int eventCount = eventService.getActiveEventsCount(context.getUserId());
            String eventMessage = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, context.getUserId());
            
            messageService.editMessageText(context.chatId(), context.messageId(), eventMessage, keyboard);
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
            log.info("Детали события отображены: eventId={}, messageId={}, userId={}", 
                    eventId, context.messageId(), context.getUserId());
            
        } catch (EventNotFoundException e) {
            log.warn("Событие не найдено при просмотре деталей: eventId={}, userId={}", 
                    eventId, context.getUserId(), e);

            answerCallbackQuerySafely(context, CallbackMessageFormatter.notFound("Событие"));

        } catch (TelegramApiException e) {
            log.warn("Ошибка Telegram API при просмотре деталей события: eventId={}, messageId={}, userId={}, error={}", 
                    eventId, context.messageId(), context.getUserId(), e.getMessage());

            answerCallbackQuerySafely(context, CallbackMessages.ERROR);

        } catch (Exception e) {
            log.error("Неожиданная ошибка при просмотре деталей события: eventId={}, userId={}, error={}", 
                     eventId, context.getUserId(), e.getMessage(), e);

            answerCallbackQuerySafely(context, CallbackMessages.ERROR);
        }
    }
    
    /**
     * Безопасно отвечает на callback query.
     */
    private void answerCallbackQuerySafely(CallbackQueryContext context, String message) {
        callbackQueryService.answerCallback(context, message);
    }
    
    /**
     * Извлекает ID события из callback data.
     */
    private @NonNull Long extractEventId(String callbackData) {
        String payload = CallbackPrefix.VIEW_EVENT.extractPayload(callbackData);
        return Long.parseLong(payload);
    }
}
