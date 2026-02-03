package ru.golubyatnikov.family.calendar.bot.service.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.User;
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
 * @author Family Calendar Bot
 * @since 2026-02-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderCreationService {
    
    private final ReminderRepository reminderRepository;
    private final EventRepository eventRepository;
    private final ReminderConfigurationService reminderConfigurationService;
    private final ru.golubyatnikov.family.calendar.bot.config.DefaultReminderConfig defaultReminderConfig;
    
    /**
     * Создает стандартные напоминания для события.
     * 
     * @param eventId идентификатор события
     * @param reminderTypes список типов напоминаний для создания
     * @return список созданных напоминаний
     * @throws EventNotFoundException если событие не найдено
     * @throws IllegalArgumentException если список типов пустой или событие не имеет даты/времени
     */
    @Transactional
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
            LocalDateTime reminderTime = reminderConfigurationService.calculateReminderTime(event, type, null);
            
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
     * @param event событие, для которого создаются напоминания
     * @param user пользователь-создатель события (используется его timezone)
     * @return список созданных напоминаний (может быть пустым)
     */
    @Transactional
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
        ZoneId userTimezone = reminderConfigurationService.getUserTimezone(user);
        
        List<Reminder> reminders = new ArrayList<>();
        
        // Создание напоминаний для каждого типа
        for (Reminder.ReminderType type : types) {
            try {
                // Расчет времени напоминания с учетом timezone (возвращает время в UTC)
                LocalDateTime reminderTimeUTC = reminderConfigurationService.calculateReminderTimeWithTimezone(
                    event, type, userTimezone, null);
                
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
     * @param eventId идентификатор события
     * @param minutesBefore количество минут до события
     * @return созданное напоминание
     * @throws UnsupportedOperationException всегда, так как функционал кастомных напоминаний удален
     * @deprecated Кастомные напоминания больше не поддерживаются.
     *             Используйте фиксированные типы: EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE
     */
    @Deprecated
    public Reminder createCustomReminder(Long eventId, int minutesBefore) {
        log.error("Попытка создать кастомное напоминание для события ID {}: " +
                 "функционал удален (minutesBefore={})", eventId, minutesBefore);
        throw new UnsupportedOperationException(
            "Кастомные напоминания больше не поддерживаются. " +
            "Используйте фиксированные типы: EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE"
        );
    }
    
    /**
     * Удаляет напоминание по идентификатору.
     * 
     * @param reminderId идентификатор напоминания
     * @throws IllegalArgumentException если напоминание не найдено
     */
    @Transactional
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
     * Отключает все автоматические напоминания для события.
     * 
     * @param eventId идентификатор события
     */
    @Transactional
    public void disableRemindersForEvent(Long eventId) {
        log.debug("Отключение напоминаний для события ID {}", eventId);
        
        // Получаем все напоминания события
        List<Reminder> reminders = reminderRepository.findByEventId(eventId);
        
        if (reminders.isEmpty()) {
            log.debug("Нет напоминаний для отключения для события ID {}", eventId);
            return;
        }
        
        // Удаляем все напоминания
        reminderRepository.deleteAll(reminders);
        
        log.info("Отключено {} напоминаний для события ID {}", reminders.size(), eventId);
    }
}
