package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.dispatcher.CallbackQueryDispatcher;
import ru.golubyatnikov.family.calendar.bot.service.dispatcher.CommandDispatcher;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.UpdateProcessor;
import ru.golubyatnikov.family.calendar.bot.service.user.UserService;
import ru.golubyatnikov.family.calendar.bot.util.TextEventParser;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для UpdateProcessor.
 * 
 * <p>Проверяет корректность асинхронной обработки обновлений от Telegram,
 * включая извлечение сообщений и делегирование обработки CommandDispatcher
 * и CallbackQueryDispatcher.</p>
 * 
 * @see UpdateProcessor
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateProcessor Unit Tests")
class UpdateProcessorTest {

    @Mock
    private CommandDispatcher commandDispatcher;

    @Mock
    private CallbackQueryDispatcher callbackQueryDispatcher;

    @Mock
    private UserService userService;

    @Mock
    private KeyboardService keyboardService;

    @Mock
    private TelegramMessageService messageService;
    
    @Mock
    private ConversationStateService conversationStateService;
    
    @Mock
    private ConversationService conversationService;
    
    @Mock
    private TextEventParser textEventParser;

    @InjectMocks
    private UpdateProcessor updateProcessor;

    private Update update;
    private Message message;
    private User user;

    @Test
    @DisplayName("Должен выбросить исключение при null обновлении")
    void shouldThrowExceptionWhenUpdateIsNull() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            updateProcessor.processUpdate(null);
        });
    }

    @Test
    @DisplayName("Должен обработать обновление с сообщением")
    void shouldProcessUpdateWithMessage() {
        // Given
        update = mock(Update.class);
        message = mock(Message.class);
        user = mock(User.class);
        
        when(update.getUpdateId()).thenReturn(12345);
        when(update.hasMessage()).thenReturn(true);
        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.getMessage()).thenReturn(message);
        when(message.getText()).thenReturn("/start");
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(123456L);
        when(keyboardService.buttonTextToCommand("/start")).thenReturn("/start");
        when(commandDispatcher.hasHandler("/start")).thenReturn(true);
        when(commandDispatcher.dispatch(any(Message.class))).thenReturn("Ответ от бота");

        // When
        updateProcessor.processUpdate(update);

        // Then
        verify(commandDispatcher, times(1)).hasHandler("/start");
        verify(commandDispatcher, times(1)).dispatch(message);
    }

    @Test
    @DisplayName("Должен пропустить обновление без сообщения")
    void shouldSkipUpdateWithoutMessage() {
        // Given
        update = mock(Update.class);
        when(update.getUpdateId()).thenReturn(12345);
        when(update.hasMessage()).thenReturn(false);

        // When
        updateProcessor.processUpdate(update);

        // Then
        verify(commandDispatcher, never()).dispatch(any(Message.class));
    }

    @Test
    @DisplayName("Должен обработать исключение от CommandDispatcher")
    void shouldHandleExceptionFromCommandDispatcher() {
        // Given
        update = mock(Update.class);
        message = mock(Message.class);
        user = mock(User.class);
        
        when(update.getUpdateId()).thenReturn(12345);
        when(update.hasMessage()).thenReturn(true);
        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.getMessage()).thenReturn(message);
        when(message.getText()).thenReturn("/start");
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(123456L);
        when(keyboardService.buttonTextToCommand("/start")).thenReturn("/start");
        when(commandDispatcher.hasHandler("/start")).thenReturn(true);
        when(commandDispatcher.dispatch(any(Message.class)))
                .thenThrow(new RuntimeException("Ошибка обработки команды"));

        // When & Then
        // Не должно выбрасывать исключение - оно должно быть залогировано
        assertDoesNotThrow(() -> {
            updateProcessor.processUpdate(update);
        });

        verify(commandDispatcher, times(1)).hasHandler("/start");
        verify(commandDispatcher, times(1)).dispatch(message);
    }

    @Test
    @DisplayName("Должен извлечь сообщение из обновления")
    void shouldExtractMessageFromUpdate() {
        // Given
        update = mock(Update.class);
        message = mock(Message.class);
        user = mock(org.telegram.telegrambots.meta.api.objects.User.class);
        
        when(update.getUpdateId()).thenReturn(12345);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getMessageId()).thenReturn(67890);
        when(message.getChatId()).thenReturn(111222333L);
        when(message.getText()).thenReturn("/start");
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(111222333L);
        when(keyboardService.buttonTextToCommand("/start")).thenReturn("/start");
        when(commandDispatcher.hasHandler("/start")).thenReturn(true);
        when(commandDispatcher.dispatch(any(Message.class))).thenReturn("Ответ");

        // When
        updateProcessor.processUpdate(update);

        // Then
        verify(commandDispatcher).hasHandler("/start");
        verify(commandDispatcher).dispatch(argThat(msg ->
                msg.getMessageId().equals(67890) &&
                msg.getChatId().equals(111222333L) &&
                msg.getText().equals("/start")
        ));
    }

    @Test
    @DisplayName("Должен преобразовать текст кнопки в команду перед обработкой")
    void shouldConvertButtonTextToCommandBeforeProcessing() {
        // Given
        update = mock(Update.class);
        message = mock(Message.class);
        user = mock(User.class);
        
        when(update.getUpdateId()).thenReturn(12345);
        when(update.hasMessage()).thenReturn(true);
        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.getMessage()).thenReturn(message);
        when(message.getMessageId()).thenReturn(67890);
        when(message.getChatId()).thenReturn(111222333L);
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(123456L);
        
        // Настраиваем последовательность вызовов getText():
        // 1. Первый вызов возвращает оригинальный текст кнопки
        // 2. После setText() последующие вызовы возвращают команду
        when(message.getText())
                .thenReturn("🗓️ Месяц")  // Первый вызов
                .thenReturn("/month");        // Последующие вызовы после setText()
        
        // Настраиваем KeyboardService для преобразования текста кнопки
        when(keyboardService.buttonTextToCommand("🗓️ Месяц"))
                .thenReturn("/month");
        
        // Мокируем userService, conversationStateService и conversationService
        when(userService.findByTelegramId(123456L)).thenReturn(Optional.empty());
        
        when(commandDispatcher.hasHandler("/month")).thenReturn(true);
        when(commandDispatcher.dispatch(any(Message.class))).thenReturn("Список событий");

        // When
        updateProcessor.processUpdate(update);

        // Then
        verify(keyboardService).buttonTextToCommand("🗓️ Месяц");
        verify(message).setText("/month");
        verify(commandDispatcher).hasHandler("/month");
        verify(commandDispatcher).dispatch(message);
    }

    @Test
    @DisplayName("Должен оставить текст без изменений, если это не кнопка")
    void shouldLeaveTextUnchangedIfNotAButton() {
        // Given
        update = mock(Update.class);
        message = mock(Message.class);
        user = mock(User.class);
        
        when(update.getUpdateId()).thenReturn(12345);
        when(update.hasMessage()).thenReturn(true);
        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.getMessage()).thenReturn(message);
        when(message.getMessageId()).thenReturn(67890);
        when(message.getChatId()).thenReturn(111222333L);
        when(message.getText()).thenReturn("/start");
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(123456L);
        
        // Настраиваем KeyboardService - текст остается без изменений
        when(keyboardService.buttonTextToCommand("/start")).thenReturn("/start");
        
        when(commandDispatcher.hasHandler("/start")).thenReturn(true);
        when(commandDispatcher.dispatch(any(Message.class))).thenReturn("Приветствие");

        // When
        updateProcessor.processUpdate(update);

        // Then
        verify(keyboardService).buttonTextToCommand("/start");
        verify(commandDispatcher).hasHandler("/start");
        verify(commandDispatcher).dispatch(argThat(msg ->
                msg.getText().equals("/start")
        ));
    }

    /**
     * Тесты для обработки callback queries.
     * 
     * <p>Проверяет делегирование callback queries в CallbackQueryDispatcher.</p>
     * 
     * _Requirements: 1.1_
     */
    @Nested
    @DisplayName("Тесты обработки Callback Queries")
    class CallbackQueryTests {

        private CallbackQuery callbackQuery;
        private Message callbackMessage;
        private User callbackUser;

        @Test
        @DisplayName("Должен делегировать callback query в CallbackQueryDispatcher")
        void shouldDelegateCallbackQueryToDispatcher() {
            // Given
            Update update = mock(Update.class);
            callbackQuery = mock(CallbackQuery.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasCallbackQuery()).thenReturn(true);
            when(update.getCallbackQuery()).thenReturn(callbackQuery);

            // When
            updateProcessor.processUpdate(update);

            // Then
            verify(update).hasCallbackQuery();
            verify(update).getCallbackQuery();
            verify(callbackQueryDispatcher).dispatch(callbackQuery);
        }

        @Test
        @DisplayName("Должен делегировать calendar_ignore callback в CallbackQueryDispatcher")
        void shouldDelegateCalendarIgnoreCallback() {
            // Given
            Update update = mock(Update.class);
            callbackQuery = mock(CallbackQuery.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasCallbackQuery()).thenReturn(true);
            when(update.getCallbackQuery()).thenReturn(callbackQuery);

            // When
            updateProcessor.processUpdate(update);

            // Then
            verify(callbackQueryDispatcher).dispatch(callbackQuery);
        }

        @Test
        @DisplayName("Должен делегировать time_ignore callback в CallbackQueryDispatcher")
        void shouldDelegateTimeIgnoreCallback() {
            // Given
            Update update = mock(Update.class);
            callbackQuery = mock(CallbackQuery.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasCallbackQuery()).thenReturn(true);
            when(update.getCallbackQuery()).thenReturn(callbackQuery);

            // When
            updateProcessor.processUpdate(update);

            // Then
            verify(callbackQueryDispatcher).dispatch(callbackQuery);
        }

        @Test
        @DisplayName("Должен делегировать date callback в CallbackQueryDispatcher")
        void shouldDelegateDateCallback() {
            // Given
            Update update = mock(Update.class);
            callbackQuery = mock(CallbackQuery.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasCallbackQuery()).thenReturn(true);
            when(update.getCallbackQuery()).thenReturn(callbackQuery);

            // When
            updateProcessor.processUpdate(update);

            // Then
            verify(callbackQueryDispatcher).dispatch(callbackQuery);
        }

        @Test
        @DisplayName("Должен делегировать unknown callback в CallbackQueryDispatcher")
        void shouldDelegateUnknownCallback() {
            // Given
            Update update = mock(Update.class);
            callbackQuery = mock(CallbackQuery.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasCallbackQuery()).thenReturn(true);
            when(update.getCallbackQuery()).thenReturn(callbackQuery);

            // When
            updateProcessor.processUpdate(update);

            // Then
            verify(callbackQueryDispatcher).dispatch(callbackQuery);
        }

        @Test
        @DisplayName("Должен обработать исключение от CallbackQueryDispatcher")
        void shouldHandleExceptionFromDispatcher() {
            // Given
            Update update = mock(Update.class);
            callbackQuery = mock(CallbackQuery.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasCallbackQuery()).thenReturn(true);
            when(update.getCallbackQuery()).thenReturn(callbackQuery);
            
            doThrow(new RuntimeException("Ошибка диспетчера"))
                .when(callbackQueryDispatcher).dispatch(any(CallbackQuery.class));

            // When & Then
            assertDoesNotThrow(() -> updateProcessor.processUpdate(update));
            verify(callbackQueryDispatcher).dispatch(callbackQuery);
        }
    }

    /**
     * Тесты для обработки сообщений.
     * 
     * <p>Проверяет различные сценарии обработки текстовых сообщений.</p>
     * 
     * _Requirements: 1.1_
     */
    @Nested
    @DisplayName("Тесты обработки сообщений")
    class MessageProcessingTests {

        @Test
        @DisplayName("Должен пропустить сообщение без текста")
        void shouldSkipMessageWithoutText() {
            // Given
            Update update = mock(Update.class);
            Message message = mock(Message.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasMessage()).thenReturn(true);
            when(update.hasCallbackQuery()).thenReturn(false);
            when(update.getMessage()).thenReturn(message);
            when(message.getText()).thenReturn(null);

            // When
            updateProcessor.processUpdate(update);

            // Then
            verify(commandDispatcher, never()).dispatch(any(Message.class));
        }

        @Test
        @DisplayName("Должен пропустить сообщение с пустым текстом")
        void shouldSkipMessageWithBlankText() {
            // Given
            Update update = mock(Update.class);
            Message message = mock(Message.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasMessage()).thenReturn(true);
            when(update.hasCallbackQuery()).thenReturn(false);
            when(update.getMessage()).thenReturn(message);
            when(message.getText()).thenReturn("   ");

            // When
            updateProcessor.processUpdate(update);

            // Then
            verify(commandDispatcher, never()).dispatch(any(Message.class));
        }

        @Test
        @DisplayName("Должен обработать сообщение с активным черновиком")
        void shouldProcessMessageWithActiveDraft() {
            // Given
            Update update = mock(Update.class);
            Message message = mock(Message.class);
            User telegramUser = mock(User.class);
            ru.golubyatnikov.family.calendar.bot.model.User dbUser = mock(ru.golubyatnikov.family.calendar.bot.model.User.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasMessage()).thenReturn(true);
            when(update.hasCallbackQuery()).thenReturn(false);
            when(update.getMessage()).thenReturn(message);
            when(message.getText()).thenReturn("Название события");
            when(message.getFrom()).thenReturn(telegramUser);
            when(message.getChatId()).thenReturn(111222333L);
            when(telegramUser.getId()).thenReturn(123456L);
            
            when(keyboardService.buttonTextToCommand("Название события")).thenReturn("Название события");
            when(userService.findByTelegramId(123456L)).thenReturn(Optional.of(dbUser));
            when(dbUser.getId()).thenReturn(1L);
            when(conversationStateService.isEditingEvent(1L)).thenReturn(false);
            when(conversationStateService.isAwaitingCompletionNote(1L)).thenReturn(false);
            when(conversationStateService.isAwaitingSearchQuery(1L)).thenReturn(false);
            when(conversationService.hasActiveDraft(1L)).thenReturn(true);

            // When
            updateProcessor.processUpdate(update);

            // Then
            // Метод может быть вызван несколько раз (для логирования и проверки условия)
            verify(conversationService, atLeastOnce()).hasActiveDraft(1L);
            // Не должен вызывать commandDispatcher, так как есть активный черновик
            verify(commandDispatcher, never()).dispatch(any(Message.class));
        }

        @Test
        @DisplayName("Должен обработать поисковый запрос")
        void shouldProcessSearchQuery() {
            // Given
            Update update = mock(Update.class);
            Message message = mock(Message.class);
            User telegramUser = mock(User.class);
            ru.golubyatnikov.family.calendar.bot.model.User dbUser = mock(ru.golubyatnikov.family.calendar.bot.model.User.class);

            when(update.getUpdateId()).thenReturn(12345);
            when(update.hasMessage()).thenReturn(true);
            when(update.hasCallbackQuery()).thenReturn(false);
            when(update.getMessage()).thenReturn(message);
            when(message.getText()).thenReturn("поисковый запрос");
            when(message.getFrom()).thenReturn(telegramUser);
            when(message.getChatId()).thenReturn(111222333L);
            when(telegramUser.getId()).thenReturn(123456L);
            
            when(keyboardService.buttonTextToCommand("поисковый запрос")).thenReturn("поисковый запрос");
            when(userService.findByTelegramId(123456L)).thenReturn(Optional.of(dbUser));
            when(dbUser.getId()).thenReturn(1L);
            when(conversationStateService.isEditingEvent(1L)).thenReturn(false);
            when(conversationStateService.isAwaitingCompletionNote(1L)).thenReturn(false);
            when(conversationStateService.isAwaitingSearchQuery(1L)).thenReturn(true);

            // When
            updateProcessor.processUpdate(update);

            // Then
            // Метод может быть вызван несколько раз (для логирования и проверки условия)
            verify(conversationStateService, atLeastOnce()).isAwaitingSearchQuery(1L);
            // Не должен вызывать commandDispatcher, так как ожидается поисковый запрос
            verify(commandDispatcher, never()).dispatch(any(Message.class));
        }
    }
}
