package ru.golubyatnikov.family.calendar.bot.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.FamilyRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.EventService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест для редактирования даты события.
 * 
 * <p>Проверяет полный цикл редактирования даты события:</p>
 * <ul>
 *   <li>Создание события</li>
 *   <li>Начало редактирования даты</li>
 *   <li>Выбор новой даты</li>
 *   <li>Проверка обновления в БД</li>
 *   <li>Проверка, что messageId не изменился</li>
 * </ul>
 * 
 * <p>Использует Testcontainers для PostgreSQL для обеспечения изоляции тестов
 * и работы с реальной базой данных.</p>
 * 
 * <p><b>Требования:</b> 1.3, 3.2, 3.3</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-19
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "telegram.bot.webhook.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
@Transactional
class EventDateEditingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private EventService eventService;

    @Autowired
    private ConversationStateService conversationStateService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    private User testUser;
    private Family testFamily;

    @BeforeEach
    void setUp() {
        // Создание тестовой семьи
        testFamily = Family.builder()
                .name("Тестовая семья")
                .build();
        testFamily = familyRepository.save(testFamily);

        // Создание тестового пользователя
        testUser = User.builder()
                .telegramId(12345L)
                .username("testuser")
                .firstName("Тест")
                .lastName("Пользователь")
                .family(testFamily)
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Должен обновить дату события и сохранить messageId")
    void shouldUpdateEventDateAndPreserveMessageId() {
        // Given: Создаем событие с messageId
        LocalDate originalDate = LocalDate.now().plusDays(5);
        LocalTime eventTime = LocalTime.of(14, 30);
        Integer originalMessageId = 12345;

        Event event = Event.builder()
                .user(testUser)
                .family(testFamily)
                .title("Тестовое событие")
                .description("Описание тестового события")
                .eventDate(originalDate)
                .eventTime(eventTime)
                .status(Event.EventStatus.ACTIVE)
                .isPersonal(false)
                .notified(false)
                .messageId((long) originalMessageId)
                .build();

        event = eventRepository.save(event);
        Long eventId = event.getId();

        // When: Начинаем редактирование даты
        conversationStateService.startEventEditing(
                testUser.getId(),
                eventId,
                testUser.getTelegramId(),
                originalMessageId
        );

        // Проверяем, что messageId сохранен в контексте
        Integer contextMessageId = conversationStateService.getEditingMessageId(testUser.getId());
        assertThat(contextMessageId).isEqualTo(originalMessageId);

        // Выбираем новую дату
        LocalDate newDate = LocalDate.now().plusDays(10);
        Event updatedEvent = eventService.updateEventDate(eventId, testUser.getId(), newDate);

        // Then: Проверяем обновление в БД
        Event eventFromDb = eventRepository.findById(eventId).orElseThrow();

        assertThat(eventFromDb.getEventDate())
                .as("Дата события должна быть обновлена в БД")
                .isEqualTo(newDate);

        assertThat(eventFromDb.getMessageId())
                .as("MessageId должен остаться неизменным")
                .isEqualTo((long) originalMessageId);

        assertThat(eventFromDb.getEventTime())
                .as("Время события не должно измениться")
                .isEqualTo(eventTime);

        assertThat(eventFromDb.getTitle())
                .as("Название события не должно измениться")
                .isEqualTo("Тестовое событие");

        // Проверяем, что возвращенное событие также содержит правильные данные
        assertThat(updatedEvent.getEventDate()).isEqualTo(newDate);
        assertThat(updatedEvent.getMessageId()).isEqualTo((long) originalMessageId);
    }

    @Test
    @DisplayName("Должен корректно обработать редактирование даты без messageId")
    void shouldHandleDateEditingWithoutMessageId() {
        // Given: Создаем событие без messageId (старое событие)
        LocalDate originalDate = LocalDate.now().plusDays(3);
        LocalTime eventTime = LocalTime.of(10, 0);

        Event event = Event.builder()
                .user(testUser)
                .family(testFamily)
                .title("Событие без messageId")
                .eventDate(originalDate)
                .eventTime(eventTime)
                .status(Event.EventStatus.ACTIVE)
                .isPersonal(false)
                .notified(false)
                .messageId(null)  // Нет messageId
                .build();

        event = eventRepository.save(event);
        Long eventId = event.getId();

        // When: Начинаем редактирование без messageId
        conversationStateService.startEventEditing(
                testUser.getId(),
                eventId,
                testUser.getTelegramId(),
                null
        );

        // Выбираем новую дату
        LocalDate newDate = LocalDate.now().plusDays(7);
        Event updatedEvent = eventService.updateEventDate(eventId, testUser.getId(), newDate);

        // Then: Проверяем обновление в БД
        Event eventFromDb = eventRepository.findById(eventId).orElseThrow();

        assertThat(eventFromDb.getEventDate())
                .as("Дата события должна быть обновлена")
                .isEqualTo(newDate);

        assertThat(eventFromDb.getMessageId())
                .as("MessageId должен остаться null")
                .isNull();
    }

    @Test
    @DisplayName("Должен сохранить messageId при множественных редактированиях")
    void shouldPreserveMessageIdThroughMultipleEdits() {
        // Given: Создаем событие
        LocalDate originalDate = LocalDate.now().plusDays(2);
        Integer messageId = 99999;

        Event event = Event.builder()
                .user(testUser)
                .family(testFamily)
                .title("Событие для множественных редактирований")
                .eventDate(originalDate)
                .eventTime(LocalTime.of(15, 0))
                .status(Event.EventStatus.ACTIVE)
                .messageId((long) messageId)
                .build();

        event = eventRepository.save(event);
        Long eventId = event.getId();

        // When: Редактируем дату несколько раз
        conversationStateService.startEventEditing(
                testUser.getId(),
                eventId,
                testUser.getTelegramId(),
                messageId
        );

        // Первое редактирование
        LocalDate firstNewDate = LocalDate.now().plusDays(5);
        eventService.updateEventDate(eventId, testUser.getId(), firstNewDate);

        // Второе редактирование
        conversationStateService.startEventEditing(
                testUser.getId(),
                eventId,
                testUser.getTelegramId(),
                messageId
        );

        LocalDate secondNewDate = LocalDate.now().plusDays(8);
        eventService.updateEventDate(eventId, testUser.getId(), secondNewDate);

        // Then: Проверяем, что messageId сохранился после всех редактирований
        Event eventFromDb = eventRepository.findById(eventId).orElseThrow();

        assertThat(eventFromDb.getEventDate())
                .as("Дата должна быть обновлена до последнего значения")
                .isEqualTo(secondNewDate);

        assertThat(eventFromDb.getMessageId())
                .as("MessageId должен остаться неизменным после множественных редактирований")
                .isEqualTo((long) messageId);
    }
}
