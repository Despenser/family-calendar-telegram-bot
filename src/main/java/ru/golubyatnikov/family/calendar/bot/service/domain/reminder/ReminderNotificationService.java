package ru.golubyatnikov.family.calendar.bot.service.domain.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.config.ReminderNotificationConfig;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.Reminder;
import ru.golubyatnikov.family.calendar.bot.repository.ReminderRepository;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.ReminderMessageFormattingService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Сервис для отправки уведомлений о напоминаниях.
 * Отвечает за автоматическую отправку напоминаний по расписанию.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderNotificationService {
    
    private final ReminderRepository reminderRepository;
    private final ReminderValidator reminderValidator;
    private final ReminderSender reminderSender;
    private final ReminderMessageFormattingService messageFormatter;
    private final DateTimeFormattingService dateTimeFormattingService;
    private final ReminderNotificationConfig reminderNotificationConfig;
    
    /**
     * Автоматически отправляет напоминания по расписанию.
     */
    @Transactional
    public void sendReminders() {
        LocalDateTime nowUTC = LocalDateTime.now(dateTimeFormattingService.getUtc());
        LocalDateTime windowStart = nowUTC.minusHours(reminderNotificationConfig.getWindowHours());
        
        List<Reminder> reminders = findRemindersToSend(nowUTC, windowStart);
        
        if (reminders.isEmpty()) {
            return;
        }
        
        processReminders(reminders, nowUTC);
    }
    
    /**
     * Создает клавиатуру для уведомления о напоминании.
     * 
     * @param event событие
     * @param reminderId идентификатор напоминания
     *
     * @return inline-клавиатура
     */
    public InlineKeyboardMarkup createSimplifiedReminderKeyboard(Event event, Long reminderId) {
        return reminderSender.createKeyboard(event, reminderId);
    }
    
    /**
     * Форматирует короткую версию уведомления о напоминании.
     * 
     * @param reminder напоминание
     * @param recipientTimezone часовой пояс получателя
     *
     * @return короткое отформатированное сообщение
     */
    public String formatShortReminderMessage(Reminder reminder, ZoneId recipientTimezone) {
        return messageFormatter.formatShortMessage(reminder, recipientTimezone);
    }
    
    /**
     * Форматирует полную версию уведомления о напоминании.
     * 
     * @param reminder напоминание
     * @param recipientTimezone часовой пояс получателя
     *
     * @return полное отформатированное сообщение
     */
    public String formatReminderMessageByType(Reminder reminder, ZoneId recipientTimezone) {
        return messageFormatter.formatFullMessage(reminder, recipientTimezone);
    }
    
    private List<Reminder> findRemindersToSend(LocalDateTime nowUTC, LocalDateTime windowStart) {
        return reminderRepository.findBySentFalseAndReminderTimeLessThanEqualAndReminderTimeGreaterThanEqual(
            nowUTC, windowStart
        );
    }
    
    private void processReminders(@NonNull List<Reminder> reminders, LocalDateTime nowUTC) {
        reminders.forEach(reminder -> processReminder(reminder, nowUTC));
    }
    
    private void processReminder(Reminder reminder, LocalDateTime nowUTC) {
        try {
            Reminder lockedReminder = acquireLock(reminder.getId());
            
            if (lockedReminder == null || Boolean.TRUE.equals(lockedReminder.getSent())) {
                return;
            }
            
            if (!reminderValidator.shouldSendReminder(lockedReminder, nowUTC)) {
                return;
            }
            
            sendReminderWithRetry(lockedReminder, nowUTC);

        } catch (Exception e) {
            log.error("Ошибка обработки напоминания ID {}: {}", reminder.getId(), e.getMessage(), e);
        }
    }
    
    private @Nullable Reminder acquireLock(Long reminderId) {
        try {
            return reminderRepository.findByIdWithLock(reminderId).orElse(null);

        } catch (PessimisticLockingFailureException e) {
            log.warn("Не удалось получить блокировку на напоминание ID {}", reminderId);
            return null;
        }
    }
    
    private void sendReminderWithRetry(Reminder reminder, LocalDateTime nowUTC) {
        try {
            reminderSender.sendNotification(reminder);
            markAsSent(reminder, nowUTC);
            
        } catch (TelegramApiException e) {
            handleSendFailure(reminder, nowUTC);
        }
    }
    
    private void markAsSent(@NonNull Reminder reminder, LocalDateTime nowUTC) {
        reminder.setSent(true);
        reminder.setSentAt(nowUTC);
        reminderRepository.save(reminder);
    }
    
    private void handleSendFailure(@NonNull Reminder reminder,
                                   @NonNull LocalDateTime nowUTC) {

        LocalDateTime threshold = nowUTC.minusHours(reminderNotificationConfig.getOldThresholdHours());
        
        if (reminder.getReminderTime().isBefore(threshold)) {
            markAsSent(reminder, nowUTC);
            log.warn("Напоминание ID {} старше {} часа, отмечено как sent", reminder.getId(), 
                    reminderNotificationConfig.getOldThresholdHours());
        }
    }
}
