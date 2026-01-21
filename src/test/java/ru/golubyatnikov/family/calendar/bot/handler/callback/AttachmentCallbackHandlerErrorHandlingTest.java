package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.*;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для проверки улучшенной обработки ошибок в AttachmentCallbackHandler.
 * 
 * <p>Проверяет обработку граничных случаев и ошибок парсинга.</p>
 * 
 * <p><b>Требования:</b> 3.1, 3.2, 3.3, 3.4</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-21
 */
@DisplayName("AttachmentCallbackHandler - Обработка ошибок")
class AttachmentCallbackHandlerErrorHandlingTest {

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
     * Тест для проверки обработки null callback-данных.
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
     */
    @Test
    @DisplayName("Обработка null callback-данных")
    void handleNullCallbackData() throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery(null);
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректные данные"));
        verify(eventService, never()).getEventById(anyLong());
        verify(attachmentService, never()).deleteAttachment(anyLong(), anyLong());
    }

    /**
     * Тест для проверки обработки пустых callback-данных.
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
     */
    @Test
    @DisplayName("Обработка пустых callback-данных")
    void handleEmptyCallbackData() throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery("");
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректные данные"));
        verify(eventService, never()).getEventById(anyLong());
        verify(attachmentService, never()).deleteAttachment(anyLong(), anyLong());
    }

    /**
     * Тест для проверки обработки callback-данных только с префиксом.
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
     */
    @Test
    @DisplayName("Обработка callback-данных только с префиксом")
    void handleCallbackDataWithOnlyPrefix() throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery("attach_file_");
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректный формат данных"));
        verify(eventService, never()).getEventById(anyLong());
        verify(attachmentService, never()).deleteAttachment(anyLong(), anyLong());
    }

    /**
     * Тест для проверки обработки callback-данных с недостаточным количеством частей.
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
     */
    @Test
    @DisplayName("Обработка callback-данных с недостаточным количеством частей")
    void handleCallbackDataWithInsufficientParts() throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery("attach_file_list");
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректный формат данных"));
        verify(eventService, never()).getEventById(anyLong());
    }

    /**
     * Тест для проверки обработки невалидного формата числа для eventId.
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
     */
    @Test
    @DisplayName("Обработка невалидного формата числа для eventId")
    void handleInvalidNumberFormatForEventId() throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery("attach_file_list_abc");
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректный формат ID"));
        verify(eventService, never()).getEventById(anyLong());
    }

    /**
     * Тест для проверки обработки невалидного формата числа для attachmentId.
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
     */
    @Test
    @DisplayName("Обработка невалидного формата числа для attachmentId")
    void handleInvalidNumberFormatForAttachmentId() throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery("attach_file_view_5_xyz");
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: некорректный формат ID"));
        verify(eventService, never()).getEventById(anyLong());
        verify(attachmentService, never()).getAttachment(anyLong());
    }

    /**
     * Тест для проверки обработки неизвестного действия.
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
     */
    @Test
    @DisplayName("Обработка неизвестного действия")
    void handleUnknownAction() throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery("attach_file_unknown_5");
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Неизвестное действие"));
        verify(eventService, never()).getEventById(anyLong());
    }

    /**
     * Тест для проверки обработки недостаточного количества частей для действия view.
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
     */
    @Test
    @DisplayName("Обработка недостаточного количества частей для действия view")
    void handleInsufficientPartsForViewAction() throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery("attach_file_view_5");
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: не указан ID вложения"));
        verify(eventService, never()).getEventById(anyLong());
        verify(attachmentService, never()).getAttachment(anyLong());
    }

    /**
     * Тест для проверки обработки недостаточного количества частей для действия delete.
     * 
     * <p><b>Требования:</b> 3.3, 3.4</p>
     */
    @Test
    @DisplayName("Обработка недостаточного количества частей для действия delete")
    void handleInsufficientPartsForDeleteAction() throws Exception {
        // Arrange
        CallbackQuery callbackQuery = createCallbackQuery("attach_file_delete_5");
        User user = createUser(1L);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq("❌ Ошибка: не указан ID вложения"));
        verify(eventService, never()).getEventById(anyLong());
        verify(attachmentService, never()).getAttachment(anyLong());
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
}
