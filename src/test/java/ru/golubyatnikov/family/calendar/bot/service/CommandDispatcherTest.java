package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.handler.CommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для CommandDispatcher.
 * 
 * <p>Проверяет корректность работы диспетчера команд:</p>
 * <ul>
 *   <li>Регистрация обработчиков команд</li>
 *   <li>Маршрутизация команд к правильным обработчикам</li>
 *   <li>Проверка авторизации пользователей</li>
 *   <li>Обработка неизвестных команд</li>
 *   <li>Обработка некорректных входных данных</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.2</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CommandDispatcher Unit Tests")
class CommandDispatcherTest {

    @Mock
    private UserService userService;

    @Mock
    private CommandHandler startHandler;

    @Mock
    private CommandHandler helpHandler;

    @Mock
    private CommandHandler addEventHandler;

    @Mock
    private Message message;

    @Mock
    private org.telegram.telegrambots.meta.api.objects.User telegramUser;

    @Mock
    private Chat chat;

    private CommandDispatcher commandDispatcher;
    private User testUser;
    private Family testFamily;
    private Long testTelegramId;

    @BeforeEach
    void setUp() {
        testTelegramId = 123456789L;
        
        testFamily = Family.builder()
                .id(1L)
                .name("Test Family")
                .build();

        testUser = User.builder()
                .id(1L)
                .telegramId(testTelegramId)
                .username("testuser")
                .firstName("John")
                .family(testFamily)
                .build();

        // Настройка моков обработчиков
        when(startHandler.getCommand()).thenReturn("/start");
        when(startHandler.getDescription()).thenReturn("Начать работу с ботом");
        when(startHandler.requiresAuth()).thenReturn(false);

        when(helpHandler.getCommand()).thenReturn("/help");
        when(helpHandler.getDescription()).thenReturn("Показать список команд");
        when(helpHandler.requiresAuth()).thenReturn(false);

        when(addEventHandler.getCommand()).thenReturn("/add_event");
        when(addEventHandler.getDescription()).thenReturn("Добавить событие");
        when(addEventHandler.requiresAuth()).thenReturn(true);

        // Создание диспетчера с моками обработчиков
        List<CommandHandler> handlers = Arrays.asList(startHandler, helpHandler, addEventHandler);
        commandDispatcher = new CommandDispatcher(handlers, userService);

        // Настройка мока сообщения
        when(message.getFrom()).thenReturn(telegramUser);
        when(message.getChat()).thenReturn(chat);
        when(message.getChatId()).thenReturn(123L);
        when(message.getMessageId()).thenReturn(456);
        when(telegramUser.getId()).thenReturn(testTelegramId);
    }

    // ========== Тесты регистрации обработчиков ==========

    @Test
    @DisplayName("Должен зарегистрировать все обработчики команд")
    void shouldRegisterAllCommandHandlers() {
        // Then
        assertEquals(3, commandDispatcher.getRegisteredHandlersCount(),
                "Должно быть зарегистрировано 3 обработчика");
        assertTrue(commandDispatcher.hasHandler("/start"),
                "Обработчик /start должен быть зарегистрирован");
        assertTrue(commandDispatcher.hasHandler("/help"),
                "Обработчик /help должен быть зарегистрирован");
        assertTrue(commandDispatcher.hasHandler("/add_event"),
                "Обработчик /add_event должен быть зарегистрирован");
    }

    @Test
    @DisplayName("Должен корректно обработать пустой список обработчиков")
    void shouldHandleEmptyHandlersList() {
        // Given
        List<CommandHandler> emptyHandlers = List.of();

        // When
        CommandDispatcher emptyDispatcher = new CommandDispatcher(emptyHandlers, userService);

        // Then
        assertEquals(0, emptyDispatcher.getRegisteredHandlersCount(),
                "Количество обработчиков должно быть 0");
    }

