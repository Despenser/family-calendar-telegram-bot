package ru.golubyatnikov.family.calendar.bot.service.domain.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.config.DefaultReminderConfig;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.ReminderType;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.ReminderRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для создания напоминаний о событиях.
 * Отвечает за создание стандартных и автоматических напоминаний.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderCreationService {
    
    private static final ZoneId UTC = ZoneId.of("UTC");
    
    private final ReminderRepository reminderRepository;
    private final EventRepository eventRepository;
    private final ReminderConfigurationService reminderConfigurationService;
    private final DefaultReminderConfig defaultReminderConfig;
    
    /**
     * Создает автоматические напоминания по умолчанию для события.
     * 
     * @param event событие, для которого создаются напоминания
     * @param user пользователь-создатель события
     *
     * @return список созданных напоминаний (может быть пустым)
     */
    @Transactional
    public List<Reminder> createDefaultReminders(@NonNull Event event, @NonNull User user) {
        log.debug("Создание автоматических напоминаний для события ID {} пользователем ID {}", 
                 event.getId(), user.getId());
        
        if (!shouldCreateDefaultReminders(event)) {
            return new ArrayList<>();
        }
        
        List<ReminderType> types = defaultReminderConfig.getTypes();
        if (types == null || types.isEmpty()) {
            log.warn("Конфигурация автоматических напоминаний пуста для события ID {}", event.getId());
            return new ArrayList<>();
        }
        
        ZoneId userTimezone = reminderConfigurationService.getUserTimezone(user);
        List<Reminder> reminders = buildRemindersWithTimezone(event, types, userTimezone);
        
        if (reminders.isEmpty()) {
            log.info("Не создано автоматических напоминаний для события ID {} (все времена в прошлом)", 
                    event.getId());
            return reminders;
        }
        
        List<Reminder> saved = reminderRepository.saveAll(reminders);
        log.info("Автоматически создано {} напоминаний для события ID {}", saved.size(), event.getId());
        
        return saved;
    }
    
    /**
     * Отключает все автоматические напоминания для события.
     * 
     * @param eventId идентификатор события
     */
    @Transactional
    public void disableRemindersForEvent(Long eventId) {
        log.debug("Отключение напоминаний для события ID {}", eventId);
        
        List<Reminder> reminders = reminderRepository.findByEventId(eventId);
        
        if (reminders.isEmpty()) {
            log.debug("Нет напоминаний для отключения для события ID {}", eventId);
            return;
        }
        
        reminderRepository.deleteAll(reminders);
        log.info("Отключено {} напоминаний для события ID {}", reminders.size(), eventId);
    }
    
    private @NonNull List<Reminder> buildRemindersWithTimezone(@NonNull Event event,
                                                               @NonNull List<ReminderType> types,
                                                               ZoneId timezone) {

        List<Reminder> reminders = new ArrayList<>();
        LocalDateTime nowUTC = LocalDateTime.now(UTC);

        types.forEach(type -> {
            try {
                LocalDateTime reminderTimeUTC = reminderConfigurationService
                        .calculateReminderTimeWithTimezone(event, type, timezone);

                if (reminderTimeUTC.isBefore(nowUTC)) {
                    log.debug("Пропуск напоминания типа {} для события ID {}: время в прошлом",
                            type, event.getId());
                    return;
                }

                reminders.add(createReminder(event, type, reminderTimeUTC));

            } catch (Exception e) {
                log.error("Ошибка создания напоминания типа {} для события ID {}: {}",
                        type, event.getId(), e.getMessage(), e);
            }
        });
        
        return reminders;
    }
    
    private boolean shouldCreateDefaultReminders(Event event) {
        if (!defaultReminderConfig.isEnabled()) {
            log.debug("Автоматические напоминания отключены глобально");
            return false;
        }
        
        if (event.getEventDate() == null || event.getEventTime() == null) {
            log.debug("Событие ID {} не имеет даты или времени", event.getId());
            return false;
        }
        
        return true;
    }
    
    private Reminder createReminder(Event event, ReminderType type, LocalDateTime reminderTime) {
        return Reminder.builder()
            .event(event)
            .reminderType(type)
            .reminderTime(reminderTime)
            .sent(false)
            .build();
    }
}
