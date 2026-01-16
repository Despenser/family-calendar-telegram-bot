package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.ChecklistItemNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.ChecklistItem;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.ChecklistItemRepository;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для управления чек-листами событий.
 * Предоставляет функциональность для создания, управления и проверки
 * выполнения пунктов чек-листов в событиях.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание чек-листа с несколькими пунктами</li>
 *   <li>Переключение статуса выполнения пункта</li>
 *   <li>Получение чек-листа события</li>
 *   <li>Проверка полного выполнения чек-листа</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 22.2, 22.3, 22.4, 22.5, 22.6</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see ChecklistItem
 * @see ChecklistItemRepository
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ChecklistService {
    
    private final ChecklistItemRepository checklistItemRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    
    /**
     * Создает чек-лист для события из списка текстовых пунктов.
     * 
     * <p>Каждому пункту автоматически присваивается порядковый номер
     * в соответствии с его позицией в списке.</p>
     * 
     * @param eventId идентификатор события
     * @param items список текстовых пунктов чек-листа
     * @return список созданных пунктов чек-листа
     * @throws EventNotFoundException если событие не найдено
     * @throws IllegalArgumentException если список пунктов пустой или содержит пустые строки
     */
    public List<ChecklistItem> createChecklist(Long eventId, List<String> items) {
        log.debug("Создание чек-листа для события ID {}: количество пунктов={}", eventId, items.size());
        
        if (items == null || items.isEmpty()) {
            log.error("Попытка создать пустой чек-лист для события ID {}", eventId);
            throw new IllegalArgumentException("Список пунктов чек-листа не может быть пустым");
        }
        
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));
        
        List<ChecklistItem> checklistItems = new java.util.ArrayList<>();
        
        for (int i = 0; i < items.size(); i++) {
            String itemText = items.get(i);
            
            if (itemText == null || itemText.isBlank()) {
                log.warn("Пропуск пустого пункта чек-листа на позиции {} для события ID {}", i, eventId);
                continue;
            }
            
            ChecklistItem item = ChecklistItem.builder()
                .event(event)
                .text(itemText.trim())
                .position(i)
                .completed(false)
                .build();
            
            checklistItems.add(item);
        }
        
        if (checklistItems.isEmpty()) {
            log.error("Все пункты чек-листа пустые для события ID {}", eventId);
            throw new IllegalArgumentException("Чек-лист должен содержать хотя бы один непустой пункт");
        }
        
        List<ChecklistItem> saved = checklistItemRepository.saveAll(checklistItems);
        log.debug("Чек-лист из {} пунктов успешно создан для события ID {}", saved.size(), eventId);
        
        return saved;
    }
    
    /**
     * Переключает статус выполнения пункта чек-листа.
     * 
     * <p>Если пункт не выполнен - отмечает как выполненный и сохраняет информацию
     * о пользователе и времени выполнения. Если пункт выполнен - снимает отметку.</p>
     * 
     * @param itemId идентификатор пункта чек-листа
     * @param userId идентификатор пользователя, выполняющего действие
     * @return обновленный пункт чек-листа
     * @throws ChecklistItemNotFoundException если пункт чек-листа не найден
     * @throws UserNotFoundException если пользователь не найден
     */
    public ChecklistItem toggleItemCompletion(Long itemId, Long userId) {
        log.debug("Переключение статуса пункта чек-листа ID {}: userId={}", itemId, userId);
        
        ChecklistItem item = checklistItemRepository.findById(itemId)
            .orElseThrow(() -> new ChecklistItemNotFoundException(itemId));
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        boolean wasCompleted = item.getCompleted();
        
        if (wasCompleted) {
            // Снять отметку о выполнении
            item.setCompleted(false);
            item.setCompletedAt(null);
            item.setCompletedBy(null);
            
            log.debug("Пункт чек-листа ID {} отмечен как невыполненный пользователем ID {}", itemId, userId);
        } else {
            // Отметить как выполненный
            item.setCompleted(true);
            item.setCompletedAt(LocalDateTime.now());
            item.setCompletedBy(user);
            
            log.debug("Пункт чек-листа ID {} отмечен как выполненный пользователем ID {}", itemId, userId);
        }
        
        ChecklistItem saved = checklistItemRepository.save(item);
        
        // Проверить, завершен ли весь чек-лист
        if (!wasCompleted && isChecklistComplete(item.getEvent().getId())) {
            log.debug("Чек-лист события ID {} полностью выполнен", item.getEvent().getId());
        }
        
        return saved;
    }
    
    /**
     * Получает все пункты чек-листа события, отсортированные по позиции.
     * 
     * @param eventId идентификатор события
     * @return список пунктов чек-листа, отсортированный по порядковому номеру
     */
    @Transactional(readOnly = true)
    public List<ChecklistItem> getEventChecklist(Long eventId) {
        log.debug("Получение чек-листа для события ID {}", eventId);
        
        List<ChecklistItem> items = checklistItemRepository.findByEventIdOrderByPositionAsc(eventId);
        
        log.debug("Найдено {} пунктов чек-листа для события ID {}", items.size(), eventId);
        return items;
    }
    
    /**
     * Проверяет, выполнены ли все пункты чек-листа события.
     * 
     * <p>Чек-лист считается полностью выполненным, если:</p>
     * <ul>
     *   <li>Чек-лист содержит хотя бы один пункт</li>
     *   <li>Все пункты отмечены как выполненные</li>
     * </ul>
     * 
     * @param eventId идентификатор события
     * @return true, если все пункты выполнены, иначе false
     */
    @Transactional(readOnly = true)
    public boolean isChecklistComplete(Long eventId) {
        log.debug("Проверка завершенности чек-листа для события ID {}", eventId);
        
        List<ChecklistItem> items = checklistItemRepository.findByEventIdOrderByPositionAsc(eventId);
        
        if (items.isEmpty()) {
            log.debug("Чек-лист события ID {} пуст", eventId);
            return false;
        }
        
        boolean allCompleted = items.stream().allMatch(ChecklistItem::getCompleted);
        
        long completedCount = items.stream().filter(ChecklistItem::getCompleted).count();
        log.debug("Чек-лист события ID {}: выполнено {}/{}, завершен={}", 
                 eventId, completedCount, items.size(), allCompleted);
        
        return allCompleted;
    }
}
