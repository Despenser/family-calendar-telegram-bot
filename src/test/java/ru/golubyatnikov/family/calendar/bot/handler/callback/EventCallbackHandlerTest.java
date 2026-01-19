package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для EventCallbackHandler.
 * 
 * <p>Проверяет корректность обработки callback queries для операций с событиями.</p>
 * 
 * @see EventCallbackHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventCallbackHandler Unit Tests")
class EventCallbackHandlerTest {

    @Mock
    private MyEventsCommandHandler myEventsCommandHandler;

    @Mock
    private TelegramMessageService messageService;

    @Mock
    private ru.golubyatnikov.family.calendar.bot.service.ConversationStateService conversationStateService;

    @Mock
    private ru.golubyatnikov.family.calendar.bot.service.KeyboardService keyboardService;

    @Mock
    private ru.golubyatnikov.family.calendar.bot.service.EventService eventService;

    @Mock
    private ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder botMessageBuilder;

    @Mock
    private CallbackQuery callbackQuery;

    @Mock
    private Message message;

    private EventCallbackHandler handler;
    private User user;

    @BeforeEach
    void setUp() {
        handler = new EventCallbackHandler(myEventsCommandHandler, messageService, conversationStateService, keyboardService, eventService, botMessageBuilder);
        
        user = new User();
        user.setId(1L);
        user.setTelegramId(123456789L);
        user.setFirstName("Тест");
    }

    @Test
    @DisplayName("Должен вернуть корректный префикс")
    void shouldReturnCorrectPrefix() {
        assertEquals(CallbackPrefix.VIEW_EVENT, handler.getPrefix());
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом view_event_")
    void shouldHandleViewEventCallback() {
        assertTrue(handler.canHandle("view_event_123"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом edit_event_")
    void shouldHandleEditEventCallback() {
        assertTrue(handler.canHandle("edit_event_456"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом delete_event_")
    void shouldHandleDeleteEventCallback() {
        assertTrue(handler.canHandle("delete_event_789"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом edit_field_")
    void shouldHandleEditFieldCallback() {
        assertTrue(handler.canHandle("edit_field_title_123"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом complete_event_")
    void shouldHandleCompleteEventCallback() {
        assertTrue(handler.canHandle("complete_event_123"));
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
    @DisplayName("Должен корректно обработать просмотр события")
    void shouldHandleViewEvent() throws Exception {
        // Given
        String callbackData = "view_event_123";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        when(myEventsCommandHandler.handleViewEventDetails(123L, user.getId()))
                .thenReturn("Детали события");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(myEventsCommandHandler).handleViewEventDetails(123L, user.getId());
        verify(messageService).sendMessage(eq(chatId), eq("Детали события"));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("Обработано"));
    }

    @Test
    @DisplayName("Должен корректно обработать редактирование события")
    void shouldHandleEditEvent() throws Exception {
        // Given
        String callbackData = "edit_event_456";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Создаем мок события
        ru.golubyatnikov.family.calendar.bot.model.Event event = 
            new ru.golubyatnikov.family.calendar.bot.model.Event();
        event.setId(456L);
        event.setTitle("Тестовое событие");
        event.setUser(user);
        
        when(eventService.getEventById(456L)).thenReturn(event);
        when(botMessageBuilder.buildEventMessage(event)).thenReturn("Информация о событии");
        when(keyboardService.createEditFieldSelectionKeyboard(456L))
            .thenReturn(new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup());

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(eventService).getEventById(456L);
        verify(conversationStateService).startEventEditing(user.getId(), 456L, chatId, messageId);
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), any());
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq(""));
    }

    @Test
    @DisplayName("Должен корректно обработать удаление события")
    void shouldHandleDeleteEvent() throws Exception {
        // Given
        String callbackData = "delete_event_789";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Создаем мок события для возврата из deleteEvent
        ru.golubyatnikov.family.calendar.bot.model.Event event = 
            new ru.golubyatnikov.family.calendar.bot.model.Event();
        event.setId(789L);
        event.setTitle("Тестовое событие");
        event.setUser(user);
        
        // Мокируем deleteEvent - он автоматически удаляет сообщение и обновляет шапку
        when(eventService.deleteEvent(789L, user.getId())).thenReturn(event);

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(eventService).deleteEvent(789L, user.getId());
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("Событие удалено"));
    }

    @Test
    @DisplayName("Должен корректно обработать редактирование поля события")
    void shouldHandleEditField() throws Exception {
        // Given
        String callbackData = "edit_field_title_123";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(conversationStateService).startEventEditing(eq(user.getId()), eq(123L), eq(chatId), eq(messageId));
        verify(conversationStateService).setEditingField(eq(user.getId()), any());
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), any());
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq(""));
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
