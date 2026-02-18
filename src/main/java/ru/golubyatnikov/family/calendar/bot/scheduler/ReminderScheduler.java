package ru.golubyatnikov.family.calendar.bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderNotificationService;
import ru.golubyatnikov.family.calendar.bot.util.CorrelationIdUtil;

/**
 * Планировщик для автоматической отправки напоминаний о событиях.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-11
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {
    
    private final ReminderNotificationService reminderNotificationService;
    
    /**
     * Проверяет и отправляет напоминания по расписанию.

     */
    @Scheduled(fixedRateString = "${app.scheduler.reminder-check-interval}")
    public void checkAndSendReminders() {
        CorrelationIdUtil.executeWithCorrelationId(() -> {
            try {
                reminderNotificationService.sendReminders();

            } catch (Exception e) {
                log.error("Ошибка при выполнении планировщика напоминаний: {}", e.getMessage(), e);
            }
        });
    }
}
