package ru.golubyatnikov.family.calendar.bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.config.DraftCleanupConfig;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.DraftCleanupService;
import ru.golubyatnikov.family.calendar.bot.util.CorrelationIdUtil;

import java.time.Duration;

/**
 * Планировщик для автоматической очистки осиротевших черновиков событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DraftCleanupScheduler {

    private final DraftCleanupService draftCleanupService;
    private final DraftCleanupConfig draftCleanupConfig;

    /**
     * Выполняет периодическую очистку осиротевших черновиков.
     */
    @Scheduled(cron = "${app.draft.cleanup.schedule-cron:0 0 */6 * * *}")
    @Transactional
    public void scheduledCleanup() {
        if (!draftCleanupConfig.isEnabled()) {
            return;
        }

        CorrelationIdUtil.executeWithCorrelationId(() -> {
            try {
                Duration threshold = Duration.ofHours(draftCleanupConfig.getPeriodicThresholdHours());
                draftCleanupService.cleanupOrphanedDrafts(threshold);

            } catch (Exception e) {
                log.error("Ошибка при выполнении периодической очистки черновиков: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Выполняет очистку осиротевших черновиков при запуске приложения.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupOrphanedDraftsOnStartup() {
        if (!draftCleanupConfig.isEnabled()) {
            return;
        }

        CorrelationIdUtil.executeWithCorrelationId(() -> {
            try {
                Duration threshold = Duration.ofHours(draftCleanupConfig.getStartupThresholdHours());
                draftCleanupService.cleanupOrphanedDrafts(threshold);

            } catch (Exception e) {
                log.error("Ошибка при очистке черновиков при запуске: {}", e.getMessage(), e);
            }
        });
    }
}
