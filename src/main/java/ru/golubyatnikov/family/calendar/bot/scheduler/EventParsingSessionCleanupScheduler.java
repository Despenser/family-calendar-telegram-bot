package ru.golubyatnikov.family.calendar.bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.ai.EventParsingSessionService;

/**
 * Планировщик для очистки устаревших сессий парсинга событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-27
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventParsingSessionCleanupScheduler {

    private final EventParsingSessionService sessionService;

    /**
     * Очищает устаревшие сессии парсинга.
     * Интервал настраивается через app.event-parsing.cleanup-interval-ms
     */
    @Scheduled(fixedRateString = "${app.event-parsing.cleanup-interval-ms}")
    public void cleanupExpiredSessions() {
        try {
            sessionService.cleanupExpiredSessions();

        } catch (Exception e) {
            log.error("Ошибка при очистке устаревших сессий парсинга: {}", e.getMessage(), e);
        }
    }
}
