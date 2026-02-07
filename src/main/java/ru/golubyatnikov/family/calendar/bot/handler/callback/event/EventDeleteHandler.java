package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import java.time.LocalDate;
import java.util.List;

/**
 * Обработчик удаления события.
 * 
 * <p>Метод выполняет следующие действия:</p>
 * <ol>
 *   <li>Получает событие и его дату</li>
 *   <li>Вызывает eventService.deleteEvent() для удаления события</li>
 *   <li>Проверяет оставшиеся события на эту дату</li>
 *   <li>Если события остались - обновляет список событий в сообщении</li>
 *   <li>Если событий не осталось - показывает экран создания события</li>
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
    private final KeyboardService keyboardService;
    private final BotMessageBuilder messageBuilder;
    
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
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        
        Long eventId = extractEventId(callbackData);
        
        log.info("Удаление события ID={} пользователем ID={}", eventId, userId);
        
        try {
            // Получаем событие до удаления, чтобы узнать его дату
            Event event = eventService.getEventById(eventId);
            LocalDate eventDate = event.getEventDate();
            Long familyId = user.getFamily().getId();
            
            // Удаляем событие (перемещаем в корзину)
            // EventService автоматически удалит сообщение и обновит шапку /my_events
            eventService.deleteEvent(eventId, userId);
            
            // Получаем оставшиеся события на эту дату
            List<Event> allRemainingEvents = eventService.getEventsByDate(familyId, eventDate);
            
            // Проверяем, есть ли у пользователя еще свои события
            List<Event> myRemainingEvents = allRemainingEvents.stream()
                .filter(e -> e.getUser().getId().equals(userId))
                .collect(java.util.stream.Collectors.toList());
            
            LocalDate today = LocalDate.now(user.getZoneId());
            boolean isPastDate = eventDate.isBefore(today);
            
            if (allRemainingEvents.isEmpty()) {
                // Событий вообще не осталось
                if (isPastDate) {
                    // Прошлая дата без событий - возвращаемся к календарю
                    log.info("На прошлую дату {} не осталось событий, возвращаемся к календарю", eventDate);
                    messageService.answerCallbackQuery(callbackQueryId, 
                        CallbackMessages.DELETED + ". На эту дату больше нет событий");
                    
                    // Удаляем сообщение и пользователь вернется к календарю
                    messageService.deleteMessageSilently(chatId, messageId);
                } else {
                    // Будущая дата без событий - показываем экран создания
                    log.info("На дату {} не осталось событий, показываем экран создания", eventDate);
                    
                    String message = messageBuilder.buildCreateEventOnDateMessage(eventDate);
                    InlineKeyboardMarkup keyboard = keyboardService.createCreateEventOnDateKeyboard(eventDate);
                    
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.DELETED);
                }
            } else if (myRemainingEvents.isEmpty()) {
                // У пользователя не осталось своих событий, но есть события других
                log.info("У пользователя не осталось своих событий на дату {}, но есть события других", eventDate);
                
                if (isPastDate) {
                    // Прошлая дата - показываем список всех событий для просмотра
                    String message = messageBuilder.buildDateEventsListMessage(eventDate, allRemainingEvents);
                    InlineKeyboardMarkup keyboard = keyboardService.createDateEventsListKeyboard(
                        eventDate, allRemainingEvents, user);
                    
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                } else {
                    // Будущая дата - возвращаемся к экрану управления
                    String message = messageBuilder.buildDateEventsManagementMessage(eventDate, allRemainingEvents);
                    InlineKeyboardMarkup keyboard = keyboardService.createDateEventsManagementKeyboard(
                        eventDate, allRemainingEvents, user);
                    
                    messageService.editMessageText(chatId, messageId, message, keyboard);
                }
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.DELETED);
            } else {
                // У пользователя остались свои события - обновляем список для удаления
                log.info("У пользователя осталось {} своих событий на дату {}, обновляем список для удаления", 
                        myRemainingEvents.size(), eventDate);
                
                String message = "🗑 Выберите событие для удаления:";
                
                java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
                
                for (Event e : myRemainingEvents) {
                    java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
                    String buttonText = String.format("%s - %s", 
                        e.getEventTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                        e.getTitle());
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button = 
                        new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(buttonText);
                    button.setCallbackData("delete_event_" + e.getId());
                    row.add(button);
                    rows.add(row);
                }
                
                java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> backRow = new java.util.ArrayList<>();
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton backButton = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("🔙 Назад");
                backButton.setCallbackData("calendar_" + eventDate.toString());
                backRow.add(backButton);
                rows.add(backRow);
                
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
                    new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                keyboard.setKeyboard(rows);
                
                messageService.editMessageText(chatId, messageId, message, keyboard);
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.DELETED);
            }
            
            log.debug("Событие ID={} успешно удалено пользователем ID={}, UI обновлен", eventId, userId);
            
        } catch (EventNotFoundException e) {
            log.error("Событие не найдено: eventId={}, userId={}", eventId, userId, e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Событие не найдено");
        } catch (UnauthorizedAccessException e) {
            log.error("Нет прав на удаление события: eventId={}, userId={}", eventId, userId, e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Нет прав на удаление");
        } catch (Exception e) {
            log.error("Ошибка при удалении события: eventId={}, userId={}, error={}", 
                     eventId, userId, e.getMessage(), e);
            messageService.answerCallbackQuery(callbackQueryId, "❌ Ошибка при удалении");
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
