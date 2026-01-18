package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import java.util.List;

/**
 * Repository интерфейс для работы с историей изменений событий.
 * Предоставляет методы для CRUD операций и поиска записей истории.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 * @see EventHistory
 */
@Repository
public interface EventHistoryRepository extends JpaRepository<EventHistory, Long> {
    
    /**
     * Находит всю историю изменений события, отсортированную по дате (от новых к старым).
     * 
     * @param eventId идентификатор события
     * @return список записей истории, отсортированный по дате изменения (новые первыми)
     */
    List<EventHistory> findByEventIdOrderByChangedAtDesc(Long eventId);
}
