package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import java.util.List;

/**
 * Repository интерфейс для работы с вложениями событий.
 * Предоставляет методы для CRUD операций и поиска вложений.
 *
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-16
 * @see Attachment
 */
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    
    /**
     * Находит все вложения события, отсортированные по дате загрузки (от старых к новым).
     * 
     * @param eventId идентификатор события
     * @return список вложений события, отсортированный по дате загрузки
     */
    List<Attachment> findByEventIdOrderByUploadedAtAsc(Long eventId);
}
