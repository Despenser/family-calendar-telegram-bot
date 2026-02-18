package ru.golubyatnikov.family.calendar.bot.service.domain.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.model.entity.Reminder;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Валидатор напоминаний.
 * Проверяет, следует ли отправлять напоминание.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReminderValidator {
    
    private final ReminderConfigurationService reminderConfigurationService;
    private final DateTimeFormattingService dateTimeFormattingService;
    
    /**
     * Проверяет, следует ли отправлять напоминание.
     * 
     * @param reminder напоминание для проверки
     * @param nowUTC текущее время в UTC
     *
     * @return true, если напоминание следует отправить
     */
    public boolean shouldSendReminder(@NonNull Reminder reminder, LocalDateTime nowUTC) {
        Event event = reminder.getEvent();
        
        if (isEventDeleted(event)) {
            return false;
        }
        
        return !isEventInPast(event, nowUTC, reminder.getId());
    }
    
    private boolean isEventDeleted(@NonNull Event event) {
        return event.getStatus() == EventStatus.DELETED;
    }
    
    private boolean isEventInPast(Event event, LocalDateTime nowUTC, Long reminderId) {
        try {
            LocalDateTime eventTimeUTC = convertEventTimeToUTC(event);
            return eventTimeUTC.isBefore(nowUTC);

        } catch (Exception e) {
            log.error("Ошибка проверки времени события для напоминания ID {}: {}", 
                     reminderId, e.getMessage(), e);

            return true;
        }
    }
    
    private LocalDateTime convertEventTimeToUTC(@NonNull Event event) {
        ZoneId userTimezone = reminderConfigurationService.getUserTimezone(event.getUser());
        
        ZonedDateTime eventZonedDateTime = ZonedDateTime.of(
            event.getEventDate(), 
            event.getEventTime(),
            userTimezone
        );
        
        return eventZonedDateTime
            .withZoneSameInstant(dateTimeFormattingService.getUtc())
            .toLocalDateTime();
    }
}
