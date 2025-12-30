package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.UserService;

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
        assertTrue(response.contains("Добро пожаловать, Иван!"));
        assertTrue(response.contains("Вы уже зарегистрированы"));
        assertTrue(response.contains("/help"));
        assertTrue(response.contains("/add_event"));
        assertTrue(response.contains("/upcoming_events"));
        assertTrue(response.contains("/my_events"));
        
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
        assertTrue(response.contains("Добро пожаловать, Мария!"));
        assertTrue(response.contains("не зарегистрированы"));
        assertTrue(response.contains("администратору"));
        assertTrue(response.contains("Создавать события"));
        assertTrue(response.contains("Просматривать предстоящие события"));
        assertTrue(response.contains("Получать уведомления"));
        
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
        assertTrue(response.contains("Добро пожаловать!"));
        assertFalse(response.contains("null"));
        
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
        assertTrue(response.contains("Добро пожаловать!"));
        assertFalse(response.contains("   "));
        
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
}
