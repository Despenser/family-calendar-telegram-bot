package ru.golubyatnikov.family.calendar.bot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import ru.golubyatnikov.family.calendar.bot.handler.command.WeekCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для WeekCommandHandler.
 * 
 * <p>Проверяет корректность обработки команды /week для различных сценариев:</p>
 * <ul>
 *   <li>Отображение событий на неделю (7 дней)</li>
 *   <li>Группировка событий по датам</li>
 *   <li>Выделение "Сегодня" и "Завтра"</li>
 *   <li>Фильтрация персональных событий</li>
 *   <li>Обработка случая отсутствия событий</li>
 *   <li>Обработка ошибок</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1, 1.2, 1.3, 1.4, 1.5</p>
 * 
 * @see WeekCommandHandler
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-12
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WeekCommandHandler Unit Tests")
class WeekCommandHandlerTest {

    @Mock
    private EventService eventService;

    @Mock
    private TelegramMessageService messageService;
    
    @Mock
    private ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderSchedulingService reminderSchedulingService;

    @Mock
    private Message message;

    @Mock
    private User telegramUser;

    private WeekCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WeekCommandHandler(eventService, reminderSchedulingService);
    }

    @Test
    @DisplayName("Должен вернуть корректную команду")
    void shouldReturnCorrectCommand() {
        // When
        String command = handler.getCommand();

        // Then
        assertEquals("/week", command);
    }

    @Test
    @DisplayName("Должен вернуть корректное описание")
    void shouldReturnCorrectDescription() {
        // When
        String description = handler.getDescription();

        // Then
        assertEquals("Показать события на неделю", description);
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
    @DisplayName("Должен отобразить события на неделю с валидными данными")
    void shouldDisplayWeekEventsWithValidData() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        List<Event> events = createTestEventsForWeek(user);
        
        when(message.getChatId()).thenReturn(123456789L);
        when(eventService.getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"))).thenReturn(events);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("События на неделю"), 
                "Ответ должен содержать заголовок с указанием недели");
        assertTrue(response.contains("День рождения"), "Ответ должен содержать название события");
        assertTrue(response.contains("Поход в кино"), "Ответ должен содержать название второго события");
        assertTrue(response.contains("Всего событий: 2"), "Ответ должен содержать счетчик событий");
        
        verify(eventService).getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"));
    }

    @Test
    @DisplayName("Должен группировать события по датам")
    void shouldGroupEventsByDate() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        
        Event todayEvent = createEvent(user, "Утренняя встреча", today, LocalTime.of(9, 0));
        Event tomorrowEvent = createEvent(user, "Вечерний ужин", tomorrow, LocalTime.of(19, 0));
        
        List<Event> events = Arrays.asList(todayEvent, tomorrowEvent);
        
        when(message.getChatId()).thenReturn(123456789L);
        when(eventService.getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"))).thenReturn(events);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Сегодня") || response.contains("📍"), 
                "Ответ должен содержать маркер 'Сегодня'");
        assertTrue(response.contains("завтра") || response.contains("🔜"), 
                "Ответ должен содержать маркер 'завтра' в новом формате");
        // Проверяем, что дни недели на русском языке со строчной буквы (проверяем хотя бы одно название)
        boolean hasRussianDayName = response.contains("понедельник") || 
                                    response.contains("вторник") || 
                                    response.contains("среда") || 
                                    response.contains("четверг") || 
                                    response.contains("пятница") || 
                                    response.contains("суббота") || 
                                    response.contains("воскресенье");
        assertTrue(hasRussianDayName, "Ответ должен содержать русское название дня недели со строчной буквы");
        
        verify(eventService).getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"));
    }

    @Test
    @DisplayName("Должен фильтровать персональные события других пользователей")
    void shouldFilterPersonalEventsOfOtherUsers() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        ru.golubyatnikov.family.calendar.bot.model.User otherUser = createOtherUserWithFamily(familyId);
        
        Event familyEvent = createFamilyEvent(user, "Семейный ужин", LocalDate.now());
        Event userPersonalEvent = createPersonalEvent(user, "Моя встреча", LocalDate.now().plusDays(1));
        Event otherUserPersonalEvent = createPersonalEvent(otherUser, "Чужая встреча", LocalDate.now().plusDays(2));
        
        List<Event> events = Arrays.asList(familyEvent, userPersonalEvent, otherUserPersonalEvent);
        
        when(message.getChatId()).thenReturn(123456789L);
        when(eventService.getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"))).thenReturn(events);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("Семейный ужин"), "Ответ должен содержать семейное событие");
        assertTrue(response.contains("Моя встреча"), "Ответ должен содержать персональное событие пользователя");
        assertFalse(response.contains("Чужая встреча"), "Ответ НЕ должен содержать персональное событие другого пользователя");
        assertTrue(response.contains("Всего событий: 2"), "Должно быть отфильтровано 2 события");
        
        verify(eventService).getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"));
    }

    @Test
    @DisplayName("Должен отобразить сообщение об отсутствии событий")
    void shouldDisplayNoEventsMessage() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        when(message.getChatId()).thenReturn(123456789L);
        when(eventService.getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"))).thenReturn(Collections.emptyList());

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("События на неделю"), 
                "Ответ должен содержать заголовок");
        assertTrue(response.contains("На ближайшую неделю событий не запланировано"), 
                "Ответ должен содержать сообщение об отсутствии событий");
        
        verify(eventService).getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"));
    }

    @Test
    @DisplayName("Должен обработать ошибку при получении событий")
    void shouldHandleErrorWhenGettingEvents() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        when(message.getChatId()).thenReturn(123456789L);
        when(eventService.getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"))).thenThrow(new RuntimeException("Database error"));

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("❌") || response.contains("ошибка") || response.contains("Произошла ошибка"), 
                "Ответ должен содержать сообщение об ошибке");
        
        verify(eventService).getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"));
    }

    @Test
    @DisplayName("Должен отображать иконки для персональных и семейных событий")
    void shouldDisplayIconsForPersonalAndFamilyEvents() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        Event familyEvent = createFamilyEvent(user, "Семейный ужин", LocalDate.now());
        Event personalEvent = createPersonalEvent(user, "Личная встреча", LocalDate.now().plusDays(1));
        
        List<Event> events = Arrays.asList(familyEvent, personalEvent);
        
        when(message.getChatId()).thenReturn(123456789L);
        when(eventService.getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"))).thenReturn(events);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("👨‍👩‍👧‍👦"), 
                "Ответ должен содержать иконку семейного события");
        assertTrue(response.contains("👤"), 
                "Ответ должен содержать иконку персонального события (изменено с 🔒 на 👤)");
        
        verify(eventService).getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"));
    }

    @Test
    @DisplayName("Должен отображать время события")
    void shouldDisplayEventTime() {
        // Given
        Long familyId = 1L;
        ru.golubyatnikov.family.calendar.bot.model.User user = createUserWithFamily(familyId);
        
        Event event = createEvent(user, "Встреча", LocalDate.now(), LocalTime.of(14, 30));
        List<Event> events = Collections.singletonList(event);
        
        when(message.getChatId()).thenReturn(123456789L);
        when(eventService.getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"))).thenReturn(events);

        // When
        String response = handler.handle(message, user);

        // Then
        assertNotNull(response);
        assertTrue(response.contains("14:30"), "Ответ должен содержать время события");
        
        verify(eventService).getUpcomingEvents(familyId, 7, ZoneId.of("Europe/Moscow"));
    }

    /**
     * Создает пользователя с семьей.
     */
    private ru.golubyatnikov.family.calendar.bot.model.User createUserWithFamily(Long familyId) {
        Family family = Family.builder()
                .id(familyId)
                .name("Test Family")
                .build();
        
        return ru.golubyatnikov.family.calendar.bot.model.User.builder()
                .id(1L)
                .telegramId(123456789L)
                .username("test_user")
                .firstName("Иван")
                .lastName("Иванов")
                .family(family)
                .timezone("Europe/Moscow")
                .build();
    }

    /**
     * Создает другого пользователя с той же семьей.
     */
    private ru.golubyatnikov.family.calendar.bot.model.User createOtherUserWithFamily(Long familyId) {
        Family family = Family.builder()
                .id(familyId)
                .name("Test Family")
                .build();
        
        return ru.golubyatnikov.family.calendar.bot.model.User.builder()
                .id(2L)
                .telegramId(987654321L)
                .username("other_user")
                .firstName("Мария")
                .lastName("Петрова")
                .family(family)
                .build();
    }

    /**
     * Создает список тестовых событий на неделю.
     */
    private List<Event> createTestEventsForWeek(ru.golubyatnikov.family.calendar.bot.model.User user) {
        Event event1 = Event.builder()
                .id(1L)
                .title("День рождения")
                .description("Празднование дня рождения")
                .eventDate(LocalDate.now().plusDays(2))
                .eventTime(LocalTime.of(18, 0))
                .isPersonal(false)
                .user(user)
                .family(user.getFamily())
                .build();
        
        Event event2 = Event.builder()
                .id(2L)
                .title("Поход в кино")
                .description("Смотрим новый фильм")
                .eventDate(LocalDate.now().plusDays(4))
                .eventTime(LocalTime.of(20, 0))
                .isPersonal(false)
                .user(user)
                .family(user.getFamily())
                .build();
        
        return Arrays.asList(event1, event2);
    }

    /**
     * Создает событие с заданными параметрами.
     */
    private Event createEvent(ru.golubyatnikov.family.calendar.bot.model.User user, 
                             String title, LocalDate date, LocalTime time) {
        return Event.builder()
                .id(1L)
                .title(title)
                .eventDate(date)
                .eventTime(time)
                .isPersonal(false)
                .user(user)
                .family(user.getFamily())
                .build();
    }

    /**
     * Создает семейное событие.
     */
    private Event createFamilyEvent(ru.golubyatnikov.family.calendar.bot.model.User user, 
                                   String title, LocalDate date) {
        return Event.builder()
                .id(1L)
                .title(title)
                .eventDate(date)
                .eventTime(LocalTime.of(18, 0))
                .isPersonal(false)
                .user(user)
                .family(user.getFamily())
                .build();
    }

    /**
     * Создает персональное событие.
     */
    private Event createPersonalEvent(ru.golubyatnikov.family.calendar.bot.model.User user, 
                                     String title, LocalDate date) {
        return Event.builder()
                .id(2L)
                .title(title)
                .eventDate(date)
                .eventTime(LocalTime.of(10, 0))
                .isPersonal(true)
                .user(user)
                .family(user.getFamily())
                .build();
    }
}
