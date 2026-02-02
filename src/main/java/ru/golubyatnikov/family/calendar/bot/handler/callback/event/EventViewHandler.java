package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Обработчик просмотра деталей события.
 * 
 * <p>Обрабатывает callback query для просмотра полной информации о событии
 * с стандартной клавиатурой действий.</p>
 * 
 * <p><b>Требования:</b> 2.2, 2.4, 8.1, 8.2</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventViewHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final EventService eventService;
    private final BotMessageBuilder botMessageBuilder;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.VIEW_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return callbackData != null && CallbackPrefix.VIEW_EVENT.matches(callbackData);
    }
    
    @Override
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        Long userId = user.getId();
        
        Long eventId = extractEventId(callbackData);
        
        log.debug("Просмотр деталей события: eventId={}, userId={}, messageId={}", 
                 eventId, userId, messageId);
        
        try {
            // Получаем событие
            Event event = eventService.getEventById(eventId);
            
            log.debug("Событие загружено: eventId={}, userId={}", eventId, userId);
            log.debug("Определен контекст: Standard_Context, eventId={}, userId={}", 
                     eventId, userId);
            
            // Формируем текст сообщения с учетом флага isMyEventsHeader
            int eventCount = eventService.getActiveEventsCount(userId);
            String eventMessage = botMessageBuilder.buildEventMessageWithHeader(event, eventCount);
            
            // Используем стандартную клавиатуру с действиями
            InlineKeyboardMarkup keyboard = keyboardService.createEventActionsKeyboard(event, userId);
            
            log.debug("Используется стандартная клавиатура с действиями: eventId={}, userId={}", 
                     eventId, userId);
            
            // Обновляем сообщение
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
            
            log.info("Детали события отображены: eventId={}, messageId={}, userId={}", 
                    eventId, messageId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (EventNotFoundException e) {
            log.warn("Событие не найдено при просмотре деталей: eventId={}, userId={}", 
                    eventId, userId, e);
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Событие"));
            } catch (TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: eventId={}, userId={}, error={}", 
                         eventId, userId, ex.getMessage());
            }
            
        } catch (TelegramApiException e) {
            log.warn("Ошибка Telegram API при просмотре деталей события: eventId={}, messageId={}, userId={}, error={}", 
                    eventId, messageId, userId, e.getMessage());
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: eventId={}, userId={}, error={}", 
                         eventId, userId, ex.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Неожиданная ошибка при просмотре деталей события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: eventId={}, userId={}, error={}", 
                         eventId, userId, ex.getMessage());
            }
        }
    }
    
    /**
     * Извлекает ID события из callback data.
     * 
     * @param callbackData строка callback data
     * @return ID события
     */
    private Long extractEventId(String callbackData) {
        String payload = CallbackPrefix.VIEW_EVENT.extractPayload(callbackData);
        return Long.parseLong(payload);
    }
}
