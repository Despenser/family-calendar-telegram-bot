package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.ReminderCallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;

/**
 * Обработчик callback queries для управления напоминаниями.
 * 
 * <p>Этот компонент обрабатывает все callback queries связанные с напоминаниями:</p>
 * <ul>
 *   <li>Настройка напоминаний (setup_reminders_)</li>
 *   <li>Переключение типов напоминаний (toggle_reminder_)</li>
 *   <li>Подтверждение создания напоминаний (confirm_reminders_)</li>
 *   <li>Просмотр напоминаний (view_reminders_)</li>
 *   <li>Удаление напоминания (delete_reminder_)</li>
 *   <li>Отключение автоматических напоминаний (disable_reminders_)</li>
 *   <li>Включение автоматических напоминаний (enable_reminders_)</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 3.2, 3.3, 13.1, 13.2, 13.3, 13.4, 13.5</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see ReminderCallbackHandler
 * @see CallbackHandler
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderCallbackHandlerImpl implements CallbackHandler {
    
    private final ReminderCallbackHandler reminderCallbackHandler;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.SETUP_REMINDERS;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return CallbackPrefix.SETUP_REMINDERS.matches(callbackData) ||
               CallbackPrefix.TOGGLE_REMINDER.matches(callbackData) ||
               CallbackPrefix.CONFIRM_REMINDERS.matches(callbackData) ||
               CallbackPrefix.VIEW_REMINDERS.matches(callbackData) ||
               CallbackPrefix.DELETE_REMINDER.matches(callbackData) ||
               CallbackPrefix.DISABLE_REMINDERS.matches(callbackData) ||
               CallbackPrefix.ENABLE_REMINDERS.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback для напоминаний: data='{}', userId={}, chatId={}", 
                 callbackData, user.getId(), chatId);
        
        // Отключение автоматических напоминаний
        if (CallbackPrefix.DISABLE_REMINDERS.matches(callbackData)) {
            String payload = CallbackPrefix.DISABLE_REMINDERS.extractPayload(callbackData);
            Long eventId = Long.parseLong(payload);
            
            log.debug("Отключение напоминаний для события: eventId={}, userId={}", eventId, user.getId());
            reminderCallbackHandler.handleDisableReminders(eventId, chatId, messageId, callbackQueryId);
            return;
        }
        
        // Включение автоматических напоминаний
        if (CallbackPrefix.ENABLE_REMINDERS.matches(callbackData)) {
            String payload = CallbackPrefix.ENABLE_REMINDERS.extractPayload(callbackData);
            Long eventId = Long.parseLong(payload);
            
            log.debug("Включение напоминаний для события: eventId={}, userId={}", eventId, user.getId());
            reminderCallbackHandler.handleEnableReminders(eventId, chatId, messageId, callbackQueryId);
            return;
        }
        
        // Настройка напоминаний
        if (CallbackPrefix.SETUP_REMINDERS.matches(callbackData)) {
            String payload = CallbackPrefix.SETUP_REMINDERS.extractPayload(callbackData);
            Long eventId = Long.parseLong(payload);
            
            log.debug("Настройка напоминаний для события: eventId={}, userId={}", eventId, user.getId());
            reminderCallbackHandler.handleSetupReminders(eventId, chatId, messageId, callbackQueryId);
            return;
        }
        
        // Переключение типа напоминания
        if (CallbackPrefix.TOGGLE_REMINDER.matches(callbackData)) {
            String payload = CallbackPrefix.TOGGLE_REMINDER.extractPayload(callbackData);
            String[] parts = payload.split("_", 2);
            
            if (parts.length != 2) {
                log.error("Некорректный формат callback для toggle_reminder: '{}'", callbackData);
                return;
            }
            
            Long eventId = Long.parseLong(parts[0]);
            ru.golubyatnikov.family.calendar.bot.model.Reminder.ReminderType type = 
                ru.golubyatnikov.family.calendar.bot.model.Reminder.ReminderType.valueOf(parts[1]);
            
            log.debug("Переключение типа напоминания: eventId={}, type={}, userId={}", 
                     eventId, type, user.getId());
            reminderCallbackHandler.handleReminderTypeSelection(eventId, type, chatId, messageId, callbackQueryId);
            return;
        }
        
        // Подтверждение создания напоминаний
        if (CallbackPrefix.CONFIRM_REMINDERS.matches(callbackData)) {
            String payload = CallbackPrefix.CONFIRM_REMINDERS.extractPayload(callbackData);
            Long eventId = Long.parseLong(payload);
            
            log.debug("Подтверждение создания напоминаний: eventId={}, userId={}", eventId, user.getId());
            reminderCallbackHandler.handleConfirmReminders(eventId, chatId, messageId, callbackQueryId);
            return;
        }
        
        // Просмотр напоминаний
        if (CallbackPrefix.VIEW_REMINDERS.matches(callbackData)) {
            String payload = CallbackPrefix.VIEW_REMINDERS.extractPayload(callbackData);
            Long eventId = Long.parseLong(payload);
            
            log.debug("Просмотр напоминаний: eventId={}, userId={}", eventId, user.getId());
            reminderCallbackHandler.handleViewReminders(eventId, chatId, messageId);
            return;
        }
        
        // Удаление напоминания
        if (CallbackPrefix.DELETE_REMINDER.matches(callbackData)) {
            String payload = CallbackPrefix.DELETE_REMINDER.extractPayload(callbackData);
            Long reminderId = Long.parseLong(payload);
            
            log.debug("Удаление напоминания: reminderId={}, userId={}", reminderId, user.getId());
            reminderCallbackHandler.handleDeleteReminder(reminderId, chatId, messageId, callbackQueryId);
            return;
        }
        
        log.warn("Неизвестный callback для напоминаний: data='{}', userId={}", callbackData, user.getId());
    }
}
