package ru.golubyatnikov.family.calendar.bot.service.domain.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.ReminderType;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
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

        log.debug("Расчет времени напоминания: eventId={}, type={}, timezone={}", 
                 event.getId(), type, userTimezone);
        
        try {
            ZonedDateTime eventZonedDateTime = createEventZonedDateTime(event, userTimezone);
            ZonedDateTime reminderZonedDateTime = calculateReminderDateTime(eventZonedDateTime, type, event);
            LocalDateTime reminderTimeUTC = convertToUTC(reminderZonedDateTime);
            
            log.info("Время напоминания рассчитано: eventId={}, type={}, reminderTimeUTC={}", 
                     event.getId(), type, reminderTimeUTC);
            
            return reminderTimeUTC;
            
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
            log.error("Пользователь null, используется UTC");
            return UTC;
        }
        
        warnIfNotInitialized(user);
        
        if (user.getTimezone() == null || user.getTimezone().isBlank()) {
            log.warn("Пользователь ID {} не имеет timezone, используется UTC", user.getId());
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
            case EVENING_BEFORE -> ZonedDateTime.of(
                event.getEventDate().minusDays(1), 
                LocalTime.of(20, 0), 
                eventDateTime.getZone()
            );
            case ONE_HOUR_BEFORE -> eventDateTime.minusHours(1);
            case FIFTEEN_MINUTES_BEFORE -> eventDateTime.minusMinutes(15);
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
        
        log.warn("Fallback на UTC для расчета времени: eventId={}, type={}", event.getId(), type);
        return calculateReminderTimeWithTimezone(event, type, UTC);
    }
    
    private void warnIfNotInitialized(User user) {
        if (!Hibernate.isInitialized(user)) {
            log.warn("Пользователь ID {} не инициализирован (Hibernate proxy)", user.getId());
        }
    }
    
    private ZoneId parseTimezone(User user) {
        try {
            ZoneId zoneId = ZoneId.of(user.getTimezone());
            log.debug("Timezone получен для пользователя ID {}: {}", user.getId(), zoneId);
            return zoneId;

        } catch (Exception e) {
            log.error("Некорректный timezone '{}' у пользователя ID {}, используется UTC", 
                     user.getTimezone(), user.getId(), e);

            return UTC;
        }
    }
}
