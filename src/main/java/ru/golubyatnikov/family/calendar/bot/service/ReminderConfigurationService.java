package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Сервис для настройки типов напоминаний.
 * Отвечает за расчет времени напоминаний и работу с часовыми поясами.
 * 
 * @author Family Calendar Bot
 * @since 2026-02-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderConfigurationService {
    
    /**
     * Рассчитывает время отправки напоминания на основе типа.
     * 
     * @param event событие
     * @param type тип напоминания
     * @param customMinutes количество минут для CUSTOM типа (может быть null для других типов)
     * @return рассчитанное время отправки напоминания
     */
    public LocalDateTime calculateReminderTime(Event event, Reminder.ReminderType type, Integer customMinutes) {
        // Используем timezone создателя события для расчета
        ZoneId userTimezone = getUserTimezone(event.getUser());
        return calculateReminderTimeWithTimezone(event, type, userTimezone, customMinutes);
    }
    
    /**
     * Рассчитывает время отправки напоминания с учетом часового пояса пользователя.
     * 
     * @param event событие
     * @param type тип напоминания
     * @param userTimezone часовой пояс пользователя
     * @param customMinutes количество минут для CUSTOM типа (может быть null для других типов)
     * @return рассчитанное время отправки напоминания в UTC
     */
    public LocalDateTime calculateReminderTimeWithTimezone(Event event, Reminder.ReminderType type, 
                                                          ZoneId userTimezone, Integer customMinutes) {
        log.debug("Начало расчета времени напоминания: eventId={}, type={}, userTimezone={}, customMinutes={}", 
                 event.getId(), type, userTimezone, customMinutes);
        
        try {
            // Шаг 1: Создаем ZonedDateTime для времени события в timezone пользователя
            ZonedDateTime eventZonedDateTime = ZonedDateTime.of(
                event.getEventDate(), 
                event.getEventTime(), 
                userTimezone
            );
            
            log.debug("Время события в User TZ: eventId={}, eventDateTime={}, timezone={}", 
                     event.getId(), eventZonedDateTime.toLocalDateTime(), userTimezone);
            
            // Шаг 2: Рассчитываем время напоминания в timezone пользователя
            ZonedDateTime reminderZonedDateTime;
            
            switch (type) {
                case EVENING_BEFORE:
                    // Вечером накануне в 20:00 в timezone пользователя
                    reminderZonedDateTime = ZonedDateTime.of(
                        event.getEventDate().minusDays(1), 
                        LocalTime.of(20, 0), 
                        userTimezone
                    );
                    log.debug("EVENING_BEFORE в User TZ: reminderDateTime={}, timezone={}", 
                             reminderZonedDateTime.toLocalDateTime(), userTimezone);
                    break;
                    
                case ONE_HOUR_BEFORE:
                    // За 1 час до события
                    reminderZonedDateTime = eventZonedDateTime.minusHours(1);
                    log.debug("ONE_HOUR_BEFORE в User TZ: eventDateTime={}, reminderDateTime={}, timezone={}", 
                             eventZonedDateTime.toLocalDateTime(), 
                             reminderZonedDateTime.toLocalDateTime(), 
                             userTimezone);
                    break;
                    
                case FIFTEEN_MINUTES_BEFORE:
                    // За 15 минут до события
                    reminderZonedDateTime = eventZonedDateTime.minusMinutes(15);
                    log.debug("FIFTEEN_MINUTES_BEFORE в User TZ: eventDateTime={}, reminderDateTime={}, timezone={}", 
                             eventZonedDateTime.toLocalDateTime(), 
                             reminderZonedDateTime.toLocalDateTime(), 
                             userTimezone);
                    break;
                    
                default:
                    throw new IllegalArgumentException(
                        "Неподдерживаемый тип напоминания: " + type + ". " +
                        "Поддерживаются только: EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE"
                    );
            }
            
            // Шаг 3: Конвертируем время напоминания в UTC
            ZonedDateTime reminderUTC = reminderZonedDateTime.withZoneSameInstant(ZoneId.of("UTC"));
            
            log.debug("Конвертация в UTC: eventId={}, type={}, userTZ={}, reminderUserTZ={}, reminderUTC={}", 
                     event.getId(), type, userTimezone,
                     reminderZonedDateTime.toLocalDateTime(),
                     reminderUTC.toLocalDateTime());
            
            // Шаг 4: Возвращаем LocalDateTime в UTC
            LocalDateTime reminderTimeUTC = reminderUTC.toLocalDateTime();
            
            log.info("Расчет времени напоминания завершен: eventId={}, type={}, userTZ={}, " +
                    "eventTimeUserTZ={}, reminderTimeUserTZ={}, reminderTimeUTC={}", 
                     event.getId(), type, userTimezone,
                     eventZonedDateTime.toLocalDateTime(),
                     reminderZonedDateTime.toLocalDateTime(),
                     reminderTimeUTC);
            
            return reminderTimeUTC;
            
        } catch (java.time.DateTimeException e) {
            log.error("Ошибка DateTimeException при расчете времени напоминания: eventId={}, type={}, " +
                     "timezone={}, eventDate={}, eventTime={}, customMinutes={}, error={}", 
                     event.getId(), type, userTimezone, event.getEventDate(), event.getEventTime(), 
                     customMinutes, e.getMessage(), e);
            
            // Если уже используем UTC, пробрасываем исключение дальше
            if (userTimezone.equals(ZoneId.of("UTC"))) {
                log.error("Критическая ошибка: не удалось рассчитать время даже с UTC: eventId={}, type={}, " +
                         "eventDate={}, eventTime={}", 
                         event.getId(), type, event.getEventDate(), event.getEventTime(), e);
                throw new RuntimeException("Не удалось рассчитать время напоминания даже с UTC: " + e.getMessage(), e);
            }
            
            log.warn("Fallback на UTC для расчета времени напоминания после DateTimeException: " +
                    "eventId={}, type={}, originalTimezone={}", 
                    event.getId(), type, userTimezone);
            
            // Повторяем расчет с UTC
            return calculateReminderTimeWithTimezone(event, type, ZoneId.of("UTC"), customMinutes);
            
        } catch (IllegalArgumentException e) {
            log.error("Ошибка IllegalArgumentException при расчете времени напоминания: eventId={}, type={}, " +
                     "timezone={}, customMinutes={}, error={}", 
                     event.getId(), type, userTimezone, customMinutes, e.getMessage(), e);
            throw e;
            
        } catch (Exception e) {
            log.error("Непредвиденная ошибка {} при расчете времени напоминания: eventId={}, type={}, " +
                     "timezone={}, eventDate={}, eventTime={}, customMinutes={}, error={}", 
                     e.getClass().getSimpleName(), event.getId(), type, userTimezone, 
                     event.getEventDate(), event.getEventTime(), customMinutes, e.getMessage(), e);
            
            // Если уже используем UTC, пробрасываем исключение дальше
            if (userTimezone.equals(ZoneId.of("UTC"))) {
                log.error("Критическая ошибка: не удалось рассчитать время даже с UTC: eventId={}, type={}", 
                         event.getId(), type, e);
                throw new RuntimeException("Не удалось рассчитать время напоминания: " + e.getMessage(), e);
            }
            
            log.warn("Fallback на UTC для расчета времени напоминания после непредвиденной ошибки: " +
                    "eventId={}, type={}, originalTimezone={}, errorType={}", 
                    event.getId(), type, userTimezone, e.getClass().getSimpleName());
            
            // Повторяем расчет с UTC
            return calculateReminderTimeWithTimezone(event, type, ZoneId.of("UTC"), customMinutes);
        }
    }
    
    /**
     * Получает часовой пояс пользователя с обработкой ошибок и fallback на UTC.
     * 
     * @param user пользователь
     * @return ZoneId пользователя или UTC при ошибке
     */
    public ZoneId getUserTimezone(User user) {
        // Проверка 1: Пользователь не null
        if (user == null) {
            log.error("Попытка получить timezone для null пользователя, используется UTC");
            return ZoneId.of("UTC");
        }
        
        // Проверка 2: Пользователь инициализирован (не Hibernate proxy)
        if (!Hibernate.isInitialized(user)) {
            log.warn("Пользователь ID {} является неинициализированным Hibernate proxy, " +
                    "это может привести к LazyInitializationException. " +
                    "Рекомендуется использовать eager fetch при загрузке Event (например, EventRepository.findByIdWithUser).",
                    user.getId());
        }
        
        // Проверка 3: Timezone установлен
        if (user.getTimezone() == null || user.getTimezone().isBlank()) {
            log.warn("Пользователь ID {} (telegramId={}, firstName={}) не имеет установленного timezone, " +
                    "используется UTC. Рекомендуется установить timezone через настройки.", 
                    user.getId(), user.getTelegramId(), user.getFirstName());
            return ZoneId.of("UTC");
        }
        
        // Проверка 4: Парсинг timezone
        try {
            ZoneId zoneId = ZoneId.of(user.getTimezone());
            log.debug("Успешно получен timezone для пользователя ID {}: {}", user.getId(), zoneId);
            return zoneId;
            
        } catch (java.time.DateTimeException e) {
            log.error("Некорректный timezone '{}' у пользователя ID {} (telegramId={}, firstName={}), " +
                     "используется UTC. Ошибка: {}. Доступные timezone можно найти в ZoneId.getAvailableZoneIds(). " +
                     "Рекомендуется исправить timezone пользователя.", 
                     user.getTimezone(), user.getId(), user.getTelegramId(), user.getFirstName(), 
                     e.getMessage(), e);
            return ZoneId.of("UTC");
            
        } catch (Exception e) {
            log.error("Непредвиденная ошибка при получении timezone '{}' для пользователя ID {} " +
                     "(telegramId={}, firstName={}), используется UTC. Ошибка: {} - {}", 
                     user.getTimezone(), user.getId(), user.getTelegramId(), user.getFirstName(),
                     e.getClass().getSimpleName(), e.getMessage(), e);
            return ZoneId.of("UTC");
        }
    }
}
