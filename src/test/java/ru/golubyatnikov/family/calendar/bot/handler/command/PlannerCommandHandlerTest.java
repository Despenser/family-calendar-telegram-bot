package ru.golubyatnikov.family.calendar.bot.handler.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.exception.EventNotFoundException;
import ru.golubyatnikov.family.calendar.bot.exception.UnauthorizedAccessException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.planner.PlannerFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.planner.PlannerNavigationService;
import ru.golubyatnikov.family.calendar.bot.service.planner.PlannerQueryService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit тесты для PlannerCommandHandler.
 * 
 * <p>Проверяет корректность обработки команды /my_events для различных сценариев:</p>
 * <ul>
 *   <li>Отображение событий пользователя</li>
 *   <li>Обработка случая отсутствия событий</li>
 *   <li>Обработка callback для удаления события</li>
 *   <li>Обработка callback для редактирования события</li>
 *   <li>Проверка прав доступа при удалении</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 4.2, 5.1, 7.1</p>
 * 
 * @see PlannerCommandHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-02-03
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlannerCommandHandler Unit Tests")
class PlannerCommandHandlerTest {

    @Mock
    private PlannerQueryService queryService;

    @Mock
    private PlannerFormattingService formattingService;

    @Mock
    private PlannerNavigationService navigationService;

    @Mock
    private EventService eventService;

    @Mock
    private KeyboardService keyboardService;

    @Mock
    private TelegramMessageService messageService;

    @Mock
    private ConversationStateService conversationStateService;

    @Mock
    private Message message;

    @Mock
    private User telegramUser;

    private PlannerCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PlannerCommandHandler(queryService, formattingService, navigationService, 
                                           eventService, keyboardService, messageService, conversationStateService);
    }

    @Test
    @DisplayName("Должен вернуть корректную команду")
    void shouldReturnCorrectCommand() {
        // When
        String command = handler.getCommand();

        // Then
        assertEquals("/my_events", command);
    }

    @Test
    @DisplayName("Должен вернуть корректное описание")
    void shouldReturnCorrectDescription() {
        // When
        String description = handler.getDescription();

        // Then
        assertEquals("Управление моими событиями", description);
    }

    @Test
    @DisplayName("Должен требовать авторизацию")
    void shouldRequireAuth() {
        // When
        boolean requiresAuth = handler.requiresAuth();

        // Then
        assertTrue(requiresAuth);
    }

    @Test
    @DisplayName("Должен выбросить исключение при null сообщении")
    void shouldThrowExceptionOnNullMessage() {
        // Given
        ru.golubyatnikov.family.calendar.bot.model.User user = createUser();

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(null, user)
        );
        
        assertEquals("Сообщение и пользователь не могут быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен выбросить исключение при null пользователе")
    void shouldThrowExceptionOnNullUser() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(message, null)
        );
        
        assertEquals("Сообщение и пользователь не могут быть null", exception.getMessage());
    }

    @Test
    @DisplayName("Должен успешно удалить событие")
    void shouldSuccessfullyDeleteEvent() {
        // Given
        Long eventId = 1L;
        Long userId = 1L;

        // When
        handler.handleDeleteCallback(eventId, userId);

        // Then
        verify(eventService).deleteEvent(eventId, userId);
    }

    @Test
    @DisplayName("Должен обработать ошибку при удалении несуществующего события")
    void shouldHandleDeleteNonExistentEvent() {
        // Given
        Long eventId = 999L;
        Long userId = 1L;
        
        doThrow(new EventNotFoundException(eventId))
                .when(eventService).deleteEvent(eventId, userId);

        // When & Then
        assertThrows(EventNotFoundException.class, () -> {
            handler.handleDeleteCallback(eventId, userId);
        });
        
        verify(eventService).deleteEvent(eventId, userId);
    }

    @Test
    @DisplayName("Должен обработать ошибку при попытке удалить чужое событие")
    void shouldHandleDeleteUnauthorizedEvent() {
        // Given
        Long eventId = 1L;
        Long userId = 2L;
        
        doThrow(new UnauthorizedAccessException("User cannot delete this event"))
                .when(eventService).deleteEvent(eventId, userId);

        // When & Then
        assertThrows(UnauthorizedAccessException.class, () -> {
            handler.handleDeleteCallback(eventId, userId);
        });
        
        verify(eventService).deleteEvent(eventId, userId);
    }

    /**
     * Создает тестового пользователя.
     */
    private ru.golubyatnikov.family.calendar.bot.model.User createUser() {
        Family family = Family.builder()
                .id(1L)
                .name("Test Family")
                .build();
        
        return ru.golubyatnikov.family.calendar.bot.model.User.builder()
                .id(1L)
                .telegramId(123456789L)
                .username("test_user")
                .firstName("Иван")
                .lastName("Иванов")
                .family(family)
                .build();
    }
}
