package ru.golubyatnikov.family.calendar.bot.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
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
 * @author Family Calendar Bot
 * @since 2026-02-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderSchedulingService {
    
    private final ReminderRepository reminderRepository;
    private final EventRepository eventRepository;
    private final ReminderConfigurationService reminderConfigurationService;
    
    /**
     * Вспомогательный класс для хранения информации о напоминании.
     * Используется при пересоздании напоминаний после изменения даты/времени события.
     */
    @Data
    @AllArgsConstructor
    private static class ReminderInfo {
        private Reminder.ReminderType reminderType;
        private Integer customMinutes;
    }
    
    /**
     * Пересчитывает время отправки для всех неотправленных напоминаний события.
     * Используется при изменении даты или времени события.
     * 
     * @param eventId идентификатор события
     * @throws EventNotFoundException если событие не найдено
     */
    @Transactional
    public void recalculateReminders(Long eventId) {
        log.info("Начало пересчета напоминаний для события ID {}", eventId);
        
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
            
            log.debug("Событие ID {} имеет дату и время: eventDate={}, eventTime={}",
                     eventId, event.getEventDate(), event.getEventTime());
            
            // Шаг 3: Получаем все неотправленные напоминания
            List<Reminder> oldReminders = reminderRepository.findByEventIdAndSentFalse(eventId);
            
            if (oldReminders.isEmpty()) {
                log.debug("Нет неотправленных напоминаний для пересчета для события ID {}", eventId);
                return;
            }
            
            log.info("Найдено {} неотправленных напоминаний для пересчета для события ID {}",
                    oldReminders.size(), eventId);
            
            // Шаг 4: Извлекаем информацию о напоминаниях (типы и параметры)
            List<ReminderInfo> reminderInfos = extractReminderInfo(oldReminders);
            
            // Шаг 5: Удаляем все старые неотправленные напоминания
            int deletedCount = deleteOldReminders(eventId);
            
            log.info("Удалено {} старых напоминаний для события ID {}", deletedCount, eventId);
            
            // Шаг 6: Создаем новые напоминания с пересчитанными временами
            List<Reminder> newReminders = createNewReminders(event, reminderInfos);
            
            // Шаг 7: Итоговое логирование
            if (newReminders.isEmpty()) {
                log.warn("Пересчет напоминаний для события ID {} завершен: " +
                        "удалено={}, создано=0 (все новые времена в прошлом)",
                        eventId, deletedCount);
            } else {
                log.info("Пересчет напоминаний для события ID {} успешно завершен: " +
                        "удалено={}, создано={}",
                        eventId, deletedCount, newReminders.size());
            }
            
        } catch (EventNotFoundException e) {
            log.error("Событие ID {} не найдено при пересчете напоминаний", eventId);
            throw e;
            
        } catch (Exception e) {
            log.error("Ошибка при пересчете напоминаний для события ID {}: {}",
                     eventId, e.getMessage(), e);
            // Транзакция будет откачена автоматически благодаря @Transactional
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
     * Удаляет все неотправленные напоминания для события.
     * 
     * @param eventId идентификатор события
     * @return количество удаленных напоминаний
     */
    private int deleteOldReminders(Long eventId) {
        log.debug("Удаление старых неотправленных напоминаний для события ID {}", eventId);
        
        List<Reminder> reminders = reminderRepository.findByEventIdAndSentFalse(eventId);
        
        if (reminders.isEmpty()) {
            log.debug("Нет неотправленных напоминаний для удаления для события ID {}", eventId);
            return 0;
        }
        
        // Логируем типы удаляемых напоминаний
        String reminderTypes = reminders.stream()
            .map(r -> r.getReminderType().toString() + 
                     (r.getCustomMinutes() != null ? "(" + r.getCustomMinutes() + "min)" : ""))
            .toList()
            .toString();
        
        log.info("Удаление {} неотправленных напоминаний для события ID {}: типы={}", 
                reminders.size(), eventId, reminderTypes);
        
        reminderRepository.deleteAll(reminders);
        
        log.info("Удалено {} неотправленных напоминаний для события ID {}", reminders.size(), eventId);
        
        return reminders.size();
    }
    
    /**
     * Извлекает информацию о напоминаниях для последующего пересоздания.
     * 
     * @param reminders список напоминаний для извлечения информации
     * @return список ReminderInfo с типами и параметрами напоминаний
     */
    private List<ReminderInfo> extractReminderInfo(List<Reminder> reminders) {
        log.debug("Извлечение информации из {} напоминаний", reminders.size());
        
        List<ReminderInfo> reminderInfos = new ArrayList<>();
        
        for (Reminder reminder : reminders) {
            ReminderInfo info = new ReminderInfo(
                reminder.getReminderType(),
                reminder.getCustomMinutes()
            );
            reminderInfos.add(info);
            
            log.debug("Извлечена информация о напоминании ID {}: type={}, customMinutes={}", 
                     reminder.getId(), info.getReminderType(), info.getCustomMinutes());
        }
        
        log.info("Извлечена информация из {} напоминаний: типы={}", 
                reminderInfos.size(),
                reminderInfos.stream()
                    .map(info -> info.getReminderType().toString() + 
                         (info.getCustomMinutes() != null ? "(" + info.getCustomMinutes() + "min)" : ""))
                    .toList());
        
        return reminderInfos;
    }
    
    /**
     * Создает новые напоминания на основе сохраненной информации.
     * 
     * @param event событие для создания напоминаний
     * @param reminderInfos список информации о напоминаниях
     * @return список созданных напоминаний
     */
    private List<Reminder> createNewReminders(Event event, List<ReminderInfo> reminderInfos) {
        log.debug("Создание {} новых напоминаний для события ID {}", reminderInfos.size(), event.getId());
        
        // Получаем текущее время в UTC для корректного сравнения
        LocalDateTime nowUTC = LocalDateTime.now(ZoneId.of("UTC"));
        
        List<Reminder> newReminders = new ArrayList<>();
        int skippedCount = 0;
        
        for (ReminderInfo info : reminderInfos) {
            try {
                // Рассчитываем новое время напоминания
                LocalDateTime reminderTimeUTC = reminderConfigurationService.calculateReminderTime(
                    event,
                    info.getReminderType(),
                    info.getCustomMinutes()
                );
                
                // Пропускаем напоминания, время которых в прошлом
                if (reminderTimeUTC.isBefore(nowUTC)) {
                    log.warn("Пропуск создания напоминания типа {} для события ID {}: " +
                            "новое время {} UTC в прошлом (nowUTC={})",
                            info.getReminderType(), event.getId(), reminderTimeUTC, nowUTC);
                    skippedCount++;
                    continue;
                }
                
                // Создаем новое напоминание
                Reminder reminder = Reminder.builder()
                    .event(event)
                    .reminderType(info.getReminderType())
                    .customMinutes(info.getCustomMinutes())
                    .reminderTime(reminderTimeUTC)
                    .sent(false)
                    .build();
                
                newReminders.add(reminder);
                
                log.debug("Подготовлено новое напоминание типа {} для события ID {}: reminderTimeUTC={}",
                         info.getReminderType(), event.getId(), reminderTimeUTC);
                
            } catch (Exception e) {
                log.error("Ошибка при создании напоминания типа {} для события ID {}: {}",
                         info.getReminderType(), event.getId(), e.getMessage(), e);
                skippedCount++;
            }
        }
        
        // Сохраняем все новые напоминания
        if (!newReminders.isEmpty()) {
            List<Reminder> saved = reminderRepository.saveAll(newReminders);
            
            String createdTypes = saved.stream()
                .map(r -> r.getReminderType().toString() +
                         (r.getCustomMinutes() != null ? "(" + r.getCustomMinutes() + "min)" : ""))
                .toList()
                .toString();
            
            log.info("Создано {} новых напоминаний для события ID {}: типы={}, пропущено={}",
                    saved.size(), event.getId(), createdTypes, skippedCount);
            
            return saved;
        } else {
            log.warn("Не создано ни одного нового напоминания для события ID {} " +
                    "(все времена в прошлом или ошибки создания), пропущено={}",
                    event.getId(), skippedCount);
            return newReminders;
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
     * Получает напоминание по идентификатору с eager загрузкой связанного события.
     * 
     * @param reminderId идентификатор напоминания
     * @return напоминание с загруженным событием
     * @throws ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException если напоминание не найдено
     */
    @Transactional(readOnly = true)
    public Reminder getReminderWithEventById(Long reminderId) {
        log.debug("Получение напоминания с событием по ID {}", reminderId);
        
        return reminderRepository.findWithEventById(reminderId)
            .orElseThrow(() -> {
                log.error("Напоминание с ID {} не найдено", reminderId);
                return new ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException(reminderId);
            });
    }
    
    /**
     * Получает напоминание по идентификатору с eager загрузкой события и пользователя.
     * 
     * @param reminderId идентификатор напоминания
     * @return напоминание с загруженным событием и пользователем
     * @throws ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException если напоминание не найдено
     */
    @Transactional(readOnly = true)
    public Reminder getReminderWithEventAndUser(Long reminderId) {
        log.debug("Получение напоминания с событием и пользователем по ID {}", reminderId);
        
        return reminderRepository.findWithEventAndUserById(reminderId)
            .orElseThrow(() -> {
                log.error("Напоминание с ID {} не найдено", reminderId);
                return new ru.golubyatnikov.family.calendar.bot.exception.ReminderNotFoundException(reminderId);
            });
    }
}
