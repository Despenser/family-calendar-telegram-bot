package ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для очистки осиротевших черновиков событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-12
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DraftCleanupService {
    
    private final EventRepository eventRepository;
    
    @Value("${draft.cleanup.enabled:true}")
    private boolean cleanupEnabled;
    
    @Value("${draft.cleanup.startup-threshold-hours:1}")
    private int startupThresholdHours;
    
    @Value("${draft.cleanup.periodic-threshold-hours:24}")
    private int periodicThresholdHours;
    
    /**
     * Проверяет, является ли событие осиротевшим черновиком.
     *
     * @param event событие для проверки
     * @return true, если событие является осиротевшим черновиком, иначе false
     */
    public boolean isOrphanedDraft(Event event) {
        if (event == null) {
            return false;
        }
        
        boolean isOrphaned = event.getStatus() == EventStatus.DRAFT
            && event.getTitle() == null
            && event.getEventDate() == null
            && event.getEventTime() == null;
        
        if (isOrphaned) {
            }
        
        return isOrphaned;
    }
    
    /**
     * Удаляет осиротевшие черновики старше указанного возраста.
     *
     * @param age минимальный возраст черновика для удаления
     *
     * @return количество удаленных черновиков
     * @throws IllegalArgumentException если age отрицательный или null
     */
    public int cleanupOrphanedDrafts(Duration age) {
        if (age == null || age.isNegative()) {
            throw new IllegalArgumentException("Возраст черновика должен быть положительным");
        }
        
        LocalDateTime threshold = LocalDateTime.now().minus(age);
        
        // Находим все черновики
        List<Event> allDrafts = eventRepository.findAll().stream()
            .filter(event -> event.getStatus() == EventStatus.DRAFT)
            .toList();
        
        // Фильтруем осиротевшие черновики старше порогового значения
        List<Event> orphanedDrafts = allDrafts.stream()
            .filter(this::isOrphanedDraft)
            .filter(event -> event.getCreatedAt() != null && event.getCreatedAt().isBefore(threshold))
            .toList();
        
        // Удаляем каждый осиротевший черновик
        int deletedCount = 0;
        for (Event draft : orphanedDrafts) {
            try {
                Long draftId = draft.getId();
                Long userId = draft.getUser() != null ? draft.getUser().getId() : null;
                LocalDateTime createdAt = draft.getCreatedAt();
                
                eventRepository.delete(draft);
                deletedCount++;
                
                log.info("Удален осиротевший черновик: ID={}, userId={}, createdAt={}, age={}", 
                         draftId, userId, createdAt, Duration.between(createdAt, LocalDateTime.now()));

            } catch (Exception e) {
                log.error("Ошибка при удалении осиротевшего черновика ID={}: {}", draft.getId(), e.getMessage(), e);
            }
        }
        
        return deletedCount;
    }
    
    /**
     * Выполняет периодическую очистку осиротевших черновиков.
     */
    @Scheduled(cron = "${draft.cleanup.schedule-cron:0 0 */6 * * *}")
    public void scheduledCleanup() {
        if (!cleanupEnabled) {
            return;
        }
        
        try {
            Duration threshold = Duration.ofHours(periodicThresholdHours);
            int deletedCount = cleanupOrphanedDrafts(threshold);
            } catch (Exception e) {
            log.error("Ошибка при выполнении периодической очистки черновиков: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Выполняет очистку осиротевших черновиков при запуске приложения.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOrphanedDraftsOnStartup() {
        if (!cleanupEnabled) {
            return;
        }
        
        try {
            Duration threshold = Duration.ofHours(startupThresholdHours);
            int deletedCount = cleanupOrphanedDrafts(threshold);
            } catch (Exception e) {
            log.error("Ошибка при очистке черновиков при запуске: {}", e.getMessage(), e);
        }
    }
}
