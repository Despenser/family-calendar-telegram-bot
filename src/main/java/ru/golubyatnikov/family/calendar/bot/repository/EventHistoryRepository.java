package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.entity.EventHistory;

/**
 * Repository интерфейс для работы с историей изменений событий.
 * Предоставляет методы для CRUD операций и поиска записей истории.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Repository
public interface EventHistoryRepository extends JpaRepository<EventHistory, Long> { }
