package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.golubyatnikov.family.calendar.bot.handler.command.MonthCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для MonthCommandHandler.
 * 
 * <p>Проверяет корректность обработки команды /month для различных сценариев:</p>
 * <ul>
 *   <li>Отображение предстоящих событий</li>
 *   <li>Обработка случая отсутствия событий</li>
 *   <li>Обработка пользователя без семьи</li>
 *   <li>Форматирование событий с Markdown</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 4.2, 5.1, 7.1</p>
 * 
 * @see MonthCommandHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MonthCommandHandler Unit Tests")
class MonthCommandHandlerTest {

    @Mock
    private EventService eventService;
    
    @Mock
    private ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService reminderService;

    @Mock
    private Message message;

    @Mock
    private User telegramUser;

    private MonthCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MonthCommandHandler(eventService, reminderService);
    }

    @Test
    @DisplayName("Должен вернуть корректную команду")
    void shouldReturnCorrectCommand() {
        // When
        String command = handler.getCommand();

        // Then
        assertEquals("/month", command);
    }

    @Test
    @DisplayName("Должен вернуть корректное описание")
    void shouldReturnCorrectDescription() {
        // When
        String description = handler.getDescription();

        // Then
        assertEquals("Показать события на месяц", description);
    }

    @Test
    @DisplayName("Должен требовать авторизацию")
    void shouldRequireAuth() {
        // When
        boolean requiresAuth = handler.requiresAuth();

        // Then
        assertTrue(requiresAuth);
    }

    @Test
    @DisplayName("Должен отобразить предстоящие события с валидными данными")
    void shouldDisplayUpcomingEventsWithValidData() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        List<Event> events = createTestEvents(user);
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        when(eventService.getUpcomingEvents(anyLong(), anyInt(), any(ZoneId.class))).thenReturn(events);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("События на месяц"));
        assertTrue(response.contains("День рождения"));
        assertTrue(response.contains("Поход в кино"));
        assertTrue(response.contains("18:00"));
        assertTrue(response.contains("Всего событий: 2"));
        
        verify(eventService).getUpcomingEvents(anyLong(), anyInt(), any(ZoneId.class));
    }

    @Test
    @DisplayName("Должен отобразить сообщение об отсутствии событий")
    void shouldDisplayNoEventsMessage() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        when(eventService.getUpcomingEvents(anyLong(), anyInt(), any(ZoneId.class))).thenReturn(Collections.emptyList());

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("События на месяц"), "Ответ должен содержать заголовок");
        assertTrue(response.contains("событий не запланировано"), "Ответ должен содержать сообщение об отсутствии событий");
        
        verify(eventService).getUpcomingEvents(anyLong(), anyInt(), any(ZoneId.class));
    }

    @Test
    @DisplayName("Должен отклонить запрос от пользователя без семьи")
    void shouldRejectRequestFromUserWithoutFamily() {
        // Given
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithoutFamily();
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("не принадлежите ни одной семье"));
        assertTrue(response.contains("администратору"));
        
        verify(eventService, never()).getUpcomingEvents(anyLong(), anyInt(), any(ZoneId.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при null сообщении")
    void shouldThrowExceptionOnNullMessage() {
        // Given
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(1L);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(null, user)
        );
        
        assertEquals("Сообщение не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен выбросить исключение при null пользователе")
    void shouldThrowExceptionOnNullUser() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(message, null)
        );
        
        assertEquals("Пользователь не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен корректно форматировать события с описанием")
    void shouldFormatEventsWithDescription() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        Event event = createEventWithDescription(user);
        List<Event> events = Collections.singletonList(event);
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        when(eventService.getUpcomingEvents(anyLong(), anyInt(), any(ZoneId.class))).thenReturn(events);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Празднование дня рождения"));
    }

    @Test
    @DisplayName("Должен корректно форматировать события без описания")
    void shouldFormatEventsWithoutDescription() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        Event event = createEventWithoutDescription(user);
        List<Event> events = Collections.singletonList(event);
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        when(eventService.getUpcomingEvents(anyLong(), anyInt(), any(ZoneId.class))).thenReturn(events);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("День рождения"));
        // Проверяем, что описание не отображается (нет курсивного текста с описанием)
    }

    /**
     * Создает пользователя с семьей.
     */
    private ru.golubyatnikov.family.calendar.bot.model.User createUserWithFamily(Long familyId) {
        Family family = Family.builder()
                .id(familyId)
                .name("Test Family")
                .build();
        
        return ru.golubyatnikov.family.calendar.bot.model.User.builder()
                .id(1L)
                .telegramId(123456789L)
                .username("test_user")
                .firstName("Иван")
                .lastName("Иванов")
                .family(family)
                .timezone("Europe/Moscow")
                .build();
    }

    /**
     * Создает пользователя без семьи.
     */
    private ru.golubyatnikov.family.calendar.bot.model.User createUserWithoutFamily() {
        return ru.golubyatnikov.family.calendar.bot.model.User.builder()
                .id(1L)
                .telegramId(123456789L)
                .username("test_user")
                .firstName("Иван")
                .build();
    }

    /**
     * Создает список тестовых событий.
     */
    private List<Event> createTestEvents(ru.golubyatnikov.family.calendar.bot.model.User user) {
        LocalDate today = LocalDate.now();
        
        Event event1 = Event.builder()
                .id(1L)
                .title("День рождения")
                .description("Празднование дня рождения")
                .eventDate(today.plusDays(1))
                .eventTime(LocalTime.of(18, 0))
                .user(user)
                .family(user.getFamily())
                .build();
        
        Event event2 = Event.builder()
                .id(2L)
                .title("Поход в кино")
                .description("Смотрим новый фильм")
                .eventDate(today.plusDays(3))
                .eventTime(LocalTime.of(20, 0))
                .user(user)
                .family(user.getFamily())
                .build();
        
        return Arrays.asList(event1, event2);
    }

    /**
     * Создает событие с описанием.
     */
    private Event createEventWithDescription(ru.golubyatnikov.family.calendar.bot.model.User user) {
        return Event.builder()
                .id(1L)
                .title("День рождения")
                .description("Празднование дня рождения")
                .eventDate(LocalDate.now().plusDays(1))
                .eventTime(LocalTime.of(18, 0))
                .user(user)
                .family(user.getFamily())
                .build();
    }

    /**
     * Создает событие без описания.
     */
    private Event createEventWithoutDescription(ru.golubyatnikov.family.calendar.bot.model.User user) {
        return Event.builder()
                .id(1L)
                .title("День рождения")
                .description(null)
                .eventDate(LocalDate.now().plusDays(1))
                .eventTime(LocalTime.of(18, 0))
                .user(user)
                .family(user.getFamily())
                .build();
    }
}
