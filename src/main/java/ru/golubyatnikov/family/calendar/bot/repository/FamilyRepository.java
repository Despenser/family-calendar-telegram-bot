package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.Family;

/**
 * Spring Data JPA репозиторий для работы с сущностью {@link Family}.
 * 
 * <p>Предоставляет стандартные CRUD операции для управления семьями в системе.
 * Наследует базовые методы от {@link JpaRepository}, включая:</p>
 * <ul>
 *   <li>save(Family) - сохранение или обновление семьи</li>
 *   <li>findById(Long) - поиск семьи по идентификатору</li>
 *   <li>findAll() - получение всех семей</li>
 *   <li>delete(Family) - удаление семьи</li>
 *   <li>count() - подсчет количества семей</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 11.1</p>
 * 
 * @see Family
 * @see JpaRepository
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Repository
public interface FamilyRepository extends JpaRepository<Family, Long> {
    // Базовые CRUD операции предоставляются JpaRepository
    // При необходимости можно добавить кастомные методы запросов
}
