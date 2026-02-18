package ru.golubyatnikov.family.calendar.bot.service.domain.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.ReminderType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Сервис для настройки типов напоминаний.
 * Отвечает за расчет времени напоминаний и работу с часовыми поясами.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderConfigurationService {
    
    private static final ZoneId UTC = ZoneId.of("UTC");
    
    /**
     * Рассчитывает время отправки напоминания на основе типа.
     * 
     * @param event событие
     * @param type тип напоминания
     *
     * @return рассчитанное время отправки напоминания в UTC
     */
    public LocalDateTime calculateReminderTime(@NonNull Event event, ReminderType type) {
        ZoneId userTimezone = getUserTimezone(event.getUser());
        return calculateReminderTimeWithTimezone(event, type, userTimezone);
    }
    
    /**
     * Рассчитывает время отправки напоминания с учетом часового пояса пользователя.
     * 
     * @param event событие
     * @param type тип напоминания
     * @param userTimezone часовой пояс пользователя
     *
     * @return рассчитанное время отправки напоминания в UTC
     */
    public LocalDateTime calculateReminderTimeWithTimezone(@NonNull Event event,
                                                           ReminderType type,
                                                           ZoneId userTimezone) {

        try {
            ZonedDateTime eventZonedDateTime = createEventZonedDateTime(event, userTimezone);
            ZonedDateTime reminderZonedDateTime = calculateReminderDateTime(eventZonedDateTime, type, event);

            return convertToUTC(reminderZonedDateTime);
            
        } catch (Exception e) {
            return handleCalculationError(e, event, type, userTimezone);
        }
    }
    
    /**
     * Получает часовой пояс пользователя с fallback на UTC.
     * 
     * @param user пользователь
     * @return ZoneId пользователя или UTC при ошибке
     */
    public ZoneId getUserTimezone(User user) {
        if (user == null) {
            return UTC;
        }

        if (user.getTimezone() == null || user.getTimezone().isBlank()) {
            return UTC;
        }
        
        return parseTimezone(user);
    }
    
    private @NonNull ZonedDateTime createEventZonedDateTime(@NonNull Event event, ZoneId timezone) {
        return ZonedDateTime.of(event.getEventDate(), event.getEventTime(), timezone);
    }
    
    private @NonNull ZonedDateTime calculateReminderDateTime(ZonedDateTime eventDateTime,
                                                             @NonNull ReminderType type,
                                                             Event event) {
        return switch (type) {
            case FIFTEEN_MINUTES_BEFORE -> eventDateTime.minusMinutes(15);
            case ONE_HOUR_BEFORE -> eventDateTime.minusHours(1);
            case EVENING_BEFORE -> ZonedDateTime.of(
                    event.getEventDate().minusDays(1),
                    LocalTime.of(20, 0),
                    eventDateTime.getZone()
            );
        };
    }
    
    private LocalDateTime convertToUTC(@NonNull ZonedDateTime zonedDateTime) {
        return zonedDateTime.withZoneSameInstant(UTC).toLocalDateTime();
    }
    
    private LocalDateTime handleCalculationError(Exception e,
                                                 @NonNull Event event,
                                                 ReminderType type,
                                                 ZoneId timezone) {

        log.error("Ошибка расчета времени напоминания: eventId={}, type={}, timezone={}, error={}", 
                 event.getId(), type, timezone, e.getMessage(), e);
        
        if (UTC.equals(timezone)) {
            throw new RuntimeException("Не удалось рассчитать время напоминания с UTC: " + e.getMessage(), e);
        }
        
        return calculateReminderTimeWithTimezone(event, type, UTC);
    }
    
    private ZoneId parseTimezone(User user) {
        try {
            return ZoneId.of(user.getTimezone());

        } catch (Exception e) {
            log.error("Некорректный timezone '{}' у пользователя ID {}, используется UTC", 
                     user.getTimezone(), user.getId(), e);

            return UTC;
        }
    }
}
