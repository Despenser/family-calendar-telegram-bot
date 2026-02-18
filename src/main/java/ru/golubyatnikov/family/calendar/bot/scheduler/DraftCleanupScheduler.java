package ru.golubyatnikov.family.calendar.bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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

    @Value("${draft.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${draft.cleanup.startup-threshold-hours:1}")
    private int startupThresholdHours;

    @Value("${draft.cleanup.periodic-threshold-hours:24}")
    private int periodicThresholdHours;

    /**
     * Выполняет периодическую очистку осиротевших черновиков.
     */
    @Scheduled(cron = "${draft.cleanup.schedule-cron:0 0 */6 * * *}")
    @Transactional
    public void scheduledCleanup() {
        if (!cleanupEnabled) {
            return;
        }

        CorrelationIdUtil.executeWithCorrelationId(() -> {
            try {
                Duration threshold = Duration.ofHours(periodicThresholdHours);
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
        if (!cleanupEnabled) {
            return;
        }

        CorrelationIdUtil.executeWithCorrelationId(() -> {
            try {
                Duration threshold = Duration.ofHours(startupThresholdHours);
                draftCleanupService.cleanupOrphanedDrafts(threshold);

            } catch (Exception e) {
                log.error("Ошибка при очистке черновиков при запуске: {}", e.getMessage(), e);
            }
        });
    }
}
