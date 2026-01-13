package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Comment;

import java.util.List;

/**
 * Repository интерфейс для работы с комментариями к событиям.
 * Предоставляет методы для CRUD операций и поиска комментариев.
 * 
 * <p>Использует Spring Data JPA для автоматической генерации реализации
 * на основе сигнатур методов.</p>
 * 
 * <p><b>Требования:</b> 21.4</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @see Comment
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    
    /**
     * Находит все комментарии события, отсортированные по дате создания (от старых к новым).
     * 
     * @param eventId идентификатор события
     * @return список комментариев события, отсортированный по дате создания
     */
    List<Comment> findByEventIdOrderByCreatedAtAsc(Long eventId);
}
