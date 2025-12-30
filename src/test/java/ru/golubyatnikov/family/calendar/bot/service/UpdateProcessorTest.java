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
        when(update.getMessage()).thenReturn(message);
        when(message.getFrom()).thenReturn(user);
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
        when(update.getMessage()).thenReturn(message);
        when(message.getFrom()).thenReturn(user);
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
}
