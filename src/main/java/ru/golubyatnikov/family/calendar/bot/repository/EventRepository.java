package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA репозиторий для работы с сущностью {@link Event}.
 * 
 * <p>Предоставляет методы для управления событиями в семейном календаре, включая
 * стандартные CRUD операции и специализированные методы поиска и фильтрации.</p>
 * 
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Стандартные CRUD операции (save, findById, findAll, delete, count)</li>
 *   <li>Поиск событий семьи в заданном диапазоне дат</li>
 *   <li>Получение всех событий пользователя с сортировкой по дате</li>
 *   <li>Поиск событий для отправки уведомлений</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 11.1</p>
 * 
 * @see Event
 * @see JpaRepository
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    /**
     * Находит все события семьи в заданном диапазоне дат.
     * 
     * <p>Этот метод используется для отображения предстоящих событий семьи
     * в календаре. Результаты автоматически сортируются по дате и времени события.</p>
     * 
     * <p>Метод использует индекс {@code idx_events_family_date} для оптимизации запроса.</p>
     * 
     * @param familyId идентификатор семьи
     * @param startDate начальная дата диапазона (включительно)
     * @param endDate конечная дата диапазона (включительно)
     * @return список событий в указанном диапазоне, отсортированный по дате и времени
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    List<Event> findByFamilyIdAndEventDateBetween(
        Long familyId, 
        LocalDate startDate, 
        LocalDate endDate
    );
    
    /**
     * Находит все события пользователя, отсортированные по дате в порядке возрастания.
     * 
     * <p>Этот метод используется для отображения личных событий пользователя
     * в команде /my_events. События сортируются от ближайших к более поздним.</p>
     * 
     * <p>Метод использует индекс {@code idx_events_user_id} для оптимизации запроса.</p>
     * 
     * @param userId идентификатор пользователя
     * @return список событий пользователя, отсортированный по дате возрастания
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    List<Event> findByUserIdOrderByEventDateAsc(Long userId);
    
    /**
     * Находит события, для которых нужно отправить уведомления.
     * 
     * <p>Этот метод используется планировщиком уведомлений для поиска событий,
     * которые начнутся в ближайшее время (обычно в течение следующего часа)
     * и для которых еще не было отправлено уведомление.</p>
     * 
     * <p>Критерии поиска:</p>
     * <ul>
     *   <li>Событие еще не помечено как notified (notified = false)</li>
     *   <li>Дата и время события находятся в заданном диапазоне</li>
     * </ul>
     * 
     * <p>Метод использует индекс {@code idx_events_datetime} для оптимизации запроса.</p>
     * 
     * <p><b>Пример использования:</b></p>
     * <pre>
     * LocalDateTime now = LocalDateTime.now();
     * LocalDateTime oneHourLater = now.plusHours(1);
     * List&lt;Event&gt; events = eventRepository.findEventsForNotification(now, oneHourLater);
     * </pre>
     * 
     * @param startDateTime начало временного диапазона для поиска событий
     * @param endDateTime конец временного диапазона для поиска событий
     * @return список событий, требующих отправки уведомлений
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @Query("SELECT e FROM Event e " +
           "WHERE e.notified = false " +
           "AND FUNCTION('TIMESTAMP', e.eventDate, e.eventTime) BETWEEN :startDateTime AND :endDateTime " +
           "ORDER BY e.eventDate, e.eventTime")
    List<Event> findEventsForNotification(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );
}
