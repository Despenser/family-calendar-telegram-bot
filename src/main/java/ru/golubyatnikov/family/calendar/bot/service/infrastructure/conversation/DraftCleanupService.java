package ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        return event.getStatus() == EventStatus.DRAFT
            && event.getTitle() == null
            && event.getEventDate() == null
            && event.getEventTime() == null;
    }
    
    /**
     * Удаляет осиротевшие черновики старше указанного возраста.
     *
     * @param age минимальный возраст черновика для удаления
     * @throws IllegalArgumentException если age отрицательный или null
     */
    public void cleanupOrphanedDrafts(Duration age) {
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
        orphanedDrafts.forEach(draft -> {
            try {
                eventRepository.delete(draft);

            } catch (Exception e) {
                log.error("Ошибка при удалении осиротевшего черновика ID={}: {}", draft.getId(), e.getMessage(), e);
            }
        });
    }
}
