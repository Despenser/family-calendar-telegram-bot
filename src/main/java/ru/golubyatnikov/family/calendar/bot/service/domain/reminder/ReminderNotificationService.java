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
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.Reminder;
import ru.golubyatnikov.family.calendar.bot.repository.ReminderRepository;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.ReminderMessageFormattingService;

import java.time.Duration;
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
    
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final int REMINDER_WINDOW_HOURS = 1;
    private static final int OLD_REMINDER_THRESHOLD_HOURS = 1;
    
    private final ReminderRepository reminderRepository;
    private final ReminderValidator reminderValidator;
    private final ReminderSender reminderSender;
    private final ReminderMessageFormattingService messageFormatter;
    
    /**
     * Автоматически отправляет напоминания по расписанию.
     */
    @Transactional
    public void sendReminders() {
        LocalDateTime nowUTC = LocalDateTime.now(UTC);
        LocalDateTime windowStart = nowUTC.minusHours(REMINDER_WINDOW_HOURS);
        
        log.debug("Запуск отправки напоминаний: nowUTC={}, windowStart={}", nowUTC, windowStart);
        
        List<Reminder> reminders = findRemindersToSend(nowUTC, windowStart);
        
        if (reminders.isEmpty()) {
            log.debug("Нет напоминаний для отправки в окне [{}, {}] UTC", windowStart, nowUTC);
            return;
        }
        
        log.info("Найдено {} напоминаний для проверки", reminders.size());
        
        SendingStatistics stats = processReminders(reminders, nowUTC);
        logStatistics(stats, nowUTC);
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
    
    private @NonNull SendingStatistics processReminders(@NonNull List<Reminder> reminders,
                                                        LocalDateTime nowUTC) {

        SendingStatistics stats = new SendingStatistics();
        reminders.forEach(reminder -> processReminder(reminder, nowUTC, stats));
        return stats;
    }
    
    private void processReminder(Reminder reminder, LocalDateTime nowUTC, SendingStatistics stats) {
        try {
            Reminder lockedReminder = acquireLock(reminder.getId());
            
            if (lockedReminder == null || Boolean.TRUE.equals(lockedReminder.getSent())) {
                stats.incrementSkipped();
                return;
            }
            
            if (!reminderValidator.shouldSendReminder(lockedReminder, nowUTC)) {
                stats.incrementSkipped();
                return;
            }
            
            sendReminderWithRetry(lockedReminder, nowUTC, stats);
            
        } catch (PessimisticLockingFailureException e) {
            stats.incrementLockFailures();

        } catch (Exception e) {
            stats.incrementFailed();
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
    
    private void sendReminderWithRetry(Reminder reminder, LocalDateTime nowUTC, SendingStatistics stats) {
        boolean isRecovery = isRecoveryReminder(reminder, nowUTC);
        
        if (isRecovery) {
            stats.incrementRecovered();
            logRecovery(reminder, nowUTC);
        }
        
        try {
            reminderSender.sendNotification(reminder);
            markAsSent(reminder, nowUTC);
            stats.incrementSent();
            
            logSuccess(reminder, nowUTC, isRecovery);
            
        } catch (TelegramApiException e) {
            handleSendFailure(reminder, nowUTC, stats, e);
        }
    }
    
    private boolean isRecoveryReminder(@NonNull Reminder reminder,
                                       @NonNull LocalDateTime nowUTC) {

        return reminder.getReminderTime().isBefore(nowUTC.minusMinutes(2));
    }
    
    private void markAsSent(@NonNull Reminder reminder, LocalDateTime nowUTC) {
        reminder.setSent(true);
        reminder.setSentAt(nowUTC);
        reminderRepository.save(reminder);
    }
    
    private void handleSendFailure(@NonNull Reminder reminder,
                                   @NonNull LocalDateTime nowUTC,
                                   SendingStatistics stats,
                                   TelegramApiException e) {

        log.error("Ошибка отправки напоминания ID {}: {}", reminder.getId(), e.getMessage(), e);
        
        LocalDateTime threshold = nowUTC.minusHours(OLD_REMINDER_THRESHOLD_HOURS);
        
        if (reminder.getReminderTime().isBefore(threshold)) {
            markAsSent(reminder, nowUTC);
            stats.incrementMarkedAsOld();
            log.warn("Напоминание ID {} старше {} часа, отмечено как sent", 
                    reminder.getId(), OLD_REMINDER_THRESHOLD_HOURS);

        } else {
            stats.incrementFailed();
        }
    }
    
    private void logRecovery(@NonNull Reminder reminder, LocalDateTime nowUTC) {
        long delayMinutes = Duration.between(reminder.getReminderTime(), nowUTC).toMinutes();
        log.warn("Восстановление пропущенного напоминания: id={}, eventId={}, delayMinutes={}", 
                reminder.getId(), reminder.getEvent().getId(), delayMinutes);
    }
    
    private void logSuccess(Reminder reminder, LocalDateTime nowUTC, boolean isRecovery) {
        if (isRecovery) {
            long delayMinutes = Duration.between(reminder.getReminderTime(), nowUTC).toMinutes();
            log.info("Восстановленное напоминание отправлено: id={}, eventId={}, delayMinutes={}", 
                    reminder.getId(), reminder.getEvent().getId(), delayMinutes);
        } else {
            log.info("Напоминание отправлено: id={}, eventId={}, type={}", 
                    reminder.getId(), reminder.getEvent().getId(), reminder.getReminderType());
        }
    }
    
    private void logStatistics(@NonNull SendingStatistics stats, LocalDateTime nowUTC) {
        if (stats.hasRecoveryOrOld()) {
            log.info("Отправка завершена: успешно={}, ошибок={}, пропущено={}, блокировок={}, " +
                    "восстановлено={}, отмечено старыми={}, nowUTC={}", 
                    stats.sent, stats.failed, stats.skipped, stats.lockFailures, 
                    stats.recovered, stats.markedAsOld, nowUTC);
        } else {
            log.info("Отправка завершена: успешно={}, ошибок={}, пропущено={}, блокировок={}, nowUTC={}", 
                    stats.sent, stats.failed, stats.skipped, stats.lockFailures, nowUTC);
        }
    }

    //TODO видимо надо вынести в model
    private static class SendingStatistics {
        private int sent = 0;
        private int failed = 0;
        private int skipped = 0;
        private int lockFailures = 0;
        private int recovered = 0;
        private int markedAsOld = 0;
        
        void incrementSent() { sent++; }
        void incrementFailed() { failed++; }
        void incrementSkipped() { skipped++; }
        void incrementLockFailures() { lockFailures++; }
        void incrementRecovered() { recovered++; }
        void incrementMarkedAsOld() { markedAsOld++; }
        
        boolean hasRecoveryOrOld() {
            return recovered > 0 || markedAsOld > 0;
        }
    }
}
