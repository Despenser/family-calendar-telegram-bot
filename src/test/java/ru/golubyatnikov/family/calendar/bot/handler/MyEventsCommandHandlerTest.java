package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для MyEventsCommandHandler.
 * 
 * <p>Проверяет корректность обработки команды /my_events для различных сценариев:</p>
 * <ul>
 *   <li>Отображение событий пользователя</li>
 *   <li>Обработка случая отсутствия событий</li>
 *   <li>Обработка callback для удаления события</li>
 *   <li>Обработка callback для редактирования события</li>
 *   <li>Проверка прав доступа при удалении</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 4.2, 5.1, 7.1</p>
 * 
 * @see MyEventsCommandHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MyEventsCommandHandler Unit Tests")
class MyEventsCommandHandlerTest {

    @Mock
    private EventService eventService;

    @Mock
    private KeyboardService keyboardService;

    @Mock
    private TelegramMessageService messageService;

    @Mock
    private Message message;

    @Mock
    private User telegramUser;

    private MyEventsCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MyEventsCommandHandler(eventService, keyboardService, messageService);
    }

    @Test
    @DisplayName("Должен вернуть корректную команду")
    void shouldReturnCorrectCommand() {
        // When
        String command = handler.getCommand();

        // Then
        assertEquals("/my_events", command);
    }

    @Test
    @DisplayName("Должен вернуть корректное описание")
    void shouldReturnCorrectDescription() {
        // When
        String description = handler.getDescription();

        // Then
        assertEquals("Управление моими событиями", description);
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
    @DisplayName("Должен отобразить события пользователя")
    void shouldDisplayUserEvents() throws Exception {
        // Given
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser();
        List<Event> events = createTestEvents(user);
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        when(message.getChatId()).thenReturn(123456789L);
        when(eventService.getUserEvents(user.getId())).thenReturn(events);
        when(keyboardService.createEventActionsKeyboard(anyLong())).thenReturn(new InlineKeyboardMarkup());
        doNothing().when(messageService).sendMessageWithInlineKeyboard(anyLong(), anyString(), any(InlineKeyboardMarkup.class));

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Мои события"), "Ответ должен содержать заголовок 'Мои события'");
        assertTrue(response.contains("Всего событий: 2"), "Ответ должен содержать количество событий");
        
        // Проверяем, что для каждого события был вызван метод отправки сообщения
        verify(messageService, times(2)).sendMessageWithInlineKeyboard(
                eq(123456789L), 
                anyString(), 
                any(InlineKeyboardMarkup.class)
        );
        
        verify(eventService).getUserEvents(user.getId());
    }

    @Test
    @DisplayName("Должен отобразить сообщение об отсутствии событий")
    void shouldDisplayNoEventsMessage() {
        // Given
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser();
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        when(eventService.getUserEvents(user.getId())).thenReturn(Collections.emptyList());

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("*Мои события*"), "Ответ должен содержать заголовок 'Мои события'");
        assertTrue(response.contains("У вас пока нет созданных событий"), "Ответ должен содержать сообщение об отсутствии событий");
        assertTrue(response.contains("/add\\_event") || response.contains("/add_event"), "Ответ должен содержать подсказку о команде /add_event");
        
        verify(eventService).getUserEvents(user.getId());
    }

    @Test
    @DisplayName("Должен выбросить исключение при null сообщении")
    void shouldThrowExceptionOnNullMessage() {
        // Given
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser();

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
    @DisplayName("Должен успешно удалить событие")
    void shouldSuccessfullyDeleteEvent() {
        // Given
        Long eventId = 1L;
        Long userId = 1L;
        
        doNothing().when(eventService).deleteEvent(eventId, userId);

        // When
        String response = handler.handleDeleteCallback(eventId, userId);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Событие удалено"));
        assertTrue(response.contains("успешно удалено"));
        assertTrue(response.contains("/my\\_events") || response.contains("my_events")); // Проверяем наличие команды (экранированной или нет)
        
        verify(eventService).deleteEvent(eventId, userId);
    }

    @Test
    @DisplayName("Должен обработать ошибку при удалении несуществующего события")
    void shouldHandleDeleteNonExistentEvent() {
        // Given
        Long eventId = 999L;
        Long userId = 1L;
        
        doThrow(new EventNotFoundException(eventId))
                .when(eventService).deleteEvent(eventId, userId);

        // When
        String response = handler.handleDeleteCallback(eventId, userId);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Ошибка удаления"));
        assertTrue(response.contains("Не удалось удалить событие"));
        
        verify(eventService).deleteEvent(eventId, userId);
    }

    @Test
    @DisplayName("Должен обработать ошибку при попытке удалить чужое событие")
    void shouldHandleDeleteUnauthorizedEvent() {
        // Given
        Long eventId = 1L;
        Long userId = 2L;
        
        doThrow(new UnauthorizedAccessException("User cannot delete this event"))
                .when(eventService).deleteEvent(eventId, userId);

        // When
        String response = handler.handleDeleteCallback(eventId, userId);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Ошибка удаления"));
        assertTrue(response.contains("нет прав"));
        
        verify(eventService).deleteEvent(eventId, userId);
    }

    @Test
    @DisplayName("Должен вернуть сообщение о редактировании события")
    void shouldReturnEditMessage() {
        // Given
        Long eventId = 1L;
        Long userId = 1L;

        // When
        String response = handler.handleEditCallback(eventId, userId);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Редактирование события"));
        assertTrue(response.contains("будет реализована"));
        assertTrue(response.contains("ID события: 1"));
    }

    @Test
    @DisplayName("Должен корректно форматировать события с описанием")
    void shouldFormatEventsWithDescription() throws Exception {
        // Given
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser();
        Event event = createEventWithDescription(user);
        List<Event> events = Collections.singletonList(event);
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        when(message.getChatId()).thenReturn(123456789L);
        when(eventService.getUserEvents(user.getId())).thenReturn(events);
        when(keyboardService.createEventActionsKeyboard(anyLong())).thenReturn(new InlineKeyboardMarkup());
        doNothing().when(messageService).sendMessageWithInlineKeyboard(anyLong(), anyString(), any(InlineKeyboardMarkup.class));

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        // Проверяем, что метод отправки был вызван с сообщением, содержащим описание
        verify(messageService).sendMessageWithInlineKeyboard(
                eq(123456789L), 
                argThat((String text) -> text.contains("Описание: Празднование дня рождения")), 
                any(InlineKeyboardMarkup.class)
        );
    }

    @Test
    @DisplayName("Должен корректно форматировать события без описания")
    void shouldFormatEventsWithoutDescription() {
        // Given
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser();
        Event event = createEventWithoutDescription(user);
        List<Event> events = Collections.singletonList(event);
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        when(eventService.getUserEvents(user.getId())).thenReturn(events);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertFalse(response.contains("Описание:"));
    }

    @Test
    @DisplayName("Должен создать inline клавиатуру для управления событием")
    void shouldCreateEventManagementKeyboard() {
        // Given
        Long eventId = 1L;

        // When
        var keyboard = handler.createEventManagementKeyboard(eventId);

        // Then
        assertNotNull(keyboard);
        assertNotNull(keyboard.getKeyboard());
        assertEquals(1, keyboard.getKeyboard().size());
        assertEquals(2, keyboard.getKeyboard().get(0).size());
        
        var editButton = keyboard.getKeyboard().get(0).get(0);
        var deleteButton = keyboard.getKeyboard().get(0).get(1);
        
        assertTrue(editButton.getText().contains("Редактировать"));
        assertEquals("edit_event_1", editButton.getCallbackData());
        
        assertTrue(deleteButton.getText().contains("Удалить"));
        assertEquals("delete_event_1", deleteButton.getCallbackData());
    }

    /**
     * Создает тестового пользователя.
     */
    private ru.golubyatnikov.family.calendar.bot.model.User createUser() {
        Family family = Family.builder()
                .id(1L)
                .name("Test Family")
                .build();
        
        return ru.golubyatnikov.family.calendar.bot.model.User.builder()
                .id(1L)
                .telegramId(123456789L)
                .username("test_user")
                .firstName("Иван")
                .lastName("Иванов")
                .family(family)
                .build();
    }

    /**
     * Создает список тестовых событий.
     */
    private List<Event> createTestEvents(ru.golubyatnikov.family.calendar.bot.model.User user) {
        Event event1 = Event.builder()
                .id(1L)
                .title("День рождения")
                .description("Празднование дня рождения")
                .eventDate(LocalDate.of(2025, 12, 31))
                .eventTime(LocalTime.of(18, 0))
                .user(user)
                .family(user.getFamily())
                .build();
        
        Event event2 = Event.builder()
                .id(2L)
                .title("Поход в кино")
                .description("Смотрим новый фильм")
                .eventDate(LocalDate.of(2026, 1, 2))
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
                .eventDate(LocalDate.of(2025, 12, 31))
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
                .eventDate(LocalDate.of(2025, 12, 31))
                .eventTime(LocalTime.of(18, 0))
                .user(user)
                .family(user.getFamily())
                .build();
    }
}
