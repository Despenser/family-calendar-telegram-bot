package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.RecurrenceRuleNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.RecurrenceRule;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.RecurrenceRuleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для управления повторяющимися событиями.
 * Предоставляет функциональность для создания, обновления и удаления
 * серий повторяющихся событий с различными правилами повторения.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание повторяющихся событий (ежедневно, еженедельно, ежемесячно)</li>
 *   <li>Обновление всей серии событий</li>
 *   <li>Удаление всей серии событий</li>
 *   <li>Обработка исключений дат</li>
 *   <li>Ограничение по дате окончания или количеству повторений</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 27.1, 27.2, 27.3, 27.4, 27.5, 27.6, 27.7, 27.8, 27.9</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see RecurrenceRule
 * @see RecurrenceRuleRepository
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class RecurrenceService {
    
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final EventRepository eventRepository;
    
    /**
     * Создает серию повторяющихся событий на основе шаблона и правила повторения.
     * 
     * <p>Генерирует события согласно правилу повторения, пропуская исключенные даты.
     * Останавливается при достижении даты окончания или количества повторений.</p>
     * 
     * @param templateEvent шаблон события для создания серии
     * @param rule правило повторения
     * @return список созданных событий
     * @throws IllegalArgumentException если правило повторения некорректно
     */
    public List<Event> createRecurringEvent(Event templateEvent, RecurrenceRule rule) {
        log.debug("Создание повторяющихся событий: frequency={}, interval={}, endDate={}, occurrences={}", 
                  rule.getFrequency(), rule.getInterval(), rule.getEndDate(), rule.getOccurrences());
        
        validateRecurrenceRule(rule);
        
        // Генерируем UUID для серии
        String seriesId = UUID.randomUUID().toString();
        rule.setSeriesId(seriesId);
        
        // Сохраняем правило повторения
        recurrenceRuleRepository.save(rule);
        log.info("Правило повторения сохранено: seriesId={}", seriesId);
        
        // Генерируем события
        List<Event> events = new ArrayList<>();
        LocalDate currentDate = templateEvent.getEventDate();
        int occurrenceCount = 0;
        
        // Парсим исключенные даты
        Set<LocalDate> excludedDates = parseExcludedDates(rule.getExceptions());
        
        // Парсим дни недели для еженедельного повторения
        Set<Integer> daysOfWeek = parseDaysOfWeek(rule.getDaysOfWeek());
        
        while (shouldCreateOccurrence(currentDate, rule, occurrenceCount)) {
            if (!isExcludedDate(currentDate, excludedDates)) {
                Event event = createEventOccurrence(templateEvent, currentDate, seriesId);
                events.add(event);
                occurrenceCount++;
                
                log.debug("Создано событие серии: date={}, occurrenceCount={}", currentDate, occurrenceCount);
            }
            
            currentDate = getNextOccurrenceDate(currentDate, rule, daysOfWeek);
        }
        
        List<Event> savedEvents = eventRepository.saveAll(events);
        log.info("Создано {} повторяющихся событий для серии {}", savedEvents.size(), seriesId);
        
        return savedEvents;
    }
    
    /**
     * Обновляет все события в серии.
     * 
     * <p>Применяет изменения ко всем активным событиям серии.
     * Не изменяет даты и время событий, только другие поля.</p>
     * 
     * @param seriesId идентификатор серии
     * @param title новое название (null = не изменять)
     * @param description новое описание (null = не изменять)
     * @return количество обновленных событий
     * @throws RecurrenceRuleNotFoundException если серия не найдена
     */
    public int updateSeries(String seriesId, String title, String description) {
        log.debug("Обновление серии событий: seriesId={}", seriesId);
        
        RecurrenceRule rule = recurrenceRuleRepository.findBySeriesId(seriesId)
            .orElseThrow(() -> new RecurrenceRuleNotFoundException(seriesId));
        
        List<Event> events = eventRepository.findBySeriesIdAndStatus(seriesId, Event.EventStatus.ACTIVE);
        
        if (events.isEmpty()) {
            log.warn("Не найдено активных событий для серии {}", seriesId);
            return 0;
        }
        
        int updatedCount = 0;
        for (Event event : events) {
            boolean updated = false;
            
            if (title != null && !title.equals(event.getTitle())) {
                event.setTitle(title);
                updated = true;
            }
            
            if (description != null && !description.equals(event.getDescription())) {
                event.setDescription(description);
                updated = true;
            }
            
            if (updated) {
                eventRepository.save(event);
                updatedCount++;
            }
        }
        
        log.info("Обновлено {} событий в серии {}", updatedCount, seriesId);
        return updatedCount;
    }
    
    /**
     * Удаляет всю серию повторяющихся событий.
     * 
     * <p>Удаляет правило повторения и все события серии из базы данных.</p>
     * 
     * @param seriesId идентификатор серии
     * @return количество удаленных событий
     * @throws RecurrenceRuleNotFoundException если серия не найдена
     */
    public int deleteSeries(String seriesId) {
        log.debug("Удаление серии событий: seriesId={}", seriesId);
        
        RecurrenceRule rule = recurrenceRuleRepository.findBySeriesId(seriesId)
            .orElseThrow(() -> new RecurrenceRuleNotFoundException(seriesId));
        
        List<Event> events = eventRepository.findBySeriesIdAndStatus(seriesId, Event.EventStatus.ACTIVE);
        
        // Удаляем все события серии
        eventRepository.deleteAll(events);
        
        // Удаляем правило повторения
        recurrenceRuleRepository.delete(rule);
        
        log.info("Удалена серия {}: удалено {} событий", seriesId, events.size());
        return events.size();
    }
    
    /**
     * Проверяет, нужно ли создавать следующее повторение события.
     * 
     * @param date дата проверяемого повторения
     * @param rule правило повторения
     * @param occurrenceCount текущее количество созданных повторений
     * @return true, если нужно создать повторение
     */
    private boolean shouldCreateOccurrence(LocalDate date, RecurrenceRule rule, int occurrenceCount) {
        // Проверка по дате окончания
        if (rule.getEndDate() != null && date.isAfter(rule.getEndDate())) {
            return false;
        }
        
        // Проверка по количеству повторений
        if (rule.getOccurrences() != null && occurrenceCount >= rule.getOccurrences()) {
            return false;
        }
        
        // Ограничение на максимум 365 повторений для безопасности
        return occurrenceCount < 365;
    }
    
    /**
     * Вычисляет дату следующего повторения события.
     * 
     * @param currentDate текущая дата
     * @param rule правило повторения
     * @param daysOfWeek дни недели для еженедельного повторения
     * @return дата следующего повторения
     */
    private LocalDate getNextOccurrenceDate(LocalDate currentDate, RecurrenceRule rule, Set<Integer> daysOfWeek) {
        switch (rule.getFrequency()) {
            case DAILY:
                return currentDate.plusDays(rule.getInterval());
                
            case WEEKLY:
                if (daysOfWeek.isEmpty()) {
                    // Если дни недели не указаны, повторять каждую неделю в тот же день
                    return currentDate.plusWeeks(rule.getInterval());
                } else {
                    // Найти следующий день недели из списка
                    return getNextWeeklyOccurrence(currentDate, rule.getInterval(), daysOfWeek);
                }
                
            case MONTHLY:
                return currentDate.plusMonths(rule.getInterval());
                
            default:
                throw new IllegalArgumentException("Неподдерживаемая частота повторения: " + rule.getFrequency());
        }
    }
    
    /**
     * Находит следующую дату для еженедельного повторения с учетом дней недели.
     * 
     * @param currentDate текущая дата
     * @param interval интервал в неделях
     * @param daysOfWeek набор дней недели (1=Пн, 7=Вс)
     * @return следующая дата повторения
     */
    private LocalDate getNextWeeklyOccurrence(LocalDate currentDate, int interval, Set<Integer> daysOfWeek) {
        LocalDate nextDate = currentDate.plusDays(1);
        int daysChecked = 0;
        int maxDaysToCheck = 7 * interval + 7; // Проверяем максимум interval недель + 1 неделя
        
        while (daysChecked < maxDaysToCheck) {
            int dayOfWeek = nextDate.getDayOfWeek().getValue(); // 1=Пн, 7=Вс
            
            if (daysOfWeek.contains(dayOfWeek)) {
                return nextDate;
            }
            
            nextDate = nextDate.plusDays(1);
            daysChecked++;
        }
        
        // Если не нашли подходящий день, возвращаем дату через interval недель
        return currentDate.plusWeeks(interval);
    }
    
    /**
     * Проверяет, является ли дата исключенной.
     * 
     * @param date проверяемая дата
     * @param excludedDates набор исключенных дат
     * @return true, если дата исключена
     */
    private boolean isExcludedDate(LocalDate date, Set<LocalDate> excludedDates) {
        return excludedDates.contains(date);
    }
    
    /**
     * Создает экземпляр события для конкретной даты в серии.
     * 
     * @param template шаблон события
     * @param date дата нового события
     * @param seriesId идентификатор серии
     * @return новое событие
     */
    private Event createEventOccurrence(Event template, LocalDate date, String seriesId) {
        return Event.builder()
            .user(template.getUser())
            .family(template.getFamily())
            .title(template.getTitle())
            .description(template.getDescription())
            .eventDate(date)
            .eventTime(template.getEventTime())
            .endTime(template.getEndTime())
            .status(Event.EventStatus.ACTIVE)
            .isPersonal(template.getIsPersonal())
            .seriesId(seriesId)
            .notified(false)
            .build();
    }
    
    /**
     * Парсит строку с исключенными датами.
     * 
     * @param exceptions строка в формате "2025-01-15,2025-02-20"
     * @return набор исключенных дат
     */
    private Set<LocalDate> parseExcludedDates(String exceptions) {
        if (exceptions == null || exceptions.isBlank()) {
            return Collections.emptySet();
        }
        
        return Arrays.stream(exceptions.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(LocalDate::parse)
            .collect(Collectors.toSet());
    }
    
    /**
     * Парсит строку с днями недели.
     * 
     * @param daysOfWeek строка в формате "1,3,5" (1=Пн, 7=Вс)
     * @return набор дней недели
     */
    private Set<Integer> parseDaysOfWeek(String daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isBlank()) {
            return Collections.emptySet();
        }
        
        return Arrays.stream(daysOfWeek.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .filter(day -> day >= 1 && day <= 7)
            .collect(Collectors.toSet());
    }
    
    /**
     * Валидирует правило повторения.
     * 
     * @param rule правило повторения
     * @throws IllegalArgumentException если правило некорректно
     */
    private void validateRecurrenceRule(RecurrenceRule rule) {
        if (rule.getFrequency() == null) {
            throw new IllegalArgumentException("Частота повторения обязательна");
        }
        
        if (rule.getInterval() == null || rule.getInterval() < 1) {
            throw new IllegalArgumentException("Интервал повторения должен быть >= 1");
        }
        
        if (rule.getEndDate() == null && rule.getOccurrences() == null) {
            throw new IllegalArgumentException("Необходимо указать дату окончания или количество повторений");
        }
        
        if (rule.getEndDate() != null && rule.getOccurrences() != null) {
            throw new IllegalArgumentException("Нельзя указывать одновременно дату окончания и количество повторений");
        }
        
        if (rule.getOccurrences() != null && rule.getOccurrences() < 1) {
            throw new IllegalArgumentException("Количество повторений должно быть >= 1");
        }
        
        if (rule.getOccurrences() != null && rule.getOccurrences() > 365) {
            throw new IllegalArgumentException("Количество повторений не может превышать 365");
        }
    }
}
