package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.ConversationState;
import java.util.Optional;

/**
 * Repository интерфейс для сохранения и извлечения состояния диалога пользователя,
 * включая контекст сообщений с вложениями
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Repository
public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {
    
    /**
     * Находит состояние диалога по идентификатору пользователя.
     * 
     * @param userId идентификатор пользователя
     * @return Optional содержащий состояние диалога, если найдено, иначе пустой Optional
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "attachmentEvent"})
    Optional<ConversationState> findByUserId(Long userId);
}
