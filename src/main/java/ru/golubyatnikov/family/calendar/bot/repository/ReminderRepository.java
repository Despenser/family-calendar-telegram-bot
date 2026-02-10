package ru.golubyatnikov.family.calendar.bot.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository интерфейс для работы с напоминаниями о событиях.
 * Предоставляет методы для CRUD операций и поиска напоминаний.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    
    /**
     * Находит все неотправленные напоминания в указанном временном диапазоне.
     * Используется планировщиком для отправки напоминаний.
     * 
     * @param startTime начало временного диапазона
     * @param endTime конец временного диапазона
     * @return список неотправленных напоминаний в указанном диапазоне
     */
    @EntityGraph(attributePaths = {"event", "event.user"})
    List<Reminder> findBySentFalseAndReminderTimeBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Находит все неотправленные напоминания, время которых наступило.
     * 
     * @param nowUTC текущее время в UTC
     * @param oneHourAgo время час назад в UTC (для фильтрации старых напоминаний)
     * @return список напоминаний для отправки
     */
    @EntityGraph(attributePaths = {"event", "event.user"})
    List<Reminder> findBySentFalseAndReminderTimeLessThanEqualAndReminderTimeGreaterThanEqual(
        LocalDateTime nowUTC, 
        LocalDateTime oneHourAgo
    );
    
    /**
     * Находит напоминание по ID с пессимистической блокировкой.
     * Предотвращает одновременную обработку одного напоминания несколькими процессами.
     * 
     * @param id идентификатор напоминания
     * @return напоминание с блокировкой или empty если не найдено
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reminder r WHERE r.id = :id")
    Optional<Reminder> findByIdWithLock(@Param("id") Long id);
    
    /**
     * Находит все напоминания для указанного события.
     * 
     * @param eventId идентификатор события
     * @return список всех напоминаний события
     */
    @EntityGraph(attributePaths = {"event"})
    List<Reminder> findByEventId(Long eventId);
    
    /**
     * Находит все неотправленные напоминания для указанного события.
     * 
     * @param eventId идентификатор события
     * @return список неотправленных напоминаний события
     */
    @EntityGraph(attributePaths = {"event"})
    List<Reminder> findByEventIdAndSentFalse(Long eventId);
    
    /**
     * Находит напоминание по ID с загрузкой связанного события.
     * 
     * @param id идентификатор напоминания
     * @return напоминание с загруженным событием или empty если не найдено
     */
    @EntityGraph(attributePaths = {"event"})
    Optional<Reminder> findWithEventById(Long id);
    
    /**
     * Находит напоминание по ID с загрузкой события и пользователя.
     * 
     * @param id идентификатор напоминания
     * @return напоминание с загруженным событием и пользователем или empty если не найдено
     */
    @EntityGraph(attributePaths = {"event", "event.user"})
    Optional<Reminder> findWithEventAndUserById(Long id);
}
