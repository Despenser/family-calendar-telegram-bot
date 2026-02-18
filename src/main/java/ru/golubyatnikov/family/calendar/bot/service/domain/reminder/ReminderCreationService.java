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
import ru.golubyatnikov.family.calendar.bot.repository.ReminderRepository;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;

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
    
    private final ReminderRepository reminderRepository;
    private final ReminderConfigurationService reminderConfigurationService;
    private final DefaultReminderConfig defaultReminderConfig;
    private final DateTimeFormattingService dateTimeFormattingService;
    
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
        if (!shouldCreateDefaultReminders(event)) {
            return new ArrayList<>();
        }
        
        List<ReminderType> types = defaultReminderConfig.getTypes();
        if (types == null || types.isEmpty()) {
            return new ArrayList<>();
        }
        
        ZoneId userTimezone = reminderConfigurationService.getUserTimezone(user);
        List<Reminder> reminders = buildRemindersWithTimezone(event, types, userTimezone);
        
        if (reminders.isEmpty()) {
            return reminders;
        }

        return reminderRepository.saveAll(reminders);
    }
    
    /**
     * Отключает все автоматические напоминания для события.
     * 
     * @param eventId идентификатор события
     */
    @Transactional
    public void disableRemindersForEvent(Long eventId) {
        List<Reminder> reminders = reminderRepository.findByEventId(eventId);
        
        if (reminders.isEmpty()) {
            return;
        }
        
        reminderRepository.deleteAll(reminders);
    }
    
    private @NonNull List<Reminder> buildRemindersWithTimezone(@NonNull Event event,
                                                               @NonNull List<ReminderType> types,
                                                               ZoneId timezone) {

        List<Reminder> reminders = new ArrayList<>();
        LocalDateTime nowUTC = LocalDateTime.now(dateTimeFormattingService.getUtc());

        types.forEach(type -> {
            try {
                LocalDateTime reminderTimeUTC = reminderConfigurationService
                        .calculateReminderTimeWithTimezone(event, type, timezone);

                if (reminderTimeUTC.isBefore(nowUTC)) {
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
            return false;
        }

        return event.getEventDate() != null && event.getEventTime() != null;
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
