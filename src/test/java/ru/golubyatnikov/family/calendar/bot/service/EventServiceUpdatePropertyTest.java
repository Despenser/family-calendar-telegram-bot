package ru.golubyatnikov.family.calendar.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.model.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.FamilyRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based тесты для проверки обновления данных событий в БД.
 * 
 * <p>Тесты проверяют, что после обновления полей события через EventService,
 * изменения корректно сохраняются в базе данных.</p>
 * 
 * <p><b>Feature: event-field-editing-fix</b></p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-19
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "telegram.bot.token=test-token",
    "telegram.bot.username=TestBot",
    "telegram.bot.webhook-url=https://test.example.com/webhook",
    "telegram.bot.webhook.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EventServiceUpdatePropertyTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private EventRepository eventRepository;

    private User testUser;
    private Family testFamily;
    private Random random = new Random();

    @BeforeEach
    void setUp() {
        // Создаем тестовую семью
        testFamily = Family.builder()
            .name("Тестовая семья")
            .build();
        testFamily = familyRepository.save(testFamily);

        // Создаем тестового пользователя
        testUser = User.builder()
            .telegramId(System.currentTimeMillis()) // Уникальный ID для каждого теста
            .firstName("Тест")
            .lastName("Пользователь")
            .username("testuser")
            .family(testFamily)
            .build();
        testUser = userRepository.save(testUser);
    }

    /**
     * Property 5: Обновление данных в БД
     * 
     * <p>Для любого редактируемого поля события, после ввода нового значения,
     * соответствующее поле в базе данных должно быть обновлено с новым значением.</p>
     * 
     * <p><b>Feature: event-field-editing-fix, Property 5: Обновление данных в БД</b></p>
     * <p><b>Validates: Requirements 3.2, 4.2, 5.2</b></p>
     */
    @RepeatedTest(100)
    @DisplayName("Property 5: Название события обновляется в БД")
    void eventTitleUpdatedInDatabase() {
        // Генерируем случайные названия
        String initialTitle = generateRandomString(1, 255);
        String newTitle = generateRandomString(1, 255);
        
        // Убеждаемся, что названия разные
        while (initialTitle.equals(newTitle)) {
            newTitle = generateRandomString(1, 255);
        }
        
        // Создаем событие с начальным названием
        Event event = Event.builder()
            .user(testUser)
            .family(testFamily)
            .title(initialTitle)
            .description("Описание")
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .isPersonal(false)
            .notified(false)
            .build();
        event = eventRepository.save(event);
        
        // Обновляем название через EventService
        Event updatedEvent = eventService.updateEventTitle(event.getId(), testUser.getId(), newTitle);
        
        // Проверяем, что возвращенное событие содержит новое название
        assertThat(updatedEvent.getTitle())
            .as("Возвращенное событие должно содержать новое название")
            .isEqualTo(newTitle);
        
        // Проверяем, что изменения сохранены в БД
        Event eventFromDb = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(eventFromDb.getTitle())
            .as("Название в БД должно быть обновлено")
            .isEqualTo(newTitle);
        
        // Проверяем, что старое название больше не используется
        assertThat(eventFromDb.getTitle())
            .as("Старое название не должно быть в БД")
            .isNotEqualTo(initialTitle);
    }
    
    /**
     * Property: Обновление описания события в БД
     * 
     * <p>Для любого описания события, после обновления через EventService,
     * новое описание должно быть сохранено в базе данных.</p>
     * 
     * <p><b>Validates: Requirements 5.2</b></p>
     */
    @RepeatedTest(100)
    @DisplayName("Описание события обновляется в БД")
    void eventDescriptionUpdatedInDatabase() {
        // Генерируем случайные описания
        String initialDescription = generateRandomString(0, 100);
        String newDescription = generateRandomString(0, 100);
        
        // Убеждаемся, что описания разные
        while (initialDescription.equals(newDescription)) {
            newDescription = generateRandomString(0, 100);
        }
        
        // Создаем событие с начальным описанием
        Event event = Event.builder()
            .user(testUser)
            .family(testFamily)
            .title("Тестовое событие")
            .description(initialDescription)
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .isPersonal(false)
            .notified(false)
            .build();
        event = eventRepository.save(event);
        
        // Обновляем описание через EventService
        Event updatedEvent = eventService.updateEventDescription(event.getId(), testUser.getId(), newDescription);
        
        // Проверяем, что возвращенное событие содержит новое описание
        assertThat(updatedEvent.getDescription())
            .as("Возвращенное событие должно содержать новое описание")
            .isEqualTo(newDescription);
        
        // Проверяем, что изменения сохранены в БД
        Event eventFromDb = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(eventFromDb.getDescription())
            .as("Описание в БД должно быть обновлено")
            .isEqualTo(newDescription);
        
        // Проверяем, что старое описание больше не используется
        assertThat(eventFromDb.getDescription())
            .as("Старое описание не должно быть в БД")
            .isNotEqualTo(initialDescription);
    }
    
    /**
     * Property: Обновление даты события в БД
     * 
     * <p>Для любой даты события, после обновления через EventService,
     * новая дата должна быть сохранена в базе данных.</p>
     * 
     * <p><b>Validates: Requirements 3.2</b></p>
     */
    @RepeatedTest(100)
    @DisplayName("Дата события обновляется в БД")
    void eventDateUpdatedInDatabase() {
        // Генерируем случайные даты
        LocalDate initialDate = generateFutureDate();
        LocalDate newDate = generateFutureDate();
        
        // Убеждаемся, что даты разные
        while (initialDate.equals(newDate)) {
            newDate = generateFutureDate();
        }
        
        // Создаем событие с начальной датой
        Event event = Event.builder()
            .user(testUser)
            .family(testFamily)
            .title("Тестовое событие")
            .description("Описание")
            .eventDate(initialDate)
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .isPersonal(false)
            .notified(false)
            .build();
        event = eventRepository.save(event);
        
        // Обновляем дату через EventService
        Event updatedEvent = eventService.updateEventDate(event.getId(), testUser.getId(), newDate);
        
        // Проверяем, что возвращенное событие содержит новую дату
        assertThat(updatedEvent.getEventDate())
            .as("Возвращенное событие должно содержать новую дату")
            .isEqualTo(newDate);
        
        // Проверяем, что изменения сохранены в БД
        Event eventFromDb = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(eventFromDb.getEventDate())
            .as("Дата в БД должна быть обновлена")
            .isEqualTo(newDate);
        
        // Проверяем, что старая дата больше не используется
        assertThat(eventFromDb.getEventDate())
            .as("Старая дата не должна быть в БД")
            .isNotEqualTo(initialDate);
    }
    
    /**
     * Property: Обновление времени события в БД
     * 
     * <p>Для любого времени события, после обновления через EventService,
     * новое время должно быть сохранено в базе данных.</p>
     * 
     * <p><b>Validates: Requirements 4.2</b></p>
     */
    @RepeatedTest(100)
    @DisplayName("Время события обновляется в БД")
    void eventTimeUpdatedInDatabase() {
        // Генерируем случайные времена
        LocalTime initialTime = generateRandomTime();
        LocalTime newTime = generateRandomTime();
        
        // Убеждаемся, что времена разные
        while (initialTime.equals(newTime)) {
            newTime = generateRandomTime();
        }
        
        // Создаем событие с начальным временем
        Event event = Event.builder()
            .user(testUser)
            .family(testFamily)
            .title("Тестовое событие")
            .description("Описание")
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(initialTime)
            .status(EventStatus.ACTIVE)
            .isPersonal(false)
            .notified(false)
            .build();
        event = eventRepository.save(event);
        
        // Обновляем время через EventService
        Event updatedEvent = eventService.updateEventTime(event.getId(), testUser.getId(), newTime);
        
        // Проверяем, что возвращенное событие содержит новое время
        assertThat(updatedEvent.getEventTime())
            .as("Возвращенное событие должно содержать новое время")
            .isEqualTo(newTime);
        
        // Проверяем, что изменения сохранены в БД
        Event eventFromDb = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(eventFromDb.getEventTime())
            .as("Время в БД должно быть обновлено")
            .isEqualTo(newTime);
        
        // Проверяем, что старое время больше не используется
        assertThat(eventFromDb.getEventTime())
            .as("Старое время не должно быть в БД")
            .isNotEqualTo(initialTime);
    }
    
    /**
     * Генерирует случайную строку заданной длины.
     */
    private String generateRandomString(int minLength, int maxLength) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        if (length == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder(length);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 ";
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * Генерирует случайную будущую дату.
     */
    private LocalDate generateFutureDate() {
        LocalDate today = LocalDate.now();
        int daysToAdd = random.nextInt(365);
        return today.plusDays(daysToAdd);
    }
    
    /**
     * Генерирует случайное время.
     */
    private LocalTime generateRandomTime() {
        int hour = random.nextInt(24);
        int minute = random.nextInt(60);
        return LocalTime.of(hour, minute);
    }
}
