package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.golubyatnikov.family.calendar.bot.exception.UserNotFoundException;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.model.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для {@link ConversationService}.
 * 
 * <p>Тестирует функциональность управления состоянием многошагового диалога
 * создания события через черновики в базе данных.</p>
 * 
 * <p>Покрываемые сценарии:</p>
 * <ul>
 *   <li>Создание черновика события</li>
 *   <li>Обновление даты, времени, названия в черновике</li>
 *   <li>Завершение создания события</li>
 *   <li>Отмена создания события</li>
 *   <li>Определение текущего шага диалога</li>
 *   <li>Удаление старых черновиков</li>
 *   <li>Обработка ошибочных ситуаций</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationService Unit Tests")
class ConversationServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private ru.golubyatnikov.family.calendar.bot.service.event.EventService eventService;

    @InjectMocks
    private ConversationService conversationService;

    private User testUser;
    private Family testFamily;
    private Event testDraft;

    @BeforeEach
    void setUp() {
        // Создаем тестовую семью
        testFamily = new Family();
        testFamily.setId(1L);
        testFamily.setName("Test Family");

        // Создаем тестового пользователя
        testUser = new User();
        testUser.setId(1L);
        testUser.setTelegramId(123456789L);
        testUser.setUsername("testuser");
        testUser.setFirstName("Test");
        testUser.setFamily(testFamily);

        // Создаем тестовый черновик
        testDraft = Event.builder()
                .id(1L)
                .user(testUser)
                .family(testFamily)
                .status(EventStatus.DRAFT)
                .notified(false)
                .build();
    }

    // ========== Тесты для startEventCreation ==========

    @Test
    @DisplayName("Должен создать черновик события для существующего пользователя")
    void shouldCreateDraftForExistingUser() {
        // Given
        Long userId = testUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(eventRepository.findAllByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Collections.emptyList());
        when(eventRepository.save(any(Event.class))).thenReturn(testDraft);

        // When
        Event result = conversationService.startEventCreation(userId);

        // Then
        assertNotNull(result);
        assertEquals(EventStatus.DRAFT, result.getStatus());
        assertEquals(testUser, result.getUser());
        assertEquals(testFamily, result.getFamily());
        assertFalse(result.getNotified());

        verify(userRepository).findById(userId);
        verify(eventRepository).findAllByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен удалить старые черновики перед созданием нового")
    void shouldDeleteOldDraftsBeforeCreatingNew() {
        // Given
        Long userId = testUser.getId();
        Event oldDraft1 = Event.builder().id(10L).status(EventStatus.DRAFT).build();
        Event oldDraft2 = Event.builder().id(11L).status(EventStatus.DRAFT).build();
        List<Event> oldDrafts = List.of(oldDraft1, oldDraft2);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(eventRepository.findAllByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(oldDrafts);
        when(eventRepository.save(any(Event.class))).thenReturn(testDraft);

        // When
        Event result = conversationService.startEventCreation(userId);

        // Then
        assertNotNull(result);
        verify(eventRepository).deleteAll(oldDrafts);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    @DisplayName("Должен выбросить UserNotFoundException для несуществующего пользователя")
    void shouldThrowUserNotFoundExceptionForNonExistentUser() {
        // Given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class, () -> 
                conversationService.startEventCreation(userId));

        verify(userRepository).findById(userId);
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ========== Тесты для updateEventDate ==========

    @Test
    @DisplayName("Должен обновить дату в черновике события")
    void shouldUpdateEventDate() {
        // Given
        Long userId = testUser.getId();
        LocalDate date = LocalDate.of(2026, 12, 31);
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.of(testDraft));
        when(eventRepository.save(testDraft)).thenReturn(testDraft);

        // When
        Event result = conversationService.updateEventDate(userId, date);

        // Then
        assertNotNull(result);
        assertEquals(date, testDraft.getEventDate());
        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository).save(testDraft);
    }

    @Test
    @DisplayName("Должен выбросить IllegalStateException если черновик не найден при обновлении даты")
    void shouldThrowExceptionWhenDraftNotFoundForDateUpdate() {
        // Given
        Long userId = testUser.getId();
        LocalDate date = LocalDate.of(2026, 12, 31);
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
                conversationService.updateEventDate(userId, date));

        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ========== Тесты для updateEventTime ==========

    @Test
    @DisplayName("Должен обновить время в черновике события")
    void shouldUpdateEventTime() {
        // Given
        Long userId = testUser.getId();
        LocalTime time = LocalTime.of(18, 30);
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.of(testDraft));
        when(eventRepository.save(testDraft)).thenReturn(testDraft);

        // When
        Event result = conversationService.updateEventTime(userId, time);

        // Then
        assertNotNull(result);
        assertEquals(time, testDraft.getEventTime());
        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository).save(testDraft);
    }

    @Test
    @DisplayName("Должен выбросить IllegalStateException если черновик не найден при обновлении времени")
    void shouldThrowExceptionWhenDraftNotFoundForTimeUpdate() {
        // Given
        Long userId = testUser.getId();
        LocalTime time = LocalTime.of(18, 30);
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
                conversationService.updateEventTime(userId, time));

        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ========== Тесты для updateEventTitle ==========

    @Test
    @DisplayName("Должен обновить название в черновике события")
    void shouldUpdateEventTitle() {
        // Given
        Long userId = testUser.getId();
        String title = "Новогодний ужин";
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.of(testDraft));
        when(eventRepository.save(testDraft)).thenReturn(testDraft);

        // When
        Event result = conversationService.updateEventTitle(userId, title);

        // Then
        assertNotNull(result);
        assertEquals(title, testDraft.getTitle());
        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository).save(testDraft);
    }

    @Test
    @DisplayName("Должен выбросить IllegalStateException если черновик не найден при обновлении названия")
    void shouldThrowExceptionWhenDraftNotFoundForTitleUpdate() {
        // Given
        Long userId = testUser.getId();
        String title = "Новогодний ужин";
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
                conversationService.updateEventTitle(userId, title));

        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ========== Тесты для completeEventCreation ==========

    @Test
    @DisplayName("Должен завершить создание события с описанием")
    void shouldCompleteEventCreationWithDescription() {
        // Given
        Long userId = testUser.getId();
        String description = "Семейный ужин в честь Нового года";
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.of(testDraft));
        when(eventRepository.save(testDraft)).thenReturn(testDraft);
        doNothing().when(eventService).handleEventCreated(any(Event.class), any(User.class));

        // When
        Event result = conversationService.completeEventCreation(userId, description);

        // Then
        assertNotNull(result);
        assertEquals(description, testDraft.getDescription());
        assertEquals(EventStatus.ACTIVE, testDraft.getStatus());
        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository).save(testDraft);
    }

    @Test
    @DisplayName("Должен завершить создание события без описания")
    void shouldCompleteEventCreationWithoutDescription() {
        // Given
        Long userId = testUser.getId();
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.of(testDraft));
        when(eventRepository.save(testDraft)).thenReturn(testDraft);
        doNothing().when(eventService).handleEventCreated(any(Event.class), any(User.class));

        // When
        Event result = conversationService.completeEventCreation(userId, null);

        // Then
        assertNotNull(result);
        assertNull(testDraft.getDescription());
        assertEquals(EventStatus.ACTIVE, testDraft.getStatus());
        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository).save(testDraft);
    }

    @Test
    @DisplayName("Должен выбросить IllegalStateException если черновик не найден при завершении")
    void shouldThrowExceptionWhenDraftNotFoundForCompletion() {
        // Given
        Long userId = testUser.getId();
        String description = "Описание";
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
                conversationService.completeEventCreation(userId, description));

        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ========== Тесты для cancelEventCreation ==========

    @Test
    @DisplayName("Должен отменить создание события и удалить черновик")
    void shouldCancelEventCreationAndDeleteDraft() {
        // Given
        Long userId = testUser.getId();
        List<Event> drafts = List.of(testDraft);
        
        when(eventRepository.findAllByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(drafts);

        // When
        conversationService.cancelEventCreation(userId);

        // Then
        verify(eventRepository).findAllByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository).deleteAll(drafts);
    }

    @Test
    @DisplayName("Должен корректно обработать отмену когда нет черновиков")
    void shouldHandleCancellationWhenNoDrafts() {
        // Given
        Long userId = testUser.getId();
        
        when(eventRepository.findAllByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Collections.emptyList());

        // When
        conversationService.cancelEventCreation(userId);

        // Then
        verify(eventRepository).findAllByUserIdAndStatus(userId, EventStatus.DRAFT);
        verify(eventRepository, never()).deleteAll(any());
    }

    // ========== Тесты для getActiveDraft ==========

    @Test
    @DisplayName("Должен получить активный черновик пользователя")
    void shouldGetActiveDraft() {
        // Given
        Long userId = testUser.getId();
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.of(testDraft));

        // When
        Event result = conversationService.getActiveDraft(userId);

        // Then
        assertNotNull(result);
        assertEquals(testDraft, result);
        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
    }

    @Test
    @DisplayName("Должен выбросить IllegalStateException если активный черновик не найден")
    void shouldThrowExceptionWhenActiveDraftNotFound() {
        // Given
        Long userId = testUser.getId();
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalStateException.class, () -> 
                conversationService.getActiveDraft(userId));

        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
    }

    // ========== Тесты для hasActiveDraft ==========

    @Test
    @DisplayName("Должен вернуть true если есть активный черновик")
    void shouldReturnTrueWhenHasActiveDraft() {
        // Given
        Long userId = testUser.getId();
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.of(testDraft));

        // When
        boolean result = conversationService.hasActiveDraft(userId);

        // Then
        assertTrue(result);
        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
    }

    @Test
    @DisplayName("Должен вернуть false если нет активного черновика")
    void shouldReturnFalseWhenNoActiveDraft() {
        // Given
        Long userId = testUser.getId();
        
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.empty());

        // When
        boolean result = conversationService.hasActiveDraft(userId);

        // Then
        assertFalse(result);
        verify(eventRepository).findByUserIdAndStatus(userId, EventStatus.DRAFT);
    }

    // ========== Тесты для getCurrentStep ==========

    @Test
    @DisplayName("Должен вернуть WAITING_FOR_DATE когда дата не установлена")
    void shouldReturnWaitingForDateWhenDateNotSet() {
        // Given
        Event draft = Event.builder()
                .eventDate(null)
                .eventTime(null)
                .title(null)
                .build();

        // When
        ConversationService.ConversationStep step = conversationService.getCurrentStep(draft);

        // Then
        assertEquals(ConversationService.ConversationStep.WAITING_FOR_DATE, step);
    }

    @Test
    @DisplayName("Должен вернуть WAITING_FOR_TIME когда дата установлена но время нет")
    void shouldReturnWaitingForTimeWhenDateSetButTimeNot() {
        // Given
        Event draft = Event.builder()
                .eventDate(LocalDate.of(2026, 12, 31))
                .eventTime(null)
                .title(null)
                .build();

        // When
        ConversationService.ConversationStep step = conversationService.getCurrentStep(draft);

        // Then
        assertEquals(ConversationService.ConversationStep.WAITING_FOR_TIME, step);
    }

    @Test
    @DisplayName("Должен вернуть WAITING_FOR_TITLE когда дата и время установлены но название нет")
    void shouldReturnWaitingForTitleWhenDateAndTimeSetButTitleNot() {
        // Given
        Event draft = Event.builder()
                .eventDate(LocalDate.of(2026, 12, 31))
                .eventTime(LocalTime.of(18, 30))
                .title(null)
                .build();

        // When
        ConversationService.ConversationStep step = conversationService.getCurrentStep(draft);

        // Then
        assertEquals(ConversationService.ConversationStep.WAITING_FOR_TITLE, step);
    }

    @Test
    @DisplayName("Должен вернуть WAITING_FOR_TITLE когда название пустое")
    void shouldReturnWaitingForTitleWhenTitleIsBlank() {
        // Given
        Event draft = Event.builder()
                .eventDate(LocalDate.of(2026, 12, 31))
                .eventTime(LocalTime.of(18, 30))
                .title("   ")
                .build();

        // When
        ConversationService.ConversationStep step = conversationService.getCurrentStep(draft);

        // Then
        assertEquals(ConversationService.ConversationStep.WAITING_FOR_TITLE, step);
    }

    @Test
    @DisplayName("Должен вернуть WAITING_FOR_DESCRIPTION когда все обязательные поля заполнены")
    void shouldReturnWaitingForDescriptionWhenAllRequiredFieldsSet() {
        // Given
        Event draft = Event.builder()
                .eventDate(LocalDate.of(2026, 12, 31))
                .eventTime(LocalTime.of(18, 30))
                .title("Новогодний ужин")
                .build();

        // When
        ConversationService.ConversationStep step = conversationService.getCurrentStep(draft);

        // Then
        assertEquals(ConversationService.ConversationStep.WAITING_FOR_DESCRIPTION, step);
    }

    // ========== Интеграционные тесты ==========

    @Test
    @DisplayName("Должен пройти полный цикл создания события")
    void shouldCompleteFullEventCreationCycle() {
        // Given
        Long userId = testUser.getId();
        LocalDate date = LocalDate.of(2026, 12, 31);
        LocalTime time = LocalTime.of(18, 30);
        String title = "Новогодний ужин";
        String description = "Семейный ужин";

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(eventRepository.findAllByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Collections.emptyList());
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.findByUserIdAndStatus(userId, EventStatus.DRAFT))
                .thenReturn(Optional.of(testDraft));
        doNothing().when(eventService).handleEventCreated(any(Event.class), any(User.class));

        // When - создаем черновик
        Event draft = conversationService.startEventCreation(userId);
        assertNotNull(draft);
        assertEquals(EventStatus.DRAFT, draft.getStatus());

        // When - обновляем дату
        conversationService.updateEventDate(userId, date);
        testDraft.setEventDate(date);

        // When - обновляем время
        conversationService.updateEventTime(userId, time);
        testDraft.setEventTime(time);

        // When - обновляем название
        conversationService.updateEventTitle(userId, title);
        testDraft.setTitle(title);

        // When - завершаем создание
        Event completed = conversationService.completeEventCreation(userId, description);

        // Then
        assertNotNull(completed);
        assertEquals(EventStatus.ACTIVE, testDraft.getStatus());
        assertEquals(date, testDraft.getEventDate());
        assertEquals(time, testDraft.getEventTime());
        assertEquals(title, testDraft.getTitle());
        assertEquals(description, testDraft.getDescription());
    }
}
