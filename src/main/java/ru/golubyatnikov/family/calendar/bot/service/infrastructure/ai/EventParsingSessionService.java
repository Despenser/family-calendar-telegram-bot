package ru.golubyatnikov.family.calendar.bot.service.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.config.EventParsingConfig;
import ru.golubyatnikov.family.calendar.bot.model.dto.EventParsingSession;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventParsingState;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для управления сессиями парсинга событий через AI.
 * Хранит состояние диалога с пользователем в памяти.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventParsingSessionService {

    private final EventParsingConfig config;
    private final Map<Long, EventParsingSession> sessions = new ConcurrentHashMap<>();

    /**
     * Создает новую сессию парсинга для пользователя.
     *
     * @param userId ID пользователя
     * @return созданная сессия
     */
    public EventParsingSession createSession(Long userId) {
        EventParsingSession session = EventParsingSession.builder()
                .userId(userId)
                .state(EventParsingState.INITIAL)
                .build();
        
        sessions.put(userId, session);
        return session;
    }

    /**
     * Получает активную сессию пользователя.
     *
     * @param userId ID пользователя
     * @return Optional с сессией или empty, если сессии нет или она устарела
     */
    public Optional<EventParsingSession> getSession(Long userId) {
        EventParsingSession session = sessions.get(userId);
        
        if (session == null) {
            return Optional.empty();
        }
        
        // Проверяем, не устарела ли сессия
        if (isSessionExpired(session)) {
            sessions.remove(userId);
            return Optional.empty();
        }
        
        return Optional.of(session);
    }

    /**
     * Получает или создает сессию для пользователя.
     *
     * @param userId ID пользователя
     * @return сессия пользователя
     */
    public EventParsingSession getOrCreateSession(Long userId) {
        return getSession(userId).orElseGet(() -> createSession(userId));
    }

    /**
     * Обновляет сессию пользователя.
     *
     * @param session обновленная сессия
     */
    public void updateSession(@NonNull EventParsingSession session) {
        session.setUpdatedAt(LocalDateTime.now());
        sessions.put(session.getUserId(), session);
    }

    /**
     * Завершает сессию пользователя.
     *
     * @param userId ID пользователя
     */
    public void completeSession(Long userId) {
        EventParsingSession session = sessions.get(userId);
        if (session != null) {
            session.updateState(EventParsingState.COMPLETED);
        }
        sessions.remove(userId);
    }

    /**
     * Отменяет сессию пользователя.
     *
     * @param userId ID пользователя
     */
    public void cancelSession(Long userId) {
        EventParsingSession session = sessions.get(userId);
        if (session != null) {
            session.updateState(EventParsingState.CANCELLED);
        }
        sessions.remove(userId);
    }

    /**
     * Проверяет, есть ли активная сессия у пользователя.
     *
     * @param userId ID пользователя
     * @return true, если есть активная сессия
     */
    public boolean hasActiveSession(Long userId) {
        return getSession(userId).isPresent();
    }

    /**
     * Проверяет, устарела ли сессия.
     *
     * @param session сессия для проверки
     * @return true, если сессия устарела
     */
    private boolean isSessionExpired(@NonNull EventParsingSession session) {
        LocalDateTime expirationTime = session.getUpdatedAt()
                .plusMinutes(config.getSessionTimeoutMinutes());

        return LocalDateTime.now().isAfter(expirationTime);
    }

    /**
     * Очищает все устаревшие сессии.
     * Вызывается периодически планировщиком.
     */
    public void cleanupExpiredSessions() {
        int removedCount = 0;
        for (Map.Entry<Long, EventParsingSession> entry : sessions.entrySet()) {
            if (isSessionExpired(entry.getValue())) {
                sessions.remove(entry.getKey());
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("Удалено устаревших сессий парсинга: {}", removedCount);
        }
    }
}
