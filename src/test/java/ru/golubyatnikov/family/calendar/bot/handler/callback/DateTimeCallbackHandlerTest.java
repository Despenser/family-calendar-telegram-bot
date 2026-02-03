package ru.golubyatnikov.family.calendar.bot.handler.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.handler.callback.datetime.DateTimeCallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.user.UserService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для DateTimeCallbackHandler.
 * 
 * <p>Проверяет корректность обработки callback queries для выбора даты и времени.</p>
 * 
 * @see DateTimeCallbackHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DateTimeCallbackHandler Unit Tests")
class DateTimeCallbackHandlerTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private TelegramMessageService messageService;

    @Mock
    private KeyboardService keyboardService;

    @Mock
    private BotMessageBuilder messageBuilder;
    
    @Mock
    private ConversationStateService conversationStateService;
    
    @Mock
    private EventService eventService;
    
    @Mock
    private UserService userService;

    @Mock
    private CallbackQuery callbackQuery;

    @Mock
    private Message message;

    @Mock
    private org.telegram.telegrambots.meta.api.objects.User telegramUser;

    private DateTimeCallbackHandler handler;
    private User user;

    @BeforeEach
    void setUp() {
        handler = new DateTimeCallbackHandler(conversationService, messageService, 
                keyboardService, messageBuilder, conversationStateService, eventService, userService);
        
        user = new User();
        user.setId(1L);
        user.setTelegramId(123456789L);
        user.setFirstName("Тест");
        user.setTimezone("Europe/Moscow");
    }

    @Test
    @DisplayName("Должен вернуть корректный префикс")
    void shouldReturnCorrectPrefix() {
        assertEquals(CallbackPrefix.DATE, handler.getPrefix());
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом date_")
    void shouldHandleDateCallback() {
        assertTrue(handler.canHandle("date_2026-01-16"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом hour_")
    void shouldHandleHourCallback() {
        assertTrue(handler.canHandle("hour_14"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback с префиксом time_ и минутами")
    void shouldHandleTimeWithMinutesCallback() {
        assertTrue(handler.canHandle("time_14:30"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback time_back")
    void shouldHandleTimeBackCallback() {
        assertTrue(handler.canHandle("time_back"));
    }

    @Test
    @DisplayName("Должен обрабатывать callback time_cancel")
    void shouldHandleTimeCancelCallback() {
        assertTrue(handler.canHandle("time_cancel"));
    }

    @Test
    @DisplayName("Не должен обрабатывать неизвестный callback")
    void shouldNotHandleUnknownCallback() {
        assertFalse(handler.canHandle("unknown_callback"));
    }

    @Test
    @DisplayName("Не должен обрабатывать null callback")
    void shouldNotHandleNullCallback() {
        assertFalse(handler.canHandle(null));
    }

    @Test
    @DisplayName("Должен корректно обработать выбор даты")
    void shouldHandleDateSelection() throws Exception {
        // Given
        String callbackData = "date_2026-01-16";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим создания нового события (не редактирование)
        when(conversationStateService.isEditingEvent(user.getId())).thenReturn(false);
        when(userService.findById(user.getId())).thenReturn(Optional.of(user));
        
        // Создаем клавиатуру с минимум 3 строками (заголовок + отмена + хотя бы один час)
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(java.util.List.of(
            java.util.List.of(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("Заголовок")),
            java.util.List.of(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("10:00")),
            java.util.List.of(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("Отмена"))
        ));
        when(keyboardService.createFilteredHourSelectionKeyboard(any(LocalDate.class), eq(user))).thenReturn(keyboard);
        when(messageBuilder.buildDateSelectedMessage(anyString())).thenReturn("Дата выбрана");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(conversationService).updateEventDate(eq(user.getId()), eq(LocalDate.of(2026, 1, 16)));
        verify(keyboardService).createFilteredHourSelectionKeyboard(eq(LocalDate.of(2026, 1, 16)), eq(user));
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), eq(keyboard));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("✅ Дата выбрано"));
    }

    @Test
    @DisplayName("Должен корректно обработать выбор часа")
    void shouldHandleHourSelection() throws Exception {
        // Given
        String callbackData = "hour_14";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим создания нового события (не редактирование)
        when(conversationStateService.isEditingEvent(user.getId())).thenReturn(false);
        when(userService.findById(user.getId())).thenReturn(Optional.of(user));
        
        // Создаем черновик события с будущей датой
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Event draft = createMockEvent(null, futureDate, null, user);
        when(conversationService.getActiveDraft(user.getId())).thenReturn(draft);
        
        // Создаем клавиатуру с минимум 3 строками (заголовок + навигация + хотя бы одна минута)
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(java.util.List.of(
            java.util.List.of(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("Заголовок")),
            java.util.List.of(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("00")),
            java.util.List.of(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("Назад"))
        ));
        when(keyboardService.createFilteredMinuteSelectionKeyboard(eq(14), eq(futureDate), eq(user))).thenReturn(keyboard);
        when(messageBuilder.buildHourSelectedMessage(14)).thenReturn("Час выбран");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(keyboardService).createFilteredMinuteSelectionKeyboard(eq(14), eq(futureDate), eq(user));
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), eq(keyboard));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("✅ Час выбрано"));
    }

    @Test
    @DisplayName("Должен корректно обработать выбор времени")
    void shouldHandleTimeSelection() throws Exception {
        // Given
        String callbackData = "time_14:30";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим создания нового события (не редактирование)
        when(conversationStateService.isEditingEvent(user.getId())).thenReturn(false);
        when(userService.findById(user.getId())).thenReturn(Optional.of(user));
        
        // Мокируем черновик события с будущей датой
        Event draft = createMockEvent(null, LocalDate.now().plusDays(1), LocalTime.of(10, 0), user);
        when(conversationService.getActiveDraft(user.getId())).thenReturn(draft);
        
        when(messageBuilder.buildTimeSelectedMessage(anyString())).thenReturn("Время выбрано");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(conversationService).updateEventTime(eq(user.getId()), eq(LocalTime.of(14, 30)));
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), isNull());
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("✅ Время выбрано"));
    }

    @Test
    @DisplayName("Должен корректно обработать возврат к выбору часа")
    void shouldHandleTimeBack() throws Exception {
        // Given
        String callbackData = "time_back";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим создания нового события (не редактирование)
        when(conversationStateService.isEditingEvent(user.getId())).thenReturn(false);
        when(userService.findById(user.getId())).thenReturn(Optional.of(user));
        
        // Создаем черновик события с будущей датой
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Event draft = createMockEvent(null, futureDate, null, user);
        when(conversationService.getActiveDraft(user.getId())).thenReturn(draft);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        when(keyboardService.createFilteredHourSelectionKeyboard(eq(futureDate), eq(user))).thenReturn(keyboard);
        when(messageBuilder.buildSelectHourMessage()).thenReturn("Выберите час");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(keyboardService).createFilteredHourSelectionKeyboard(eq(futureDate), eq(user));
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), eq(keyboard));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq(""));
    }

    @Test
    @DisplayName("Должен корректно обработать отмену выбора времени")
    void shouldHandleTimeCancel() throws Exception {
        // Given
        String callbackData = "time_cancel";
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим создания нового события (не редактирование)
        when(conversationStateService.isEditingEvent(user.getId())).thenReturn(false);
        when(messageBuilder.buildEventCancelledMessage()).thenReturn("Отменено");

        // When
        handler.handle(callbackQuery, user);

        // Then
        verify(conversationService).cancelEventCreation(user.getId());
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), isNull());
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("🚫 Создание отменено"));
    }

    /**
     * Настраивает моки для CallbackQuery.
     */
    private void setupCallbackQueryMocks(String callbackData, Long chatId, 
                                         Integer messageId, String callbackQueryId) {
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(callbackQuery.getId()).thenReturn(callbackQueryId);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageId()).thenReturn(messageId);
    }
    
    /**
     * Создает mock User с указанной timezone для тестов.
     * 
     * @param userId ID пользователя
     * @param timezone timezone пользователя
     * @return mock User
     */
    private User createMockUser(Long userId, String timezone) {
        Family family = Family.builder().id(1L).name("Test Family").build();
        return User.builder()
                .id(userId)
                .telegramId(123456789L)
                .firstName("Тест")
                .family(family)
                .timezone(timezone)
                .build();
    }
    
    /**
     * Создает mock Event для тестов.
     * 
     * @param eventId ID события
     * @param eventDate дата события
     * @param eventTime время события
     * @param user пользователь-владелец события
     * @return mock Event
     */
    private Event createMockEvent(Long eventId, LocalDate eventDate, LocalTime eventTime, User user) {
        return Event.builder()
                .id(eventId)
                .user(user)
                .family(user.getFamily())
                .eventDate(eventDate)
                .eventTime(eventTime)
                .status(Event.EventStatus.ACTIVE)
                .build();
    }
    
    // ========== Тесты валидации времени ==========
    
    @Test
    @DisplayName("Должен принять будущее время для сегодняшнего дня при создании нового события")
    void shouldAcceptFutureTimeForTodayWhenCreatingNewEvent() throws Exception {
        // Given
        User mockUser = createMockUser(1L, "Europe/Moscow");
        LocalDate today = mockUser.getCurrentDate();
        LocalTime currentTime = mockUser.getCurrentDateTime().toLocalTime();
        LocalTime futureTime = currentTime.plusHours(2);
        
        String callbackData = "time_" + futureTime.toString();
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим создания нового события (не редактирование)
        when(conversationStateService.isEditingEvent(mockUser.getId())).thenReturn(false);
        when(userService.findById(mockUser.getId())).thenReturn(Optional.of(mockUser));
        
        // Создаем черновик события с сегодняшней датой
        Event draft = createMockEvent(null, today, null, mockUser);
        when(conversationService.getActiveDraft(mockUser.getId())).thenReturn(draft);
        
        when(messageBuilder.buildTimeSelectedMessage(anyString())).thenReturn("Время выбрано");

        // When
        handler.handle(callbackQuery, mockUser);

        // Then
        // Проверяем, что время было обновлено
        verify(conversationService).updateEventTime(eq(mockUser.getId()), eq(futureTime));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("✅ Время выбрано"));
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), isNull());
    }
    
    @Test
    @DisplayName("Должен принять любое время для будущей даты при создании нового события")
    void shouldAcceptAnyTimeForFutureDateWhenCreatingNewEvent() throws Exception {
        // Given
        User mockUser = createMockUser(1L, "Europe/Moscow");
        LocalDate futureDate = mockUser.getCurrentDate().plusDays(1);
        LocalTime anyTime = LocalTime.of(8, 0); // Раннее утро
        
        String callbackData = "time_" + anyTime.toString();
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим создания нового события (не редактирование)
        when(conversationStateService.isEditingEvent(mockUser.getId())).thenReturn(false);
        when(userService.findById(mockUser.getId())).thenReturn(Optional.of(mockUser));
        
        // Создаем черновик события с будущей датой
        Event draft = createMockEvent(null, futureDate, null, mockUser);
        when(conversationService.getActiveDraft(mockUser.getId())).thenReturn(draft);
        
        when(messageBuilder.buildTimeSelectedMessage(anyString())).thenReturn("Время выбрано");

        // When
        handler.handle(callbackQuery, mockUser);

        // Then
        // Проверяем, что время было обновлено без валидации
        verify(conversationService).updateEventTime(eq(mockUser.getId()), eq(anyTime));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("✅ Время выбрано"));
        verify(messageService).editMessageText(eq(chatId), eq(messageId), anyString(), isNull());
    }
    
    @Test
    @DisplayName("Должен принять будущее время для сегодняшнего дня при редактировании события")
    void shouldAcceptFutureTimeForTodayWhenEditingEvent() throws Exception {
        // Given
        User mockUser = createMockUser(1L, "Europe/Moscow");
        LocalDate today = mockUser.getCurrentDate();
        LocalTime currentTime = mockUser.getCurrentDateTime().toLocalTime();
        LocalTime futureTime = currentTime.plusHours(1);
        
        String callbackData = "time_" + futureTime.toString();
        Long chatId = 100L;
        Integer messageId = 1;
        String callbackQueryId = "query123";
        Long eventId = 101L;
        
        setupCallbackQueryMocks(callbackData, chatId, messageId, callbackQueryId);
        
        // Мокируем режим редактирования события
        when(conversationStateService.isEditingEvent(mockUser.getId())).thenReturn(true);
        when(userService.findById(mockUser.getId())).thenReturn(Optional.of(mockUser));
        ConversationStateService.EditingContext context = new ConversationStateService.EditingContext(
                eventId, chatId, ConversationStateService.EditField.TIME, messageId);
        when(conversationStateService.getEditingContext(mockUser.getId())).thenReturn(context);
        
        // Создаем событие с сегодняшней датой
        Event event = createMockEvent(eventId, today, LocalTime.of(10, 0), mockUser);
        when(eventService.getEventById(eventId)).thenReturn(event);
        
        // Мокируем обновленное событие
        Event updatedEvent = createMockEvent(eventId, today, futureTime, mockUser);
        when(eventService.updateEventTime(eq(eventId), eq(mockUser.getId()), eq(futureTime)))
                .thenReturn(updatedEvent);
        
        when(eventService.getActiveEventsCount(mockUser.getId())).thenReturn(1);
        when(messageBuilder.buildEventMessageWithHeader(any(Event.class), anyInt()))
                .thenReturn("Событие обновлено");
        when(keyboardService.createEventActionsKeyboard(any(Event.class), eq(mockUser.getId())))
                .thenReturn(new InlineKeyboardMarkup());

        // When
        handler.handle(callbackQuery, mockUser);

        // Then
        // Проверяем, что событие было обновлено
        verify(eventService).updateEventTime(eq(eventId), eq(mockUser.getId()), eq(futureTime));
        verify(messageService).answerCallbackQuery(eq(callbackQueryId), eq("✅ Обновлено"));
    }
}
