package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.*;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для проверки корректного парсинга callback-данных в AttachmentCallbackHandler.
 * 
 * <p>Проверяет исправление бага с парсингом составных действий (confirm/cancel + delete).</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 4.3, 4.4</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-21
 */
@DisplayName("AttachmentCallbackHandler - Парсинг callback-данных")
class AttachmentCallbackHandlerParsingTest {

    private final TelegramMessageService messageService = mock(TelegramMessageService.class);
    private final AttachmentService attachmentService = mock(AttachmentService.class);
    private final EventService eventService = mock(EventService.class);
    private final KeyboardService keyboardService = mock(KeyboardService.class);
    private final ConversationStateService conversationStateService = mock(ConversationStateService.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final BotMessageBuilder botMessageBuilder = mock(BotMessageBuilder.class);

    private final AttachmentCallbackHandler handler = new AttachmentCallbackHandler(
            messageService,
            attachmentService,
            eventService,
            keyboardService,
            conversationStateService,
            authorizationService,
            botMessageBuilder
    );

    /**
     * Тест для проверки корректного парсинга callback-данных для отмены удаления.
     * 
     * <p>Формат: attach_file_cancel_delete_{eventId}</p>
     * <p>После извлечения префикса: cancel_delete_{eventId}</p>
     * <p>parts = ["cancel", "delete", "{eventId}"]</p>
     * 
     * <p><b>Требования:</b> 1.1, 1.2, 1.3</p>
     */
    @Test
    @DisplayName("Корректный парсинг callback-данных для отмены удаления")
    void cancelDeleteCallbackParsingIsCorrect() throws Exception {
        // Arrange
        Long eventId = 9L;
        String callbackData = "attach_file_cancel_delete_" + eventId;
        
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        Event event = createEvent(eventId, user);
        
        when(eventService.getEventById(eventId)).thenReturn(event);
        when(attachmentService.getEventAttachments(eventId)).thenReturn(Collections.emptyList());
        when(keyboardService.createAttachmentsListKeyboard(anyLong(), anyList(), anyBoolean()))
                .thenReturn(null);
        when(messageService.tryEditMessageText(anyLong(), anyInt(), anyString(), any()))
                .thenReturn(true);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        // Проверяем, что метод answerCallbackQuery был вызван с правильным сообщением
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("Удаление отменено"));
        
        // Проверяем, что eventService.getEventById был вызван с правильным eventId
        verify(eventService, atLeastOnce()).getEventById(eq(eventId));
        
        // Проверяем, что не было ошибок парсинга
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Тест для проверки корректного парсинга callback-данных для подтверждения удаления.
     * 
     * <p>Формат: attach_file_confirm_delete_{eventId}_{attachmentId}</p>
     * <p>После извлечения префикса: confirm_delete_{eventId}_{attachmentId}</p>
     * <p>parts = ["confirm", "delete", "{eventId}", "{attachmentId}"]</p>
     * 
     * <p><b>Требования:</b> 2.1, 2.2, 2.3</p>
     */
    @Test
    @DisplayName("Корректный парсинг callback-данных для подтверждения удаления")
    void confirmDeleteCallbackParsingIsCorrect() throws Exception {
        // Arrange
        Long eventId = 9L;
        Long attachmentId = 123L;
        String callbackData = "attach_file_confirm_delete_" + eventId + "_" + attachmentId;
        
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        Event event = createEvent(eventId, user);
        
        when(eventService.getEventById(eventId)).thenReturn(event);
        when(attachmentService.getEventAttachments(eventId)).thenReturn(Collections.emptyList());
        when(keyboardService.createAttachmentsListKeyboard(anyLong(), anyList(), anyBoolean()))
                .thenReturn(null);
        when(messageService.tryEditMessageText(anyLong(), anyInt(), anyString(), any()))
                .thenReturn(true);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        // Проверяем, что attachmentService.deleteAttachment был вызван с правильными параметрами
        verify(attachmentService).deleteAttachment(eq(attachmentId), eq(user.getId()));
        
        // Проверяем, что answerCallbackQuery был вызван с подтверждением
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("✅ Вложение удалено"));
        
        // Проверяем, что eventService.getEventById был вызван с правильным eventId
        verify(eventService, atLeastOnce()).getEventById(eq(eventId));
        
        // Проверяем, что не было ошибок парсинга
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Тест для проверки обратной совместимости с простыми действиями.
     * 
     * <p><b>Требования:</b> 4.3, 4.4</p>
     */
    @ParameterizedTest
    @CsvSource({
            "attach_file_list_5, 5",
            "attach_file_add_10, 10",
            "attach_file_back_15, 15"
    })
    @DisplayName("Обратная совместимость с простыми действиями")
    void simpleActionsStillWork(String callbackData, Long expectedEventId) throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        Event event = createEvent(expectedEventId, user);
        
        when(eventService.getEventById(expectedEventId)).thenReturn(event);
        when(attachmentService.getEventAttachments(expectedEventId)).thenReturn(Collections.emptyList());
        when(keyboardService.createAttachmentsListKeyboard(anyLong(), anyList(), anyBoolean()))
                .thenReturn(null);
        when(keyboardService.createEventActionsKeyboard(any(), anyLong())).thenReturn(null);
        when(messageService.tryEditMessageText(anyLong(), anyInt(), anyString(), any()))
                .thenReturn(true);
        when(botMessageBuilder.buildEventMessage(any())).thenReturn("Event message");
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        // Проверяем, что eventService.getEventById был вызван с правильным eventId
        verify(eventService, atLeastOnce()).getEventById(eq(expectedEventId));
        
        // Проверяем, что не было ошибок парсинга
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Тест для проверки обработки ошибок при некорректном формате.
     */
    @Test
    @DisplayName("Обработка ошибок при некорректном формате для cancel")
    void errorHandlingForInvalidCancelFormat() throws Exception {
        // Arrange
        String callbackData = "attach_file_cancel_remove_9"; // Некорректный subAction
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: неподдерживаемое действие"));
    }

    /**
     * Тест для проверки обработки ошибок при некорректном формате.
     */
    @Test
    @DisplayName("Обработка ошибок при некорректном формате для confirm")
    void errorHandlingForInvalidConfirmFormat() throws Exception {
        // Arrange
        String callbackData = "attach_file_confirm_remove_9_123"; // Некорректный subAction
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: неподдерживаемое действие"));
    }

    /**
     * Тест для проверки обработки ошибок при недостаточном количестве частей.
     */
    @Test
    @DisplayName("Обработка ошибок при недостаточном количестве частей для cancel")
    void errorHandlingForInsufficientPartsCancel() throws Exception {
        // Arrange
        String callbackData = "attach_file_cancel_delete"; // Нет eventId
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректный формат данных"));
    }

    /**
     * Тест для проверки обработки ошибок при недостаточном количестве частей.
     */
    @Test
    @DisplayName("Обработка ошибок при недостаточном количестве частей для confirm")
    void errorHandlingForInsufficientPartsConfirm() throws Exception {
        // Arrange
        String callbackData = "attach_file_confirm_delete_9"; // Нет attachmentId
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректный формат данных"));
    }

    /**
     * Тест для проверки корректного парсинга callback-данных для отмены добавления файла.
     * 
     * <p>Формат: attach_file_cancel_add_{eventId}</p>
     * <p>После извлечения префикса: cancel_add_{eventId}</p>
     * <p>parts = ["cancel", "add", "{eventId}"]</p>
     * 
     * <p><b>Требования:</b> 6.2, 6.3</p>
     */
    @Test
    @DisplayName("Корректный парсинг callback-данных для отмены добавления файла")
    void cancelAddFileCallbackParsingIsCorrect() throws Exception {
        // Arrange
        Long eventId = 10L;
        String callbackData = "attach_file_cancel_add_" + eventId;
        
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        Event event = createEvent(eventId, user);
        
        when(eventService.getEventById(eventId)).thenReturn(event);
        when(botMessageBuilder.buildEventMessage(any())).thenReturn("Event message");
        when(keyboardService.createEventActionsKeyboard(any(), anyLong())).thenReturn(null);
        when(messageService.tryEditMessageText(anyLong(), anyInt(), anyString(), any()))
                .thenReturn(true);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        // Проверяем, что состояние ожидания файла было очищено
        verify(conversationStateService).clearAwaitingFile(eq(user.getId()));
        
        // Проверяем, что метод answerCallbackQuery был вызван с правильным сообщением
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("Отменено"));
        
        // Проверяем, что eventService.getEventById был вызван с правильным eventId
        verify(eventService).getEventById(eq(eventId));
        
        // Проверяем, что сообщение было восстановлено к стандартному виду события
        verify(botMessageBuilder).buildEventMessage(eq(event));
        verify(keyboardService).createEventActionsKeyboard(eq(event), eq(user.getId()));
        
        // Проверяем, что не было ошибок парсинга
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Тест для проверки обработки ошибок при некорректном subAction для cancel_add.
     */
    @Test
    @DisplayName("Обработка ошибок при некорректном subAction для cancel (не add и не delete)")
    void errorHandlingForInvalidCancelAddSubAction() throws Exception {
        // Arrange
        String callbackData = "attach_file_cancel_upload_10"; // Некорректный subAction
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: неподдерживаемое действие"));
    }

    /**
     * Тест для проверки обработки ошибок при недостаточном количестве частей для cancel_add.
     */
    @Test
    @DisplayName("Обработка ошибок при недостаточном количестве частей для cancel_add")
    void errorHandlingForInsufficientPartsCancelAdd() throws Exception {
        // Arrange
        String callbackData = "attach_file_cancel_add"; // Нет eventId
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректный формат данных"));
    }

    /**
     * Тест для проверки обработки ошибок парсинга eventId для cancel_add.
     */
    @Test
    @DisplayName("Обработка ошибок парсинга eventId для cancel_add")
    void errorHandlingForInvalidEventIdCancelAdd() throws Exception {
        // Arrange
        String callbackData = "attach_file_cancel_add_abc"; // Некорректный eventId
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректный формат ID"));
    }

    // Вспомогательные методы

    private CallbackQuery createCallbackQuery(String callbackData) {
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(callbackQuery.getId()).thenReturn("callback-123");
        when(message.getChatId()).thenReturn(100L);
        when(message.getMessageId()).thenReturn(200);
        
        return callbackQuery;
    }

    private User createUser(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setTelegramId(userId);
        user.setUsername("testuser");
        return user;
    }

    private Event createEvent(Long eventId, User user) {
        Event event = new Event();
        event.setId(eventId);
        event.setUser(user);
        event.setTitle("Test Event");
        event.setEventDate(LocalDateTime.now().toLocalDate());
        event.setEventTime(LocalDateTime.now().toLocalTime());
        return event;
    }
}
