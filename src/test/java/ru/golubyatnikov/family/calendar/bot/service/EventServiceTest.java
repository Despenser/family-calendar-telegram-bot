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
import ru.golubyatnikov.family.calendar.bot.handler.command.PlannerCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventHistory;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.event.EventHistoryService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
    
    @Mock
    private TelegramMessageService telegramMessageService;
    
    @Mock
    private KeyboardService keyboardService;
    
    @Mock
    private BotMessageBuilder botMessageBuilder;
    
    @Mock
    private PlannerCommandHandler plannerCommandHandler;

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

        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
                familyId, startDate, endDate, Event.EventStatus.ACTIVE))
                .thenReturn(expectedEvents);

        // When
        List<Event> result = eventService.getUpcomingEvents(familyId, days, ZoneId.of("UTC"));

        // Then
        assertNotNull(result, "Результат не должен быть null");
        assertEquals(2, result.size(), "Должно быть найдено 2 события");
        assertEquals("Event 1", result.get(0).getTitle());
        assertEquals("Event 2", result.get(1).getTitle());
        verify(eventRepository).findByFamilyIdAndEventDateBetweenAndStatus(
                familyId, startDate, endDate, Event.EventStatus.ACTIVE);
    }

    @Test
    @DisplayName("Должен вернуть пустой список когда нет предстоящих событий")
    void shouldReturnEmptyListWhenNoUpcomingEvents() {
        // Given
        Long familyId = testFamily.getId();
        int days = 7;
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);

        when(eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
                familyId, startDate, endDate, Event.EventStatus.ACTIVE))
                .thenReturn(List.of());

        // When
        List<Event> result = eventService.getUpcomingEvents(familyId, days, ZoneId.of("UTC"));

        // Then
        assertNotNull(result, "Результат не должен быть null");
        assertTrue(result.isEmpty(), "Список должен быть пустым");
        verify(eventRepository).findByFamilyIdAndEventDateBetweenAndStatus(
                familyId, startDate, endDate, Event.EventStatus.ACTIVE);
    }

    @Test
    @DisplayName("Должен выбросить исключение когда количество дней меньше или равно 0")
    void shouldThrowExceptionWhenDaysIsZeroOrNegative() {
        // Given
        Long familyId = testFamily.getId();

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.getUpcomingEvents(familyId, 0, ZoneId.of("UTC")),
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
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(testUser));
        when(eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(userId, Event.EventStatus.ACTIVE))
                .thenReturn(List.of());

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
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(testUser));
        when(eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(userId, Event.EventStatus.ACTIVE))
                .thenReturn(List.of());

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
    
    // ========== Тесты для isToday и isTomorrow ==========
    
    /**
     * Создает mock User с указанной timezone для тестов.
     * 
     * @param userId ID пользователя
     * @param timezone timezone пользователя
     * @return mock User
     */
    private User createMockUser(Long userId, String timezone) {
        Family family = Family.builder().id(1L).name("Test Family").build();
        return User.builder()
                .id(userId)
                .telegramId(123456789L)
                .firstName("Тест")
                .family(family)
                .timezone(timezone)
                .build();
    }
    
    @Test
    @DisplayName("Должен корректно определить сегодняшнюю дату в timezone пользователя")
    void shouldCorrectlyIdentifyTodayInUserTimezone() {
        // Given
        User mockUser = createMockUser(1L, "Europe/Moscow");
        LocalDate today = mockUser.getCurrentDate();
        
        // When
        boolean result = eventService.isToday(today, mockUser);
        
        // Then
        assertTrue(result, "Сегодняшняя дата должна быть определена как 'сегодня'");
    }
    
    @Test
    @DisplayName("Должен корректно определить что дата не сегодня")
    void shouldCorrectlyIdentifyNotToday() {
        // Given
        User mockUser = createMockUser(1L, "Europe/Moscow");
        LocalDate today = mockUser.getCurrentDate();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate yesterday = today.minusDays(1);
        
        // When
        boolean resultTomorrow = eventService.isToday(tomorrow, mockUser);
        boolean resultYesterday = eventService.isToday(yesterday, mockUser);
        
        // Then
        assertFalse(resultTomorrow, "Завтрашняя дата не должна быть определена как 'сегодня'");
        assertFalse(resultYesterday, "Вчерашняя дата не должна быть определена как 'сегодня'");
    }
    
    @Test
    @DisplayName("Должен корректно определить завтрашнюю дату в timezone пользователя")
    void shouldCorrectlyIdentifyTomorrowInUserTimezone() {
        // Given
        User mockUser = createMockUser(1L, "Europe/Moscow");
        LocalDate today = mockUser.getCurrentDate();
        LocalDate tomorrow = today.plusDays(1);
        
        // When
        boolean result = eventService.isTomorrow(tomorrow, mockUser);
        
        // Then
        assertTrue(result, "Завтрашняя дата должна быть определена как 'завтра'");
    }
    
    @Test
    @DisplayName("Должен корректно определить что дата не завтра")
    void shouldCorrectlyIdentifyNotTomorrow() {
        // Given
        User mockUser = createMockUser(1L, "Europe/Moscow");
        LocalDate today = mockUser.getCurrentDate();
        LocalDate dayAfterTomorrow = today.plusDays(2);
        
        // When
        boolean resultToday = eventService.isTomorrow(today, mockUser);
        boolean resultDayAfterTomorrow = eventService.isTomorrow(dayAfterTomorrow, mockUser);
        
        // Then
        assertFalse(resultToday, "Сегодняшняя дата не должна быть определена как 'завтра'");
        assertFalse(resultDayAfterTomorrow, "Послезавтрашняя дата не должна быть определена как 'завтра'");
    }
    
    @Test
    @DisplayName("Должен корректно работать с разными timezone для isToday")
    void shouldCorrectlyWorkWithDifferentTimezonesForIsToday() {
        // Given - пользователь в timezone UTC+10 (Владивосток)
        User userVladivostok = createMockUser(1L, "Asia/Vladivostok");
        LocalDate todayVladivostok = userVladivostok.getCurrentDate();
        
        // Given - пользователь в timezone UTC-8 (Лос-Анджелес)
        User userLA = createMockUser(2L, "America/Los_Angeles");
        LocalDate todayLA = userLA.getCurrentDate();
        
        // When
        boolean resultVladivostok = eventService.isToday(todayVladivostok, userVladivostok);
        boolean resultLA = eventService.isToday(todayLA, userLA);
        
        // Then
        assertTrue(resultVladivostok, "Сегодняшняя дата во Владивостоке должна быть определена как 'сегодня'");
        assertTrue(resultLA, "Сегодняшняя дата в Лос-Анджелесе должна быть определена как 'сегодня'");
        
        // Проверяем, что даты могут отличаться из-за разницы во времени
        // (это нормально, так как в разных timezone может быть разная дата)
        if (!todayVladivostok.equals(todayLA)) {
            // Если даты разные, проверяем что каждый метод использует свою timezone
            assertFalse(eventService.isToday(todayVladivostok, userLA), 
                    "Дата Владивостока не должна быть 'сегодня' для пользователя из Лос-Анджелеса");
            assertFalse(eventService.isToday(todayLA, userVladivostok), 
                    "Дата Лос-Анджелеса не должна быть 'сегодня' для пользователя из Владивостока");
        }
    }
    
    @Test
    @DisplayName("Должен корректно работать с разными timezone для isTomorrow")
    void shouldCorrectlyWorkWithDifferentTimezonesForIsTomorrow() {
        // Given - пользователь в timezone UTC+3 (Москва)
        User userMoscow = createMockUser(1L, "Europe/Moscow");
        LocalDate tomorrowMoscow = userMoscow.getCurrentDate().plusDays(1);
        
        // Given - пользователь в timezone UTC+9 (Токио)
        User userTokyo = createMockUser(2L, "Asia/Tokyo");
        LocalDate tomorrowTokyo = userTokyo.getCurrentDate().plusDays(1);
        
        // When
        boolean resultMoscow = eventService.isTomorrow(tomorrowMoscow, userMoscow);
        boolean resultTokyo = eventService.isTomorrow(tomorrowTokyo, userTokyo);
        
        // Then
        assertTrue(resultMoscow, "Завтрашняя дата в Москве должна быть определена как 'завтра'");
        assertTrue(resultTokyo, "Завтрашняя дата в Токио должна быть определена как 'завтра'");
    }
    
    @Test
    @DisplayName("Должен выбросить NullPointerException при null eventDate в isToday")
    void shouldThrowNullPointerExceptionWhenEventDateIsNullInIsToday() {
        // Given
        User mockUser = createMockUser(1L, "Europe/Moscow");
        
        // When & Then
        assertThrows(NullPointerException.class, 
                () -> eventService.isToday(null, mockUser),
                "Должно быть выброшено NullPointerException при null eventDate");
    }
    
    @Test
    @DisplayName("Должен выбросить NullPointerException при null user в isToday")
    void shouldThrowNullPointerExceptionWhenUserIsNullInIsToday() {
        // Given
        LocalDate today = LocalDate.now();
        
        // When & Then
        assertThrows(NullPointerException.class, 
                () -> eventService.isToday(today, null),
                "Должно быть выброшено NullPointerException при null user");
    }
    
    @Test
    @DisplayName("Должен выбросить NullPointerException при null eventDate в isTomorrow")
    void shouldThrowNullPointerExceptionWhenEventDateIsNullInIsTomorrow() {
        // Given
        User mockUser = createMockUser(1L, "Europe/Moscow");
        
        // When & Then
        assertThrows(NullPointerException.class, 
                () -> eventService.isTomorrow(null, mockUser),
                "Должно быть выброшено NullPointerException при null eventDate");
    }
    
    @Test
    @DisplayName("Должен выбросить NullPointerException при null user в isTomorrow")
    void shouldThrowNullPointerExceptionWhenUserIsNullInIsTomorrow() {
        // Given
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        
        // When & Then
        assertThrows(NullPointerException.class, 
                () -> eventService.isTomorrow(tomorrow, null),
                "Должно быть выброшено NullPointerException при null user");
    }
}
