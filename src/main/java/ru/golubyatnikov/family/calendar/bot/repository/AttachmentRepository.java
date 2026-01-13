package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;

import java.util.List;

/**
 * Repository интерфейс для работы с вложениями событий.
 * Предоставляет методы для CRUD операций и поиска вложений.
 * 
 * <p>Использует Spring Data JPA для автоматической генерации реализации
 * на основе сигнатур методов.</p>
 * 
 * <p><b>Требования:</b> 20.4</p>
 * 
 * @author Family Calendar Bot
 * @version 1.0
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
