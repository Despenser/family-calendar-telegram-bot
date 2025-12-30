package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit тесты для {@link HelpCommandHandler}.
 * 
 * <p>Проверяет корректность обработки команды /help и формирования
 * справочного сообщения со списком всех доступных команд.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HelpCommandHandler Unit Tests")
class HelpCommandHandlerTest {

    private HelpCommandHandler helpCommandHandler;

    @Test
    @DisplayName("Должен вернуть корректную команду /help")
    void shouldReturnCorrectCommand() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        // When
        String command = helpCommandHandler.getCommand();

        // Then
        assertEquals("/help", command, "Команда должна быть /help");
    }

    @Test
    @DisplayName("Должен вернуть корректное описание команды")
    void shouldReturnCorrectDescription() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        // When
        String description = helpCommandHandler.getDescription();

        // Then
        assertEquals("Показать список всех команд", description);
    }

    @Test
    @DisplayName("Команда /help не должна требовать авторизации")
    void shouldNotRequireAuth() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        // When
        boolean requiresAuth = helpCommandHandler.requiresAuth();

        // Then
        assertFalse(requiresAuth, "Команда /help не должна требовать авторизации");
    }

    @Test
    @DisplayName("Должен сформировать справку со списком команд")
    void shouldGenerateHelpMessageWithCommands() {
        // Given
        CommandHandler startHandler = createMockHandler("/start", "Начать работу с ботом");
        CommandHandler addEventHandler = createMockHandler("/add_event", "Добавить новое событие");
        CommandHandler helpHandler = createMockHandler("/help", "Показать список всех команд");

        List<CommandHandler> handlers = Arrays.asList(startHandler, addEventHandler, helpHandler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        assertNotNull(response, "Ответ не должен быть null");
        assertTrue(response.contains("Справка по командам"), "Ответ должен содержать заголовок");
        assertTrue(response.contains("/start"), "Ответ должен содержать команду /start");
        assertTrue(response.contains("/add_event"), "Ответ должен содержать команду /add_event");
        assertTrue(response.contains("/help"), "Ответ должен содержать команду /help");
        assertTrue(response.contains("Начать работу с ботом"), "Ответ должен содержать описание команды /start");
        assertTrue(response.contains("Добавить новое событие"), "Ответ должен содержать описание команды /add_event");
    }

    @Test
    @DisplayName("Должен отсортировать команды в алфавитном порядке")
    void shouldSortCommandsAlphabetically() {
        // Given
        CommandHandler zCommand = createMockHandler("/z_command", "Z команда");
        CommandHandler aCommand = createMockHandler("/a_command", "A команда");
        CommandHandler mCommand = createMockHandler("/m_command", "M команда");

        List<CommandHandler> handlers = Arrays.asList(zCommand, aCommand, mCommand);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        int aIndex = response.indexOf("/a_command");
        int mIndex = response.indexOf("/m_command");
        int zIndex = response.indexOf("/z_command");

        assertTrue(aIndex < mIndex, "/a_command должна быть перед /m_command");
        assertTrue(mIndex < zIndex, "/m_command должна быть перед /z_command");
    }

    @Test
    @DisplayName("Должен обработать пустой список команд")
    void shouldHandleEmptyCommandList() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        assertNotNull(response, "Ответ не должен быть null");
        assertTrue(response.contains("команды недоступны"), 
                "Ответ должен содержать сообщение об отсутствии команд");
    }

    @Test
    @DisplayName("Должен обработать null список команд")
    void shouldHandleNullCommandList() {
        // Given
        helpCommandHandler = new HelpCommandHandler(null);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        assertNotNull(response, "Ответ не должен быть null");
        assertTrue(response.contains("команды недоступны"), 
                "Ответ должен содержать сообщение об отсутствии команд");
    }

    @Test
    @DisplayName("Должен выбросить исключение при null сообщении")
    void shouldThrowExceptionWhenMessageIsNull() {
        // Given
        List<CommandHandler> handlers = Collections.emptyList();
        helpCommandHandler = new HelpCommandHandler(handlers);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> helpCommandHandler.handle(null, null),
                "Должно быть выброшено исключение при null сообщении"
        );

        assertEquals("Сообщение не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен использовать Markdown форматирование")
    void shouldUseMarkdownFormatting() {
        // Given
        CommandHandler handler = createMockHandler("/test", "Тестовая команда");
        List<CommandHandler> handlers = Collections.singletonList(handler);
        helpCommandHandler = new HelpCommandHandler(handlers);

        Message message = createMockMessage();

        // When
        String response = helpCommandHandler.handle(message, null);

        // Then
        assertTrue(response.contains("*"), "Ответ должен содержать Markdown форматирование (жирный текст)");
    }

    /**
     * Создает mock обработчик команды с заданной командой и описанием.
     *
     * @param command команда
     * @param description описание
     * @return mock обработчик
     */
    private CommandHandler createMockHandler(String command, String description) {
        CommandHandler handler = mock(CommandHandler.class);
        when(handler.getCommand()).thenReturn(command);
        when(handler.getDescription()).thenReturn(description);
        return handler;
    }

    /**
     * Создает mock сообщения с базовыми настройками.
     *
     * @return mock сообщение
     */
    private Message createMockMessage() {
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User telegramUser = 
                mock(org.telegram.telegrambots.meta.api.objects.User.class);
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(123456789L);
        when(telegramUser.getUserName()).thenReturn("test_user");
        
        return message;
    }
}
