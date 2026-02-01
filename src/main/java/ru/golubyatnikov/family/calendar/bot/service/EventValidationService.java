package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Сервис для валидации бизнес-правил событий.
 * 
 * <p>Предоставляет методы для проверки корректности данных событий
 * и прав доступа пользователей.</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-01
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class EventValidationService {
    
    /**
     * Валидирует дату события.
     * 
     * <p>Проверяет, что дата события не находится в прошлом относительно
     * текущей даты пользователя в его временной зоне.</p>
     * 
     * @param eventDate дата события
     * @param user пользователь для определения текущей даты
     * @throws InvalidDateException если дата в прошлом
     */
    public void validateEventDate(LocalDate eventDate, User user) {
        if (eventDate.isBefore(user.getCurrentDate())) {
            log.warn("Попытка использовать дату в прошлом: {} для пользователя ID={}", 
                     eventDate, user.getId());
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
    }
    
    /**
     * Валидирует дату и время события.
     * 
     * <p>Проверяет, что дата события не находится в прошлом относительно
     * текущей даты пользователя в его временной зоне.</p>
     * 
     * @param eventDateTime дата и время события
     * @param user пользователь для определения текущей даты
     * @throws InvalidDateException если дата в прошлом
     */
    public void validateEventDateTime(LocalDateTime eventDateTime, User user) {
        if (eventDateTime.toLocalDate().isBefore(user.getCurrentDate())) {
            log.warn("Попытка использовать дату в прошлом: {} для пользователя ID={}", 
                     eventDateTime, user.getId());
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
    }
    
    /**
     * Валидирует временной интервал события.
     * 
     * <p>Проверяет, что время окончания события не раньше времени начала.</p>
     * 
     * @param startTime время начала события
     * @param endTime время окончания события
     * @throws InvalidDateException если endTime раньше startTime
     */
    public void validateTimeInterval(LocalTime startTime, LocalTime endTime) {
        if (endTime != null && endTime.isBefore(startTime)) {
            log.warn("Попытка создать событие с временем окончания раньше времени начала: start={}, end={}", 
                     startTime, endTime);
            throw new InvalidDateException("Время окончания не может быть раньше времени начала");
        }
    }
    
    /**
     * Проверяет права пользователя на редактирование события.
     * 
     * <p>Пользователь может редактировать событие, если:</p>
     * <ul>
     *   <li>Он является создателем события</li>
     *   <li>Событие семейное (не персональное) и пользователь из той же семьи</li>
     * </ul>
     * 
     * @param event событие для проверки
     * @param userId идентификатор пользователя
     * @throws UnauthorizedAccessException если нет прав на редактирование
     */
    public void checkEditPermission(Event event, Long userId) {
        if (event.getUser().getId().equals(userId)) {
            return;
        }
        
        if (!event.getIsPersonal() && event.getFamily() != null) {
            boolean isFromSameFamily = event.getFamily().getMembers().stream()
                .anyMatch(u -> u.getId().equals(userId));
            
            if (isFromSameFamily) {
                return;
            }
        }
        
        log.warn("Пользователь ID={} попытался отредактировать событие ID={} без прав доступа", 
                 userId, event.getId());
        throw new UnauthorizedAccessException(
            "У вас нет прав для редактирования этого события");
    }
    
    /**
     * Проверяет права пользователя на удаление события.
     * 
     * <p>Только создатель события может его удалить.</p>
     * 
     * @param event событие для проверки
     * @param userId идентификатор пользователя
     * @throws UnauthorizedAccessException если пользователь не является создателем
     */
    public void checkDeletePermission(Event event, Long userId) {
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался удалить чужое событие ID={} (владелец: ID={})", 
                     userId, event.getId(), event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его удалить");
        }
    }
    
    /**
     * Проверяет, что событие находится в активном статусе.
     * 
     * @param event событие для проверки
     * @throws IllegalStateException если событие не активно
     */
    public void checkEventIsActive(Event event) {
        if (event.getStatus() != Event.EventStatus.ACTIVE) {
            log.warn("Попытка выполнить операцию с неактивным событием ID={} (статус: {})", 
                     event.getId(), event.getStatus());
            throw new IllegalStateException(
                String.format("Операция доступна только для активных событий (текущий статус: %s)", 
                             event.getStatus()));
        }
    }
    
    /**
     * Проверяет, что событие завершено.
     * 
     * @param event событие для проверки
     * @throws IllegalStateException если событие не завершено
     */
    public void checkEventIsCompleted(Event event) {
        if (!event.isCompleted()) {
            log.warn("Попытка выполнить операцию с незавершенным событием ID={}", event.getId());
            throw new IllegalStateException("Операция доступна только для завершенных событий");
        }
    }
    
    /**
     * Проверяет, что пользователь принадлежит семье.
     * 
     * @param user пользователь для проверки
     * @throws IllegalStateException если пользователь не принадлежит семье
     */
    public void checkUserHasFamily(User user) {
        if (user.getFamily() == null) {
            log.error("Пользователь ID={} не принадлежит ни одной семье", user.getId());
            throw new IllegalStateException("Пользователь должен принадлежать семье для создания событий");
        }
    }
}
