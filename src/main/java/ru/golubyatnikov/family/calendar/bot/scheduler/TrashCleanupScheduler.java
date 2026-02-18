package ru.golubyatnikov.family.calendar.bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.presentation.message.TrashMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CorrelationIdUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Планировщик для автоматической очистки корзины от старых событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrashCleanupScheduler {

    private static final int TRASH_RETENTION_DAYS = 30;

    private final EventRepository eventRepository;
    private final TrashMessageService trashMessageService;

    /**
     * Автоматически очищает корзину от старых событий.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldTrash() {
        CorrelationIdUtil.executeWithCorrelationId(() -> {
            try {
                LocalDateTime cutoffDate = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);
                List<Event> oldEvents = eventRepository.findByStatusAndDeletedAtBefore(
                    EventStatus.DELETED,
                    cutoffDate
                );

                if (oldEvents.isEmpty()) {
                    log.debug("Нет старых событий для удаления из корзины");
                    return;
                }

                log.info("Начинается очистка корзины. Найдено {} старых событий", oldEvents.size());
                deleteOldEvents(oldEvents);
                log.info("Очистка корзины завершена. Удалено {} событий", oldEvents.size());

            } catch (Exception e) {
                log.error("Ошибка при выполнении автоматической очистки корзины: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Удаляет старые события из корзины.
     *
     * @param oldEvents список событий для удаления
     */
    private void deleteOldEvents(@NonNull List<Event> oldEvents) {
        int successCount = 0;
        int errorCount = 0;

        for (Event event : oldEvents) {
            try {
                trashMessageService.deleteEventMessage(event);
                eventRepository.delete(event);
                successCount++;

            } catch (Exception e) {
                errorCount++;
                log.error("Ошибка при удалении события ID={}: {}", event.getId(), e.getMessage(), e);
            }
        }

        if (errorCount > 0) {
            log.warn("Очистка завершена с ошибками. Успешно: {}, Ошибок: {}", successCount, errorCount);
        }
    }
}
