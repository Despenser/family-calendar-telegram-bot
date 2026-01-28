package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тесты для {@link TodayCommandHandler}.
 * 
 * <p>Проверяет корректность обработки команды /today, включая:</p>
 * <ul>
 *   <li>Отображение заголовка дня</li>
 *   <li>Форматирование событий</li>
 *   <li>Отсутствие разделителей между событиями</li>
 *   <li>Отображение счетчика событий</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-26
 */
@ExtendWith(MockitoExtension.class)
class TodayCommandHandlerTest {
    
    @Mock
    private EventService eventService;
    
    @Mock
    private TelegramMessageService messageService;
    
    @Mock
    private ru.golubyatnikov.family.calendar.bot.service.ReminderService reminderService;
    
    @InjectMocks
    private TodayCommandHandler handler;
    
    private User testUser;
    private Family testFamily;
    private Message testMessage;
    private LocalDate today;
    
    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        
        testFamily = new Family();
        testFamily.setId(1L);
        testFamily.setName("Test Family");
        
        testUser = new User();
        testUser.setId(1L);
        testUser.setTelegramId(123456789L);
        testUser.setFirstName("Test");
        testUser.setFamily(testFamily);
        
        testMessage = mock(Message.class);
        when(testMessage.getChatId()).thenReturn(123456789L);
    }
    
    @Test
    @DisplayName("Не должен отображать дублирующийся заголовок дня, так как дата уже в основном заголовке")
    void shouldNotDisplayDuplicateDayHeader() {
        // Given
        Event event = createTestEvent("Тестовое событие", today, false);
        when(eventService.getUpcomingEvents(anyLong(), anyInt()))
            .thenReturn(Collections.singletonList(event));
        
        // When
        String result = handler.handle(testMessage, testUser);
        
        // Then
        // Проверяем, что основной заголовок команды присутствует
        assertTrue(result.contains("📅"), 
                  "Результат должен содержать иконку основного заголовка '📅'");
        assertTrue(result.contains("События на сегодня"), 
                  "Результат должен содержать текст 'События на сегодня'");
        
        // Проверяем, что дублирующийся заголовок дня НЕ добавляется
        // Заголовок дня содержит текст "(сегодня -" который не должен присутствовать
        assertFalse(result.contains("(сегодня -"), 
                    "Результат не должен содержать дублирующийся заголовок дня с текстом '(сегодня -', " +
                    "так как дата уже указана в основном заголовке команды");
    }
    
    @Test
    @DisplayName("Не должен добавлять разделители между событиями одного дня")
    void shouldNotAddSeparatorsBetweenEvents() {
        // Given
        Event event1 = createTestEvent("Событие 1", today, false);
        Event event2 = createTestEvent("Событие 2", today, false);
        when(eventService.getUpcomingEvents(anyLong(), anyInt()))
            .thenReturn(Arrays.asList(event1, event2));
        
        // When
        String result = handler.handle(testMessage, testUser);
        
        // Then
        assertFalse(result.contains("─────────────────────"), 
                   "Результат не должен содержать разделители между событиями одного дня");
    }
    
    @Test
    @DisplayName("Должен отображать счетчик событий")
    void shouldDisplayEventCounter() {
        // Given
        Event event1 = createTestEvent("Событие 1", today, false);
        Event event2 = createTestEvent("Событие 2", today, false);
        when(eventService.getUpcomingEvents(anyLong(), anyInt()))
            .thenReturn(Arrays.asList(event1, event2));
        
        // When
        String result = handler.handle(testMessage, testUser);
        
        // Then
        assertTrue(result.contains("_Всего событий: 2_"), 
                  "Результат должен содержать счетчик событий в формате '_Всего событий: N_'");
    }
    
    @Test
    @DisplayName("Должен отображать заголовок команды с правильной датой")
    void shouldDisplayCommandHeaderWithCorrectDate() {
        // Given
        Event event = createTestEvent("Тестовое событие", today, false);
        when(eventService.getUpcomingEvents(anyLong(), anyInt()))
            .thenReturn(Collections.singletonList(event));
        
        // When
        String result = handler.handle(testMessage, testUser);
        
        // Then
        assertTrue(result.contains("📅"), 
                  "Результат должен содержать иконку заголовка команды '📅'");
        assertTrue(result.contains("События на сегодня"), 
                  "Результат должен содержать текст 'События на сегодня'");
    }
    
    @Test
    @DisplayName("Должен возвращать сообщение об отсутствии событий, когда событий нет")
    void shouldReturnNoEventsMessageWhenNoEvents() {
        // Given
        when(eventService.getUpcomingEvents(anyLong(), anyInt()))
            .thenReturn(Collections.emptyList());
        
        // When
        String result = handler.handle(testMessage, testUser);
        
        // Then
        assertTrue(result.contains("📅"), 
                  "Результат должен содержать иконку заголовка '📅'");
        assertTrue(result.contains("События на сегодня"), 
                  "Результат должен содержать текст 'События на сегодня'");
        assertTrue(result.contains("На сегодня событий не запланировано"), 
                  "Результат должен содержать сообщение об отсутствии событий");
    }
    
    @Test
    @DisplayName("Должен фильтровать персональные события других пользователей")
    void shouldFilterPersonalEventsOfOtherUsers() {
        // Given
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setFirstName("Other");
        
        Event familyEvent = createTestEvent("Семейное событие", today, false);
        Event personalEvent = createTestEvent("Персональное событие", today, true);
        personalEvent.setUser(otherUser);
        
        when(eventService.getUpcomingEvents(anyLong(), anyInt()))
            .thenReturn(Arrays.asList(familyEvent, personalEvent));
        
        // When
        String result = handler.handle(testMessage, testUser);
        
        // Then
        assertTrue(result.contains("Семейное событие"), 
                  "Результат должен содержать семейное событие");
        assertFalse(result.contains("Персональное событие"), 
                   "Результат не должен содержать персональное событие другого пользователя");
        assertTrue(result.contains("_Всего событий: 1_"), 
                  "Счетчик должен показывать только одно событие");
    }
    
    @Test
    @DisplayName("Должен форматировать дату с русской локалью и дефисом")
    void shouldFormatDateWithRussianLocaleAndDash() {
        // Given
        Event event = createTestEvent("Тестовое событие", today, false);
        when(eventService.getUpcomingEvents(anyLong(), anyInt()))
            .thenReturn(Collections.singletonList(event));
        
        // When
        String result = handler.handle(testMessage, testUser);
        
        // Then
        // Проверяем формат даты с дефисом (с учетом экранирования)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru"));
        String expectedDatePart = today.format(formatter);
        
        // Дата может быть экранирована, поэтому проверяем оба варианта
        boolean containsDate = result.contains(expectedDatePart) || 
                               result.contains(expectedDatePart.replace(".", "\\."));
        
        assertTrue(containsDate, 
                  String.format("Результат должен содержать дату в формате '%s'", expectedDatePart));
        
        // Проверяем, что день недели на русском языке
        String[] russianDays = {"понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"};
        boolean containsRussianDay = Arrays.stream(russianDays)
            .anyMatch(day -> result.toLowerCase().contains(day));
        
        assertTrue(containsRussianDay, 
                  "Результат должен содержать день недели на русском языке");
        
        // Проверяем, что используется дефис в формате даты (с учетом экранирования)
        boolean containsDash = result.contains(" - ") || result.contains(" \\- ");
        assertTrue(containsDash, 
                  "Результат должен содержать дефис после даты");
    }
    
    /**
     * Создает тестовое событие с заданными параметрами.
     * 
     * @param title название события
     * @param date дата события
     * @param isPersonal является ли событие персональным
     * @return созданное событие
     */
    private Event createTestEvent(String title, LocalDate date, boolean isPersonal) {
        Event event = new Event();
        event.setId(1L);
        event.setTitle(title);
        event.setEventDate(date);
        event.setEventTime(LocalTime.of(10, 0));
        event.setIsPersonal(isPersonal);
        event.setUser(testUser);
        event.setFamily(testFamily);
        return event;
    }
}
