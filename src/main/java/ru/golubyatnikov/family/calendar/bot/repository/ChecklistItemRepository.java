package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.ChecklistItem;

import java.util.List;

/**
 * Repository интерфейс для работы с пунктами чек-листов событий.
 * Предоставляет методы для CRUD операций и поиска пунктов чек-листов.
 * 
 * <p>Использует Spring Data JPA для автоматической генерации реализации
 * на основе сигнатур методов.</p>
 * 
 * <p><b>Требования:</b> 22.4</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
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
