package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для управления событиями в семейном календаре.
 * 
 * <p>Этот сервис предоставляет бизнес-логику для работы с событиями, включая:</p>
 * <ul>
 *   <li>Создание новых событий с валидацией даты</li>
 *   <li>Получение предстоящих событий семьи</li>
 *   <li>Получение событий конкретного пользователя</li>
 *   <li>Обновление существующих событий с проверкой прав доступа</li>
 *   <li>Удаление событий с проверкой прав доступа</li>
 * </ul>
 * 
 * <p>Все операции изменения данных выполняются в транзакциях для обеспечения
 * целостности данных. Сервис выполняет валидацию входных данных и проверку
 * прав доступа перед выполнением операций.</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.3, 5.1, 5.4, 7.1, 7.2, 7.3, 7.4, 7.5</p>
 * 
 * @see Event
 * @see EventRepository
 * @see UserRepository
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class EventService {
    
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    
    /**
     * Создает новое событие в календаре.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет существование пользователя в базе данных</li>
     *   <li>Валидирует дату и время события (не должно быть в прошлом)</li>
     *   <li>Создает событие с привязкой к пользователю и его семье</li>
     *   <li>Сохраняет событие в базе данных</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 4.1, 4.2, 4.3</p>
     * 
     * @param userId идентификатор пользователя, создающего событие
     * @param title название события (обязательное)
     * @param description описание события (может быть null)
     * @param eventDateTime дата и время события
     * @return созданное и сохраненное событие
     * @throws UserNotFoundException если пользователь с указанным ID не найден
     * @throws InvalidDateException если дата события находится в прошлом
     * @throws IllegalArgumentException если title пустой или null
     */
    public Event createEvent(Long userId, String title, String description, LocalDateTime eventDateTime) {
        log.debug("Создание события для пользователя ID={}: title='{}', dateTime={}", 
                  userId, title, eventDateTime);
        
        // Валидация входных параметров
        if (title == null || title.isBlank()) {
            log.warn("Попытка создать событие с пустым названием для пользователя ID={}", userId);
            throw new IllegalArgumentException("Название события не может быть пустым");
        }
        
        // Валидация даты - событие не должно быть в прошлом
        if (eventDateTime.isBefore(LocalDateTime.now())) {
            log.warn("Попытка создать событие с датой в прошлом: {} для пользователя ID={}", 
                     eventDateTime, userId);
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
        
        // Поиск пользователя
        User user = userRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("Пользователь с ID={} не найден при создании события", userId);
                return new UserNotFoundException(userId);
            });
        
        // Проверка наличия семьи у пользователя
        if (user.getFamily() == null) {
            log.error("Пользователь ID={} не принадлежит ни одной семье", userId);
            throw new IllegalStateException("Пользователь должен принадлежать семье для создания событий");
        }
        
        // Создание события
        Event event = Event.builder()
            .user(user)
            .family(user.getFamily())
            .title(title)
            .description(description)
            .eventDate(eventDateTime.toLocalDate())
            .eventTime(eventDateTime.toLocalTime())
            .notified(false)
            .build();
        
        Event savedEvent = eventRepository.save(event);
        log.info("Событие ID={} успешно создано пользователем ID={} для семьи ID={}", 
                 savedEvent.getId(), userId, user.getFamily().getId());
        
        return savedEvent;
    }
    
    /**
     * Получает предстоящие события семьи на указанное количество дней.
     * 
     * <p>Метод возвращает все события семьи, которые запланированы в диапазоне
     * от текущей даты до указанного количества дней в будущем. События
     * автоматически сортируются по дате и времени.</p>
     * 
     * <p><b>Требования:</b> 5.1, 5.4</p>
     * 
     * @param familyId идентификатор семьи
     * @param days количество дней для поиска событий (от текущей даты)
     * @return список предстоящих событий, отсортированный по дате и времени
     * @throws IllegalArgumentException если days меньше или равно 0
     */
    @Transactional(readOnly = true)
    public List<Event> getUpcomingEvents(Long familyId, int days) {
        log.debug("Получение предстоящих событий для семьи ID={} на {} дней", familyId, days);
        
        if (days <= 0) {
            throw new IllegalArgumentException("Количество дней должно быть больше 0");
        }
        
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);
        
        List<Event> events = eventRepository.findByFamilyIdAndEventDateBetween(
            familyId, startDate, endDate);
        
        log.debug("Найдено {} предстоящих событий для семьи ID={}", events.size(), familyId);
        return events;
    }
    
    /**
     * Получает все события пользователя, отсортированные по дате.
     * 
     * <p>Метод возвращает все события, созданные указанным пользователем,
     * отсортированные по дате в порядке возрастания (от ближайших к более поздним).</p>
     * 
     * <p><b>Требования:</b> 7.1</p>
     * 
     * @param userId идентификатор пользователя
     * @return список событий пользователя, отсортированный по дате
     */
    @Transactional(readOnly = true)
    public List<Event> getUserEvents(Long userId) {
        log.debug("Получение событий пользователя ID={}", userId);
        
        List<Event> events = eventRepository.findByUserIdOrderByEventDateAsc(userId);
        
        log.debug("Найдено {} событий для пользователя ID={}", events.size(), userId);
        return events;
    }
    
    /**
     * Обновляет существующее событие.
     * 
     * <p>Метод выполняет следующие проверки перед обновлением:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа (только создатель может редактировать)</li>
     *   <li>Валидирует новую дату (не должна быть в прошлом)</li>
     *   <li>Обновляет поля события</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 7.2, 7.4, 7.5</p>
     * 
     * @param eventId идентификатор события для обновления
     * @param userId идентификатор пользователя, выполняющего обновление
     * @param title новое название события (обязательное)
     * @param description новое описание события (может быть null)
     * @param eventDateTime новая дата и время события
     * @return обновленное событие
     * @throws EventNotFoundException если событие с указанным ID не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     * @throws InvalidDateException если новая дата находится в прошлом
     * @throws IllegalArgumentException если title пустой или null
     */
    public Event updateEvent(Long eventId, Long userId, String title, 
                            String description, LocalDateTime eventDateTime) {
        log.debug("Обновление события ID={} пользователем ID={}", eventId, userId);
        
        // Валидация входных параметров
        if (title == null || title.isBlank()) {
            log.warn("Попытка обновить событие ID={} с пустым названием", eventId);
            throw new IllegalArgumentException("Название события не может быть пустым");
        }
        
        // Валидация даты
        if (eventDateTime.isBefore(LocalDateTime.now())) {
            log.warn("Попытка обновить событие ID={} с датой в прошлом: {}", eventId, eventDateTime);
            throw new InvalidDateException("Дата события не может быть в прошлом");
        }
        
        // Поиск события
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке обновления", eventId);
                return new EventNotFoundException(eventId);
            });
        
        // Проверка прав доступа - только создатель может редактировать
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался обновить чужое событие ID={} (владелец: ID={})", 
                     userId, eventId, event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его редактировать");
        }
        
        // Обновление полей
        event.setTitle(title);
        event.setDescription(description);
        event.setEventDate(eventDateTime.toLocalDate());
        event.setEventTime(eventDateTime.toLocalTime());
        
        Event updatedEvent = eventRepository.save(event);
        log.info("Событие ID={} успешно обновлено пользователем ID={}", eventId, userId);
        
        return updatedEvent;
    }
    
    /**
     * Удаляет событие из календаря.
     * 
     * <p>Метод выполняет следующие проверки перед удалением:</p>
     * <ol>
     *   <li>Проверяет существование события</li>
     *   <li>Проверяет права доступа (только создатель может удалять)</li>
     *   <li>Удаляет событие из базы данных</li>
     * </ol>
     * 
     * <p><b>Требования:</b> 7.3, 7.5</p>
     * 
     * @param eventId идентификатор события для удаления
     * @param userId идентификатор пользователя, выполняющего удаление
     * @throws EventNotFoundException если событие с указанным ID не найдено
     * @throws UnauthorizedAccessException если пользователь не является создателем события
     */
    public void deleteEvent(Long eventId, Long userId) {
        log.debug("Удаление события ID={} пользователем ID={}", eventId, userId);
        
        // Поиск события
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> {
                log.error("Событие с ID={} не найдено при попытке удаления", eventId);
                return new EventNotFoundException(eventId);
            });
        
        // Проверка прав доступа - только создатель может удалять
        if (!event.belongsToUser(userId)) {
            log.warn("Пользователь ID={} попытался удалить чужое событие ID={} (владелец: ID={})", 
                     userId, eventId, event.getUser().getId());
            throw new UnauthorizedAccessException(
                "Только создатель события может его удалить");
        }
        
        eventRepository.delete(event);
        log.info("Событие ID={} успешно удалено пользователем ID={}", eventId, userId);
    }
}
