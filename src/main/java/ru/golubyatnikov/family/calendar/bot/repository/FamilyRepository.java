package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Family;

/**
 * Spring Data JPA репозиторий для работы с сущностью {@link Family}.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Repository
public interface FamilyRepository extends JpaRepository<Family, Long> { }
