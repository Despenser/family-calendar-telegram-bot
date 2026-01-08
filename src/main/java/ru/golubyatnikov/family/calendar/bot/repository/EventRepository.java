package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA репозиторий для работы с сущностью {@link Event}.
 * 
 * <p>Предоставляет методы для:</p>
 * <ul>
 *   <li>Поиска событий семьи в диапазоне дат</li>
 *   <li>Получения событий пользователя</li>
 *   <li>Поиска событий для отправки уведомлений</li>
 *   <li>Работы с черновиками событий (для многошагового создания)</li>
 * </ul>
 *
 * <p><b>Требования:</b> 11.1, 15.1, 15.2, 5.6</p>
 *
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    /**
     * Находит все события семьи в заданном диапазоне дат.
     * Использует @EntityGraph для загрузки связанной сущности User,
     * чтобы избежать LazyInitializationException при доступе к event.getUser().
     * 
     * @param familyId идентификатор семьи
     * @param startDate начальная дата диапазона (включительно)
     * @param endDate конечная дата диапазона (включительно)
     *
     * @return список событий в указанном диапазоне, отсортированный по дате и времени
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByFamilyIdAndEventDateBetween(
        Long familyId, 
        LocalDate startDate, 
        LocalDate endDate
    );
    
    /**
     * Находит все события семьи в заданном диапазоне дат с определенным статусом.
     * Используется для получения только активных событий (без черновиков) для отображения в календаре.
     * Использует @EntityGraph для загрузки связанной сущности User для доступа к имени создателя.
     * 
     * @param familyId идентификатор семьи
     * @param startDate начальная дата диапазона (включительно)
     * @param endDate конечная дата диапазона (включительно)
     * @param status статус события (обычно ACTIVE)
     *
     * @return список событий в указанном диапазоне с заданным статусом, отсортированный по дате и времени
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByFamilyIdAndEventDateBetweenAndStatus(
        Long familyId, 
        LocalDate startDate, 
        LocalDate endDate,
        Event.EventStatus status
    );
    
    /**
     * Находит все события пользователя, отсортированные по дате в порядке возрастания.
     * 
     * @param userId идентификатор пользователя
     *
     * @return список событий пользователя, отсортированный по дате возрастания
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    List<Event> findByUserIdOrderByEventDateAsc(Long userId);
    
    /**
     * Находит события, для которых нужно отправить уведомления.
     * Ищет только активные события (не черновики), для которых уведомление еще не отправлено.
     * 
     * @param startDateTime начало временного диапазона для поиска событий
     * @param endDateTime конец временного диапазона для поиска событий
     *
     * @return список событий, требующих отправки уведомлений
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @Query(value = """
                        SELECT * FROM events e
                        WHERE e.notified = false
                        AND e.status = 'active'
                        AND (e.event_date + e.event_time) BETWEEN :startDateTime AND :endDateTime
                        ORDER BY e.event_date, e.event_time
                  """, nativeQuery = true)
    List<Event> findEventsForNotification(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );
    
    /**
     * Находит черновик события пользователя по статусу.
     * Используется для получения активного черновика в процессе создания события.
     * 
     * @param userId идентификатор пользователя
     * @param status статус события (обычно DRAFT)
     *
     * @return Optional с черновиком события, если найден
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    Optional<Event> findByUserIdAndStatus(Long userId, Event.EventStatus status);
    
    /**
     * Находит все черновики пользователя по статусу.
     * Используется для очистки старых незавершенных черновиков.
     * 
     * @param userId идентификатор пользователя
     * @param status статус события (обычно DRAFT)
     *
     * @return список всех черновиков пользователя с указанным статусом
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    List<Event> findAllByUserIdAndStatus(Long userId, Event.EventStatus status);
}
