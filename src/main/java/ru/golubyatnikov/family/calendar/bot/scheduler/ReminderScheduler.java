package ru.golubyatnikov.family.calendar.bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderNotificationService;
import ru.golubyatnikov.family.calendar.bot.util.CorrelationIdUtil;

/**
 * Планировщик для автоматической отправки напоминаний о событиях.
 * 
 * <p>Этот компонент выполняет следующие задачи:</p>
 * <ul>
 *   <li>Проверка неотправленных напоминаний каждую минуту</li>
 *   <li>Делегирование отправки напоминаний в ReminderService</li>
 *   <li>Логирование работы планировщика</li>
 * </ul>
 * 
 * <p>Планировщик запускается каждую минуту (fixedRate = 60000 мс) для проверки
 * напоминаний, время которых наступило.</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 10.1, 10.5</p>
 * 
 * @see ReminderService
 * @author Family Calendar Bot
 * @version 1.0
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
    @Scheduled(fixedRate = 60000) // Каждую минуту
    public void checkAndSendReminders() {
        CorrelationIdUtil.executeWithCorrelationId(() -> {
            log.debug("Запуск планировщика напоминаний");
            
            try {
                reminderNotificationService.sendReminders();
                log.debug("Планировщик напоминаний завершил работу");
                
            } catch (Exception e) {
                log.error("Ошибка при выполнении планировщика напоминаний: {}", e.getMessage(), e);
            }
        });
    }
}
