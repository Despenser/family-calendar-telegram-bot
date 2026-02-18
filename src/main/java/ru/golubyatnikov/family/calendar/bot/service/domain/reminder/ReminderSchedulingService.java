package ru.golubyatnikov.family.calendar.bot.service.domain.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.Reminder;
import ru.golubyatnikov.family.calendar.bot.model.enums.ReminderType;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.ReminderRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для планирования отправки напоминаний.
 * Отвечает за пересчет времени напоминаний при изменении событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderSchedulingService {
    
    private final ReminderRepository reminderRepository;
    private final EventRepository eventRepository;
    private final ReminderConfigurationService reminderConfigurationService;
    
    /**
     * Пересчитывает время отправки для всех неотправленных напоминаний события.
     * Используется при изменении даты или времени события.
     * 
     * @param eventId идентификатор события
     * @throws EventNotFoundException если событие не найдено
     */
    @Transactional
    public void recalculateReminders(Long eventId) {
        try {
            // Шаг 1: Получаем событие
            Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
            
            // Шаг 2: Проверяем наличие даты и времени
            if (event.getEventDate() == null || event.getEventTime() == null) {
                log.warn("Невозможно пересчитать напоминания для события ID {} без даты или времени: " +
                        "eventDate={}, eventTime={}",
                        eventId, event.getEventDate(), event.getEventTime());
                return;
            }
            
            // Шаг 3: Получаем все неотправленные напоминания
            List<Reminder> oldReminders = reminderRepository.findByEventIdAndSentFalse(eventId);
            
            if (oldReminders.isEmpty()) {
                return;
            }
            
            // Шаг 4: Извлекаем типы напоминаний для последующего пересоздания
            List<ReminderType> reminderTypes = extractReminderTypes(oldReminders);
            
            // Шаг 5: Удаляем все старые неотправленные напоминания
            int deletedCount = deleteOldReminders(eventId);
            
            // Шаг 6: Создаем новые напоминания с пересчитанными временами
            List<Reminder> newReminders = createNewReminders(event, reminderTypes);
            
            // Шаг 7: Итоговое логирование
            if (newReminders.isEmpty()) {
                log.warn("Пересчет напоминаний для события ID {} завершен: " +
                        "удалено={}, создано=0 (все новые времена в прошлом)",
                        eventId, deletedCount);
            }
            
        } catch (EventNotFoundException e) {
            log.error("Событие ID {} не найдено при пересчете напоминаний", eventId);
            throw e;
            
        } catch (Exception e) {
            log.error("Ошибка при пересчете напоминаний для события ID {}: {}",
                     eventId, e.getMessage(), e);

            throw new RuntimeException("Не удалось пересчитать напоминания для события ID " + eventId, e);
        }
    }
    
    /**
     * Отмечает все неотправленные напоминания как отправленные.
     * Используется при завершении события.
     * 
     * @param eventId идентификатор события
     */
    @Transactional
    public void markRemindersAsSent(Long eventId) {
        List<Reminder> reminders = reminderRepository.findByEventIdAndSentFalse(eventId);
        
        if (reminders.isEmpty()) {
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        reminders.forEach(reminder -> {
            reminder.setSent(true);
            reminder.setSentAt(now);
        });
        
        reminderRepository.saveAll(reminders);
        }
    
    /**
     * Проверяет наличие активных (неотправленных) напоминаний для события.
     * 
     * @param eventId идентификатор события
     * @return true, если есть хотя бы одно неотправленное напоминание, иначе false
     */
    @Transactional(readOnly = true)
    public boolean hasActiveReminders(Long eventId) {
        return !reminderRepository.findByEventIdAndSentFalse(eventId).isEmpty();
    }
    
    /**
     * Удаляет все неотправленные напоминания для события.
     * 
     * @param eventId идентификатор события
     * @return количество удаленных напоминаний
     */
    private int deleteOldReminders(Long eventId) {
        List<Reminder> reminders = reminderRepository.findByEventIdAndSentFalse(eventId);
        
        if (reminders.isEmpty()) {
            return 0;
        }
        
        reminderRepository.deleteAll(reminders);
        return reminders.size();
    }
    
    /**
     * Извлекает типы напоминаний для последующего пересоздания.
     * 
     * @param reminders список напоминаний для извлечения типов
     * @return список типов напоминаний
     */
    private @NonNull List<ReminderType> extractReminderTypes(@NonNull List<Reminder> reminders) {
        return reminders.stream()
            .map(Reminder::getReminderType)
            .toList();
    }
    
    /**
     * Создает новые напоминания на основе сохраненных типов.
     * 
     * @param event событие для создания напоминаний
     * @param reminderTypes список типов напоминаний
     *
     * @return список созданных напоминаний
     */
    private @NonNull List<Reminder> createNewReminders(@NonNull Event event,
                                                       @NonNull List<ReminderType> reminderTypes) {

        // Получаем текущее время в UTC для корректного сравнения
        LocalDateTime nowUTC = LocalDateTime.now(ZoneId.of("UTC"));
        
        List<Reminder> newReminders = new ArrayList<>();
        
        for (ReminderType reminderType : reminderTypes) {
            try {
                // Рассчитываем новое время напоминания
                LocalDateTime reminderTimeUTC = reminderConfigurationService.calculateReminderTime(
                    event,
                    reminderType
                );
                
                // Пропускаем напоминания, время которых в прошлом
                if (reminderTimeUTC.isBefore(nowUTC)) {
                    log.warn("Пропуск создания напоминания типа {} для события ID {}: " +
                            "новое время {} UTC в прошлом (nowUTC={})",
                            reminderType, event.getId(), reminderTimeUTC, nowUTC);

                    continue;
                }
                
                // Создаем новое напоминание
                Reminder reminder = Reminder.builder()
                    .event(event)
                    .reminderType(reminderType)
                    .reminderTime(reminderTimeUTC)
                    .sent(false)
                    .build();
                
                newReminders.add(reminder);
                
            } catch (Exception e) {
                log.error("Ошибка при создании напоминания типа {} для события ID {}: {}",
                         reminderType, event.getId(), e.getMessage(), e);
            }
        }
        
        // Сохраняем все новые напоминания
        if (!newReminders.isEmpty()) {
            return reminderRepository.saveAll(newReminders);

        } else {
            log.warn("Не создано ни одного нового напоминания для события ID {} " +
                    "(все времена в прошлом или ошибки создания)", event.getId());

            return newReminders;
        }
    }

    /**
     * Получает напоминание по идентификатору с eager загрузкой события и пользователя.
     * 
     * @param reminderId идентификатор напоминания
     *
     * @return напоминание с загруженным событием и пользователем
     * @throws ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException если напоминание не найдено
     */
    @Transactional(readOnly = true)
    public Reminder getReminderWithEventAndUser(Long reminderId) {
        return reminderRepository.findWithEventAndUserById(reminderId)
            .orElseThrow(() -> new ReminderNotFoundException(reminderId));
    }
}
