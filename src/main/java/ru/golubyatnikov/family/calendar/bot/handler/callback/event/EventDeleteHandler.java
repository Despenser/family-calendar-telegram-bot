package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Обработчик удаления события.
 * 
 * <p>Метод выполняет следующие действия:</p>
 * <ol>
 *   <li>Вызывает eventService.deleteEvent() для удаления события</li>
 *   <li>Отвечает на callback query с текстом "Событие удалено"</li>
 * </ol>
 * 
 * <p>EventService.deleteEvent() автоматически:</p>
 * <ul>
 *   <li>Удаляет сообщение события из чата</li>
 *   <li>Обновляет статус события на DELETED</li>
 *   <li>Сбрасывает messageId и isMyEventsHeader</li>
 *   <li>Вызывает updateMyEventsHeaderAfterRemoval для обновления шапки</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.4</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventDeleteHandler implements CallbackHandler {
    
    private final EventService eventService;
    private final TelegramMessageService messageService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.DELETE_EVENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return callbackData != null && CallbackPrefix.DELETE_EVENT.matches(callbackData);
    }
    
    @Override
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        String callbackQueryId = callbackQuery.getId();
        Long userId = user.getId();
        
        Long eventId = extractEventId(callbackData);
        
        log.info("Удаление события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Удаляем событие (перемещаем в корзину)
            // EventService автоматически удалит сообщение и обновит шапку /my_events
            eventService.deleteEvent(eventId, userId);
            
            // Отвечаем на callback query с подтверждением
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.DELETED);
            
            log.debug("Событие ID={} успешно удалено пользователем ID={}", eventId, userId);
            
        } catch (EventNotFoundException e) {
            log.error("Событие не найдено: eventId={}, userId={}", eventId, userId, e);
        } catch (UnauthorizedAccessException e) {
            log.error("Нет прав на удаление события: eventId={}, userId={}", eventId, userId, e);
        } catch (Exception e) {
            log.error("Ошибка при удалении события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
        }
    }
    
    /**
     * Извлекает ID события из callback data.
     * 
     * @param callbackData строка callback data
     * @return ID события
     */
    private Long extractEventId(String callbackData) {
        String payload = CallbackPrefix.DELETE_EVENT.extractPayload(callbackData);
        return Long.parseLong(payload);
    }
}
