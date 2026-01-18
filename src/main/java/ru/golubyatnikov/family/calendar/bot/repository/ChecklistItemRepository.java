package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.ChecklistItem;
import java.util.List;

/**
 * Repository интерфейс для работы с пунктами чек-листов событий.
 * Предоставляет методы для CRUD операций и поиска пунктов чек-листов.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 * @see ChecklistItem
 */
@Repository
public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
    
    /**
     * Находит все пункты чек-листа события, отсортированные по позиции.
     * 
     * @param eventId идентификатор события
     * @return список пунктов чек-листа, отсортированный по порядковому номеру
     */
    List<ChecklistItem> findByEventIdOrderByPositionAsc(Long eventId);
}
