package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для DateTimeCallbackHandler.
 * 
 * <p>Проверяет корректность обработки callback queries для выбора даты и времени.</p>
 * 
 * @see DateTimeCallbackHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DateTimeCallbackHandler Unit Tests")
class DateTimeCallbackHandlerTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private TelegramMessageService messageService;

    @Mock
    private KeyboardService keyboardService;

    @Mock
    private BotMessageBuilder messageBuilder;
    
    @Mock
    private ConversationStateService conversationStateService;
    
    @Mock
    private EventService eventService;

    @Mock
    private CallbackQuery callbackQuery;

    @Mock
    private Message message;

    @Mock
    private org.telegram.telegrambots.meta.api.objects.User telegramUser;

    private DateTimeCallbackHandler handler;
    private User user;

    @BeforeEach
    void setUp() {
        handler = new DateTimeCallbackHandler(conversationService, messageService, 
                keyboardService, messageBuilder, conversationStateService, eventService);
        
        user = new User();
        user.setId(1L);
        user.setTelegramId(123456789L);
        user.setFirstName("Тест");
    }

    @Test
    @DisplayName("Должен вернуть корректный префикс")
    void shouldReturnCorrectPrefix() {
        assertEquals(CallbackPrefix.DATE, handler.getPrefix());
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом date_")
    void shouldHandleDateCallback() {
        assertTrue(handler.canHandle("date_2026-01-16"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом hour_")
    void shouldHandleHourCallback() {
        assertTrue(handler.canHandle("hour_14"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом time_ и минутами")
    void shouldHandleTimeWithMinutesCallback() {
        assertTrue(handler.canHandle("time_14:30"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback time_back")
    void shouldHandleTimeBackCallback() {
        assertTrue(handler.canHandle("time_back"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback time_cancel")
    void shouldHandleTimeCancelCallback() {
        assertTrue(handler.canHandle("time_cancel"));
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
    @DisplayName("Должен корректно обработать выбор даты")
    void shouldHandleDateSelection() throws Exception {
        // Given
        String callbackData = "date_2026-01-16";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим создания нового события (не редактирование)
        when(conversationStateService.isEditingEvent(user.getId())).thenReturn(false);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        when(keyboardService.createHourSelectionKeyboard()).thenReturn(keyboard);
        when(messageBuilder.buildDateSelectedMessage(anyString())).thenReturn("Дата выбрана");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(conversationService).updateEventDate(eq(user.getId()), eq(LocalDate.of(2026, 1, 16)));
        verify(keyboardService).createHourSelectionKeyboard();
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), eq(keyboard));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("Дата выбрана"));
    }

    @Test
    @DisplayName("Должен корректно обработать выбор часа")
    void shouldHandleHourSelection() throws Exception {
        // Given
        String callbackData = "hour_14";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        when(keyboardService.createMinuteSelectionKeyboard(14)).thenReturn(keyboard);
        when(messageBuilder.buildHourSelectedMessage(14)).thenReturn("Час выбран");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(keyboardService).createMinuteSelectionKeyboard(14);
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), eq(keyboard));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("Час выбран"));
    }

    @Test
    @DisplayName("Должен корректно обработать выбор времени")
    void shouldHandleTimeSelection() throws Exception {
        // Given
        String callbackData = "time_14:30";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим создания нового события (не редактирование)
        when(conversationStateService.isEditingEvent(user.getId())).thenReturn(false);
        
        when(messageBuilder.buildTimeSelectedMessage(anyString())).thenReturn("Время выбрано");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(conversationService).updateEventTime(eq(user.getId()), eq(LocalTime.of(14, 30)));
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), isNull());
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("Время выбрано"));
    }

    @Test
    @DisplayName("Должен корректно обработать возврат к выбору часа")
    void shouldHandleTimeBack() throws Exception {
        // Given
        String callbackData = "time_back";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        when(keyboardService.createHourSelectionKeyboard()).thenReturn(keyboard);
        when(messageBuilder.buildSelectHourMessage()).thenReturn("Выберите час");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(keyboardService).createHourSelectionKeyboard();
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), eq(keyboard));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq(""));
    }

    @Test
    @DisplayName("Должен корректно обработать отмену выбора времени")
    void shouldHandleTimeCancel() throws Exception {
        // Given
        String callbackData = "time_cancel";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        when(messageBuilder.buildEventCancelledMessage()).thenReturn("Отменено");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(conversationService).cancelEventCreation(user.getId());
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), isNull());
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("Отменено"));
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
