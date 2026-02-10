package ru.golubyatnikov.family.calendar.bot.repository;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository интерфейс для работы с событиями
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    /**
     * Находит все события семьи в заданном диапазоне дат.
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
        EventStatus status
    );
    
    /**
     * Находит все события пользователя, отсортированные по дате в порядке возрастания.
     * 
     * @param userId идентификатор пользователя
     *
     * @return список событий пользователя, отсортированный по дате возрастания
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByUserIdOrderByEventDateAsc(Long userId);
    
    /**
     * Находит все события пользователя с определенным статусом, отсортированные по дате и времени в порядке возрастания.
     * 
     * @param userId идентификатор пользователя
     * @param status статус события (ACTIVE, DELETED, COMPLETED, DRAFT)
     *
     * @return список событий пользователя с указанным статусом, отсортированный по дате и времени возрастания
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    @QueryHints(@QueryHint(name = org.hibernate.annotations.QueryHints.CACHEABLE, value = "false"))
    List<Event> findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(Long userId, EventStatus status);

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
    @EntityGraph(attributePaths = {"user", "family"})
    Optional<Event> findByUserIdAndStatus(Long userId, EventStatus status);
    
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
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findAllByUserIdAndStatus(Long userId, EventStatus status);
    
    /**
     * Находит удаленные события пользователя (корзина), отсортированные по дате удаления.
     * Используется для отображения корзины пользователя.
     * 
     * @param userId идентификатор пользователя
     * @param status статус события (DELETED)
     *
     * @return список удаленных событий, отсортированный по дате удаления (новые первыми)
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByUserIdAndStatusOrderByDeletedAtDesc(Long userId, EventStatus status);
    
    /**
     * Находит удаленные события старше указанной даты для автоматической очистки корзины.
     * Используется планировщиком для удаления событий старше 30 дней.
     * 
     * @param status статус события (DELETED)
     * @param deletedBefore дата, до которой события должны быть удалены
     *
     * @return список старых удаленных событий для окончательного удаления
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByStatusAndDeletedAtBefore(EventStatus status, LocalDateTime deletedBefore);
    
    /**
     * Находит активные события, время окончания которых уже прошло.
     * Используется планировщиком для автоматического завершения событий.
     * 
     * @param currentDateTime текущая дата и время
     *
     * @return список активных событий, которые должны быть завершены
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    @Query("""
                SELECT e FROM Event e
                WHERE e.status = 'ACTIVE'
                AND e.eventDate IS NOT NULL
                AND e.eventTime IS NOT NULL
                AND (
                    (e.endTime IS NOT NULL AND CAST(CONCAT(CAST(e.eventDate AS string),
                        ' ', CAST(e.endTime AS string)) AS timestamp) < :currentDateTime)
                    OR (e.endTime IS NULL AND CAST(CONCAT(CAST(e.eventDate AS string),
                        ' ', CAST(e.eventTime AS string)) AS timestamp) < :currentDateTime)
                )
    """)
    List<Event> findExpiredActiveEvents(@Param("currentDateTime") LocalDateTime currentDateTime);
    
    /**
     * Поиск событий по названию или описанию.
     * Используется для функции поиска событий пользователя.
     *
     * @param familyId идентификатор семьи
     * @param userId идентификатор пользователя
     * @param query поисковый запрос
     *
     * @return список событий, содержащих запрос в названии или описании
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    @Query("""
                SELECT e FROM Event e
                WHERE e.family.id = :familyId
                AND e.status = 'ACTIVE'
                AND (
                    (e.isPersonal = false)
                    OR (e.isPersonal = true AND e.user.id = :userId)
                )
                AND (
                    LOWER(e.title) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(e.description) LIKE LOWER(CONCAT('%', :query, '%'))
                )
                ORDER BY e.eventDate DESC, e.eventTime DESC
    """)
    List<Event> searchByTitleOrDescription(
        @Param("familyId") Long familyId,
        @Param("userId") Long userId,
        @Param("query") String query
    );
    
    /**
     * Находит семейные события (не персональные) с определенным статусом.
     * 
     * @param familyId идентификатор семьи
     * @param status статус события
     *
     * @return список семейных событий с указанным статусом
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByFamilyIdAndIsPersonalFalseAndStatus(Long familyId, EventStatus status);
    
    /**
     * Находит персональные события пользователя с определенным статусом.
     * 
     * @param userId идентификатор пользователя
     * @param status статус события
     *
     * @return список персональных событий пользователя с указанным статусом
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByUserIdAndIsPersonalTrueAndStatus(Long userId, EventStatus status);
    
    /**
     * Находит все события семьи с определенным статусом.
     * 
     * @param familyId идентификатор семьи
     * @param status статус события
     *
     * @return список событий семьи с указанным статусом
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByFamilyIdAndStatus(Long familyId, EventStatus status);
    
    /**
     * Находит предстоящие события семьи и пользователя.
     * Включает семейные события и персональные события пользователя.
     * 
     * @param familyId идентификатор семьи
     * @param userId идентификатор пользователя
     * @param currentDate текущая дата
     *
     * @return список предстоящих событий
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    @Query("""
                SELECT e FROM Event e
                WHERE e.family.id = :familyId
                AND e.status = 'ACTIVE'
                AND e.eventDate >= :currentDate
                AND (
                    (e.isPersonal = false)
                    OR (e.isPersonal = true AND e.user.id = :userId)
                )
                ORDER BY e.eventDate ASC, e.eventTime ASC
    """)
    List<Event> findUpcomingEvents(
        @Param("familyId") Long familyId,
        @Param("userId") Long userId,
        @Param("currentDate") LocalDate currentDate
    );
    
    /**
     * Находит все события серии с определенным статусом.
     * Используется для операций с повторяющимися событиями.
     * 
     * @param seriesId UUID серии событий
     * @param status статус события
     *
     * @return список событий серии с указанным статусом
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findBySeriesIdAndStatus(String seriesId, EventStatus status);
    
    /**
     * Подсчитывает количество событий семьи в диапазоне дат с определенным статусом.
     * 
     * @param familyId идентификатор семьи
     * @param startDate начальная дата
     * @param endDate конечная дата
     * @param status статус события
     *
     * @return количество событий
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    int countByFamilyIdAndEventDateBetweenAndStatus(
        Long familyId,
        LocalDate startDate,
        LocalDate endDate,
        EventStatus status
    );
    
    /**
     * Подсчитывает количество событий пользователя в диапазоне дат по типу и статусу.
     * Используется для статистики активных событий с разделением на семейные и персональные.
     * 
     * @param userId идентификатор пользователя
     * @param startDate начальная дата диапазона (включительно)
     * @param endDate конечная дата диапазона (включительно)
     * @param isPersonal флаг персонального события (true - персональные, false - семейные)
     * @param status статус события (обычно ACTIVE)
     *
     * @return количество событий пользователя с указанным типом и статусом
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    int countByUserIdAndEventDateBetweenAndIsPersonalAndStatus(
        Long userId,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isPersonal,
        EventStatus status
    );
    
    /**
     * Находит все события семьи с определенным статусом, отсортированные по дате и времени.
     * Используется для фильтрации событий по типу (ALL).
     * 
     * @param familyId идентификатор семьи
     * @param status статус события
     *
     * @return список событий семьи с указанным статусом, отсортированный по дате и времени
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByFamilyIdAndStatusOrderByEventDateAscEventTimeAsc(
        Long familyId, 
        EventStatus status
    );
    
    /**
     * Находит семейные события (не персональные) с определенным статусом, отсортированные по дате и времени.
     * Используется для фильтрации событий по типу (FAMILY).
     * 
     * @param familyId идентификатор семьи
     * @param isPersonal флаг персонального события (false для семейных)
     * @param status статус события
     *
     * @return список семейных событий с указанным статусом, отсортированный по дате и времени
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByFamilyIdAndIsPersonalAndStatusOrderByEventDateAscEventTimeAsc(
        Long familyId, 
        Boolean isPersonal, 
        EventStatus status
    );
    
    /**
     * Находит персональные события пользователя с определенным статусом, отсортированные по дате и времени.
     * Используется для фильтрации событий по типу (PERSONAL).
     * 
     * @param userId идентификатор пользователя
     * @param isPersonal флаг персонального события (true для личных)
     * @param status статус события
     *
     * @return список персональных событий пользователя с указанным статусом, отсортированный по дате и времени
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByUserIdAndIsPersonalAndStatusOrderByEventDateAscEventTimeAsc(
        Long userId, 
        Boolean isPersonal, 
        EventStatus status
    );
    
    /**
     * Подсчитывает количество событий пользователя с определенным статусом.
     * 
     * @param userId идентификатор пользователя
     * @param status статус события (обычно ACTIVE)
     *
     * @return количество событий пользователя с указанным статусом
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    int countByUserIdAndStatus(Long userId, EventStatus status);
    
    /**
     * Находит событие по ID с загрузкой пользователя.
     * 
     * @param id идентификатор события
     *
     * @return Optional с событием и инициализированным User, или empty если событие не найдено
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdWithUser(@Param("id") Long id);
    
    /**
     * Находит событие по ID с загрузкой напоминаний.
     * 
     * @param id идентификатор события
     *
     * @return Optional с событием и инициализированной коллекцией reminders, или empty если событие не найдено
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    @EntityGraph(attributePaths = {"reminders"})
    Optional<Event> findByIdWithReminders(@Param("id") Long id);
    
    /**
     * Находит активные события семьи на указанную дату, отсортированные по времени.
     * Используется для просмотра событий на конкретную дату в календаре.
     * 
     * @param familyId идентификатор семьи
     * @param eventDate дата события
     * @param status статус события (обычно ACTIVE)
     *
     * @return список активных событий на указанную дату, отсортированный по времени
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    List<Event> findByFamilyIdAndEventDateAndStatusOrderByEventTimeAsc(
        Long familyId,
        LocalDate eventDate,
        EventStatus status
    );
    
    /**
     * Находит активные и завершенные события семьи на указанную дату, отсортированные по времени.
     * Используется для просмотра событий на конкретную дату в календаре, включая завершенные события в прошлом.
     * 
     * @param familyId идентификатор семьи
     * @param eventDate дата события
     *
     * @return список активных и завершенных событий на указанную дату, отсортированный по времени
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "family"})
    @Query("""
                SELECT e FROM Event e
                WHERE e.family.id = :familyId
                AND e.eventDate = :eventDate
                AND e.status IN ('ACTIVE', 'COMPLETED')
                ORDER BY e.eventTime ASC
    """)
    List<Event> findByFamilyIdAndEventDateIncludingCompleted(
        @Param("familyId") Long familyId,
        @Param("eventDate") LocalDate eventDate
    );
}
