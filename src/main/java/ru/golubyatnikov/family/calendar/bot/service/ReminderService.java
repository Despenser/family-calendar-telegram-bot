package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.ReminderRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Сервис для управления напоминаниями о событиях.
 * Предоставляет функциональность для создания напоминаний с различными типами
 * и автоматической отправки уведомлений по расписанию.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание стандартных напоминаний (утром, вечером, за час, за 10 минут)</li>
 *   <li>Создание кастомных напоминаний с произвольным временем</li>
 *   <li>Автоматическая отправка напоминаний по расписанию (каждую минуту)</li>
 *   <li>Отправка напоминаний только создателю для персональных событий</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 26.6</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see Reminder
 * @see ReminderRepository
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ReminderService {
    
    private final ReminderRepository reminderRepository;
    private final EventRepository eventRepository;
    private final TelegramMessageService telegramMessageService;
    private final ru.golubyatnikov.family.calendar.bot.config.DefaultReminderConfig defaultReminderConfig;
    
    /**
     * Создает стандартные напоминания для события.
     * 
     * <p>Создает напоминания указанных типов с автоматическим расчетом времени отправки.</p>
     * 
     * @param eventId идентификатор события
     * @param reminderTypes список типов напоминаний для создания
     * @return список созданных напоминаний
     * @throws EventNotFoundException если событие не найдено
     * @throws IllegalArgumentException если список типов пустой или событие не имеет даты/времени
     */
    public List<Reminder> createReminders(Long eventId, List<Reminder.ReminderType> reminderTypes) {
        log.debug("Создание напоминаний для события ID {}: типы={}", eventId, reminderTypes);
        
        if (reminderTypes == null || reminderTypes.isEmpty()) {
            log.error("Попытка создать пустой список напоминаний для события ID {}", eventId);
            throw new IllegalArgumentException("Список типов напоминаний не может быть пустым");
        }
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (event.getEventDate() == null || event.getEventTime() == null) {
            log.error("Попытка создать напоминания для события ID {} без даты или времени", eventId);
            throw new IllegalArgumentException("Событие должно иметь дату и время для создания напоминаний");
        }
        
        List<Reminder> reminders = new ArrayList<>();
        
        for (Reminder.ReminderType type : reminderTypes) {
            LocalDateTime reminderTime = calculateReminderTime(event, type, null);
            
            // Не создавать напоминания для прошедшего времени
            if (reminderTime.isBefore(LocalDateTime.now())) {
                log.warn("Пропуск создания напоминания типа {} для события ID {}: время в прошлом", 
                        type, eventId);
                continue;
            }
            
            Reminder reminder = Reminder.builder()
                .event(event)
                .reminderType(type)
                .reminderTime(reminderTime)
                .sent(false)
                .build();
            
            reminders.add(reminder);
        }
        
        if (reminders.isEmpty()) {
            log.warn("Не создано ни одного напоминания для события ID {} (все времена в прошлом)", eventId);
            return reminders;
        }
        
        List<Reminder> saved = reminderRepository.saveAll(reminders);
        log.info("Создано {} напоминаний для события ID {}", saved.size(), eventId);
        
        return saved;
    }
    
    /**
     * Создает автоматические напоминания по умолчанию для события.
     * 
     * <p>Автоматически создает набор напоминаний на основе конфигурации по умолчанию.
     * Учитывает часовой пояс пользователя при расчете времени напоминаний.</p>
     * 
     * <p>Алгоритм работы:</p>
     * <ul>
     *   <li>Проверяет, включены ли автоматические напоминания глобально (config.isEnabled())</li>
     *   <li>Проверяет наличие даты и времени у события</li>
     *   <li>Получает список типов напоминаний из конфигурации</li>
     *   <li>Для каждого типа рассчитывает время с учетом timezone пользователя</li>
     *   <li>Пропускает напоминания, время которых в прошлом</li>
     *   <li>Сохраняет все созданные напоминания одной транзакцией</li>
     * </ul>
     * 
     * <p>Обработка edge cases:</p>
     * <ul>
     *   <li>Если автоматические напоминания отключены глобально - возвращает пустой список</li>
     *   <li>Если событие без времени - возвращает пустой список</li>
     *   <li>Если все напоминания в прошлом - возвращает пустой список</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5, 6.1, 6.2, 6.3, 10.1</p>
     * 
     * @param event событие, для которого создаются напоминания
     * @param user пользователь-создатель события (используется его timezone)
     * @return список созданных напоминаний (может быть пустым)
     */
    public List<Reminder> createDefaultReminders(Event event, User user) {
        log.debug("Попытка создания автоматических напоминаний для события ID {} пользователем ID {}", 
                 event.getId(), user.getId());
        
        // Проверка 1: Включены ли автоматические напоминания глобально
        if (!defaultReminderConfig.isEnabled()) {
            log.debug("Автоматические напоминания отключены глобально, пропуск создания для события ID {}", 
                     event.getId());
            return new ArrayList<>();
        }
        
        // Проверка 2: Наличие даты и времени у события
        if (event.getEventDate() == null || event.getEventTime() == null) {
            log.debug("Событие ID {} не имеет даты или времени, пропуск создания автоматических напоминаний", 
                     event.getId());
            return new ArrayList<>();
        }
        
        // Получение списка типов из конфигурации
        List<Reminder.ReminderType> types = defaultReminderConfig.getTypes();
        if (types == null || types.isEmpty()) {
            log.warn("Конфигурация автоматических напоминаний пуста, пропуск создания для события ID {}", 
                    event.getId());
            return new ArrayList<>();
        }
        
        log.debug("Создание автоматических напоминаний для события ID {}: типы={}, timezone={}", 
                 event.getId(), types, user.getTimezone());
        
        // Получение timezone пользователя
        ZoneId userTimezone = getUserTimezone(user);
        
        List<Reminder> reminders = new ArrayList<>();
        
        // Создание напоминаний для каждого типа
        for (Reminder.ReminderType type : types) {
            try {
                // Расчет времени напоминания с учетом timezone (возвращает время в UTC)
                LocalDateTime reminderTimeUTC = calculateReminderTimeWithTimezone(event, type, userTimezone, null);
                
                // Получаем текущее время в UTC для корректного сравнения
                LocalDateTime nowUTC = LocalDateTime.now(ZoneId.of("UTC"));
                
                // Пропуск напоминаний, время которых в прошлом (сравнение в UTC)
                if (reminderTimeUTC.isBefore(nowUTC)) {
                    log.debug("Пропуск создания напоминания типа {} для события ID {}: время {} UTC в прошлом (nowUTC={})", 
                             type, event.getId(), reminderTimeUTC, nowUTC);
                    continue;
                }
                
                // Создание напоминания с временем в UTC
                Reminder reminder = Reminder.builder()
                    .event(event)
                    .reminderType(type)
                    .reminderTime(reminderTimeUTC)
                    .sent(false)
                    .build();
                
                reminders.add(reminder);
                
                log.debug("Подготовлено напоминание типа {} для события ID {}: reminderTimeUTC={} (userTZ={})", 
                         type, event.getId(), reminderTimeUTC, userTimezone);
                
            } catch (Exception e) {
                log.error("Ошибка при создании автоматического напоминания типа {} для события ID {}: {}", 
                         type, event.getId(), e.getMessage(), e);
                // Продолжаем создание остальных напоминаний
            }
        }
        
        // Сохранение всех напоминаний одной транзакцией
        if (reminders.isEmpty()) {
            log.info("Не создано ни одного автоматического напоминания для события ID {} " +
                    "(все времена в прошлом или ошибки создания)", event.getId());
            return reminders;
        }
        
        List<Reminder> saved = reminderRepository.saveAll(reminders);
        
        log.info("Автоматически создано {} напоминаний для события ID {} (типы: {}, времена сохранены в UTC)", 
                saved.size(), event.getId(), 
                saved.stream().map(r -> r.getReminderType().toString()).toList());
        
        return saved;
    }
    
    /**
     * Создает кастомное напоминание с произвольным временем.
     * 
     * <p>Позволяет указать точное количество минут до события для отправки напоминания.</p>
     * 
     * @param eventId идентификатор события
     * @param minutesBefore количество минут до события
     * @return созданное напоминание
     * @throws EventNotFoundException если событие не найдено
     * @throws IllegalArgumentException если minutesBefore некорректно или событие не имеет даты/времени
     */
    public Reminder createCustomReminder(Long eventId, int minutesBefore) {
        log.debug("Создание кастомного напоминания для события ID {}: за {} минут", eventId, minutesBefore);
        
        if (minutesBefore < 1) {
            log.error("Некорректное количество минут для напоминания: {}", minutesBefore);
            throw new IllegalArgumentException("Количество минут должно быть >= 1");
        }
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (event.getEventDate() == null || event.getEventTime() == null) {
            log.error("Попытка создать напоминание для события ID {} без даты или времени", eventId);
            throw new IllegalArgumentException("Событие должно иметь дату и время для создания напоминания");
        }
        
        LocalDateTime reminderTime = calculateReminderTime(event, Reminder.ReminderType.CUSTOM, minutesBefore);
        
        // Не создавать напоминание для прошедшего времени
        if (reminderTime.isBefore(LocalDateTime.now())) {
            log.error("Попытка создать напоминание для прошедшего времени: eventId={}, reminderTime={}", 
                     eventId, reminderTime);
            throw new IllegalArgumentException("Время напоминания не может быть в прошлом");
        }
        
        Reminder reminder = Reminder.builder()
            .event(event)
            .reminderType(Reminder.ReminderType.CUSTOM)
            .customMinutes(minutesBefore)
            .reminderTime(reminderTime)
            .sent(false)
            .build();
        
        Reminder saved = reminderRepository.save(reminder);
        log.info("Кастомное напоминание ID {} создано для события ID {} (за {} минут)", 
                 saved.getId(), eventId, minutesBefore);
        
        return saved;
    }
    
    /**
     * Автоматически отправляет напоминания по расписанию.
     * 
     * <p>Выполняется каждую минуту. Находит все неотправленные напоминания,
     * время которых наступило, и отправляет уведомления.</p>
     * 
     * <p>Использует UTC для всех операций со временем:</p>
     * <ul>
     *   <li>Получает текущее время в UTC</li>
     *   <li>Сравнивает reminder_time (в UTC) с текущим временем (в UTC)</li>
     *   <li>Использует окно проверки reminder_time <= nowUTC для предотвращения пропусков</li>
     * </ul>
     * 
     * <p>Фильтрует напоминания:</p>
     * <ul>
     *   <li>Пропускает напоминания для событий в прошлом (event_date < now)</li>
     *   <li>Пропускает напоминания для удаленных событий (status = DELETED)</li>
     *   <li>Пропускает напоминания с reminder_time < nowUTC - 1 час</li>
     *   <li>Пропускает напоминания с sent=true (проверка после получения блокировки)</li>
     * </ul>
     * 
     * <p>Использует пессимистические блокировки для предотвращения race conditions:</p>
     * <ul>
     *   <li>Получает блокировку на напоминание через findByIdWithLock</li>
     *   <li>Проверяет флаг sent после получения блокировки</li>
     *   <li>Атомарно обновляет флаг sent и sent_at в той же транзакции</li>
     *   <li>Обрабатывает PessimisticLockingFailureException при конфликтах блокировок</li>
     * </ul>
     * 
     * <p>Обработка восстановления после сбоя:</p>
     * <ul>
     *   <li>Обрабатывает напоминания не старше 1 часа (окно восстановления)</li>
     *   <li>Отмечает старые напоминания (старше 1 часа) как sent для предотвращения бесконечных попыток</li>
     *   <li>Логирует восстановление пропущенных напоминаний</li>
     * </ul>
     * 
     * <p>Для персональных событий напоминание отправляется только создателю.
     * Для семейных событий - всем членам семьи.</p>
     * 
     * <p>Расписание: каждую минуту</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 3.3, 3.4, 5.2, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 6.4, 6.5, 11.1, 11.3, 11.4</p>
     */
    @Scheduled(fixedRate = 60000) // Каждую минуту
    public void sendReminders() {
        // Получаем текущее время в UTC (Требование 1.1, 1.2)
        LocalDateTime nowUTC = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDateTime oneHourAgo = nowUTC.minusHours(1);
        
        log.debug("Запуск отправки напоминаний: nowUTC={}, oneHourAgo={}", nowUTC, oneHourAgo);
        
        // Используем новую логику окна проверки: reminder_time <= nowUTC AND reminder_time >= oneHourAgo
        // Это предотвращает пропуск напоминаний (Требование 3.3)
        // Окно в 1 час позволяет восстановить пропущенные напоминания после сбоя (Требование 3.4)
        List<Reminder> reminders = reminderRepository.findBySentFalseAndReminderTimeLessThanEqualAndReminderTimeGreaterThanEqual(
            nowUTC, oneHourAgo
        );
        
        if (reminders.isEmpty()) {
            log.debug("Нет напоминаний для отправки в окне [{}, {}] UTC", oneHourAgo, nowUTC);
            return;
        }
        
        log.info("Найдено {} напоминаний для проверки в окне [{}, {}] UTC", 
                reminders.size(), oneHourAgo, nowUTC);
        
        int sentCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int lockFailureCount = 0;
        int recoveredCount = 0;
        int markedAsOldCount = 0;
        
        for (Reminder reminder : reminders) {
            try {
                // Получаем блокировку на напоминание для предотвращения race conditions (Требование 2.3)
                Reminder lockedReminder;
                try {
                    lockedReminder = reminderRepository.findByIdWithLock(reminder.getId())
                        .orElse(null);
                } catch (PessimisticLockingFailureException e) {
                    // Обработка ошибки получения блокировки (Требование 2.4)
                    log.warn("Не удалось получить блокировку на напоминание ID {}, пропуск (обрабатывается другим процессом): {}", 
                            reminder.getId(), e.getMessage());
                    lockFailureCount++;
                    continue;
                }
                
                if (lockedReminder == null) {
                    log.warn("Напоминание ID {} не найдено при попытке получить блокировку", reminder.getId());
                    skippedCount++;
                    continue;
                }
                
                // Проверяем флаг sent после получения блокировки (Требование 1.5, 2.1, 2.2)
                if (Boolean.TRUE.equals(lockedReminder.getSent())) {
                    log.debug("Напоминание ID {} уже отправлено другим процессом, пропуск", reminder.getId());
                    skippedCount++;
                    continue;
                }
                
                Event event = lockedReminder.getEvent();
                
                // Фильтр 1: Пропускаем напоминания для удаленных событий
                if (event.getStatus() == Event.EventStatus.DELETED) {
                    log.debug("Пропуск напоминания ID={}: событие удалено", lockedReminder.getId());
                    skippedCount++;
                    continue;
                }
                
                // Фильтр 2: Пропускаем напоминания для событий в прошлом (с учетом UTC)
                if (!shouldSendReminder(lockedReminder, nowUTC)) {
                    skippedCount++;
                    continue;
                }
                
                // Проверяем, является ли это восстановлением после сбоя (Требование 3.4)
                boolean isRecovery = lockedReminder.getReminderTime().isBefore(nowUTC.minusMinutes(2));
                if (isRecovery) {
                    recoveredCount++;
                    log.warn("Восстановление пропущенного напоминания: id={}, eventId={}, reminderTimeUTC={}, " +
                            "delayMinutes={}, nowUTC={}", 
                            lockedReminder.getId(), event.getId(), lockedReminder.getReminderTime(),
                            java.time.Duration.between(lockedReminder.getReminderTime(), nowUTC).toMinutes(),
                            nowUTC);
                }
                
                // Отправляем напоминание
                try {
                    sendReminderNotification(lockedReminder);
                    
                    // Атомарно обновляем флаг sent в той же транзакции (Требование 1.5, 2.1)
                    lockedReminder.setSent(true);
                    lockedReminder.setSentAt(nowUTC);
                    reminderRepository.save(lockedReminder);
                    
                    sentCount++;
                    
                    if (isRecovery) {
                        log.info("Восстановленное напоминание успешно отправлено: id={}, eventId={}, reminderType={}, " +
                                "reminderTimeUTC={}, sentAtUTC={}, delayMinutes={}", 
                                lockedReminder.getId(), event.getId(), lockedReminder.getReminderType(),
                                lockedReminder.getReminderTime(), nowUTC,
                                java.time.Duration.between(lockedReminder.getReminderTime(), nowUTC).toMinutes());
                    } else {
                        log.info("Напоминание отправлено: id={}, eventId={}, reminderType={}, reminderTimeUTC={}, sentAtUTC={}", 
                                lockedReminder.getId(), event.getId(), lockedReminder.getReminderType(),
                                lockedReminder.getReminderTime(), nowUTC);
                    }
                    
                } catch (TelegramApiException e) {
                    // Обработка ошибок отправки уведомлений (Требование 3.4)
                    log.error("Ошибка отправки напоминания ID {}: {}", lockedReminder.getId(), e.getMessage(), e);
                    
                    // Если напоминание старше 1 часа, отмечаем как sent для предотвращения бесконечных попыток
                    if (lockedReminder.getReminderTime().isBefore(oneHourAgo)) {
                        log.warn("Напоминание ID {} старше 1 часа и не может быть отправлено, отмечаем как sent: " +
                                "reminderTimeUTC={}, nowUTC={}, ageMinutes={}", 
                                lockedReminder.getId(), lockedReminder.getReminderTime(), nowUTC,
                                java.time.Duration.between(lockedReminder.getReminderTime(), nowUTC).toMinutes());
                        
                        lockedReminder.setSent(true);
                        lockedReminder.setSentAt(nowUTC);
                        reminderRepository.save(lockedReminder);
                        markedAsOldCount++;
                    } else {
                        // Напоминание не старше 1 часа, будет повторная попытка при следующей проверке
                        failedCount++;
                    }
                }
                
            } catch (PessimisticLockingFailureException e) {
                // Дополнительная обработка на случай, если исключение произошло не в блоке try-catch выше
                lockFailureCount++;
                log.warn("Не удалось получить блокировку на напоминание ID {} (race condition): {}", 
                        reminder.getId(), e.getMessage());
            } catch (Exception e) {
                failedCount++;
                log.error("Ошибка при обработке напоминания ID {}: {}", reminder.getId(), e.getMessage(), e);
            }
        }
        
        // Итоговое логирование с информацией о восстановлении (Требование 3.4)
        if (recoveredCount > 0 || markedAsOldCount > 0) {
            log.info("Отправка напоминаний завершена: успешно={}, ошибок={}, пропущено={}, блокировок не получено={}, " +
                    "восстановлено после сбоя={}, отмечено как старые={}, nowUTC={}", 
                    sentCount, failedCount, skippedCount, lockFailureCount, recoveredCount, markedAsOldCount, nowUTC);
        } else {
            log.info("Отправка напоминаний завершена: успешно={}, ошибок={}, пропущено={}, блокировок не получено={}, nowUTC={}", 
                    sentCount, failedCount, skippedCount, lockFailureCount, nowUTC);
        }
    }
    
    /**
     * Проверяет, следует ли отправлять напоминание.
     * 
     * <p>Применяет фильтры:</p>
     * <ul>
     *   <li>Событие не удалено</li>
     *   <li>Событие не в прошлом (с учетом UTC)</li>
     * </ul>
     * 
     * <p>Алгоритм работы:</p>
     * <ul>
     *   <li>Получает timezone пользователя-создателя события</li>
     *   <li>Конвертирует время события из User TZ в UTC</li>
     *   <li>Сравнивает время события в UTC с текущим временем в UTC</li>
     *   <li>Логирует все этапы конвертации и сравнения</li>
     * </ul>
     * 
     * <p>Обработка ошибок:</p>
     * <ul>
     *   <li>При ошибке конвертации timezone логирует ошибку и возвращает false</li>
     *   <li>При некорректном timezone использует fallback на UTC через getUserTimezone()</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2</p>
     * 
     * @param reminder напоминание для проверки
     * @param nowUTC текущее время в UTC
     * @return true если напоминание следует отправить, иначе false
     */
    private boolean shouldSendReminder(Reminder reminder, LocalDateTime nowUTC) {
        Event event = reminder.getEvent();
        
        log.debug("Проверка фильтров для напоминания ID {}: eventId={}, reminderType={}, reminderTimeUTC={}", 
                 reminder.getId(), event.getId(), reminder.getReminderType(), reminder.getReminderTime());
        
        // Фильтр 1: Событие не удалено
        if (event.getStatus() == Event.EventStatus.DELETED) {
            log.debug("Пропуск напоминания ID {}: событие ID {} удалено (status=DELETED)", 
                     reminder.getId(), event.getId());
            return false;
        }
        
        // Фильтр 2: Событие не в прошлом (с учетом UTC)
        try {
            // Получаем timezone пользователя-создателя события
            ZoneId userTimezone = getUserTimezone(event.getUser());
            
            log.debug("Конвертация времени события в UTC для напоминания ID {}: " +
                     "eventId={}, eventDate={}, eventTime={}, userTimezone={}", 
                     reminder.getId(), event.getId(), event.getEventDate(), event.getEventTime(), userTimezone);
            
            // Конвертируем время события из User TZ в UTC для корректного сравнения
            ZonedDateTime eventZonedDateTime = ZonedDateTime.of(
                event.getEventDate(), 
                event.getEventTime(),
                userTimezone
            );
            
            LocalDateTime eventDateTimeUTC = eventZonedDateTime
                .withZoneSameInstant(ZoneId.of("UTC"))
                .toLocalDateTime();
            
            log.debug("Время события сконвертировано в UTC для напоминания ID {}: " +
                     "eventTimeUserTZ={}, eventTimeUTC={}, nowUTC={}", 
                     reminder.getId(), eventZonedDateTime.toLocalDateTime(), eventDateTimeUTC, nowUTC);
            
            // Сравниваем время события в UTC с текущим временем в UTC
            if (eventDateTimeUTC.isBefore(nowUTC)) {
                log.debug("Пропуск напоминания ID {}: событие ID {} в прошлом " +
                         "(eventTimeUTC={}, nowUTC={}, diffMinutes={})", 
                         reminder.getId(), event.getId(), eventDateTimeUTC, nowUTC,
                         java.time.Duration.between(eventDateTimeUTC, nowUTC).toMinutes());
                return false;
            }
            
            log.debug("Напоминание ID {} прошло все фильтры: eventId={}, eventTimeUTC={}, nowUTC={}, " +
                     "timeUntilEventMinutes={}", 
                     reminder.getId(), event.getId(), eventDateTimeUTC, nowUTC,
                     java.time.Duration.between(nowUTC, eventDateTimeUTC).toMinutes());
            
        } catch (java.time.DateTimeException e) {
            // Ошибка при работе с датами/временем
            log.error("Ошибка DateTimeException при проверке времени события для напоминания ID {}: " +
                     "eventId={}, eventDate={}, eventTime={}, userTimezone={}, error={}", 
                     reminder.getId(), event.getId(), event.getEventDate(), event.getEventTime(),
                     event.getUser().getTimezone(), e.getMessage(), e);
            // В случае ошибки не отправляем напоминание для безопасности
            return false;
            
        } catch (Exception e) {
            // Любая другая непредвиденная ошибка
            log.error("Непредвиденная ошибка {} при проверке времени события для напоминания ID {}: " +
                     "eventId={}, eventDate={}, eventTime={}, userTimezone={}, error={}", 
                     e.getClass().getSimpleName(), reminder.getId(), event.getId(), 
                     event.getEventDate(), event.getEventTime(),
                     event.getUser().getTimezone(), e.getMessage(), e);
            // В случае ошибки не отправляем напоминание для безопасности
            return false;
        }
        
        return true;
    }
    
    /**
     * Рассчитывает время отправки напоминания на основе типа.
     * 
     * @param event событие
     * @param type тип напоминания
     * @param customMinutes количество минут для CUSTOM типа (может быть null для других типов)
     * @return рассчитанное время отправки напоминания
     */
    private LocalDateTime calculateReminderTime(Event event, Reminder.ReminderType type, Integer customMinutes) {
        // Используем timezone создателя события для расчета
        ZoneId userTimezone = getUserTimezone(event.getUser());
        return calculateReminderTimeWithTimezone(event, type, userTimezone, customMinutes);
    }
    
    /**
     * Рассчитывает время отправки напоминания с учетом часового пояса пользователя.
     * 
     * <p>Этот метод выполняет расчет времени напоминания в указанном часовом поясе,
     * конвертирует результат в UTC и возвращает время в UTC для хранения в БД.</p>
     * 
     * <p>Алгоритм работы:</p>
     * <ul>
     *   <li>Создает ZonedDateTime для времени события в timezone пользователя</li>
     *   <li>Рассчитывает время напоминания в timezone пользователя</li>
     *   <li>Конвертирует время напоминания в UTC</li>
     *   <li>Возвращает LocalDateTime в UTC для хранения в БД</li>
     * </ul>
     * 
     * <p>Типы напоминаний:</p>
     * <ul>
     *   <li>EVENING_BEFORE: 20:00 предыдущего дня в timezone пользователя → UTC</li>
     *   <li>MORNING_OF_DAY: 9:00 дня события в timezone пользователя → UTC</li>
     *   <li>ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE, CUSTOM: вычитает время из события → UTC</li>
     * </ul>
     * 
     * <p>Обработка ошибок:</p>
     * <ul>
     *   <li>При некорректном timezone используется UTC с логированием предупреждения</li>
     *   <li>При ошибке конвертации используется UTC с логированием ошибки</li>
     * </ul>
     * 
     * <p><b>ВАЖНО:</b> Возвращаемое время всегда в UTC для консистентного хранения в БД.</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 4.1, 4.2, 5.1</p>
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
                case MORNING_OF_DAY:
                    // Утром в день события в 9:00 в timezone пользователя
                    reminderZonedDateTime = ZonedDateTime.of(
                        event.getEventDate(), 
                        LocalTime.of(9, 0), 
                        userTimezone
                    );
                    log.debug("MORNING_OF_DAY в User TZ: reminderDateTime={}, timezone={}", 
                             reminderZonedDateTime.toLocalDateTime(), userTimezone);
                    break;
                    
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
                    
                case TEN_MINUTES_BEFORE:
                    // За 10 минут до события (deprecated)
                    reminderZonedDateTime = eventZonedDateTime.minusMinutes(10);
                    log.debug("TEN_MINUTES_BEFORE в User TZ: eventDateTime={}, reminderDateTime={}, timezone={}", 
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
                    
                case CUSTOM:
                    // За указанное количество минут до события
                    if (customMinutes == null || customMinutes < 1) {
                        throw new IllegalArgumentException("Для CUSTOM типа необходимо указать customMinutes >= 1");
                    }
                    reminderZonedDateTime = eventZonedDateTime.minusMinutes(customMinutes);
                    log.debug("CUSTOM ({}min) в User TZ: eventDateTime={}, reminderDateTime={}, timezone={}", 
                             customMinutes,
                             eventZonedDateTime.toLocalDateTime(), 
                             reminderZonedDateTime.toLocalDateTime(), 
                             userTimezone);
                    break;
                    
                default:
                    throw new IllegalArgumentException("Неподдерживаемый тип напоминания: " + type);
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
            // Ошибка при работе с датами/временем (например, некорректная дата или время)
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
            // Некорректные аргументы (например, некорректный тип напоминания или customMinutes)
            log.error("Ошибка IllegalArgumentException при расчете времени напоминания: eventId={}, type={}, " +
                     "timezone={}, customMinutes={}, error={}", 
                     event.getId(), type, userTimezone, customMinutes, e.getMessage(), e);
            
            // Для IllegalArgumentException не делаем fallback, так как это ошибка в логике
            throw e;
            
        } catch (Exception e) {
            // Любая другая непредвиденная ошибка
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
     * <p>Обрабатывает следующие случаи:</p>
     * <ul>
     *   <li>Timezone не установлен (null или пустая строка) → возвращает UTC</li>
     *   <li>Некорректный timezone (неизвестный ZoneId) → возвращает UTC</li>
     *   <li>Любая другая ошибка при парсинге → возвращает UTC</li>
     * </ul>
     * 
     * <p>Все ошибки логируются с подробной информацией для отладки.</p>
     * 
     * <p><b>Требования:</b> 5.4</p>
     * 
     * @param user пользователь
     * @return ZoneId пользователя или UTC при ошибке
     */
    private ZoneId getUserTimezone(User user) {
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
            // Некорректный формат timezone (например, "Invalid/Timezone")
            log.error("Некорректный timezone '{}' у пользователя ID {} (telegramId={}, firstName={}), " +
                     "используется UTC. Ошибка: {}. Доступные timezone можно найти в ZoneId.getAvailableZoneIds(). " +
                     "Рекомендуется исправить timezone пользователя.", 
                     user.getTimezone(), user.getId(), user.getTelegramId(), user.getFirstName(), 
                     e.getMessage(), e);
            return ZoneId.of("UTC");
            
        } catch (Exception e) {
            // Любая другая непредвиденная ошибка
            log.error("Непредвиденная ошибка при получении timezone '{}' для пользователя ID {} " +
                     "(telegramId={}, firstName={}), используется UTC. Ошибка: {} - {}", 
                     user.getTimezone(), user.getId(), user.getTelegramId(), user.getFirstName(),
                     e.getClass().getSimpleName(), e.getMessage(), e);
            return ZoneId.of("UTC");
        }
    }
    
    /**
     * Отправляет уведомление о напоминании.
     * 
     * <p>Для персональных событий отправляет только создателю.
     * Для семейных событий отправляет всем членам семьи.</p>
     * 
     * <p>Включает inline-кнопки для взаимодействия с событием:</p>
     * <ul>
     *   <li>📋 Посмотреть детали - для всех пользователей</li>
     *   <li>✏️ Редактировать - только для создателя события</li>
     *   <li>🗑️ Удалить - только для создателя события</li>
     * </ul>
     * 
     * <p>Обработка timezone:</p>
     * <ul>
     *   <li>Для каждого получателя получает его timezone из user.getTimezone()</li>
     *   <li>Передает timezone в метод formatReminderMessageByType()</li>
     *   <li>Использует ZonedDateTime для конвертации времени события в timezone получателя</li>
     *   <li>При ошибке конвертации использует fallback на UTC с логированием</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 2.4, 2.5, 8.1, 8.2, 8.3, 8.4, 14.1, 14.2, 14.3, 14.4, 14.5</p>
     * 
     * @param reminder напоминание для отправки
     * @throws TelegramApiException если не удалось отправить уведомление
     */
    private void sendReminderNotification(Reminder reminder) throws TelegramApiException {
        Event event = reminder.getEvent();
        
        if (event.getIsPersonal()) {
            // Персональное событие - отправить только создателю
            log.debug("Отправка напоминания о персональном событии ID {} создателю ID {}", 
                     event.getId(), event.getUser().getId());
            
            try {
                // Получаем timezone получателя
                ZoneId recipientTimezone = getUserTimezone(event.getUser());
                log.debug("Форматирование сообщения для пользователя ID {} в timezone {}", 
                         event.getUser().getId(), recipientTimezone);
                
                // Форматируем сообщение с учетом timezone получателя
                String message = formatReminderMessageByType(reminder, recipientTimezone);
                
                var keyboard = createReminderKeyboard(event, event.getUser().getId());
                telegramMessageService.sendMessageWithInlineKeyboard(event.getUser().getTelegramId(), message, keyboard);
                
            } catch (java.time.DateTimeException e) {
                // Ошибка при работе с датами/временем
                log.error("Ошибка DateTimeException при форматировании/отправке напоминания пользователю ID {}: " +
                         "reminderId={}, eventId={}, userTimezone={}, error={}", 
                         event.getUser().getId(), reminder.getId(), event.getId(), 
                         event.getUser().getTimezone(), e.getMessage(), e);
                
                // Fallback: пытаемся отправить с UTC
                try {
                    log.warn("Fallback на UTC для пользователя ID {} после DateTimeException", event.getUser().getId());
                    String message = formatReminderMessageByType(reminder, ZoneId.of("UTC"));
                    var keyboard = createReminderKeyboard(event, event.getUser().getId());
                    telegramMessageService.sendMessageWithInlineKeyboard(event.getUser().getTelegramId(), message, keyboard);
                } catch (Exception fallbackError) {
                    log.error("Критическая ошибка {} при отправке напоминания пользователю ID {} даже с UTC: {}", 
                             fallbackError.getClass().getSimpleName(), event.getUser().getId(), 
                             fallbackError.getMessage(), fallbackError);
                    throw fallbackError;
                }
                
            } catch (TelegramApiException e) {
                // Ошибка Telegram API - пробрасываем дальше для обработки в sendReminders
                log.error("Ошибка TelegramApiException при отправке напоминания пользователю ID {}: " +
                         "reminderId={}, eventId={}, telegramId={}, error={}", 
                         event.getUser().getId(), reminder.getId(), event.getId(), 
                         event.getUser().getTelegramId(), e.getMessage(), e);
                throw e;
                
            } catch (Exception e) {
                // Любая другая непредвиденная ошибка
                log.error("Непредвиденная ошибка {} при форматировании/отправке напоминания пользователю ID {}: " +
                         "reminderId={}, eventId={}, error={}", 
                         e.getClass().getSimpleName(), event.getUser().getId(), 
                         reminder.getId(), event.getId(), e.getMessage(), e);
                
                // Fallback: пытаемся отправить с UTC
                try {
                    log.warn("Fallback на UTC для пользователя ID {} после непредвиденной ошибки {}", 
                            event.getUser().getId(), e.getClass().getSimpleName());
                    String message = formatReminderMessageByType(reminder, ZoneId.of("UTC"));
                    var keyboard = createReminderKeyboard(event, event.getUser().getId());
                    telegramMessageService.sendMessageWithInlineKeyboard(event.getUser().getTelegramId(), message, keyboard);
                } catch (Exception fallbackError) {
                    log.error("Критическая ошибка {} при отправке напоминания пользователю ID {} даже с UTC: {}", 
                             fallbackError.getClass().getSimpleName(), event.getUser().getId(), 
                             fallbackError.getMessage(), fallbackError);
                    throw fallbackError;
                }
            }
            
        } else {
            // Семейное событие - отправить всем членам семьи
            log.debug("Отправка напоминания о семейном событии ID {} всем членам семьи", event.getId());
            
            if (event.getFamily() != null && event.getFamily().getMembers() != null) {
                for (User member : event.getFamily().getMembers()) {
                    try {
                        // Получаем timezone получателя
                        ZoneId recipientTimezone = getUserTimezone(member);
                        log.debug("Форматирование сообщения для члена семьи ID {} в timezone {}", 
                                 member.getId(), recipientTimezone);
                        
                        // Форматируем сообщение с учетом timezone каждого получателя
                        String message = formatReminderMessageByType(reminder, recipientTimezone);
                        
                        var keyboard = createReminderKeyboard(event, member.getId());
                        telegramMessageService.sendMessageWithInlineKeyboard(member.getTelegramId(), message, keyboard);
                        
                    } catch (java.time.DateTimeException e) {
                        // Ошибка при работе с датами/временем
                        log.error("Ошибка DateTimeException при форматировании/отправке напоминания члену семьи ID {} " +
                                 "(telegramId={}): reminderId={}, eventId={}, memberTimezone={}, error={}", 
                                 member.getId(), member.getTelegramId(), reminder.getId(), event.getId(), 
                                 member.getTimezone(), e.getMessage(), e);
                        
                        // Fallback: пытаемся отправить с UTC
                        try {
                            log.warn("Fallback на UTC для члена семьи ID {} после DateTimeException", member.getId());
                            String message = formatReminderMessageByType(reminder, ZoneId.of("UTC"));
                            var keyboard = createReminderKeyboard(event, member.getId());
                            telegramMessageService.sendMessageWithInlineKeyboard(member.getTelegramId(), message, keyboard);
                        } catch (Exception fallbackError) {
                            log.error("Критическая ошибка {} при отправке напоминания члену семьи ID {} даже с UTC: {}", 
                                     fallbackError.getClass().getSimpleName(), member.getId(), 
                                     fallbackError.getMessage(), fallbackError);
                            // Продолжаем отправку остальным членам семьи
                        }
                        
                    } catch (TelegramApiException e) {
                        // Ошибка Telegram API
                        log.error("Ошибка TelegramApiException при отправке напоминания члену семьи ID {} " +
                                 "(telegramId={}): reminderId={}, eventId={}, error={}", 
                                 member.getId(), member.getTelegramId(), reminder.getId(), event.getId(), 
                                 e.getMessage(), e);
                        // Продолжаем отправку остальным членам семьи
                        
                    } catch (Exception e) {
                        // Любая другая непредвиденная ошибка
                        log.error("Непредвиденная ошибка {} при форматировании/отправке напоминания члену семьи ID {} " +
                                 "(telegramId={}): reminderId={}, eventId={}, error={}", 
                                 e.getClass().getSimpleName(), member.getId(), member.getTelegramId(), 
                                 reminder.getId(), event.getId(), e.getMessage(), e);
                        
                        // Fallback: пытаемся отправить с UTC
                        try {
                            log.warn("Fallback на UTC для члена семьи ID {} после непредвиденной ошибки {}", 
                                    member.getId(), e.getClass().getSimpleName());
                            String message = formatReminderMessageByType(reminder, ZoneId.of("UTC"));
                            var keyboard = createReminderKeyboard(event, member.getId());
                            telegramMessageService.sendMessageWithInlineKeyboard(member.getTelegramId(), message, keyboard);
                        } catch (Exception fallbackError) {
                            log.error("Критическая ошибка {} при отправке напоминания члену семьи ID {} даже с UTC: {}", 
                                     fallbackError.getClass().getSimpleName(), member.getId(), 
                                     fallbackError.getMessage(), fallbackError);
                            // Продолжаем отправку остальным членам семьи
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Создает упрощенную клавиатуру для уведомления о напоминании.
     * 
     * <p>Клавиатура содержит только одну кнопку "📋 Посмотреть детали",
     * которая открывает детали события в том же сообщении.</p>
     * 
     * <p>Структура клавиатуры:</p>
     * <ul>
     *   <li>1 ряд с 1 кнопкой</li>
     *   <li>Кнопка: "📋 Посмотреть детали"</li>
     *   <li>Callback data: "view_event_{eventId}"</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3, 9.1</p>
     * 
     * @param event событие для создания клавиатуры
     * @return inline-клавиатура с одной кнопкой
     */
    public org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup createSimplifiedReminderKeyboard(
            Event event) {
        
        log.debug("Создание упрощенной клавиатуры напоминания для события ID {}", event.getId());
        
        var keyboard = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        var rows = new java.util.ArrayList<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>();
        
        // Единственная кнопка: "Посмотреть детали"
        var viewButton = org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton.builder()
            .text("📋 Посмотреть детали")
            .callbackData("view_event_" + event.getId())
            .build();
        
        // Один ряд с одной кнопкой
        rows.add(java.util.List.of(viewButton));
        
        keyboard.setKeyboard(rows);
        
        log.debug("Упрощенная клавиатура создана для события ID {}: 1 ряд, 1 кнопка", event.getId());
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для уведомления о напоминании.
     * 
     * <p>Изменено: теперь использует упрощенную клавиатуру с одной кнопкой
     * "📋 Посмотреть детали" для всех пользователей. Кнопки редактирования
     * и удаления удалены для упрощения интерфейса уведомлений.</p>
     * 
     * <p>Включает кнопки:</p>
     * <ul>
     *   <li>📋 Посмотреть детали - единственная кнопка для всех пользователей</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3</p>
     * 
     * @param event событие
     * @param userId идентификатор пользователя, которому отправляется уведомление (не используется)
     * @return inline-клавиатура с одной кнопкой
     */
    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup createReminderKeyboard(
            Event event, Long userId) {
        
        // Используем упрощенную клавиатуру с одной кнопкой "Посмотреть детали"
        return createSimplifiedReminderKeyboard(event);
    }
    
    /**
     * Форматирует текст уведомления о напоминании с уникальным форматом по типу.
     * 
     * <p>Создает персонализированное сообщение в зависимости от типа напоминания:</p>
     * <ul>
     *   <li>EVENING_BEFORE: "🌙 Напоминание: завтра в [время] у вас событие **[название]**"</li>
     *   <li>ONE_HOUR_BEFORE: "⏰ Напоминание: через 1 час начнется событие **[название]** ([дата] в [время])"</li>
     *   <li>FIFTEEN_MINUTES_BEFORE: "⚡ Напоминание: через 15 минут начнется событие **[название]** ([время])"</li>
     * </ul>
     * 
     * <p>Включает в сообщение:</p>
     * <ul>
     *   <li>Уникальный эмодзи и текст для каждого типа напоминания</li>
     *   <li>Название события (жирным шрифтом)</li>
     *   <li>Дату и время события в timezone получателя</li>
     *   <li>Описание события (обрезанное до 100 символов)</li>
     *   <li>Эмодзи типа события: 👤 для персональных, 👨‍👩‍👧‍👦 для семейных</li>
     * </ul>
     * 
     * <p>Обработка ошибок:</p>
     * <ul>
     *   <li>При ошибке конвертации timezone используется UTC с логированием предупреждения</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 8.1, 2.4</p>
     * 
     * @param reminder напоминание
     * @param recipientTimezone часовой пояс получателя для форматирования времени
     * @return отформатированное сообщение с уникальным форматом по типу
     */
    public String formatReminderMessageByType(Reminder reminder, ZoneId recipientTimezone) {
        Event event = reminder.getEvent();
        
        try {
            // Получаем timezone создателя события (Требование 4.4)
            ZoneId creatorTimezone = getUserTimezone(event.getUser());
            
            log.debug("Форматирование сообщения напоминания ID {} для получателя: " +
                     "eventId={}, eventDate={}, eventTime={}, creatorTimezone={}, recipientTimezone={}", 
                     reminder.getId(), event.getId(), event.getEventDate(), event.getEventTime(), 
                     creatorTimezone, recipientTimezone);
            
            // Создаем ZonedDateTime для времени события в timezone создателя
            ZonedDateTime eventInCreatorTZ = ZonedDateTime.of(
                event.getEventDate(), 
                event.getEventTime(), 
                creatorTimezone
            );
            
            // Конвертируем время события из timezone создателя в timezone получателя (Требование 4.4)
            ZonedDateTime eventInRecipientTZ = eventInCreatorTZ.withZoneSameInstant(recipientTimezone);
            
            log.debug("Конвертация времени события для напоминания ID {}: " +
                     "eventTimeCreatorTZ={}, eventTimeRecipientTZ={}, creatorTZ={}, recipientTZ={}", 
                     reminder.getId(), eventInCreatorTZ.toLocalDateTime(), 
                     eventInRecipientTZ.toLocalDateTime(), creatorTimezone, recipientTimezone);
            
            // Форматтеры для даты и времени
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            
            String formattedTime = eventInRecipientTZ.format(timeFormatter);
            String formattedDate = eventInRecipientTZ.format(dateFormatter);
            
            StringBuilder message = new StringBuilder();
            
            // Уникальный заголовок в зависимости от типа напоминания (Требования 14.1, 14.2, 14.3)
            switch (reminder.getReminderType()) {
                case EVENING_BEFORE:
                    message.append("🌙 ").append(bold("Напоминание: завтра в " + formattedTime + " у вас событие "));
                    break;
                    
                case ONE_HOUR_BEFORE:
                    message.append("⏰ ").append(bold("Напоминание: через 1 час начнется событие "));
                    break;
                    
                case FIFTEEN_MINUTES_BEFORE:
                    message.append("⚡ ").append(bold("Напоминание: через 15 минут начнется событие "));
                    break;
                    
                case MORNING_OF_DAY:
                    message.append("☀️ ").append(bold("Напоминание: сегодня в " + formattedTime + " у вас событие "));
                    break;
                    
                case TEN_MINUTES_BEFORE:
                    message.append("⚡ ").append(bold("Напоминание: через 10 минут начнется событие "));
                    break;
                    
                case CUSTOM:
                    message.append("🔔 ").append(bold("Напоминание: через " + reminder.getCustomMinutes() + " минут начнется событие "));
                    break;
                    
                default:
                    message.append("🔔 ").append(bold("Напоминание о событии "));
            }
            
            // Эмодзи типа события + название события (Требования 14.6, 14.7, 14.4)
            if (event.getIsPersonal()) {
                message.append("👤 ");
            } else {
                message.append("👨‍👩‍👧‍👦 ");
            }
            
            message.append(bold(event.getTitle())).append("\n\n");
            
            // Дата и время в зависимости от типа напоминания (Требование 14.5)
            switch (reminder.getReminderType()) {
                case EVENING_BEFORE:
                case MORNING_OF_DAY:
                    // Для напоминаний накануне/утром показываем только дату
                    message.append(formatMessage("📅 Дата: %s\n", formattedDate));
                    message.append(formatMessage("⏰ Время: %s\n", formattedTime));
                    break;
                    
                case ONE_HOUR_BEFORE:
                    // Для напоминания за 1 час показываем дату и время
                    message.append(formatMessage("📅 %s в %s\n", formattedDate, formattedTime));
                    break;
                    
                case FIFTEEN_MINUTES_BEFORE:
                case TEN_MINUTES_BEFORE:
                case CUSTOM:
                    // Для коротких напоминаний показываем только время
                    message.append(formatMessage("⏰ %s\n", formattedTime));
                    break;
            }
            
            // Описание события (обрезка до 100 символов) (Требование 14.4)
            if (event.getDescription() != null && !event.getDescription().isBlank()) {
                String truncatedDesc = event.getDescription().length() > 100 
                    ? event.getDescription().substring(0, 100) + "..." 
                    : event.getDescription();
                message.append(formatMessage("\n📝 %s\n", truncatedDesc));
            }
            
            // Информация о создателе для семейных событий
            if (!event.getIsPersonal()) {
                message.append(formatMessage("\n👤 Создал: %s", event.getUser().getFirstName()));
            }
            
            log.debug("Сформировано уведомление для напоминания ID {} в timezone {}: " +
                     "eventTimeCreatorTZ={}, eventTimeRecipientTZ={}, creatorTZ={}, recipientTZ={}, длина={}", 
                     reminder.getId(), recipientTimezone, eventInCreatorTZ.toLocalDateTime(), 
                     eventInRecipientTZ.toLocalDateTime(), creatorTimezone, recipientTimezone, message.length());
            
            return message.toString();
            
        } catch (java.time.DateTimeException e) {
            // Ошибка при работе с датами/временем
            log.error("Ошибка DateTimeException при форматировании сообщения напоминания ID {} в timezone {}: " +
                     "eventId={}, eventDate={}, eventTime={}, error={}", 
                     reminder.getId(), recipientTimezone, event.getId(), 
                     event.getEventDate(), event.getEventTime(), e.getMessage(), e);
            
            // Fallback на UTC
            if (!recipientTimezone.equals(ZoneId.of("UTC"))) {
                log.warn("Fallback на UTC для форматирования сообщения напоминания ID {} после DateTimeException", 
                        reminder.getId());
                return formatReminderMessageByType(reminder, ZoneId.of("UTC"));
            }
            
            // Если и с UTC ошибка, используем старый метод форматирования
            log.error("Критическая ошибка форматирования напоминания ID {} даже с UTC, используется базовый формат", 
                     reminder.getId(), e);
            return formatReminderMessage(reminder);
            
        } catch (Exception e) {
            // Любая другая непредвиденная ошибка
            log.error("Непредвиденная ошибка {} при форматировании сообщения напоминания ID {} в timezone {}: " +
                     "eventId={}, eventDate={}, eventTime={}, error={}", 
                     e.getClass().getSimpleName(), reminder.getId(), recipientTimezone, 
                     event.getId(), event.getEventDate(), event.getEventTime(), e.getMessage(), e);
            
            // Fallback на UTC
            if (!recipientTimezone.equals(ZoneId.of("UTC"))) {
                log.warn("Fallback на UTC для форматирования сообщения напоминания ID {} после непредвиденной ошибки {}", 
                        reminder.getId(), e.getClass().getSimpleName());
                return formatReminderMessageByType(reminder, ZoneId.of("UTC"));
            }
            
            // Если и с UTC ошибка, используем старый метод форматирования
            log.error("Критическая ошибка {} форматирования напоминания ID {} даже с UTC, используется базовый формат", 
                     e.getClass().getSimpleName(), reminder.getId(), e);
            return formatReminderMessage(reminder);
        }
    }
    
    /**
     * Форматирует текст уведомления о напоминании.
     * 
     * <p>Включает:</p>
     * <ul>
     *   <li>Эмодзи 🔔 в начале</li>
     *   <li>Название события</li>
     *   <li>Дату и время (включая время окончания если указано)</li>
     *   <li>Описание события (обрезанное до 100 символов)</li>
     *   <li>Маркер персонального события (🔒) или семейного (👨‍👩‍👧‍👦 с именем создателя)</li>
     *   <li>Тип напоминания</li>
     * </ul>
     * 
     * <p>Использует Markdown форматирование для улучшения читаемости.</p>
     * 
     * <p><b>Требования:</b> 5.1, 5.2, 5.3, 5.4, 5.5, 5.6</p>
     * 
     * @deprecated Используйте {@link #formatReminderMessageByType(Reminder, ZoneId)} для уникальных форматов по типам
     * @param reminder напоминание
     * @return отформатированное сообщение
     */
    @Deprecated
    private String formatReminderMessage(Reminder reminder) {
        Event event = reminder.getEvent();
        String timeInfo = getReminderTimeInfo(reminder);
        
        StringBuilder message = new StringBuilder();
        
        // Эмодзи в начале (Требование 5.1)
        message.append("🔔 ").append(bold("Напоминание о событии")).append("\n\n");
        
        // Название события (Требование 5.2)
        message.append(formatMessage("📅 Событие: %s\n", event.getTitle()));
        
        // Дата и время (Требование 5.2)
        message.append(formatMessage("🕐 Дата: %s\n", event.getFormattedDate()));
        
        if (event.getEventTime() != null) {
            if (event.getEndTime() != null) {
                // Временной интервал
                message.append(formatMessage("⏰ Время: %s - %s\n", 
                    event.getFormattedTime(), 
                    event.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))));
            } else {
                message.append(formatMessage("⏰ Время: %s\n", event.getFormattedTime()));
            }
        }
        
        // Описание события (Требование 5.3)
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            String truncatedDesc = event.getDescription().length() > 100 
                ? event.getDescription().substring(0, 100) + "..." 
                : event.getDescription();
            message.append(formatMessage("📝 Описание: %s\n", truncatedDesc));
        }
        
        message.append("\n");
        
        // Маркер типа события (Требования 5.4, 5.5)
        if (event.getIsPersonal()) {
            message.append("🔒 ").append(bold("Персональное событие")).append("\n");
        } else {
            message.append(formatMessage("👨‍👩‍👧‍👦 Семейное событие (создал: %s)\n", 
                event.getUser().getFirstName()));
        }
        
        // Тип напоминания
        message.append(formatMessage("\n⏱ %s", timeInfo));
        
        return message.toString();
    }
    
    /**
     * Возвращает текстовое описание времени напоминания.
     * 
     * @param reminder напоминание
     * @return описание времени
     */
    private String getReminderTimeInfo(Reminder reminder) {
        switch (reminder.getReminderType()) {
            case MORNING_OF_DAY:
                return "Напоминание: утром в день события";
            case EVENING_BEFORE:
                return "Напоминание: вечером накануне";
            case ONE_HOUR_BEFORE:
                return "Напоминание: за 1 час до события";
            case TEN_MINUTES_BEFORE:
                return "Напоминание: за 10 минут до события";
            case FIFTEEN_MINUTES_BEFORE:
                return "Напоминание: за 15 минут до события";
            case CUSTOM:
                return formatMessage("Напоминание: за %d минут до события", reminder.getCustomMinutes());
            default:
                return "Напоминание о событии";
        }
    }
    
    /**
     * Получает все напоминания для указанного события.
     * 
     * @param eventId идентификатор события
     * @return список всех напоминаний события
     */
    @Transactional(readOnly = true)
    public List<Reminder> getEventReminders(Long eventId) {
        log.debug("Получение напоминаний для события ID {}", eventId);
        return reminderRepository.findByEventId(eventId);
    }
    
    /**
     * Получает напоминание по идентификатору.
     * 
     * <p>Метод используется для восстановления контекста напоминания
     * при возврате к минималистичному виду уведомления.</p>
     * 
     * <p><b>Требования:</b> 6.2</p>
     * 
     * @param reminderId идентификатор напоминания
     * @return напоминание
     * @throws ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException если напоминание не найдено
     */
    @Transactional(readOnly = true)
    public Reminder getReminderById(Long reminderId) {
        log.debug("Получение напоминания по ID {}", reminderId);
        
        return reminderRepository.findById(reminderId)
            .orElseThrow(() -> {
                log.error("Напоминание с ID {} не найдено", reminderId);
                return new ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException(reminderId);
            });
    }
    
    /**
     * Удаляет напоминание по идентификатору.
     * 
     * @param reminderId идентификатор напоминания
     * @throws IllegalArgumentException если напоминание не найдено
     */
    public void deleteReminder(Long reminderId) {
        log.debug("Удаление напоминания ID {}", reminderId);
        
        if (!reminderRepository.existsById(reminderId)) {
            log.error("Попытка удалить несуществующее напоминание ID {}", reminderId);
            throw new IllegalArgumentException("Напоминание с ID " + reminderId + " не найдено");
        }
        
        reminderRepository.deleteById(reminderId);
        log.info("Напоминание ID {} удалено", reminderId);
    }
    
    /**
     * Пересчитывает время отправки для всех неотправленных напоминаний события.
     * Используется при изменении даты или времени события.
     * 
     * @param eventId идентификатор события
     * @throws EventNotFoundException если событие не найдено
     */
    public void recalculateReminders(Long eventId) {
        log.debug("Пересчет напоминаний для события ID {}", eventId);
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        if (event.getEventDate() == null || event.getEventTime() == null) {
            log.warn("Невозможно пересчитать напоминания для события ID {} без даты или времени", eventId);
            return;
        }
        
        List<Reminder> reminders = reminderRepository.findByEventIdAndSentFalse(eventId);
        
        if (reminders.isEmpty()) {
            log.debug("Нет неотправленных напоминаний для пересчета для события ID {}", eventId);
            return;
        }
        
        int recalculatedCount = 0;
        for (Reminder reminder : reminders) {
            try {
                LocalDateTime newReminderTime = calculateReminderTime(
                    event, 
                    reminder.getReminderType(), 
                    reminder.getCustomMinutes()
                );
                
                // Пропускаем напоминания, время которых оказалось в прошлом
                if (newReminderTime.isBefore(LocalDateTime.now())) {
                    log.warn("Пропуск пересчета напоминания ID {} для события ID {}: новое время в прошлом", 
                            reminder.getId(), eventId);
                    continue;
                }
                
                reminder.setReminderTime(newReminderTime);
                reminderRepository.save(reminder);
                recalculatedCount++;
                
            } catch (Exception e) {
                log.error("Ошибка при пересчете напоминания ID {}: {}", 
                         reminder.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Пересчитано {} напоминаний для события ID {}", recalculatedCount, eventId);
    }
    
    /**
     * Отмечает все неотправленные напоминания события как отправленные.
     * Используется при завершении события.
     * 
     * @param eventId идентификатор события
     */
    public void markRemindersAsSent(Long eventId) {
        log.debug("Отметка напоминаний как отправленных для события ID {}", eventId);
        
        List<Reminder> reminders = reminderRepository.findByEventIdAndSentFalse(eventId);
        
        if (reminders.isEmpty()) {
            log.debug("Нет неотправленных напоминаний для события ID {}", eventId);
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        for (Reminder reminder : reminders) {
            reminder.setSent(true);
            reminder.setSentAt(now);
        }
        
        reminderRepository.saveAll(reminders);
        log.info("Отмечено {} напоминаний как отправленных для события ID {}", reminders.size(), eventId);
    }
    
    /**
     * Проверяет наличие активных (неотправленных) напоминаний для события.
     * 
     * @param eventId идентификатор события
     * @return true если есть хотя бы одно неотправленное напоминание, иначе false
     */
    @Transactional(readOnly = true)
    public boolean hasActiveReminders(Long eventId) {
        List<Reminder> reminders = reminderRepository.findByEventIdAndSentFalse(eventId);
        return !reminders.isEmpty();
    }
    
    /**
     * Отключает все автоматические напоминания для события.
     * 
     * <p>Этот метод удаляет все напоминания (как отправленные, так и неотправленные)
     * для указанного события. Используется когда пользователь хочет отключить
     * автоматические напоминания для конкретного события.</p>
     * 
     * <p>Алгоритм работы:</p>
     * <ul>
     *   <li>Получает все напоминания события через {@link #getEventReminders(Long)}</li>
     *   <li>Удаляет все напоминания через {@link ReminderRepository#deleteAll(Iterable)}</li>
     *   <li>Логирует отключение с уровнем INFO</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 3.2, 3.3, 13.1, 13.2</p>
     * 
     * @param eventId идентификатор события
     */
    public void disableRemindersForEvent(Long eventId) {
        log.debug("Отключение напоминаний для события ID {}", eventId);
        
        // Получаем все напоминания события
        List<Reminder> reminders = getEventReminders(eventId);
        
        if (reminders.isEmpty()) {
            log.debug("Нет напоминаний для отключения для события ID {}", eventId);
            return;
        }
        
        // Удаляем все напоминания
        reminderRepository.deleteAll(reminders);
        
        log.info("Отключено {} напоминаний для события ID {}", reminders.size(), eventId);
    }
}