    @Test
    @DisplayName("Должен заменить дублирующийся обработчик")
    void shouldReplaceDuplicateHandler() {
        // Given
        CommandHandler duplicateHandler = mock(CommandHandler.class);
        when(duplicateHandler.getCommand()).thenReturn("/start");
        when(duplicateHandler.getDescription()).thenReturn("Дубликат");
        when(duplicateHandler.requiresAuth()).thenReturn(false);

        List<CommandHandler> handlersWithDuplicate = Arrays.asList(
                startHandler, helpHandler, duplicateHandler);

        // When
        CommandDispatcher dispatcherWithDuplicate = 
                new CommandDispatcher(handlersWithDuplicate, userService);

        // Then
        assertEquals(2, dispatcherWithDuplicate.getRegisteredHandlersCount(),
                "Должно быть зарегистрировано 2 уникальных команды");
        assertTrue(dispatcherWithDuplicate.hasHandler("/start"),
                "Обработчик /start должен быть зарегистрирован");
    }

    // ========== Тесты маршрутизации команд ==========

    @Test
    @DisplayName("Должен маршрутизировать команду к правильному обработчику")
    void shouldRouteCommandToCorrectHandler() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/start");
        when(startHandler.handle(any(Message.class), any()))
                .thenReturn("Добро пожаловать!");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertEquals("Добро пожаловать!", response);
        verify(startHandler).handle(message, null);
        verify(helpHandler, never()).handle(any(), any());
        verify(addEventHandler, never()).handle(any(), any());
    }

    @Test
    @DisplayName("Должен маршрутизировать команду с параметрами")
    void shouldRouteCommandWithParameters() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/add_event Встреча с друзьями");
        when(userService.findByTelegramId(testTelegramId))
                .thenReturn(Optional.of(testUser));
        when(addEventHandler.handle(any(Message.class), any(User.class)))
                .thenReturn("Событие создано");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertEquals("Событие создано", response);
        verify(addEventHandler).handle(message, testUser);
        verify(userService).findByTelegramId(testTelegramId);
    }

    @Test
    @DisplayName("Должен игнорировать регистр команды")
    void shouldIgnoreCommandCase() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/START");
        when(startHandler.handle(any(Message.class), any()))
                .thenReturn("Добро пожаловать!");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertEquals("Добро пожаловать!", response);
        verify(startHandler).handle(message, null);
    }

    @Test
    @DisplayName("Должен обработать команду с пробелами")
    void shouldHandleCommandWithSpaces() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("  /help  ");
        when(helpHandler.handle(any(Message.class), any()))
                .thenReturn("Список команд");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertEquals("Список команд", response);
        verify(helpHandler).handle(message, null);
    }

    @Test
    @DisplayName("Должен вернуть сообщение об ошибке для неизвестной команды")
    void shouldReturnErrorMessageForUnknownCommand() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/unknown");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertTrue(response.contains("Неизвестная команда"),
                "Ответ должен содержать сообщение о неизвестной команде");
        assertTrue(response.contains("/unknown"),
                "Ответ должен содержать название неизвестной команды");
        assertTrue(response.contains("/help"),
                "Ответ должен предлагать использовать /help");
        verify(startHandler, never()).handle(any(), any());
        verify(helpHandler, never()).handle(any(), any());
        verify(addEventHandler, never()).handle(any(), any());
    }

    // ========== Тесты проверки авторизации ==========

    @Test
    @DisplayName("Должен проверить авторизацию для команды, требующей авторизации")
    void shouldCheckAuthorizationForProtectedCommand() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/add_event");
        when(userService.findByTelegramId(testTelegramId))
                .thenReturn(Optional.of(testUser));
        when(addEventHandler.handle(any(Message.class), any(User.class)))
                .thenReturn("Событие создано");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertEquals("Событие создано", response);
        verify(userService).findByTelegramId(testTelegramId);
        verify(addEventHandler).handle(message, testUser);
    }

    @Test
    @DisplayName("Должен выбросить исключение для неавторизованного пользователя")
    void shouldThrowExceptionForUnauthorizedUser() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/add_event");
        when(userService.findByTelegramId(testTelegramId))
                .thenReturn(Optional.empty());

        // When & Then
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> commandDispatcher.dispatch(message),
                "Должно быть выброшено UnauthorizedAccessException"
        );

        assertTrue(exception.getMessage().contains("/add_event"),
                "Сообщение об ошибке должно содержать название команды");
        assertTrue(exception.getMessage().contains("авторизации"),
                "Сообщение об ошибке должно упоминать авторизацию");
        verify(userService).findByTelegramId(testTelegramId);
        verify(addEventHandler, never()).handle(any(), any());
    }

    @Test
    @DisplayName("Не должен проверять авторизацию для команды, не требующей авторизации")
    void shouldNotCheckAuthorizationForPublicCommand() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/start");
        when(userService.findByTelegramId(testTelegramId))
                .thenReturn(Optional.empty());
        when(startHandler.handle(any(Message.class), any()))
                .thenReturn("Добро пожаловать!");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertEquals("Добро пожаловать!", response);
        // Пользователь загружается всегда, но для публичных команд отсутствие пользователя не вызывает ошибку
        verify(userService).findByTelegramId(testTelegramId);
        verify(startHandler).handle(message, null);
    }

    // ========== Тесты обработки некорректных входных данных ==========

    @Test
    @DisplayName("Должен выбросить исключение при null сообщении")
    void shouldThrowExceptionWhenMessageIsNull() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> commandDispatcher.dispatch(null),
                "Должно быть выброшено IllegalArgumentException"
        );

        assertEquals("Сообщение не может быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен вернуть сообщение об ошибке для сообщения без текста")
    void shouldReturnErrorMessageForMessageWithoutText() {
        // Given
        when(message.hasText()).thenReturn(false);

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertTrue(response.contains("текстовую команду"),
                "Ответ должен просить отправить текстовую команду");
        assertTrue(response.contains("/help"),
                "Ответ должен предлагать использовать /help");
        verify(startHandler, never()).handle(any(), any());
        verify(helpHandler, never()).handle(any(), any());
        verify(addEventHandler, never()).handle(any(), any());
    }

    @Test
    @DisplayName("Должен вернуть сообщение об ошибке для текста без команды")
    void shouldReturnErrorMessageForTextWithoutCommand() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("Привет, бот!");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertTrue(response.contains("должна начинаться с символа '/'"),
                "Ответ должен объяснять формат команды");
        assertTrue(response.contains("/help"),
                "Ответ должен предлагать использовать /help");
        verify(startHandler, never()).handle(any(), any());
        verify(helpHandler, never()).handle(any(), any());
        verify(addEventHandler, never()).handle(any(), any());
    }

    @Test
    @DisplayName("Должен обработать пустой текст сообщения")
    void shouldHandleEmptyMessageText() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertTrue(response.contains("должна начинаться с символа '/'"),
                "Ответ должен объяснять формат команды");
    }

    @Test
    @DisplayName("Должен обработать текст с только пробелами")
    void shouldHandleWhitespaceOnlyText() {
        // Given
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("   ");

        // When
        String response = commandDispatcher.dispatch(message);

        // Then
        assertTrue(response.contains("должна начинаться с символа '/'"),
                "Ответ должен объяснять формат команды");
    }

    // ========== Тесты вспомогательных методов ==========

    @Test
    @DisplayName("Должен корректно определить наличие обработчика")
    void shouldCorrectlyCheckHandlerExistence() {
        // Then
        assertTrue(commandDispatcher.hasHandler("/start"));
        assertTrue(commandDispatcher.hasHandler("/help"));
        assertTrue(commandDispatcher.hasHandler("/add_event"));
        assertFalse(commandDispatcher.hasHandler("/unknown"));
        assertFalse(commandDispatcher.hasHandler(null));
    }

    @Test
    @DisplayName("Должен вернуть правильное количество зарегистрированных обработчиков")
    void shouldReturnCorrectHandlersCount() {
        // Then
        assertEquals(3, commandDispatcher.getRegisteredHandlersCount());
    }
}
