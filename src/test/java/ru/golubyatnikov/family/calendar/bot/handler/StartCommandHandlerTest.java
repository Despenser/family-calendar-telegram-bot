package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.handler.command.StartCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.user.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для StartCommandHandler.
 * 
 * <p>Проверяет корректность обработки команды /start для различных сценариев:</p>
 * <ul>
 *   <li>Обработка команды для зарегистрированного пользователя</li>
 *   <li>Обработка команды для незарегистрированного пользователя</li>
 *   <li>Проверка метаданных команды (команда, описание, требование авторизации)</li>
 * </ul>
 * 
 * @see StartCommandHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StartCommandHandler Unit Tests")
class StartCommandHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private Message message;

    @Mock
    private org.telegram.telegrambots.meta.api.objects.User telegramUser;

    private StartCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StartCommandHandler(userService);
    }

    @Test
    @DisplayName("Должен вернуть приветствие для зарегистрированного пользователя")
    void shouldReturnWelcomeMessageForRegisteredUser() {
        // Given
        Long telegramId = 123456789L;
        String firstName = "Иван";
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(telegramId);
        when(telegramUser.getFirstName()).thenReturn(firstName);
        when(telegramUser.getUserName()).thenReturn("ivan_user");
        when(userService.isUserAuthorized(telegramId)).thenReturn(true);

        // When
        String response = handler.handle(message, null);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Добро пожаловать, Иван"), "Ответ должен содержать приветствие с именем");
        assertTrue(response.contains("Семейный Календарь Бот"), "Ответ должен содержать название бота");
        assertTrue(response.contains("Вы уже зарегистрированы"), "Ответ должен содержать подтверждение регистрации");
        assertTrue(response.contains("Основные возможности"), "Ответ должен содержать описание возможностей");
        assertTrue(response.contains("Создание и управление событиями"), "Ответ должен содержать информацию о возможностях");
        assertTrue(response.contains("/help"), "Ответ должен содержать команду /help");
        assertTrue(response.contains("/add\\_event") || response.contains("/add_event"), "Ответ должен содержать команду /add_event");
        assertTrue(response.contains("/upcoming\\_events") || response.contains("/upcoming_events"), "Ответ должен содержать команду /upcoming_events");
        assertTrue(response.contains("/my\\_events") || response.contains("/my_events"), "Ответ должен содержать команду /my_events");
        
        verify(userService).isUserAuthorized(telegramId);
    }

    @Test
    @DisplayName("Должен вернуть сообщение о регистрации для незарегистрированного пользователя")
    void shouldReturnRegistrationMessageForUnregisteredUser() {
        // Given
        Long telegramId = 987654321L;
        String firstName = "Мария";
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(telegramId);
        when(telegramUser.getFirstName()).thenReturn(firstName);
        when(telegramUser.getUserName()).thenReturn("maria_user");
        when(userService.isUserAuthorized(telegramId)).thenReturn(false);

        // When
        String response = handler.handle(message, null);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Добро пожаловать, Мария"), "Ответ должен содержать приветствие с именем");
        assertTrue(response.contains("не зарегистрированы"), "Ответ должен содержать информацию о незарегистрированном статусе");
        assertTrue(response.contains("администратору"), "Ответ должен содержать инструкцию обратиться к администратору");
        assertTrue(response.contains("Семейный Календарь Бот"), "Ответ должен содержать название бота");
        assertTrue(response.contains("Создавать и управлять событиями"), "Ответ должен содержать информацию о возможностях");
        assertTrue(response.contains("Получать напоминания"), "Ответ должен содержать информацию о возможностях");
        assertTrue(response.contains("Как получить доступ"), "Ответ должен содержать инструкции по получению доступа");
        
        verify(userService).isUserAuthorized(telegramId);
    }

    @Test
    @DisplayName("Должен обработать пользователя без firstName")
    void shouldHandleUserWithoutFirstName() {
        // Given
        Long telegramId = 111222333L;
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(telegramId);
        when(telegramUser.getFirstName()).thenReturn(null);
        when(telegramUser.getUserName()).thenReturn("user123");
        when(userService.isUserAuthorized(telegramId)).thenReturn(true);

        // When
        String response = handler.handle(message, null);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Добро пожаловать"), "Ответ должен содержать приветствие");
        assertFalse(response.contains("null"), "Ответ не должен содержать 'null'");
        
        verify(userService).isUserAuthorized(telegramId);
    }

    @Test
    @DisplayName("Должен обработать пользователя с пустым firstName")
    void shouldHandleUserWithBlankFirstName() {
        // Given
        Long telegramId = 444555666L;
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(telegramId);
        when(telegramUser.getFirstName()).thenReturn("   ");
        when(telegramUser.getUserName()).thenReturn("user456");
        when(userService.isUserAuthorized(telegramId)).thenReturn(false);

        // When
        String response = handler.handle(message, null);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Добро пожаловать"), "Ответ должен содержать приветствие");
        // Проверяем, что в ответе нет пробелов вместо имени (пробелы могут быть экранированы)
        assertFalse(response.matches(".*Добро пожаловать,\\s+\\\\?!.*"), "Ответ не должен содержать пустое имя");
        
        verify(userService).isUserAuthorized(telegramId);
    }

    @Test
    @DisplayName("Должен выбросить исключение при null сообщении")
    void shouldThrowExceptionWhenMessageIsNull() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(null, null)
        );
        
        assertEquals("Сообщение не может быть null", exception.getMessage());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("Должен вернуть корректную команду")
    void shouldReturnCorrectCommand() {
        // When
        String command = handler.getCommand();

        // Then
        assertEquals("/start", command);
    }

    @Test
    @DisplayName("Должен вернуть корректное описание")
    void shouldReturnCorrectDescription() {
        // When
        String description = handler.getDescription();

        // Then
        assertEquals("Начать работу с ботом", description);
    }

    @Test
    @DisplayName("Не должен требовать авторизации")
    void shouldNotRequireAuth() {
        // When
        boolean requiresAuth = handler.requiresAuth();

        // Then
        assertFalse(requiresAuth);
    }

    @Test
    @DisplayName("Команды в приветствии должны быть кликабельными (без моноширинного форматирования)")
    void shouldDisplayCommandsAsClickableInWelcomeMessage() {
        // Given
        Long telegramId = 123456789L;
        String firstName = "Тест";
        
        when(message.getFrom()).thenReturn(telegramUser);
        when(telegramUser.getId()).thenReturn(telegramId);
        when(telegramUser.getFirstName()).thenReturn(firstName);
        when(telegramUser.getUserName()).thenReturn("test_user");
        when(userService.isUserAuthorized(telegramId)).thenReturn(true);

        // When
        String response = handler.handle(message, null);

        // Then
        assertNotNull(response);
        
        // Проверяем, что команды присутствуют в формате /command
        assertTrue(response.contains("/help"), "Должна содержать команду /help");
        assertTrue(response.contains("/add_event") || response.contains("/add\\_event"), 
                "Должна содержать команду /add_event");
        assertTrue(response.contains("/upcoming_events") || response.contains("/upcoming\\_events"), 
                "Должна содержать команду /upcoming_events");
        assertTrue(response.contains("/my_events") || response.contains("/my\\_events"), 
                "Должна содержать команду /my_events");
        
        // Проверяем, что команды НЕ обернуты в backticks (моноширинный формат)
        assertFalse(response.contains("`/help`"), "НЕ должна содержать команду /help в backticks");
        assertFalse(response.contains("`/add_event`"), "НЕ должна содержать команду /add_event в backticks");
        assertFalse(response.contains("`/upcoming_events`"), "НЕ должна содержать команду /upcoming_events в backticks");
        assertFalse(response.contains("`/my_events`"), "НЕ должна содержать команду /my_events в backticks");
        
        // Проверяем, что команды НЕ содержат экранированные backticks
        assertFalse(response.contains("\\`/help\\`"), "НЕ должна содержать экранированные backticks для /help");
        assertFalse(response.contains("\\`/add_event\\`"), "НЕ должна содержать экранированные backticks для /add_event");
        assertFalse(response.contains("\\`/upcoming_events\\`"), "НЕ должна содержать экранированные backticks для /upcoming_events");
        assertFalse(response.contains("\\`/my_events\\`"), "НЕ должна содержать экранированные backticks для /my_events");
        
        verify(userService).isUserAuthorized(telegramId);
    }
}
