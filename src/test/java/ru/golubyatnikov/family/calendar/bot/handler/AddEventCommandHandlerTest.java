package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.golubyatnikov.family.calendar.bot.exception.InvalidDateException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.service.EventService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для AddEventCommandHandler.
 * 
 * <p>Проверяет корректность работы многошагового диалога создания события,
 * валидацию входных данных и интеграцию с EventService.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddEventCommandHandler Tests")
class AddEventCommandHandlerTest {

    @Mock
    private EventService eventService;

    private AddEventCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddEventCommandHandler(eventService);
    }

    @Test
    @DisplayName("Должен возвращать правильную команду")
    void shouldReturnCorrectCommand() {
        assertEquals("/add_event", handler.getCommand());
    }

    @Test
    @DisplayName("Должен возвращать правильное описание")
    void shouldReturnCorrectDescription() {
        assertEquals("Добавить новое событие в календарь", handler.getDescription());
    }

    @Test
    @DisplayName("Должен требовать авторизацию")
    void shouldRequireAuth() {
        assertTrue(handler.requiresAuth());
    }

    @Test
    @DisplayName("Должен начинать новый диалог при команде /add_event")
    void shouldStartNewConversationOnCommand() {
        // Given
        Message message = createMessage("/add_event", 123456789L);
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(123456789L, true);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Создание нового события"));
        assertTrue(response.contains("Введите дату события"));
        assertTrue(response.contains("ДД.ММ.ГГГГ"));
    }

    @Test
    @DisplayName("Должен отклонять создание события для пользователя без семьи")
    void shouldRejectEventCreationForUserWithoutFamily() {
        // Given
        Message message = createMessage("/add_event", 123456789L);
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(123456789L, false);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("не принадлежите ни одной семье"));
    }

    @Test
    @DisplayName("Должен выбрасывать исключение при null сообщении")
    void shouldThrowExceptionOnNullMessage() {
        // Given
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(123456789L, true);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> handler.handle(null, user));
    }

    @Test
    @DisplayName("Должен выбрасывать исключение при null пользователе")
    void shouldThrowExceptionOnNullUser() {
        // Given
        Message message = createMessage("/add_event", 123456789L);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> handler.handle(message, null));
    }

    @Test
    @DisplayName("Должен принять валидную дату и запросить время")
    void shouldAcceptValidDateAndRequestTime() {
        // Given
        Long telegramId = 123456789L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(telegramId, true);
        
        // Начинаем диалог
        Message startMessage = createMessage("/add_event", telegramId);
        handler.handle(startMessage, user);
        
        // Отправляем дату
        Message dateMessage = createMessage("31.12.2025", telegramId);

        // When
        String response = handler.handle(dateMessage, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Отлично"));
        assertTrue(response.contains("время события"));
        assertTrue(response.contains("ЧЧ:ММ"));
    }

    @Test
    @DisplayName("Должен отклонить дату в прошлом")
    void shouldRejectPastDate() {
        // Given
        Long telegramId = 123456789L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(telegramId, true);
        
        // Начинаем диалог
        Message startMessage = createMessage("/add_event", telegramId);
        handler.handle(startMessage, user);
        
        // Отправляем дату в прошлом
        Message dateMessage = createMessage("01.01.2020", telegramId);

        // When
        String response = handler.handle(dateMessage, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Дата не может быть в прошлом"));
        assertTrue(response.contains("дату в будущем"));
    }

    @Test
    @DisplayName("Должен отклонить неверный формат даты")
    void shouldRejectInvalidDateFormat() {
        // Given
        Long telegramId = 123456789L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(telegramId, true);
        
        // Начинаем диалог
        Message startMessage = createMessage("/add_event", telegramId);
        handler.handle(startMessage, user);
        
        // Отправляем дату в неверном формате
        Message dateMessage = createMessage("2025-12-31", telegramId);

        // When
        String response = handler.handle(dateMessage, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Неверный формат даты"));
        assertTrue(response.contains("ДД.ММ.ГГГГ"));
    }

    @Test
    @DisplayName("Должен принять валидное время и запросить название")
    void shouldAcceptValidTimeAndRequestTitle() {
        // Given
        Long telegramId = 123456789L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(telegramId, true);
        
        // Начинаем диалог
        Message startMessage = createMessage("/add_event", telegramId);
        handler.handle(startMessage, user);
        
        // Отправляем дату
        Message dateMessage = createMessage("31.12.2025", telegramId);
        handler.handle(dateMessage, user);
        
        // Отправляем время
        Message timeMessage = createMessage("18:00", telegramId);

        // When
        String response = handler.handle(timeMessage, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Отлично"));
        assertTrue(response.contains("название события"));
    }

    @Test
    @DisplayName("Должен отклонить неверный формат времени")
    void shouldRejectInvalidTimeFormat() {
        // Given
        Long telegramId = 123456789L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(telegramId, true);
        
        // Начинаем диалог
        Message startMessage = createMessage("/add_event", telegramId);
        handler.handle(startMessage, user);
        
        // Отправляем дату
        Message dateMessage = createMessage("31.12.2025", telegramId);
        handler.handle(dateMessage, user);
        
        // Отправляем время в неверном формате
        Message timeMessage = createMessage("6 PM", telegramId);

        // When
        String response = handler.handle(timeMessage, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Неверный формат времени"));
        assertTrue(response.contains("ЧЧ:ММ"));
    }

    @Test
    @DisplayName("Должен создать событие с валидными данными")
    void shouldCreateEventWithValidData() {
        // Given
        Long telegramId = 123456789L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(telegramId, true);
        
        Event createdEvent = Event.builder()
                .id(1L)
                .title("Новогодний ужин")
                .description(null)
                .eventDate(LocalDate.of(2025, 12, 31))
                .eventTime(LocalTime.of(18, 0))
                .user(user)
                .family(user.getFamily())
                .build();
        
        when(eventService.createEvent(anyLong(), anyString(), any(), any(LocalDateTime.class)))
                .thenReturn(createdEvent);
        
        // Начинаем диалог
        Message startMessage = createMessage("/add_event", telegramId);
        handler.handle(startMessage, user);
        
        // Отправляем дату
        Message dateMessage = createMessage("31.12.2025", telegramId);
        handler.handle(dateMessage, user);
        
        // Отправляем время
        Message timeMessage = createMessage("18:00", telegramId);
        handler.handle(timeMessage, user);
        
        // Отправляем название
        Message titleMessage = createMessage("Новогодний ужин", telegramId);

        // When
        String response = handler.handle(titleMessage, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Событие успешно создано"));
        assertTrue(response.contains("Новогодний ужин"));
        assertTrue(response.contains("31.12.2025"));
        assertTrue(response.contains("18:00"));
        
        verify(eventService).createEvent(eq(user.getId()), eq("Новогодний ужин"), 
                isNull(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Должен создать событие с названием и описанием")
    void shouldCreateEventWithTitleAndDescription() {
        // Given
        Long telegramId = 123456789L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(telegramId, true);
        
        Event createdEvent = Event.builder()
                .id(1L)
                .title("Новогодний ужин")
                .description("Встречаемся у бабушки")
                .eventDate(LocalDate.of(2025, 12, 31))
                .eventTime(LocalTime.of(18, 0))
                .user(user)
                .family(user.getFamily())
                .build();
        
        when(eventService.createEvent(anyLong(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(createdEvent);
        
        // Начинаем диалог
        Message startMessage = createMessage("/add_event", telegramId);
        handler.handle(startMessage, user);
        
        // Отправляем дату
        Message dateMessage = createMessage("31.12.2025", telegramId);
        handler.handle(dateMessage, user);
        
        // Отправляем время
        Message timeMessage = createMessage("18:00", telegramId);
        handler.handle(timeMessage, user);
        
        // Отправляем название с описанием
        Message titleMessage = createMessage("Новогодний ужин\nВстречаемся у бабушки", telegramId);

        // When
        String response = handler.handle(titleMessage, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Событие успешно создано"));
        assertTrue(response.contains("Новогодний ужин"));
        assertTrue(response.contains("Встречаемся у бабушки"));
        
        verify(eventService).createEvent(eq(user.getId()), eq("Новогодний ужин"), 
                eq("Встречаемся у бабушки"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Должен отклонить пустое название события")
    void shouldRejectEmptyTitle() {
        // Given
        Long telegramId = 123456789L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser(telegramId, true);
        
        // Начинаем диалог
        Message startMessage = createMessage("/add_event", telegramId);
        handler.handle(startMessage, user);
        
        // Отправляем дату
        Message dateMessage = createMessage("31.12.2025", telegramId);
        handler.handle(dateMessage, user);
        
        // Отправляем время
        Message timeMessage = createMessage("18:00", telegramId);
        handler.handle(timeMessage, user);
        
        // Отправляем пустое название
        Message titleMessage = createMessage("   ", telegramId);

        // When
        String response = handler.handle(titleMessage, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Название события не может быть пустым"));
        
        verify(eventService, never()).createEvent(anyLong(), anyString(), any(), any(LocalDateTime.class));
    }

    /**
     * Создает mock объект Message с указанным текстом и ID пользователя.
     */
    private Message createMessage(String text, Long userId) {
        Message message = mock(Message.class);
        User telegramUser = mock(User.class);
        
        lenient().when(message.getText()).thenReturn(text);
        lenient().when(message.getFrom()).thenReturn(telegramUser);
        lenient().when(telegramUser.getId()).thenReturn(userId);
        lenient().when(telegramUser.getUserName()).thenReturn("test_user");
        lenient().when(telegramUser.getFirstName()).thenReturn("Test");
        
        return message;
    }

    /**
     * Создает тестового пользователя с указанным Telegram ID.
     */
    private ru.golubyatnikov.family.calendar.bot.model.User createUser(Long telegramId, boolean withFamily) {
        ru.golubyatnikov.family.calendar.bot.model.User user = 
            ru.golubyatnikov.family.calendar.bot.model.User.builder()
                .id(1L)
                .telegramId(telegramId)
                .username("test_user")
                .firstName("Test")
                .build();
        
        if (withFamily) {
            Family family = Family.builder()
                .id(1L)
                .name("Test Family")
                .build();
            user.setFamily(family);
        }
        
        return user;
    }
}
