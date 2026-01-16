package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для TextEventCallbackHandler.
 * 
 * <p>Проверяет корректность обработки callback queries для создания событий из текста,
 * включая проверку разделения транзакций и вызовов Telegram API.</p>
 * 
 * @see TextEventCallbackHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TextEventCallbackHandler Unit Tests")
class TextEventCallbackHandlerTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private TelegramMessageService messageService;

    @Mock
    private BotMessageBuilder messageBuilder;

    @Mock
    private CallbackQuery callbackQuery;

    @Mock
    private Message message;

    private TextEventCallbackHandler handler;
    private User user;

    @BeforeEach
    void setUp() {
        handler = new TextEventCallbackHandler(conversationService, messageService, messageBuilder);
        
        user = new User();
        user.setId(1L);
        user.setTelegramId(123456789L);
        user.setFirstName("Тест");
    }

    @Test
    @DisplayName("Должен вернуть корректный префикс")
    void shouldReturnCorrectPrefix() {
        assertEquals(CallbackPrefix.CONFIRM_TEXT_EVENT, handler.getPrefix());
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом confirm_text_event:")
    void shouldHandleConfirmTextEventCallback() {
        assertTrue(handler.canHandle("confirm_text_event:encodedData"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback cancel_text_event")
    void shouldHandleCancelTextEventCallback() {
        assertTrue(handler.canHandle("cancel_text_event"));
    }

    @Test
    @DisplayName("Не должен обрабатывать неизвестный callback")
    void shouldNotHandleUnknownCallback() {
        assertFalse(handler.canHandle("unknown_callback"));
    }

    @Test
    @DisplayName("Не должен обрабатывать null callback")
    void shouldNotHandleNullCallback() {
        assertFalse(handler.canHandle(null));
    }

    @Test
    @DisplayName("Должен корректно обработать подтверждение создания события")
    void shouldHandleConfirmTextEvent() throws Exception {
        // Given
        String title = "Тестовое событие";
        LocalDate date = LocalDate.of(2026, 1, 16);
        LocalTime time = LocalTime.of(14, 30);
        String eventData = title + "|" + date + "|" + time;
        String encodedData = Base64.getEncoder().encodeToString(eventData.getBytes());
        String callbackData = "confirm_text_event:" + encodedData;
        
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        Event createdEvent = new Event();
        createdEvent.setId(1L);
        createdEvent.setTitle(title);
        createdEvent.setEventDate(date);
        createdEvent.setEventTime(time);
        
        when(conversationService.completeEventCreation(eq(user.getId()), isNull()))
                .thenReturn(createdEvent);

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(conversationService).startEventCreation(user.getId());
        verify(conversationService).updateEventDate(user.getId(), date);
        verify(conversationService).updateEventTime(user.getId(), time);
        verify(conversationService).updateEventTitle(user.getId(), title);
        verify(conversationService).completeEventCreation(user.getId(), null);
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), isNull());
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), contains("Событие создано"));
    }

    @Test
    @DisplayName("Должен корректно обработать отмену создания события")
    void shouldHandleCancelTextEvent() throws Exception {
        // Given
        String callbackData = "cancel_text_event";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        when(messageBuilder.buildEventCancelledMessage()).thenReturn("Отменено");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(messageService).editMessageText(eq(chatId), eq(messageId), eq("Отменено"), isNull());
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("Отменено"));
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("Должен очистить черновик при ошибке создания события")
    void shouldCleanupDraftOnError() throws Exception {
        // Given
        String title = "Тестовое событие";
        LocalDate date = LocalDate.of(2026, 1, 16);
        LocalTime time = LocalTime.of(14, 30);
        String eventData = title + "|" + date + "|" + time;
        String encodedData = Base64.getEncoder().encodeToString(eventData.getBytes());
        String callbackData = "confirm_text_event:" + encodedData;
        
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Симулируем ошибку при создании события
        doThrow(new RuntimeException("Ошибка БД"))
                .when(conversationService).startEventCreation(user.getId());

        // When
        handler.handle(callbackQuery, user);

        // Then
        // Проверяем, что черновик был очищен после ошибки
        verify(conversationService).cancelEventCreation(user.getId());
        // Проверяем, что сообщение об ошибке было отправлено
        verify(messageService).editMessageText(eq(chatId), eq(messageId), contains("ошибка"), isNull());
    }

    @Test
    @DisplayName("Должен обработать некорректный формат данных события")
    void shouldHandleInvalidEventDataFormat() throws Exception {
        // Given
        String invalidData = "invalid_data_without_pipes";
        String encodedData = Base64.getEncoder().encodeToString(invalidData.getBytes());
        String callbackData = "confirm_text_event:" + encodedData;
        
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);

        // When
        handler.handle(callbackQuery, user);

        // Then
        // Проверяем, что черновик был очищен
        verify(conversationService).cancelEventCreation(user.getId());
        // Проверяем, что сообщение об ошибке было отправлено
        verify(messageService).editMessageText(eq(chatId), eq(messageId), contains("ошибка"), isNull());
    }

    @Test
    @DisplayName("Транзакционный метод должен создавать событие без вызовов Telegram API")
    void transactionalMethodShouldNotCallTelegramApi() {
        // Given
        Long userId = 1L;
        String title = "Тестовое событие";
        LocalDate date = LocalDate.of(2026, 1, 16);
        LocalTime time = LocalTime.of(14, 30);
        
        Event createdEvent = new Event();
        createdEvent.setId(1L);
        createdEvent.setTitle(title);
        
        when(conversationService.completeEventCreation(eq(userId), isNull()))
                .thenReturn(createdEvent);

        // When
        Event result = handler.createEventInTransaction(userId, title, date, time);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        
        // Проверяем, что ConversationService был вызван
        verify(conversationService).startEventCreation(userId);
        verify(conversationService).updateEventDate(userId, date);
        verify(conversationService).updateEventTime(userId, time);
        verify(conversationService).updateEventTitle(userId, title);
        verify(conversationService).completeEventCreation(userId, null);
        
        // Проверяем, что Telegram API НЕ был вызван в транзакционном методе
        verifyNoInteractions(messageService);
    }

    /**
     * Настраивает моки для CallbackQuery.
     */
    private void setupCallbackQueryMocks(String callbackData, Long chatId, 
                                         Integer messageId, String callbackQueryId) {
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(callbackQuery.getId()).thenReturn(callbackQueryId);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageId()).thenReturn(messageId);
    }
}
