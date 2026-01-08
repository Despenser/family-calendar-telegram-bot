package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для UpdateProcessor.
 * 
 * <p>Проверяет корректность асинхронной обработки обновлений от Telegram,
 * включая извлечение сообщений и делегирование обработки CommandDispatcher.</p>
 * 
 * @see UpdateProcessor
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateProcessor Unit Tests")
class UpdateProcessorTest {

    @Mock
    private CommandDispatcher commandDispatcher;

    @Mock
    private UserService userService;

    @Mock
    private KeyboardService keyboardService;

    @Mock
    private TelegramMessageService messageService;

    @Mock
    private ru.golubyatnikov.family.calendar.bot.handler.MyEventsCommandHandler myEventsCommandHandler;

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
        when(commandDispatcher.dispatch(any(Message.class))).thenReturn("Ответ от бота");

        // When
        updateProcessor.processUpdate(update);

        // Then
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
        when(commandDispatcher.dispatch(any(Message.class)))
                .thenThrow(new RuntimeException("Ошибка обработки команды"));

        // When & Then
        // Не должно выбрасывать исключение - оно должно быть залогировано
        assertDoesNotThrow(() -> {
            updateProcessor.processUpdate(update);
        });

        verify(commandDispatcher, times(1)).dispatch(message);
    }

    @Test
    @DisplayName("Должен извлечь сообщение из обновления")
    void shouldExtractMessageFromUpdate() {
        // Given
        update = mock(Update.class);
        message = mock(Message.class);
        user = mock(User.class);
        
        when(update.getUpdateId()).thenReturn(12345);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getMessageId()).thenReturn(67890);
        when(message.getChatId()).thenReturn(111222333L);
        when(message.getText()).thenReturn("/start");
        when(message.getFrom()).thenReturn(user);
        when(commandDispatcher.dispatch(any(Message.class))).thenReturn("Ответ");

        // When
        updateProcessor.processUpdate(update);

        // Then
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
        when(message.getText()).thenReturn("📅 Предстоящие события");
        when(message.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(123456L);
        
        // Настраиваем KeyboardService для преобразования текста кнопки
        when(keyboardService.buttonTextToCommand("📅 Предстоящие события"))
                .thenReturn("/upcoming_events");
        
        when(commandDispatcher.dispatch(any(Message.class))).thenReturn("Список событий");

        // When
        updateProcessor.processUpdate(update);

        // Then
        verify(keyboardService).buttonTextToCommand("📅 Предстоящие события");
        verify(message).setText("/upcoming_events");
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
        
        when(commandDispatcher.dispatch(any(Message.class))).thenReturn("Приветствие");

        // When
        updateProcessor.processUpdate(update);

        // Then
        verify(keyboardService).buttonTextToCommand("/start");
        verify(commandDispatcher).dispatch(argThat(msg ->
                msg.getText().equals("/start")
        ));
    }
}
