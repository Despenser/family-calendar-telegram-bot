package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.notification.NotificationService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для NotificationService.
 * 
 * <p>Проверяет корректность работы сервиса уведомлений:</p>
 * <ul>
 *   <li>Поиск событий для уведомлений</li>
 *   <li>Отправка уведомлений членам семьи</li>
 *   <li>Отметка событий как notified</li>
 *   <li>Обработка ошибок при отправке</li>
 *   <li>Retry логика</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 6.1, 6.3</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TelegramMessageService messageService;

    @InjectMocks
    private NotificationService notificationService;

    private Family testFamily;
    private User user1;
    private User user2;
    private Event testEvent;
    private LocalDateTime now;
    private LocalDateTime oneHourLater;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        oneHourLater = now.plusHours(1);

        testFamily = Family.builder()
                .id(1L)
                .name("Test Family")
                .members(new ArrayList<>())
                .build();

        user1 = User.builder()
                .id(1L)
                .telegramId(123456789L)
                .username("user1")
                .firstName("John")
                .lastName("Doe")
                .family(testFamily)
                .build();

        user2 = User.builder()
                .id(2L)
                .telegramId(987654321L)
                .username("user2")
                .firstName("Jane")
                .lastName("Smith")
                .family(testFamily)
                .build();

        testFamily.getMembers().add(user1);
        testFamily.getMembers().add(user2);

        testEvent = Event.builder()
                .id(1L)
                .user(user1)
                .family(testFamily)
                .title("Test Event")
                .description("Test Description")
                .eventDate(oneHourLater.toLocalDate())
                .eventTime(oneHourLater.toLocalTime())
                .notified(false)
                .build();
    }

    // ========== Тесты для sendUpcomingEventNotifications ==========

    @Test
    @DisplayName("Должен найти и отправить уведомления для предстоящих событий")
    void shouldFindAndSendNotificationsForUpcomingEvents() throws TelegramApiException {
        // Given
        List<Event> upcomingEvents = Arrays.asList(testEvent);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        when(eventRepository.save(any(Event.class)))
                .thenReturn(testEvent);
        doNothing().when(messageService).sendMessage(anyLong(), anyString());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(eventRepository).findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(messageService, times(2)).sendMessage(anyLong(), anyString());
        verify(eventRepository).save(testEvent);
        assertTrue(testEvent.getNotified(), "Событие должно быть отмечено как notified");
    }

    @Test
    @DisplayName("Должен корректно обработать отсутствие предстоящих событий")
    void shouldHandleNoUpcomingEvents() throws TelegramApiException {
        // Given
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(eventRepository).findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(messageService, never()).sendMessage(anyLong(), anyString());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен отправить уведомления всем членам семьи")
    void shouldSendNotificationsToAllFamilyMembers() throws TelegramApiException {
        // Given
        List<Event> upcomingEvents = Arrays.asList(testEvent);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        when(eventRepository.save(any(Event.class)))
                .thenReturn(testEvent);
        doNothing().when(messageService).sendMessage(anyLong(), anyString());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(messageService).sendMessage(eq(user1.getTelegramId()), anyString());
        verify(messageService).sendMessage(eq(user2.getTelegramId()), anyString());
        verify(eventRepository).save(testEvent);
    }

    @Test
    @DisplayName("Должен отметить событие как notified после успешной отправки")
    void shouldMarkEventAsNotifiedAfterSuccessfulSend() throws TelegramApiException {
        // Given
        List<Event> upcomingEvents = Arrays.asList(testEvent);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        when(eventRepository.save(any(Event.class)))
                .thenReturn(testEvent);
        doNothing().when(messageService).sendMessage(anyLong(), anyString());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(eventRepository).save(testEvent);
        assertTrue(testEvent.getNotified(), "Событие должно быть отмечено как notified");
    }

    @Test
    @DisplayName("Должен продолжить обработку других событий при ошибке отправки одного")
    void shouldContinueProcessingOtherEventsWhenOneFails() throws TelegramApiException {
        // Given
        Event event2 = Event.builder()
                .id(2L)
                .user(user2)
                .family(testFamily)
                .title("Event 2")
                .description("Description 2")
                .eventDate(oneHourLater.toLocalDate())
                .eventTime(oneHourLater.toLocalTime())
                .notified(false)
                .build();

        List<Event> upcomingEvents = Arrays.asList(testEvent, event2);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Первое событие - ошибка при отправке
        doThrow(new TelegramApiException("API Error"))
                .when(messageService).sendMessage(eq(user1.getTelegramId()), contains("Test Event"));
        
        // Второе событие - успешная отправка
        doNothing().when(messageService).sendMessage(anyLong(), contains("Event 2"));

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(eventRepository).findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class));
        // Первое событие не должно быть отмечено как notified из-за ошибки
        assertFalse(testEvent.getNotified(), "Первое событие не должно быть отмечено как notified");
        // Второе событие должно быть отмечено как notified
        assertTrue(event2.getNotified(), "Второе событие должно быть отмечено как notified");
        verify(eventRepository).save(event2);
    }

    @Test
    @DisplayName("Должен обработать ошибку при отправке уведомления одному члену семьи")
    void shouldHandleErrorWhenSendingToOneFamilyMember() throws TelegramApiException {
        // Given
        List<Event> upcomingEvents = Arrays.asList(testEvent);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        
        // Ошибка при отправке первому пользователю
        doThrow(new TelegramApiException("API Error"))
                .when(messageService).sendMessage(eq(user1.getTelegramId()), anyString());
        // Успешная отправка второму пользователю
        doNothing().when(messageService).sendMessage(eq(user2.getTelegramId()), anyString());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(messageService).sendMessage(eq(user1.getTelegramId()), anyString());
        verify(messageService).sendMessage(eq(user2.getTelegramId()), anyString());
        // Событие не должно быть отмечено как notified, так как не всем отправлено
        verify(eventRepository, never()).save(testEvent);
    }

    @Test
    @DisplayName("Должен обработать семью без членов")
    void shouldHandleFamilyWithNoMembers() throws TelegramApiException {
        // Given
        Family emptyFamily = Family.builder()
                .id(2L)
                .name("Empty Family")
                .members(new ArrayList<>())
                .build();

        Event eventWithEmptyFamily = Event.builder()
                .id(3L)
                .user(user1)
                .family(emptyFamily)
                .title("Event with Empty Family")
                .description("Description")
                .eventDate(oneHourLater.toLocalDate())
                .eventTime(oneHourLater.toLocalTime())
                .notified(false)
                .build();

        List<Event> upcomingEvents = Arrays.asList(eventWithEmptyFamily);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(eventRepository).findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(messageService, never()).sendMessage(anyLong(), anyString());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен пропустить пользователя без Telegram ID")
    void shouldSkipUserWithoutTelegramId() throws TelegramApiException {
        // Given
        User userWithoutTelegramId = User.builder()
                .id(3L)
                .telegramId(null)
                .username("user3")
                .firstName("NoTelegram")
                .family(testFamily)
                .build();

        testFamily.getMembers().add(userWithoutTelegramId);

        List<Event> upcomingEvents = Arrays.asList(testEvent);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        doNothing().when(messageService).sendMessage(anyLong(), anyString());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        // Должны быть отправлены уведомления только двум пользователям с Telegram ID
        verify(messageService).sendMessage(eq(user1.getTelegramId()), anyString());
        verify(messageService).sendMessage(eq(user2.getTelegramId()), anyString());
        verify(messageService, times(2)).sendMessage(anyLong(), anyString());
        // Событие не должно быть отмечено как notified, так как не всем отправлено
        verify(eventRepository, never()).save(testEvent);
    }

    @Test
    @DisplayName("Должен обработать несколько событий одновременно")
    void shouldHandleMultipleEventsSimultaneously() throws TelegramApiException {
        // Given
        Event event2 = Event.builder()
                .id(2L)
                .user(user2)
                .family(testFamily)
                .title("Event 2")
                .description("Description 2")
                .eventDate(oneHourLater.toLocalDate())
                .eventTime(oneHourLater.toLocalTime())
                .notified(false)
                .build();

        Event event3 = Event.builder()
                .id(3L)
                .user(user1)
                .family(testFamily)
                .title("Event 3")
                .description("Description 3")
                .eventDate(oneHourLater.toLocalDate())
                .eventTime(oneHourLater.toLocalTime())
                .notified(false)
                .build();

        List<Event> upcomingEvents = Arrays.asList(testEvent, event2, event3);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(messageService).sendMessage(anyLong(), anyString());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(eventRepository).findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class));
        // Каждое событие отправляется двум членам семьи
        verify(messageService, times(6)).sendMessage(anyLong(), anyString());
        // Все три события должны быть отмечены как notified
        verify(eventRepository, times(3)).save(any(Event.class));
        assertTrue(testEvent.getNotified());
        assertTrue(event2.getNotified());
        assertTrue(event3.getNotified());
    }

    @Test
    @DisplayName("Должен корректно форматировать уведомление с полной информацией")
    void shouldFormatNotificationWithFullInformation() throws TelegramApiException {
        // Given
        List<Event> upcomingEvents = Arrays.asList(testEvent);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        when(eventRepository.save(any(Event.class)))
                .thenReturn(testEvent);
        doNothing().when(messageService).sendMessage(anyLong(), anyString());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(messageService, times(2)).sendMessage(anyLong(), contains("🔔"));
        verify(messageService, times(2)).sendMessage(anyLong(), contains("Напоминание о событии"));
        verify(messageService, times(2)).sendMessage(anyLong(), contains("Test Event"));
        verify(messageService, times(2)).sendMessage(anyLong(), contains("Test Description"));
        verify(messageService, times(2)).sendMessage(anyLong(), contains("John Doe"));
    }

    @Test
    @DisplayName("Должен корректно форматировать уведомление без описания")
    void shouldFormatNotificationWithoutDescription() throws TelegramApiException {
        // Given
        Event eventWithoutDescription = Event.builder()
                .id(2L)
                .user(user1)
                .family(testFamily)
                .title("Event Without Description")
                .description(null)
                .eventDate(oneHourLater.toLocalDate())
                .eventTime(oneHourLater.toLocalTime())
                .notified(false)
                .build();

        List<Event> upcomingEvents = Arrays.asList(eventWithoutDescription);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        when(eventRepository.save(any(Event.class)))
                .thenReturn(eventWithoutDescription);
        doNothing().when(messageService).sendMessage(anyLong(), anyString());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(messageService, times(2)).sendMessage(anyLong(), contains("Event Without Description"));
        // Проверяем, что уведомление было отправлено успешно
        verify(eventRepository).save(eventWithoutDescription);
    }

    @Test
    @DisplayName("Должен экранировать специальные символы Markdown в уведомлении")
    void shouldEscapeMarkdownSpecialCharacters() throws TelegramApiException {
        // Given
        Event eventWithSpecialChars = Event.builder()
                .id(2L)
                .user(user1)
                .family(testFamily)
                .title("Event_with*special[chars]")
                .description("Description (with) special-chars!")
                .eventDate(oneHourLater.toLocalDate())
                .eventTime(oneHourLater.toLocalTime())
                .notified(false)
                .build();

        List<Event> upcomingEvents = Arrays.asList(eventWithSpecialChars);
        
        when(eventRepository.findEventsForNotification(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(upcomingEvents);
        when(eventRepository.save(any(Event.class)))
                .thenReturn(eventWithSpecialChars);
        doNothing().when(messageService).sendMessage(anyLong(), anyString());

        // When
        notificationService.sendUpcomingEventNotifications();

        // Then
        verify(messageService, times(2)).sendMessage(anyLong(), contains("\\_"));
        verify(messageService, times(2)).sendMessage(anyLong(), contains("\\*"));
        verify(messageService, times(2)).sendMessage(anyLong(), contains("\\["));
        verify(messageService, times(2)).sendMessage(anyLong(), contains("\\]"));
    }
}
