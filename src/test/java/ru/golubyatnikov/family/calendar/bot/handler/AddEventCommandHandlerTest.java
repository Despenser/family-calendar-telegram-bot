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
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.service.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для {@link AddEventCommandHandler}.
 * 
 * <p>Проверяет корректность работы команды создания события через inline-календарь,
 * валидацию входных данных и интеграцию с ConversationService.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddEventCommandHandler Unit Tests")
class AddEventCommandHandlerTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private KeyboardService keyboardService;

    @Mock
    private TelegramMessageService messageService;

    private AddEventCommandHandler handler;
    private ru.golubyatnikov.family.calendar.bot.model.User testUser;
    private Family testFamily;
    private Event testDraft;

    @BeforeEach
    void setUp() {
        handler = new AddEventCommandHandler(conversationService, keyboardService, messageService);

        // Создаем тестовую семью
        testFamily = Family.builder()
                .id(1L)
                .name("Test Family")
                .build();

        // Создаем тестового пользователя
        testUser = ru.golubyatnikov.family.calendar.bot.model.User.builder()
                .id(1L)
                .telegramId(123456789L)
                .username("test_user")
                .firstName("Test")
                .family(testFamily)
                .build();

        // Создаем тестовый черновик
        testDraft = Event.builder()
                .id(1L)
                .user(testUser)
                .family(testFamily)
                .status(Event.EventStatus.DRAFT)
                .notified(false)
                .build();
    }

    // ========== Тесты метаданных команды ==========

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

    // ========== Тесты валидации параметров ==========

    @Test
    @DisplayName("Должен выбрасывать исключение при null сообщении")
    void shouldThrowExceptionOnNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> 
                handler.handle(null, testUser));
    }

    @Test
    @DisplayName("Должен выбрасывать исключение при null пользователе")
    void shouldThrowExceptionOnNullUser() {
        // Given
        Message message = createMessage("/add_event", 123456789L, 123L);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
                handler.handle(message, null));
    }

    // ========== Тесты создания события ==========

    @Test
    @DisplayName("Должен создать черновик и отправить inline-календарь")
    void shouldCreateDraftAndSendCalendar() throws TelegramApiException {
        // Given
        Message message = createMessage("/add_event", testUser.getTelegramId(), 123L);
        InlineKeyboardMarkup calendar = mock(InlineKeyboardMarkup.class);
        
        when(conversationService.startEventCreation(testUser.getId())).thenReturn(testDraft);
        when(keyboardService.createEventTypeSelectionKeyboard())
                .thenReturn(calendar);

        // When
        String response = handler.handle(message, testUser);

        // Then
        assertNull(response); // Ответ отправляется через TelegramMessageService
        verify(conversationService).startEventCreation(testUser.getId());
        verify(keyboardService).createEventTypeSelectionKeyboard();
        verify(messageService).sendMessageWithInlineKeyboard(eq(123L), 
                contains("Создание нового события"), eq(calendar));
    }

    @Test
    @DisplayName("Должен отклонить создание события для пользователя без семьи")
    void shouldRejectEventCreationForUserWithoutFamily() {
        // Given
        Message message = createMessage("/add_event", testUser.getTelegramId(), 123L);
        testUser.setFamily(null);

        // When
        String response = handler.handle(message, testUser);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("не принадлежите ни одной семье"));
        verify(conversationService, never()).startEventCreation(anyLong());
        verifyNoInteractions(keyboardService);
        verifyNoInteractions(messageService);
    }

    @Test
    @DisplayName("Должен обработать ошибку при создании черновика")
    void shouldHandleErrorWhenCreatingDraft() {
        // Given
        Message message = createMessage("/add_event", testUser.getTelegramId(), 123L);
        
        when(conversationService.startEventCreation(testUser.getId()))
                .thenThrow(new RuntimeException("Database error"));

        // When
        String response = handler.handle(message, testUser);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Произошла ошибка"));
        assertTrue(response.contains("Database error"));
        verify(conversationService).startEventCreation(testUser.getId());
        verifyNoInteractions(messageService);
    }

    @Test
    @DisplayName("Должен обработать ошибку при отправке клавиатуры выбора типа")
    void shouldHandleErrorWhenSendingCalendar() throws TelegramApiException {
        // Given
        Message message = createMessage("/add_event", testUser.getTelegramId(), 123L);
        InlineKeyboardMarkup typeKeyboard = mock(InlineKeyboardMarkup.class);
        
        when(conversationService.startEventCreation(testUser.getId())).thenReturn(testDraft);
        when(keyboardService.createEventTypeSelectionKeyboard())
                .thenReturn(typeKeyboard);
        doThrow(new TelegramApiException("Network error"))
                .when(messageService).sendMessageWithInlineKeyboard(anyLong(), anyString(), any());

        // When
        String response = handler.handle(message, testUser);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Произошла ошибка"));
        assertTrue(response.contains("Network error"));
        verify(conversationService).startEventCreation(testUser.getId());
        verify(messageService).sendMessageWithInlineKeyboard(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("Должен показать выбор типа события")
    void shouldUseCurrentMonthForCalendar() throws TelegramApiException {
        // Given
        Message message = createMessage("/add_event", testUser.getTelegramId(), 123L);
        InlineKeyboardMarkup typeKeyboard = mock(InlineKeyboardMarkup.class);
        
        when(conversationService.startEventCreation(testUser.getId())).thenReturn(testDraft);
        when(keyboardService.createEventTypeSelectionKeyboard()).thenReturn(typeKeyboard);

        // When
        handler.handle(message, testUser);

        // Then
        verify(keyboardService).createEventTypeSelectionKeyboard();
    }

    @Test
    @DisplayName("Должен отправить сообщение с правильным текстом")
    void shouldSendMessageWithCorrectText() throws TelegramApiException {
        // Given
        Message message = createMessage("/add_event", testUser.getTelegramId(), 123L);
        InlineKeyboardMarkup typeKeyboard = mock(InlineKeyboardMarkup.class);
        
        when(conversationService.startEventCreation(testUser.getId())).thenReturn(testDraft);
        when(keyboardService.createEventTypeSelectionKeyboard())
                .thenReturn(typeKeyboard);

        // When
        handler.handle(message, testUser);

        // Then
        verify(messageService).sendMessageWithInlineKeyboard(
                eq(123L),
                eq("📅 Создание нового события\n\nВыберите тип события:"),
                eq(typeKeyboard)
        );
    }

    /**
     * Создает mock объект Message с указанным текстом, ID пользователя и ID чата.
     */
    private Message createMessage(String text, Long userId, Long chatId) {
        Message message = mock(Message.class);
        User telegramUser = mock(User.class);
        
        lenient().when(message.getText()).thenReturn(text);
        lenient().when(message.getChatId()).thenReturn(chatId);
        lenient().when(message.getFrom()).thenReturn(telegramUser);
        lenient().when(telegramUser.getId()).thenReturn(userId);
        lenient().when(telegramUser.getUserName()).thenReturn("test_user");
        lenient().when(telegramUser.getFirstName()).thenReturn("Test");
        
        return message;
    }
}
