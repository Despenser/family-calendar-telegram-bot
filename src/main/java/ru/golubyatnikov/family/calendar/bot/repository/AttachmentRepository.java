package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import java.util.List;

/**
 * Repository интерфейс для работы с вложениями событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    
    /**
     * Находит все вложения события, отсортированные по дате загрузки (от старых к новым).
     * 
     * @param eventId идентификатор события
     * @return список вложений события, отсортированный по дате загрузки
     */
    @EntityGraph(attributePaths = {"event"})
    List<Attachment> findByEventIdOrderByUploadedAtAsc(Long eventId);
    
    /**
     * Подсчитывает количество вложений у события.
     * 
     * @param eventId идентификатор события
     * @return количество вложений
     */
    long countByEventId(Long eventId);
}
