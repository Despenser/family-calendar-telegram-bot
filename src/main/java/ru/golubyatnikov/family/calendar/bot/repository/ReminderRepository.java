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
 * @version 1.0.0
 * @since 2026-01-16
 * @see Reminder
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
     * Использует <= вместо BETWEEN для предотвращения пропусков.
     * 
     * <p>Этот метод используется новой логикой отправки напоминаний с UTC.
     * Он находит все напоминания, где:</p>
     * <ul>
     *   <li>sent = false</li>
     *   <li>reminder_time <= nowUTC (время наступило)</li>
     *   <li>reminder_time >= oneHourAgo (не старше 1 часа)</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
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
     * <p>Использует PESSIMISTIC_WRITE блокировку для обеспечения атомарности
     * операций проверки и обновления флага sent.</p>
     * 
     * <p><b>Требования:</b> 2.3, 3.1, 3.3</p>
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
     * Находит напоминание по ID с eager загрузкой связанного события.
     * Использует EntityGraph для предотвращения LazyInitializationException.
     * 
     * <p>Загружает напоминание вместе со связанным событием в одном запросе,
     * что позволяет использовать event вне транзакции.</p>
     * 
     * @param id идентификатор напоминания
     * @return напоминание с загруженным событием или empty если не найдено
     */
    @EntityGraph(attributePaths = {"event"})
    Optional<Reminder> findWithEventById(Long id);
    
    /**
     * Находит напоминание по ID с eager загрузкой события и пользователя.
     * 
     * <p>Использует @EntityGraph для загрузки связанных сущностей в одном запросе.
     * Это предотвращает LazyInitializationException при доступе к event.user
     * вне транзакции.</p>
     * 
     * <p>Загружаемые связи:</p>
     * <ul>
     *   <li>event - событие напоминания</li>
     *   <li>event.user - пользователь-создатель события</li>
     * </ul>
     * 
     * <p>Метод используется в сценариях, где необходим доступ к timezone
     * пользователя-создателя события для корректного форматирования времени.</p>
     * 
     * <p><b>Требования:</b> 1.1, 2.1</p>
     * 
     * @param id идентификатор напоминания
     * @return напоминание с загруженным событием и пользователем или empty если не найдено
     */
    @EntityGraph(attributePaths = {"event", "event.user"})
    Optional<Reminder> findWithEventAndUserById(Long id);
}
