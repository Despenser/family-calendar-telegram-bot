package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для EventService.
 * 
 * <p>Проверяет корректность работы сервиса управления событиями:</p>
 * <ul>
 *   <li>Создание событий с валидацией даты</li>
 *   <li>Получение предстоящих событий семьи</li>
 *   <li>Получение событий пользователя</li>
 *   <li>Обновление событий с проверкой прав доступа</li>
 *   <li>Удаление событий с проверкой прав доступа</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 4.2, 5.1, 7.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventService Unit Tests")
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventHistoryService eventHistoryService;

    @Mock
    private ReminderService reminderService;

    @InjectMocks
    private EventService eventService;

    private User testUser;
    private User anotherUser;
    private Family testFamily;
    private Event testEvent;
    private LocalDateTime futureDateTime;

    @BeforeEach
    void setUp() {
        testFamily = Family.builder()
                .id(1L)
                .name("Test Family")
                .build();

        testUser = User.builder()
                .id(1L)
                .telegramId(123456789L)
                .username("testuser")
                .firstName("John")
                .family(testFamily)
                .build();

        anotherUser = User.builder()
                .id(2L)
                .telegramId(987654321L)
                .username("anotheruser")
                .firstName("Jane")
                .family(testFamily)
                .build();

        futureDateTime = LocalDateTime.now().plusDays(1);

        testEvent = Event.builder()
                .id(1L)
                .user(testUser)
                .family(testFamily)
                .title("Test Event")
                .description("Test Description")
                .eventDate(futureDateTime.toLocalDate())
                .eventTime(futureDateTime.toLocalTime())
                .notified(false)
                .build();
    }

    // ========== Тесты для createEvent ==========

    @Test
    @DisplayName("Должен создать событие с валидными данными")
    void shouldCreateEventWithValidData() {
        // Given
        String title = "Birthday Party";
        String description = "John's birthday celebration";
        LocalDateTime eventDateTime = LocalDateTime.now().plusDays(7);

        when(userRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testUser));
        when(eventRepository.save(any(Event.class)))
                .thenReturn(testEvent);

        // When
        Event result = eventService.createEvent(
                testUser.getId(), title, description, eventDateTime);

        // Then
        assertNotNull(result, "Созданное событие не должно быть null");
        verify(userRepository).findById(testUser.getId());
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при создании события с датой в прошлом")
    void shouldThrowExceptionWhenCreatingEventWithPastDate() {
        // Given
        String title = "Past Event";
        String description = "This should fail";
        LocalDateTime pastDateTime = LocalDateTime.now().minusDays(1);

        // When & Then
        InvalidDateException exception = assertThrows(
                InvalidDateException.class,
                () -> eventService.createEvent(
                        testUser.getId(), title, description, pastDateTime),
                "Должно быть выброшено InvalidDateException"
        );

        assertEquals("Дата события не может быть в прошлом", exception.getMessage());
        verify(userRepository, never()).findById(any());
        verify(eventRepository, never()).save(any(Event.class));
    }

    // Примечание: Тесты на пустой/null title перенесены в EventServiceBeanValidationPropertyTest,
    // так как Bean Validation требует Spring контекста с @Validated

    @Test
    @DisplayName("Должен выбросить исключение когда пользователь не найден")
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        Long nonExistentUserId = 999L;
        String title = "Test Event";
        String description = "Test Description";
        LocalDateTime eventDateTime = LocalDateTime.now().plusDays(1);

        when(userRepository.findById(nonExistentUserId))
                .thenReturn(Optional.empty());

        // When & Then
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> eventService.createEvent(
                        nonExistentUserId, title, description, eventDateTime),
                "Должно быть выброшено UserNotFoundException"
        );

        verify(userRepository).findById(nonExistentUserId);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение когда пользователь не принадлежит семье")
    void shouldThrowExceptionWhenUserHasNoFamily() {
        // Given
        User userWithoutFamily = User.builder()
                .id(3L)
                .telegramId(111222333L)
                .username("nofamily")
                .firstName("NoFamily")
                .family(null)
                .build();

        String title = "Test Event";
        String description = "Test Description";
        LocalDateTime eventDateTime = LocalDateTime.now().plusDays(1);

        when(userRepository.findById(userWithoutFamily.getId()))
                .thenReturn(Optional.of(userWithoutFamily));

        // When & Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> eventService.createEvent(
                        userWithoutFamily.getId(), title, description, eventDateTime),
                "Должно быть выброшено IllegalStateException"
        );

        assertEquals("Пользователь должен принадлежать семье для создания событий", 
                     exception.getMessage());
        verify(userRepository).findById(userWithoutFamily.getId());
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ========== Тесты для getUpcomingEvents ==========

    @Test
    @DisplayName("Должен получить предстоящие события семьи")
    void shouldGetUpcomingEvents() {
        // Given
        Long familyId = testFamily.getId();
        int days = 7;
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);

        Event event1 = Event.builder()
                .id(1L)
                .title("Event 1")
                .eventDate(startDate.plusDays(1))
                .eventTime(LocalTime.of(10, 0))
                .build();

        Event event2 = Event.builder()
                .id(2L)
                .title("Event 2")
                .eventDate(startDate.plusDays(3))
                .eventTime(LocalTime.of(14, 0))
                .build();

        List<Event> expectedEvents = Arrays.asList(event1, event2);

        when(eventRepository.findByFamilyIdAndEventDateBetween(familyId, startDate, endDate))
                .thenReturn(expectedEvents);

        // When
        List<Event> result = eventService.getUpcomingEvents(familyId, days);

        // Then
        assertNotNull(result, "Результат не должен быть null");
        assertEquals(2, result.size(), "Должно быть найдено 2 события");
        assertEquals("Event 1", result.get(0).getTitle());
        assertEquals("Event 2", result.get(1).getTitle());
        verify(eventRepository).findByFamilyIdAndEventDateBetween(familyId, startDate, endDate);
    }

    @Test
    @DisplayName("Должен вернуть пустой список когда нет предстоящих событий")
    void shouldReturnEmptyListWhenNoUpcomingEvents() {
        // Given
        Long familyId = testFamily.getId();
        int days = 7;
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);

        when(eventRepository.findByFamilyIdAndEventDateBetween(familyId, startDate, endDate))
                .thenReturn(List.of());

        // When
        List<Event> result = eventService.getUpcomingEvents(familyId, days);

        // Then
        assertNotNull(result, "Результат не должен быть null");
        assertTrue(result.isEmpty(), "Список должен быть пустым");
        verify(eventRepository).findByFamilyIdAndEventDateBetween(familyId, startDate, endDate);
    }

    @Test
    @DisplayName("Должен выбросить исключение когда количество дней меньше или равно 0")
    void shouldThrowExceptionWhenDaysIsZeroOrNegative() {
        // Given
        Long familyId = testFamily.getId();

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.getUpcomingEvents(familyId, 0),
                "Должно быть выброшено IllegalArgumentException"
        );

        assertEquals("Количество дней должно быть больше 0", exception.getMessage());
        verify(eventRepository, never()).findByFamilyIdAndEventDateBetween(any(), any(), any());
    }

    // ========== Тесты для getUserEvents ==========

    @Test
    @DisplayName("Должен получить события пользователя")
    void shouldGetUserEvents() {
        // Given
        Long userId = testUser.getId();

        Event event1 = Event.builder()
                .id(1L)
                .title("User Event 1")
                .eventDate(LocalDate.now().plusDays(1))
                .status(Event.EventStatus.ACTIVE)
                .build();

        Event event2 = Event.builder()
                .id(2L)
                .title("User Event 2")
                .eventDate(LocalDate.now().plusDays(5))
                .status(Event.EventStatus.ACTIVE)
                .build();

        List<Event> expectedEvents = Arrays.asList(event1, event2);

        when(eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(userId, Event.EventStatus.ACTIVE))
                .thenReturn(expectedEvents);

        // When
        List<Event> result = eventService.getUserEvents(userId);

        // Then
        assertNotNull(result, "Результат не должен быть null");
        assertEquals(2, result.size(), "Должно быть найдено 2 события");
        assertEquals("User Event 1", result.get(0).getTitle());
        assertEquals("User Event 2", result.get(1).getTitle());
        verify(eventRepository).findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(userId, Event.EventStatus.ACTIVE);
    }

    @Test
    @DisplayName("Должен вернуть пустой список когда у пользователя нет событий")
    void shouldReturnEmptyListWhenUserHasNoEvents() {
        // Given
        Long userId = testUser.getId();

        when(eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(userId, Event.EventStatus.ACTIVE))
                .thenReturn(List.of());

        // When
        List<Event> result = eventService.getUserEvents(userId);

        // Then
        assertNotNull(result, "Результат не должен быть null");
        assertTrue(result.isEmpty(), "Список должен быть пустым");
        verify(eventRepository).findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(userId, Event.EventStatus.ACTIVE);
    }

    // ========== Тесты для updateEvent ==========

    @Test
    @DisplayName("Должен обновить событие когда пользователь является создателем")
    void shouldUpdateEventWhenUserIsCreator() {
        // Given
        Long eventId = testEvent.getId();
        Long userId = testUser.getId();
        String newTitle = "Updated Event";
        String newDescription = "Updated Description";
        LocalDateTime newDateTime = LocalDateTime.now().plusDays(10);

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(testEvent));
        when(eventRepository.save(any(Event.class)))
                .thenReturn(testEvent);

        // When
        Event result = eventService.updateEvent(
                eventId, userId, newTitle, newDescription, newDateTime);

        // Then
        assertNotNull(result, "Обновленное событие не должно быть null");
        verify(eventRepository).findById(eventId);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при обновлении события не создателем")
    void shouldThrowExceptionWhenNonCreatorTriesToUpdate() {
        // Given
        Long eventId = testEvent.getId();
        Long unauthorizedUserId = anotherUser.getId();
        String newTitle = "Updated Event";
        String newDescription = "Updated Description";
        LocalDateTime newDateTime = LocalDateTime.now().plusDays(10);

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(testEvent));

        // When & Then
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> eventService.updateEvent(
                        eventId, unauthorizedUserId, newTitle, newDescription, newDateTime),
                "Должно быть выброшено UnauthorizedAccessException"
        );

        assertEquals("Только создатель события может его редактировать", 
                     exception.getMessage());
        verify(eventRepository).findById(eventId);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при обновлении несуществующего события")
    void shouldThrowExceptionWhenUpdatingNonExistentEvent() {
        // Given
        Long nonExistentEventId = 999L;
        Long userId = testUser.getId();
        String newTitle = "Updated Event";
        String newDescription = "Updated Description";
        LocalDateTime newDateTime = LocalDateTime.now().plusDays(10);

        when(eventRepository.findById(nonExistentEventId))
                .thenReturn(Optional.empty());

        // When & Then
        EventNotFoundException exception = assertThrows(
                EventNotFoundException.class,
                () -> eventService.updateEvent(
                        nonExistentEventId, userId, newTitle, newDescription, newDateTime),
                "Должно быть выброшено EventNotFoundException"
        );

        verify(eventRepository).findById(nonExistentEventId);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при обновлении события с датой в прошлом")
    void shouldThrowExceptionWhenUpdatingWithPastDate() {
        // Given
        Long eventId = testEvent.getId();
        Long userId = testUser.getId();
        String newTitle = "Updated Event";
        String newDescription = "Updated Description";
        LocalDateTime pastDateTime = LocalDateTime.now().minusDays(1);

        // When & Then
        InvalidDateException exception = assertThrows(
                InvalidDateException.class,
                () -> eventService.updateEvent(
                        eventId, userId, newTitle, newDescription, pastDateTime),
                "Должно быть выброшено InvalidDateException"
        );

        assertEquals("Дата события не может быть в прошлом", exception.getMessage());
        // Валидация даты происходит до поиска события, поэтому репозиторий не вызывается
        verify(eventRepository, never()).findById(any());
        verify(eventRepository, never()).save(any(Event.class));
    }

    // Примечание: Тест на пустой title при обновлении перенесен в EventServiceBeanValidationPropertyTest,
    // так как Bean Validation требует Spring контекста с @Validated

    // ========== Тесты для deleteEvent ==========

    @Test
    @DisplayName("Должен удалить событие когда пользователь является создателем")
    void shouldDeleteEventWhenUserIsCreator() {
        // Given
        Long eventId = testEvent.getId();
        Long userId = testUser.getId();

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(testEvent));
        when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

        // When
        eventService.deleteEvent(eventId, userId);

        // Then
        verify(eventRepository).findById(eventId);
        verify(eventRepository).save(any(Event.class));
        verify(eventHistoryService).recordDeletion(eq(eventId), eq(userId));
    }

    @Test
    @DisplayName("Должен выбросить исключение при удалении события не создателем")
    void shouldThrowExceptionWhenNonCreatorTriesToDelete() {
        // Given
        Long eventId = testEvent.getId();
        Long unauthorizedUserId = anotherUser.getId();

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(testEvent));

        // When & Then
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> eventService.deleteEvent(eventId, unauthorizedUserId),
                "Должно быть выброшено UnauthorizedAccessException"
        );

        assertEquals("Только создатель события может его удалить", 
                     exception.getMessage());
        verify(eventRepository).findById(eventId);
        verify(eventRepository, never()).delete(any(Event.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при удалении несуществующего события")
    void shouldThrowExceptionWhenDeletingNonExistentEvent() {
        // Given
        Long nonExistentEventId = 999L;
        Long userId = testUser.getId();

        when(eventRepository.findById(nonExistentEventId))
                .thenReturn(Optional.empty());

        // When & Then
        EventNotFoundException exception = assertThrows(
                EventNotFoundException.class,
                () -> eventService.deleteEvent(nonExistentEventId, userId),
                "Должно быть выброшено EventNotFoundException"
        );

        verify(eventRepository).findById(nonExistentEventId);
        verify(eventRepository, never()).delete(any(Event.class));
    }

    // ========== Тесты для completeEvent ==========

    @Test
    @DisplayName("Должен завершить активное событие когда пользователь является создателем")
    void shouldCompleteActiveEventWhenUserIsCreator() {
        // Given
        Long eventId = testEvent.getId();
        Long userId = testUser.getId();
        testEvent.setStatus(Event.EventStatus.ACTIVE);

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(testEvent));
        when(eventRepository.save(any(Event.class)))
                .thenReturn(testEvent);

        // When
        Event result = eventService.completeEvent(eventId, userId);

        // Then
        assertNotNull(result, "Завершенное событие не должно быть null");
        verify(eventRepository).findById(eventId);
        verify(eventRepository).save(any(Event.class));
        verify(eventHistoryService).recordChange(
                eq(eventId),
                eq(userId),
                eq(EventHistory.ActionType.UPDATED),
                eq("status"),
                eq("ACTIVE"),
                eq("COMPLETED")
        );
    }

    @Test
    @DisplayName("Должен выбросить исключение при завершении события не создателем")
    void shouldThrowExceptionWhenNonCreatorTriesToComplete() {
        // Given
        Long eventId = testEvent.getId();
        Long unauthorizedUserId = anotherUser.getId();
        testEvent.setStatus(Event.EventStatus.ACTIVE);

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(testEvent));

        // When & Then
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> eventService.completeEvent(eventId, unauthorizedUserId),
                "Должно быть выброшено UnauthorizedAccessException"
        );

        assertEquals("Только создатель события может его завершить",
                exception.getMessage());
        verify(eventRepository).findById(eventId);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при завершении несуществующего события")
    void shouldThrowExceptionWhenCompletingNonExistentEvent() {
        // Given
        Long nonExistentEventId = 999L;
        Long userId = testUser.getId();

        when(eventRepository.findById(nonExistentEventId))
                .thenReturn(Optional.empty());

        // When & Then
        EventNotFoundException exception = assertThrows(
                EventNotFoundException.class,
                () -> eventService.completeEvent(nonExistentEventId, userId),
                "Должно быть выброшено EventNotFoundException"
        );

        verify(eventRepository).findById(nonExistentEventId);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при завершении неактивного события")
    void shouldThrowExceptionWhenCompletingNonActiveEvent() {
        // Given
        Long eventId = testEvent.getId();
        Long userId = testUser.getId();
        testEvent.setStatus(Event.EventStatus.COMPLETED);

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.of(testEvent));

        // When & Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> eventService.completeEvent(eventId, userId),
                "Должно быть выброшено IllegalStateException"
        );

        assertTrue(exception.getMessage().contains("Можно завершить только активное событие"));
        verify(eventRepository).findById(eventId);
        verify(eventRepository, never()).save(any(Event.class));
    }
}
