package ru.golubyatnikov.family.calendar.bot.service.domain.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.util.List;

/**
 * Сервис для поиска и фильтрации событий.
 * Предоставляет функциональность для текстового поиска по событиям
 * и фильтрации по различным критериям.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
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
     * @param familyId идентификатор семьи
     * @param userId идентификатор пользователя
     * @param searchQuery поисковый запрос
     *
     * @return список найденных событий
     * @throws IllegalArgumentException если поисковый запрос пустой
     */
    public List<Event> searchEvents(Long familyId, Long userId, String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            throw new IllegalArgumentException("Поисковый запрос не может быть пустым");
        }
        
        String trimmedQuery = searchQuery.trim();
        return eventRepository.searchByTitleOrDescription(familyId, userId, trimmedQuery);
    }
}
