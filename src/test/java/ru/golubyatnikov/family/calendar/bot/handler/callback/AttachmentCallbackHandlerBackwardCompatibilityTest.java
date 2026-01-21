package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.*;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Регрессионные тесты для проверки обратной совместимости AttachmentCallbackHandler.
 * 
 * <p>Проверяет, что исправление бага с парсингом составных действий (confirm/cancel + delete)
 * не нарушило работу простых действий (list, add, view, delete, back).</p>
 * 
 * <p><b>Требования:</b> 4.1, 4.2, 4.3, 4.4</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-21
 */
@DisplayName("AttachmentCallbackHandler - Регрессионные тесты обратной совместимости")
class AttachmentCallbackHandlerBackwardCompatibilityTest {

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
     * Тест для проверки корректного парсинга простого действия "list".
     * 
     * <p>Формат: attach_file_list_{eventId}</p>
     * <p>После извлечения префикса: list_{eventId}</p>
     * <p>parts = ["list", "{eventId}"]</p>
     * 
     * <p><b>Требования:</b> 4.1, 4.3, 4.4</p>
     */
    @Test
    @DisplayName("Простое действие 'list' работает корректно")
    void simpleActionListWorksCorrectly() throws Exception {
        // Arrange
        Long eventId = 5L;
        String callbackData = "attach_file_list_" + eventId;
        
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
        verify(eventService, atLeastOnce()).getEventById(eq(eventId));
        verify(attachmentService).getEventAttachments(eq(eventId));
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq(""));
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Тест для проверки корректного парсинга простого действия "add".
     * 
     * <p>Формат: attach_file_add_{eventId}</p>
     * <p>После извлечения префикса: add_{eventId}</p>
     * <p>parts = ["add", "{eventId}"]</p>
     * 
     * <p><b>Требования:</b> 4.1, 4.3, 4.4</p>
     */
    @Test
    @DisplayName("Простое действие 'add' работает корректно")
    void simpleActionAddWorksCorrectly() throws Exception {
        // Arrange
        Long eventId = 10L;
        String callbackData = "attach_file_add_" + eventId;
        
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        Event event = createEvent(eventId, user);
        
        when(eventService.getEventById(eventId)).thenReturn(event);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(eventService).getEventById(eq(eventId));
        verify(conversationStateService).saveAttachmentMessageId(eq(user.getId()), eq(eventId), anyLong(), anyInt());
        verify(conversationStateService).setAwaitingFile(eq(user.getId()), eq(eventId), anyLong(), anyInt());
        verify(messageService).sendMessage(anyLong(), anyString());
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq(""));
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Тест для проверки корректного парсинга простого действия "view".
     * 
     * <p>Формат: attach_file_view_{eventId}_{attachmentId}</p>
     * <p>После извлечения префикса: view_{eventId}_{attachmentId}</p>
     * <p>parts = ["view", "{eventId}", "{attachmentId}"]</p>
     * 
     * <p><b>Требования:</b> 4.1, 4.3, 4.4</p>
     */
    @Test
    @DisplayName("Простое действие 'view' работает корректно")
    void simpleActionViewWorksCorrectly() throws Exception {
        // Arrange
        Long eventId = 7L;
        Long attachmentId = 42L;
        String callbackData = "attach_file_view_" + eventId + "_" + attachmentId;
        
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        Attachment attachment = createAttachment(attachmentId, eventId);
        
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachment);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(attachmentService).getAttachment(eq(attachmentId));
        verify(messageService).sendFileWithKeyboard(anyLong(), anyString(), anyString(), anyString(), any());
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq(""));
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Тест для проверки корректного парсинга простого действия "delete".
     * 
     * <p>Формат: attach_file_delete_{eventId}_{attachmentId}</p>
     * <p>После извлечения префикса: delete_{eventId}_{attachmentId}</p>
     * <p>parts = ["delete", "{eventId}", "{attachmentId}"]</p>
     * 
     * <p><b>Требования:</b> 4.1, 4.3, 4.4</p>
     */
    @Test
    @DisplayName("Простое действие 'delete' работает корректно")
    void simpleActionDeleteWorksCorrectly() throws Exception {
        // Arrange
        Long eventId = 8L;
        Long attachmentId = 55L;
        String callbackData = "attach_file_delete_" + eventId + "_" + attachmentId;
        
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        Event event = createEvent(eventId, user);
        Attachment attachment = createAttachment(attachmentId, eventId);
        
        when(eventService.getEventById(eventId)).thenReturn(event);
        when(attachmentService.getAttachment(attachmentId)).thenReturn(attachment);
        when(keyboardService.createDeleteAttachmentConfirmationKeyboard(anyLong(), anyLong()))
                .thenReturn(null);
        when(messageService.tryEditMessageText(anyLong(), anyInt(), anyString(), any()))
                .thenReturn(true);
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(eventService).getEventById(eq(eventId));
        verify(attachmentService).getAttachment(eq(attachmentId));
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq(""));
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Тест для проверки корректного парсинга простого действия "back".
     * 
     * <p>Формат: attach_file_back_{eventId}</p>
     * <p>После извлечения префикса: back_{eventId}</p>
     * <p>parts = ["back", "{eventId}"]</p>
     * 
     * <p><b>Требования:</b> 4.1, 4.3, 4.4</p>
     */
    @Test
    @DisplayName("Простое действие 'back' работает корректно")
    void simpleActionBackWorksCorrectly() throws Exception {
        // Arrange
        Long eventId = 15L;
        String callbackData = "attach_file_back_" + eventId;
        
        CallbackQuery callbackQuery = createCallbackQuery(callbackData);
        User user = createUser(1L);
        Event event = createEvent(eventId, user);
        
        when(eventService.getEventById(eventId)).thenReturn(event);
        when(keyboardService.createEventActionsKeyboard(any(), anyLong())).thenReturn(null);
        when(botMessageBuilder.buildEventMessage(any())).thenReturn("Event message");
        
        // Act
        handler.handle(callbackQuery, user);
        
        // Assert
        verify(eventService).getEventById(eq(eventId));
        verify(botMessageBuilder).buildEventMessage(eq(event));
        verify(messageService).editMessageText(anyLong(), anyInt(), anyString(), any());
        verify(conversationStateService).clearAttachmentMessageContext(eq(user.getId()));
        verify(messageService).answerCallbackQuery(eq("callback-123"), eq(""));
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Параметризованный тест для проверки всех простых действий.
     * 
     * <p><b>Требования:</b> 4.1, 4.3, 4.4</p>
     */
    @ParameterizedTest
    @CsvSource({
            "attach_file_list_5, 5",
            "attach_file_add_10, 10",
            "attach_file_back_15, 15"
    })
    @DisplayName("Все простые действия парсятся корректно")
    void allSimpleActionsParseCorrectly(String callbackData, Long expectedEventId) throws Exception {
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
        verify(eventService, atLeastOnce()).getEventById(eq(expectedEventId));
        verify(messageService, never()).answerCallbackQuery(anyString(), contains("❌ Ошибка"));
    }

    /**
     * Тест для проверки формата callback-данных, генерируемых KeyboardService.
     * 
     * <p>Проверяет, что KeyboardService генерирует callback-данные в ожидаемом формате
     * для списка вложений.</p>
     * 
     * <p><b>Требования:</b> 4.2</p>
     */
    @Test
    @DisplayName("KeyboardService генерирует корректный формат callback-данных для списка вложений")
    void keyboardServiceGeneratesCorrectCallbackDataForAttachmentsList() {
        // Arrange
        Long eventId = 123L;
        List<Attachment> attachments = new ArrayList<>();
        attachments.add(createAttachment(1L, eventId));
        attachments.add(createAttachment(2L, eventId));
        
        // Создаем реальный экземпляр KeyboardService для этого теста
        KeyboardService realKeyboardService = new KeyboardService(null, null);
        
        // Act
        InlineKeyboardMarkup keyboard = realKeyboardService.createAttachmentsListKeyboard(eventId, attachments, true);
        
        // Assert
        assertNotNull(keyboard);
        assertNotNull(keyboard.getKeyboard());
        assertFalse(keyboard.getKeyboard().isEmpty());
        
        // Проверяем кнопки просмотра вложений
        List<InlineKeyboardButton> firstRow = keyboard.getKeyboard().get(0);
        InlineKeyboardButton viewBtn = firstRow.get(0);
        assertEquals("attach_file_view_123_1", viewBtn.getCallbackData(), 
                "Callback data для просмотра должен быть 'attach_file_view_{eventId}_{attachmentId}'");
        
        // Проверяем кнопку удаления
        InlineKeyboardButton deleteBtn = firstRow.get(1);
        assertEquals("attach_file_delete_123_1", deleteBtn.getCallbackData(), 
                "Callback data для удаления должен быть 'attach_file_delete_{eventId}_{attachmentId}'");
        
        // Проверяем кнопку добавления файла
        List<InlineKeyboardButton> addRow = keyboard.getKeyboard().get(2);
        InlineKeyboardButton addBtn = addRow.get(0);
        assertEquals("attach_file_add_123", addBtn.getCallbackData(), 
                "Callback data для добавления должен быть 'attach_file_add_{eventId}'");
        
        // Проверяем кнопку "Назад к событию"
        List<InlineKeyboardButton> backRow = keyboard.getKeyboard().get(3);
        InlineKeyboardButton backBtn = backRow.get(0);
        assertEquals("attach_file_back_123", backBtn.getCallbackData(), 
                "Callback data для возврата должен быть 'attach_file_back_{eventId}'");
    }

    /**
     * Тест для проверки формата callback-данных подтверждения удаления.
     * 
     * <p><b>Требования:</b> 4.2</p>
     */
    @Test
    @DisplayName("KeyboardService генерирует корректный формат callback-данных для подтверждения удаления")
    void keyboardServiceGeneratesCorrectCallbackDataForDeleteConfirmation() {
        // Arrange
        Long eventId = 456L;
        Long attachmentId = 789L;
        
        // Создаем реальный экземпляр KeyboardService для этого теста
        KeyboardService realKeyboardService = new KeyboardService(null, null);
        
        // Act
        InlineKeyboardMarkup keyboard = realKeyboardService.createDeleteAttachmentConfirmationKeyboard(eventId, attachmentId);
        
        // Assert
        assertNotNull(keyboard);
        assertNotNull(keyboard.getKeyboard());
        assertEquals(1, keyboard.getKeyboard().size());
        
        List<InlineKeyboardButton> row = keyboard.getKeyboard().get(0);
        assertEquals(2, row.size());
        
        // Проверяем кнопку подтверждения
        InlineKeyboardButton confirmBtn = row.get(0);
        assertEquals("attach_file_confirm_delete_456_789", confirmBtn.getCallbackData(), 
                "Callback data для подтверждения должен быть 'attach_file_confirm_delete_{eventId}_{attachmentId}'");
        
        // Проверяем кнопку отмены
        InlineKeyboardButton cancelBtn = row.get(1);
        assertEquals("attach_file_cancel_delete_456", cancelBtn.getCallbackData(), 
                "Callback data для отмены должен быть 'attach_file_cancel_delete_{eventId}'");
    }

    /**
     * Тест для проверки формата callback-данных просмотра файла.
     * 
     * <p><b>Требования:</b> 4.2</p>
     */
    @Test
    @DisplayName("KeyboardService генерирует корректный формат callback-данных для просмотра файла")
    void keyboardServiceGeneratesCorrectCallbackDataForFileView() {
        // Arrange
        Long eventId = 999L;
        
        // Создаем реальный экземпляр KeyboardService для этого теста
        KeyboardService realKeyboardService = new KeyboardService(null, null);
        
        // Act
        InlineKeyboardMarkup keyboard = realKeyboardService.createFileViewKeyboard(eventId);
        
        // Assert
        assertNotNull(keyboard);
        assertNotNull(keyboard.getKeyboard());
        assertEquals(1, keyboard.getKeyboard().size());
        
        List<InlineKeyboardButton> row = keyboard.getKeyboard().get(0);
        assertEquals(1, row.size());
        
        InlineKeyboardButton backBtn = row.get(0);
        assertEquals("attach_file_list_999", backBtn.getCallbackData(), 
                "Callback data для возврата к вложениям должен быть 'attach_file_list_{eventId}'");
    }

    /**
     * Тест для проверки, что формат callback-данных не изменился после исправления.
     * 
     * <p><b>Требования:</b> 4.2</p>
     */
    @Test
    @DisplayName("Формат callback-данных в KeyboardService не изменился")
    void callbackDataFormatRemainsUnchanged() {
        // Arrange
        Long eventId = 100L;
        Long attachmentId = 200L;
        
        // Создаем реальный экземпляр KeyboardService для этого теста
        KeyboardService realKeyboardService = new KeyboardService(null, null);
        
        // Act & Assert - проверяем все форматы
        
        // Список вложений
        InlineKeyboardMarkup listKeyboard = realKeyboardService.createAttachmentsListKeyboard(
                eventId, Collections.emptyList(), true);
        String addCallbackData = listKeyboard.getKeyboard().get(0).get(0).getCallbackData();
        assertTrue(addCallbackData.matches("attach_file_add_\\d+"), 
                "Формат 'add' должен быть 'attach_file_add_{eventId}'");
        
        // Подтверждение удаления
        InlineKeyboardMarkup confirmKeyboard = realKeyboardService.createDeleteAttachmentConfirmationKeyboard(
                eventId, attachmentId);
        String confirmCallbackData = confirmKeyboard.getKeyboard().get(0).get(0).getCallbackData();
        String cancelCallbackData = confirmKeyboard.getKeyboard().get(0).get(1).getCallbackData();
        
        assertTrue(confirmCallbackData.matches("attach_file_confirm_delete_\\d+_\\d+"), 
                "Формат 'confirm' должен быть 'attach_file_confirm_delete_{eventId}_{attachmentId}'");
        assertTrue(cancelCallbackData.matches("attach_file_cancel_delete_\\d+"), 
                "Формат 'cancel' должен быть 'attach_file_cancel_delete_{eventId}'");
        
        // Просмотр файла
        InlineKeyboardMarkup viewKeyboard = realKeyboardService.createFileViewKeyboard(eventId);
        String backCallbackData = viewKeyboard.getKeyboard().get(0).get(0).getCallbackData();
        assertTrue(backCallbackData.matches("attach_file_list_\\d+"), 
                "Формат 'list' должен быть 'attach_file_list_{eventId}'");
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

    private Attachment createAttachment(Long attachmentId, Long eventId) {
        Attachment attachment = new Attachment();
        attachment.setId(attachmentId);
        attachment.setFileName("test-file.txt");
        attachment.setFileId("file-id-" + attachmentId);
        attachment.setFileType("document");
        attachment.setFileSize(1024L);
        attachment.setUploadedAt(LocalDateTime.now());
        return attachment;
    }
}
