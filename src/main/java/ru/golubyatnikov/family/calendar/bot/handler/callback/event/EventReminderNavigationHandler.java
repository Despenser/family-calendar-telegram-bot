package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.ReminderService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.UserService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessageFormatter;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик навигации между событием и напоминанием.
 * 
 * <p>Обрабатывает переходы между полным представлением события
 * и минималистичным представлением напоминания.</p>
 * 
 * <p><b>Требования:</b> 2.1, 2.3, 3.1, 3.2, 3.5, 4.1, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.4, 9.2, 9.3, 9.4, 9.5, 10.1, 10.2</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventReminderNavigationHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final EventService eventService;
    private final ReminderService reminderService;
    private final UserService userService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.VIEW_EVENT_FROM_REMINDER;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        return CallbackPrefix.VIEW_EVENT_FROM_REMINDER.matches(callbackData) ||
               CallbackPrefix.BACK_TO_REMINDER.matches(callbackData);
    }
    
    @Override
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        Long userId = user.getId();
        
        if (CallbackPrefix.VIEW_EVENT_FROM_REMINDER.matches(callbackData)) {
            handleViewEventFromReminder(callbackData, userId, chatId, messageId, callbackQueryId);
        } else if (CallbackPrefix.BACK_TO_REMINDER.matches(callbackData)) {
            handleBackToReminder(callbackData, userId, chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает просмотр деталей события из уведомления о напоминании.
     * 
     * @param callbackData данные callback (формат: view_event_from_reminder_{eventId}_{reminderId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для обновления
     * @param callbackQueryId идентификатор callback query
     */
    private void handleViewEventFromReminder(String callbackData, Long userId, Long chatId, 
                                            Integer messageId, String callbackQueryId) {
        log.debug("Просмотр деталей события из напоминания: callbackData='{}', userId={}, messageId={}", 
                 callbackData, userId, messageId);
        
        try {
            // Извлекаем payload из callback data
            String payload = CallbackPrefix.VIEW_EVENT_FROM_REMINDER.extractPayload(callbackData);
            
            // Разделяем payload на eventId и reminderId
            String[] parts = payload.split("_", 2);
            
            // Валидация формата
            if (parts.length != 2) {
                log.error("Некорректный формат callback data для view_event_from_reminder: " +
                         "ожидается 2 части, получено {}. CallbackData='{}', userId={}", 
                         parts.length, callbackData, userId);
                
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.INVALID_REQUEST);
                return;
            }
            
            Long eventId;
            Long reminderId;
            
            // Парсинг eventId и reminderId с обработкой NumberFormatException
            try {
                eventId = Long.parseLong(parts[0]);
                reminderId = Long.parseLong(parts[1]);
                
                log.debug("Успешно извлечены данные: eventId={}, reminderId={}, userId={}", 
                         eventId, reminderId, userId);
            } catch (NumberFormatException e) {
                log.error("Некорректный eventId или reminderId в callback data: " +
                         "eventId='{}', reminderId='{}', callbackData='{}', userId={}, error={}", 
                         parts[0], parts[1], callbackData, userId, e.getMessage());
                
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.INVALID_REQUEST);
                return;
            }
            
            // Получаем событие
            Event event = eventService.getEventById(eventId);
            
            log.debug("Событие загружено: eventId={}, userId={}", eventId, userId);
            log.debug("Определен контекст: Reminder_Context, eventId={}, reminderId={}, userId={}", 
                     eventId, reminderId, userId);
            
            // Загружаем напоминание из базы данных с eager загрузкой события И пользователя
            Reminder reminder = reminderService.getReminderWithEventAndUser(reminderId);
            
            log.debug("Напоминание загружено с событием и пользователем: reminderId={}, eventId={}, userId={}", 
                     reminderId, eventId, userId);
            
            // Загружаем пользователя-получателя для получения timezone
            User recipient = userService.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                    "Пользователь не найден: userId=" + userId));
            
            // Получаем timezone получателя для форматирования
            ZoneId userTimezone = recipient.getTimezone() != null 
                ? ZoneId.of(recipient.getTimezone()) 
                : ZoneId.of("UTC");
            
            // Формируем текст сообщения с ПОЛНОЙ информацией о напоминании
            String eventMessage = reminderService.formatReminderMessageByType(reminder, userTimezone);
            
            // Создаем упрощенную клавиатуру с кнопкой "Назад к напоминанию"
            InlineKeyboardMarkup keyboard = createDetailsKeyboard(eventId, reminderId);
            
            log.debug("Создана упрощенная клавиатура для напоминания: eventId={}, reminderId={}, userId={}", 
                     eventId, reminderId, userId);
            
            // Обновляем сообщение
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
            
            log.info("Детали события отображены из напоминания: eventId={}, reminderId={}, " +
                    "messageId={}, userId={}", eventId, reminderId, messageId, userId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (EventNotFoundException e) {
            log.warn("Событие не найдено при просмотре деталей из напоминания: userId={}", userId, e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, 
                    CallbackMessageFormatter.notFound("Событие"));
            } catch (TelegramApiException ex) {
                log.error("Ошибка при отправке callback answer: {}", ex.getMessage());
            }
                
        } catch (TelegramApiException e) {
            log.warn("Ошибка Telegram API при просмотре деталей из напоминания: " +
                    "messageId={}, userId={}, error={}", messageId, userId, e.getMessage());
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (TelegramApiException ex) {
                log.error("Ошибка при отправке callback answer: {}", ex.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Неожиданная ошибка при просмотре деталей из напоминания: " +
                     "userId={}, error={}", userId, e.getMessage(), e);
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (TelegramApiException ex) {
                log.error("Ошибка при отправке callback answer: {}", ex.getMessage());
            }
        }
    }
    
    /**
     * Обрабатывает возврат к минималистичному виду напоминания.
     * 
     * @param callbackData данные callback (формат: back_to_reminder_{eventId}_{reminderId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для обновления
     * @param callbackQueryId идентификатор callback query
     */
    private void handleBackToReminder(String callbackData, Long userId, Long chatId, 
                                      Integer messageId, String callbackQueryId) {
        log.debug("Возврат к напоминанию: callbackData='{}', userId={}, messageId={}", 
                 callbackData, userId, messageId);
        
        try {
            // Извлекаем payload из callback data
            String payload = CallbackPrefix.BACK_TO_REMINDER.extractPayload(callbackData);
            
            // Разделяем payload на eventId и reminderId
            String[] parts = payload.split("_", 2);
            
            // Валидация формата
            if (parts.length != 2) {
                log.error("Некорректный формат callback data для back_to_reminder: ожидается 2 части, получено {}. " +
                         "CallbackData='{}', userId={}", parts.length, callbackData, userId);
                
                try {
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.INVALID_REQUEST);
                } catch (TelegramApiException ex) {
                    log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                             userId, ex.getMessage());
                }
                return;
            }
            
            Long eventId;
            Long reminderId;
            
            // Парсинг eventId и reminderId с обработкой NumberFormatException
            try {
                eventId = Long.parseLong(parts[0]);
                reminderId = Long.parseLong(parts[1]);
                
                log.debug("Успешно извлечены данные: eventId={}, reminderId={}, userId={}", 
                         eventId, reminderId, userId);
            } catch (NumberFormatException e) {
                log.error("Некорректный eventId или reminderId в callback data: eventId='{}', reminderId='{}', " +
                         "callbackData='{}', userId={}, error={}", 
                         parts[0], parts[1], callbackData, userId, e.getMessage());
                
                try {
                    messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.INVALID_REQUEST);
                } catch (TelegramApiException ex) {
                    log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                             userId, ex.getMessage());
                }
                return;
            }
            
            // Загружаем напоминание с eager загрузкой события и пользователя
            Reminder reminder = reminderService.getReminderWithEventAndUser(reminderId);
            
            log.debug("Напоминание загружено с событием и пользователем: reminderId={}, eventId={}, userId={}", 
                     reminderId, reminder.getEvent().getId(), userId);
            
            // Получаем событие из напоминания
            Event event = reminder.getEvent();
            
            // Получаем timezone создателя события
            ZoneId creatorTimezone = event.getUser().getTimezone() != null 
                ? ZoneId.of(event.getUser().getTimezone()) 
                : ZoneId.of("UTC");
            
            log.debug("Timezone создателя события получен: eventId={}, creatorTimezone={}, userId={}", 
                     event.getId(), creatorTimezone, userId);
            
            // Восстанавливаем КОРОТКИЙ текст напоминания
            String reminderMessage = reminderService.formatShortReminderMessage(reminder, creatorTimezone);
            
            log.debug("Текст напоминания восстановлен: eventId={}, reminderId={}, userId={}", 
                     eventId, reminderId, userId);
            
            // Создаем упрощенную клавиатуру
            InlineKeyboardMarkup keyboard = reminderService.createSimplifiedReminderKeyboard(event, reminderId);
            
            log.debug("Упрощенная клавиатура создана: eventId={}, reminderId={}, userId={}", 
                     eventId, reminderId, userId);
            
            // Обновляем сообщение
            messageService.editMessageText(chatId, messageId, reminderMessage, keyboard);
            
            log.info("Возврат к напоминанию выполнен: eventId={}, reminderId={}, messageId={}, userId={}", 
                    eventId, reminderId, messageId, userId);
            
            // Отвечаем на callback query с подтверждением
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
            
        } catch (ReminderNotFoundException e) {
            log.warn("Напоминание не найдено при возврате к напоминанию: userId={}", userId, e);
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessageFormatter.notFound("Напоминание"));
            } catch (TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                         userId, ex.getMessage());
            }
            
        } catch (TelegramApiException e) {
            log.warn("Ошибка Telegram API при возврате к напоминанию: messageId={}, userId={}, error={}", 
                    messageId, userId, e.getMessage());
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                         userId, ex.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Неожиданная ошибка при возврате к напоминанию: userId={}, error={}", 
                     userId, e.getMessage(), e);
            
            try {
                messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.ERROR);
            } catch (TelegramApiException ex) {
                log.error("Ошибка отправки callback query answer: userId={}, error={}", 
                         userId, ex.getMessage());
            }
        }
    }
    
    /**
     * Создает клавиатуру для просмотра деталей события из напоминания.
     * 
     * @param eventId идентификатор события
     * @param reminderId идентификатор напоминания
     * @return inline-клавиатура с кнопкой "Назад к напоминанию"
     */
    private InlineKeyboardMarkup createDetailsKeyboard(Long eventId, Long reminderId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад к напоминанию");
        backButton.setCallbackData(CallbackPrefix.BACK_TO_REMINDER.withPayload(eventId + "_" + reminderId));
        row.add(backButton);
        keyboard.add(row);
        
        markup.setKeyboard(keyboard);
        return markup;
    }
}
