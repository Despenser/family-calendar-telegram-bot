package ru.golubyatnikov.family.calendar.bot.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.golubyatnikov.family.calendar.bot.model.ConversationState;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.Optional;

/**
 * Spring Data JPA репозиторий для работы с сущностью {@link ConversationState}.
 * 
 * <p>Предоставляет методы для сохранения и извлечения состояния диалога пользователя,
 * включая контекст сообщений с вложениями.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-21
 */
@Repository
public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {
    
    /**
     * Находит состояние диалога по пользователю.
     * 
     * <p>Использует EntityGraph для загрузки связанных сущностей (user, attachmentEvent)
     * одним запросом, избегая проблемы N+1.</p>
     * 
     * @param user пользователь, для которого нужно найти состояние диалога
     * @return Optional содержащий состояние диалога, если найдено, иначе пустой Optional
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "attachmentEvent"})
    Optional<ConversationState> findByUser(User user);
    
    /**
     * Находит состояние диалога по идентификатору пользователя.
     * 
     * <p>Использует EntityGraph для загрузки связанных сущностей (user, attachmentEvent)
     * одним запросом, избегая проблемы N+1.</p>
     * 
     * @param userId идентификатор пользователя
     * @return Optional содержащий состояние диалога, если найдено, иначе пустой Optional
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    @EntityGraph(attributePaths = {"user", "attachmentEvent"})
    Optional<ConversationState> findByUserId(Long userId);
}
