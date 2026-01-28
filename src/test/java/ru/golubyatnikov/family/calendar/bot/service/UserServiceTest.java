package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для UserService.
 * 
 * <p>Проверяет корректность работы сервиса управления пользователями:</p>
 * <ul>
 *   <li>Поиск пользователя по Telegram ID</li>
 *   <li>Создание нового пользователя</li>
 *   <li>Проверка авторизации пользователя</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 3.1, 3.2</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

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
                .lastName("Doe")
                .family(testFamily)
                .build();
    }

    // ========== Тесты для findByTelegramId ==========

    @Test
    @DisplayName("Должен найти пользователя по Telegram ID, когда пользователь существует")
    void shouldFindUserByTelegramIdWhenUserExists() {
        // Given
        when(userRepository.findByTelegramId(testTelegramId))
                .thenReturn(Optional.of(testUser));

        // When
        Optional<User> result = userService.findByTelegramId(testTelegramId);

        // Then
        assertTrue(result.isPresent(), "Пользователь должен быть найден");
        assertEquals(testTelegramId, result.get().getTelegramId());
        assertEquals("testuser", result.get().getUsername());
        assertEquals("John", result.get().getFirstName());
        verify(userRepository).findByTelegramId(testTelegramId);
    }

    @Test
    @DisplayName("Должен вернуть пустой Optional, когда пользователь не найден")
    void shouldReturnEmptyOptionalWhenUserNotFound() {
        // Given
        Long nonExistentTelegramId = 999999999L;
        when(userRepository.findByTelegramId(nonExistentTelegramId))
                .thenReturn(Optional.empty());

        // When
        Optional<User> result = userService.findByTelegramId(nonExistentTelegramId);

        // Then
        assertTrue(result.isEmpty(), "Optional должен быть пустым");
        verify(userRepository).findByTelegramId(nonExistentTelegramId);
    }

    // ========== Тесты для createUser ==========

    @Test
    @DisplayName("Должен создать нового пользователя с валидными данными")
    void shouldCreateUserWithValidData() {
        // Given
        Long newTelegramId = 987654321L;
        String username = "newuser";
        String firstName = "Jane";
        
        User newUser = User.builder()
                .id(2L)
                .telegramId(newTelegramId)
                .username(username)
                .firstName(firstName)
                .family(testFamily)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // When
        User result = userService.createUser(newTelegramId, username, firstName, testFamily, null);

        // Then
        assertNotNull(result, "Созданный пользователь не должен быть null");
        assertEquals(newTelegramId, result.getTelegramId());
        assertEquals(username, result.getUsername());
        assertEquals(firstName, result.getFirstName());
        assertEquals(testFamily, result.getFamily());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Должен создать пользователя без семьи")
    void shouldCreateUserWithoutFamily() {
        // Given
        Long newTelegramId = 111222333L;
        String username = "solo_user";
        String firstName = "Solo";
        
        User newUser = User.builder()
                .id(3L)
                .telegramId(newTelegramId)
                .username(username)
                .firstName(firstName)
                .family(null)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // When
        User result = userService.createUser(newTelegramId, username, firstName, null, null);

        // Then
        assertNotNull(result);
        assertEquals(newTelegramId, result.getTelegramId());
        assertNull(result.getFamily(), "Семья должна быть null");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Должен создать пользователя без username")
    void shouldCreateUserWithoutUsername() {
        // Given
        Long newTelegramId = 444555666L;
        String firstName = "NoUsername";
        
        User newUser = User.builder()
                .id(4L)
                .telegramId(newTelegramId)
                .username(null)
                .firstName(firstName)
                .family(testFamily)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // When
        User result = userService.createUser(newTelegramId, null, firstName, testFamily, null);

        // Then
        assertNotNull(result);
        assertEquals(newTelegramId, result.getTelegramId());
        assertNull(result.getUsername(), "Username должен быть null");
        assertEquals(firstName, result.getFirstName());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при null Telegram ID")
    void shouldThrowExceptionWhenTelegramIdIsNull() {
        // Given
        String username = "testuser";
        String firstName = "Test";

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(null, username, firstName, testFamily, null),
                "Должно быть выброшено IllegalArgumentException"
        );

        assertEquals("Telegram ID не может быть null", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при null firstName")
    void shouldThrowExceptionWhenFirstNameIsNull() {
        // Given
        Long newTelegramId = 777888999L;
        String username = "testuser";

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(newTelegramId, username, null, testFamily, null),
                "Должно быть выброшено IllegalArgumentException"
        );

        assertEquals("Имя пользователя не может быть пустым", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Должен выбросить исключение при пустом firstName")
    void shouldThrowExceptionWhenFirstNameIsBlank() {
        // Given
        Long newTelegramId = 777888999L;
        String username = "testuser";
        String blankFirstName = "   ";

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(newTelegramId, username, blankFirstName, testFamily, null),
                "Должно быть выброшено IllegalArgumentException"
        );

        assertEquals("Имя пользователя не может быть пустым", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    // ========== Тесты для isUserAuthorized ==========

    @Test
    @DisplayName("Должен вернуть true, когда пользователь авторизован")
    void shouldReturnTrueWhenUserIsAuthorized() {
        // Given
        when(userRepository.findByTelegramId(testTelegramId))
                .thenReturn(Optional.of(testUser));

        // When
        boolean result = userService.isUserAuthorized(testTelegramId);

        // Then
        assertTrue(result, "Пользователь должен быть авторизован");
        verify(userRepository).findByTelegramId(testTelegramId);
    }

    @Test
    @DisplayName("Должен вернуть false, когда пользователь не авторизован")
    void shouldReturnFalseWhenUserIsNotAuthorized() {
        // Given
        Long unauthorizedTelegramId = 999999999L;
        when(userRepository.findByTelegramId(unauthorizedTelegramId))
                .thenReturn(Optional.empty());

        // When
        boolean result = userService.isUserAuthorized(unauthorizedTelegramId);

        // Then
        assertFalse(result, "Пользователь не должен быть авторизован");
        verify(userRepository).findByTelegramId(unauthorizedTelegramId);
    }
}
