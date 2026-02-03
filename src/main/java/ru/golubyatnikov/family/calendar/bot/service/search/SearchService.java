package ru.golubyatnikov.family.calendar.bot.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для поиска и фильтрации событий.
 * Предоставляет функциональность для текстового поиска по событиям
 * и фильтрации по различным критериям.
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Текстовый поиск по названию и описанию событий</li>
 *   <li>Фильтрация событий по типу (семейные/персональные)</li>
 *   <li>Фильтрация по статусу события</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 28.3, 28.4, 28.5</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see Event
 * @see EventRepository
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class SearchService {
    
    private final EventRepository eventRepository;
    
    /**
     * Выполняет текстовый поиск событий по названию и описанию.
     * 
     * <p>Ищет события семьи, содержащие поисковый запрос в названии или описании.
     * Включает семейные события и персональные события пользователя.
     * Поиск регистронезависимый.</p>
     * 
     * @param familyId идентификатор семьи
     * @param userId идентификатор пользователя
     * @param searchQuery поисковый запрос
     * @return список найденных событий
     * @throws IllegalArgumentException если поисковый запрос пустой
     */
    public List<Event> searchEvents(Long familyId, Long userId, String searchQuery) {
        log.debug("Поиск событий для семьи ID {} и пользователя ID {}: query='{}'", familyId, userId, searchQuery);
        
        if (searchQuery == null || searchQuery.isBlank()) {
            log.error("Попытка поиска с пустым запросом для пользователя ID {}", userId);
            throw new IllegalArgumentException("Поисковый запрос не может быть пустым");
        }
        
        String trimmedQuery = searchQuery.trim();
        List<Event> results = eventRepository.searchByTitleOrDescription(familyId, userId, trimmedQuery);
        
        log.info("Поиск событий для семьи ID {} и пользователя ID {} по запросу '{}': найдено {} результатов", 
                 familyId, userId, trimmedQuery, results.size());
        
        return results;
    }
    
    /**
     * Фильтрует события семьи по заданному критерию.
     * 
     * <p>Применяет фильтр к событиям семьи и возвращает отфильтрованный список.
     * Включает семейные события и персональные события пользователя.</p>
     * 
     * @param familyId идентификатор семьи
     * @param userId идентификатор пользователя
     * @param filter критерий фильтрации
     * @return список отфильтрованных событий
     * @throws IllegalArgumentException если фильтр null
     */
    public List<Event> filterEvents(Long familyId, Long userId, EventFilter filter) {
        log.debug("Фильтрация событий для семьи ID {} и пользователя ID {}: filter={}", familyId, userId, filter);
        
        if (filter == null) {
            log.error("Попытка фильтрации с null фильтром для пользователя ID {}", userId);
            throw new IllegalArgumentException("Фильтр не может быть null");
        }
        
        List<Event> results;
        
        switch (filter) {
            case ALL:
                // Все активные события семьи (семейные + персональные пользователя)
                results = eventRepository.findByFamilyIdAndStatus(familyId, Event.EventStatus.ACTIVE)
                    .stream()
                    .filter(event -> !event.getIsPersonal() || event.belongsToUser(userId))
                    .collect(Collectors.toList());
                break;
                
            case FAMILY_ONLY:
                // Только семейные события (is_personal = false)
                results = eventRepository.findByFamilyIdAndIsPersonalFalseAndStatus(
                    familyId, Event.EventStatus.ACTIVE
                );
                break;
                
            case PERSONAL_ONLY:
                // Только персональные события пользователя (is_personal = true)
                results = eventRepository.findByUserIdAndIsPersonalTrueAndStatus(
                    userId, Event.EventStatus.ACTIVE
                );
                break;
                
            case COMPLETED:
                // Завершенные события семьи (семейные + персональные пользователя)
                results = eventRepository.findByFamilyIdAndStatus(familyId, Event.EventStatus.COMPLETED)
                    .stream()
                    .filter(event -> !event.getIsPersonal() || event.belongsToUser(userId))
                    .collect(Collectors.toList());
                break;
                
            case RECURRING:
                // Повторяющиеся события (имеют series_id)
                results = eventRepository.findByFamilyIdAndStatus(familyId, Event.EventStatus.ACTIVE)
                    .stream()
                    .filter(event -> !event.getIsPersonal() || event.belongsToUser(userId))
                    .filter(Event::isRecurring)
                    .collect(Collectors.toList());
                break;
                
            default:
                log.error("Неподдерживаемый фильтр: {}", filter);
                throw new IllegalArgumentException("Неподдерживаемый фильтр: " + filter);
        }
        
        log.info("Фильтрация событий для семьи ID {} и пользователя ID {} по фильтру {}: найдено {} результатов", 
                 familyId, userId, filter, results.size());
        
        return results;
    }
    
    /**
     * ENUM для типов фильтров событий.
     */
    public enum EventFilter {
        /**
         * Все активные события
         */
        ALL,
        
        /**
         * Только семейные события
         */
        FAMILY_ONLY,
        
        /**
         * Только персональные события
         */
        PERSONAL_ONLY,
        
        /**
         * Завершенные события
         */
        COMPLETED,
        
        /**
         * Повторяющиеся события
         */
        RECURRING
    }
}
