package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для очистки осиротевших черновиков событий.
 * 
 * <p>Осиротевший черновик - это событие со статусом DRAFT, у которого все обязательные
 * поля (title, eventDate, eventTime) равны NULL. Такие черновики возникают, когда процесс
 * создания события прерывается из-за ошибки, но созданный черновик не удаляется.</p>
 * 
 * <p>Сервис предоставляет методы для:</p>
 * <ul>
 *   <li>Идентификации осиротевших черновиков по критериям</li>
 *   <li>Удаления черновиков старше указанного возраста</li>
 *   <li>Периодической автоматической очистки черновиков</li>
 *   <li>Очистки черновиков при запуске приложения</li>
 *   <li>Логирования всех операций очистки</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 3.1, 3.2, 3.3, 3.4, 1.4</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
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
     * <p>Критерии осиротевшего черновика:</p>
     * <ul>
     *   <li>Статус = DRAFT</li>
     *   <li>title = NULL</li>
     *   <li>eventDate = NULL</li>
     *   <li>eventTime = NULL</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 3.4</p>
     * 
     * @param event событие для проверки
     * @return true, если событие является осиротевшим черновиком, иначе false
     */
    public boolean isOrphanedDraft(Event event) {
        if (event == null) {
            return false;
        }
        
        boolean isOrphaned = event.getStatus() == Event.EventStatus.DRAFT
            && event.getTitle() == null
            && event.getEventDate() == null
            && event.getEventTime() == null;
        
        if (isOrphaned) {
            log.debug("Обнаружен осиротевший черновик: ID={}, createdAt={}, userId={}", 
                     event.getId(), event.getCreatedAt(), 
                     event.getUser() != null ? event.getUser().getId() : "unknown");
        }
        
        return isOrphaned;
    }
    
    /**
     * Удаляет осиротевшие черновики старше указанного возраста.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Находит все черновики со статусом DRAFT</li>
     *   <li>Фильтрует их по критериям осиротевшего черновика</li>
     *   <li>Проверяет возраст каждого черновика</li>
     *   <li>Удаляет черновики старше указанного возраста</li>
     *   <li>Логирует информацию о каждом удаленном черновике</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 3.2, 3.4</p>
     * 
     * @param age минимальный возраст черновика для удаления
     * @return количество удаленных черновиков
     * @throws IllegalArgumentException если age отрицательный или null
     */
    public int cleanupOrphanedDrafts(Duration age) {
        if (age == null || age.isNegative()) {
            throw new IllegalArgumentException("Возраст черновика должен быть положительным");
        }
        
        log.info("Начало очистки осиротевших черновиков старше {}", age);
        
        LocalDateTime threshold = LocalDateTime.now().minus(age);
        
        // Находим все черновики
        List<Event> allDrafts = eventRepository.findAll().stream()
            .filter(event -> event.getStatus() == Event.EventStatus.DRAFT)
            .toList();
        
        log.debug("Найдено {} черновиков для проверки", allDrafts.size());
        
        // Фильтруем осиротевшие черновики старше порогового значения
        List<Event> orphanedDrafts = allDrafts.stream()
            .filter(this::isOrphanedDraft)
            .filter(event -> event.getCreatedAt() != null && event.getCreatedAt().isBefore(threshold))
            .toList();
        
        log.info("Найдено {} осиротевших черновиков старше {} для удаления", 
                 orphanedDrafts.size(), age);
        
        // Удаляем каждый осиротевший черновик с логированием
        int deletedCount = 0;
        for (Event draft : orphanedDrafts) {
            try {
                Long draftId = draft.getId();
                Long userId = draft.getUser() != null ? draft.getUser().getId() : null;
                LocalDateTime createdAt = draft.getCreatedAt();
                
                eventRepository.delete(draft);
                deletedCount++;
                
                log.info("Удален осиротевший черновик: ID={}, userId={}, createdAt={}, age={}", 
                         draftId, userId, createdAt, 
                         Duration.between(createdAt, LocalDateTime.now()));
            } catch (Exception e) {
                log.error("Ошибка при удалении осиротевшего черновика ID={}: {}", 
                         draft.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Очистка завершена. Удалено {} осиротевших черновиков", deletedCount);
        return deletedCount;
    }
    
    /**
     * Выполняет периодическую очистку осиротевших черновиков.
     * 
     * <p>Метод запускается автоматически каждые 6 часов (по умолчанию) и удаляет
     * черновики старше 24 часов (по умолчанию). Расписание и пороговое значение
     * настраиваются через конфигурационные параметры.</p>
     * 
     * <p>Очистка выполняется только если параметр {@code draft.cleanup.enabled} установлен в true.</p>
     * 
     * <p><b>Требования:</b> 3.1, 3.3</p>
     * 
     * @see #cleanupOrphanedDrafts(Duration)
     */
    @Scheduled(cron = "${draft.cleanup.schedule-cron:0 0 */6 * * *}")
    public void scheduledCleanup() {
        if (!cleanupEnabled) {
            log.debug("Периодическая очистка черновиков отключена");
            return;
        }
        
        log.info("Запуск периодической очистки осиротевших черновиков");
        
        try {
            Duration threshold = Duration.ofHours(periodicThresholdHours);
            int deletedCount = cleanupOrphanedDrafts(threshold);
            
            log.info("Периодическая очистка завершена успешно. Удалено черновиков: {}", deletedCount);
        } catch (Exception e) {
            log.error("Ошибка при выполнении периодической очистки черновиков: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Выполняет очистку осиротевших черновиков при запуске приложения.
     * 
     * <p>Метод запускается автоматически после полной инициализации Spring контекста
     * (событие {@link ApplicationReadyEvent}). Удаляет черновики старше 1 часа (по умолчанию),
     * что позволяет очистить черновики, оставшиеся после предыдущего запуска приложения.</p>
     * 
     * <p>Очистка выполняется только если параметр {@code draft.cleanup.enabled} установлен в true.</p>
     * 
     * <p><b>Требования:</b> 3.1, 1.4</p>
     * 
     * @see #cleanupOrphanedDrafts(Duration)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOrphanedDraftsOnStartup() {
        if (!cleanupEnabled) {
            log.info("Очистка черновиков при запуске отключена");
            return;
        }
        
        log.info("Запуск очистки осиротевших черновиков при старте приложения");
        
        try {
            Duration threshold = Duration.ofHours(startupThresholdHours);
            int deletedCount = cleanupOrphanedDrafts(threshold);
            
            log.info("Очистка при запуске завершена успешно. Удалено черновиков: {}", deletedCount);
        } catch (Exception e) {
            log.error("Ошибка при очистке черновиков при запуске: {}", e.getMessage(), e);
        }
    }
}
