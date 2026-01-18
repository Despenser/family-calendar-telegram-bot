package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Reminder;
import java.time.LocalDateTime;
import java.util.List;

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
    List<Reminder> findBySentFalseAndReminderTimeBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Находит все напоминания для указанного события.
     * 
     * @param eventId идентификатор события
     * @return список всех напоминаний события
     */
    List<Reminder> findByEventId(Long eventId);
    
    /**
     * Находит все неотправленные напоминания для указанного события.
     * 
     * @param eventId идентификатор события
     * @return список неотправленных напоминаний события
     */
    List<Reminder> findByEventIdAndSentFalse(Long eventId);
}
