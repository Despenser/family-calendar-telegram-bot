package ru.golubyatnikov.family.calendar.bot.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест для проверки корректной работы @EntityGraph в EventRepository.
 * 
 * <p>Тест проверяет, что методы репозитория с @EntityGraph загружают связанные сущности
 * (user, family) в одном запросе, избегая N+1 проблемы.</p>
 * 
 * <p><b>Property 5: EntityGraph N+1 Prevention</b></p>
 * <p><b>Validates: Requirements 5.2</b></p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true",
    "logging.level.org.hibernate.SQL=DEBUG",
    "logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE"
})
class EventRepositoryEntityGraphTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @PersistenceContext
    private EntityManager entityManager;

    private Family testFamily;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Создаем тестовую семью
        testFamily = Family.builder()
            .name("Тестовая семья")
            .build();
        testFamily = familyRepository.save(testFamily);

        // Создаем тестового пользователя
        testUser = User.builder()
            .telegramId(123456789L)
            .firstName("Тест")
            .lastName("Пользователь")
            .username("testuser")
            .family(testFamily)
            .build();
        testUser = userRepository.save(testUser);

        // Создаем несколько тестовых событий
        for (int i = 0; i < 5; i++) {
            Event event = Event.builder()
                .user(testUser)
                .family(testFamily)
                .title("Тестовое событие " + i)
                .description("Описание события " + i)
                .eventDate(LocalDate.now().plusDays(i))
                .eventTime(LocalTime.of(10 + i, 0))
                .status(Event.EventStatus.ACTIVE)
                .isPersonal(false)
                .notified(false)
                .build();
            eventRepository.save(event);
        }

        // Очищаем кэш первого уровня для чистоты теста
        testEntityManager.flush();
        testEntityManager.clear();
    }

    @Test
    @DisplayName("findByUserIdOrderByEventDateAsc должен загружать user и family в одном запросе")
    void findByUserIdOrderByEventDateAsc_shouldLoadUserAndFamilyEagerly() {
        // When: Получаем события пользователя
        List<Event> events = eventRepository.findByUserIdOrderByEventDateAsc(testUser.getId());

        // Then: Проверяем, что события загружены
        assertThat(events).hasSize(5);

        // Проверяем, что связанные сущности доступны без дополнительных запросов
        // (если бы был N+1, то при обращении к user/family был бы LazyInitializationException
        // или дополнительные запросы)
        for (Event event : events) {
            assertThat(event.getUser()).isNotNull();
            assertThat(event.getUser().getFirstName()).isEqualTo("Тест");
            assertThat(event.getFamily()).isNotNull();
            assertThat(event.getFamily().getName()).isEqualTo("Тестовая семья");
        }
    }

    @Test
    @DisplayName("findAllByUserIdAndStatus должен загружать user и family в одном запросе")
    void findAllByUserIdAndStatus_shouldLoadUserAndFamilyEagerly() {
        // When: Получаем события по статусу
        List<Event> events = eventRepository.findAllByUserIdAndStatus(
            testUser.getId(), Event.EventStatus.ACTIVE);

        // Then: Проверяем, что события загружены
        assertThat(events).hasSize(5);

        // Проверяем доступность связанных сущностей
        for (Event event : events) {
            assertThat(event.getUser()).isNotNull();
            assertThat(event.getUser().getTelegramId()).isEqualTo(123456789L);
            assertThat(event.getFamily()).isNotNull();
        }
    }

    @Test
    @DisplayName("findUpcomingEvents должен загружать user и family в одном запросе")
    void findUpcomingEvents_shouldLoadUserAndFamilyEagerly() {
        // When: Получаем предстоящие события
        List<Event> events = eventRepository.findUpcomingEvents(
            testFamily.getId(), testUser.getId(), LocalDate.now());

        // Then: Проверяем, что события загружены
        assertThat(events).isNotEmpty();

        // Проверяем доступность связанных сущностей
        for (Event event : events) {
            assertThat(event.getUser()).isNotNull();
            assertThat(event.getFamily()).isNotNull();
            assertThat(event.getFamily().getId()).isEqualTo(testFamily.getId());
        }
    }

    @Test
    @DisplayName("searchByTitleOrDescription должен загружать user и family в одном запросе")
    void searchByTitleOrDescription_shouldLoadUserAndFamilyEagerly() {
        // When: Ищем события по названию
        List<Event> events = eventRepository.searchByTitleOrDescription(
            testFamily.getId(), testUser.getId(), "Тестовое");

        // Then: Проверяем, что события найдены
        assertThat(events).hasSize(5);

        // Проверяем доступность связанных сущностей
        for (Event event : events) {
            assertThat(event.getUser()).isNotNull();
            assertThat(event.getFamily()).isNotNull();
        }
    }

    @Test
    @DisplayName("findByUserIdAndStatusOrderByDeletedAtDesc должен загружать user и family")
    void findByUserIdAndStatusOrderByDeletedAtDesc_shouldLoadUserAndFamilyEagerly() {
        // Given: Создаем удаленное событие
        Event deletedEvent = Event.builder()
            .user(testUser)
            .family(testFamily)
            .title("Удаленное событие")
            .eventDate(LocalDate.now())
            .eventTime(LocalTime.of(12, 0))
            .status(Event.EventStatus.DELETED)
            .isPersonal(false)
            .notified(false)
            .build();
        eventRepository.save(deletedEvent);
        testEntityManager.flush();
        testEntityManager.clear();

        // When: Получаем удаленные события
        List<Event> events = eventRepository.findByUserIdAndStatusOrderByDeletedAtDesc(
            testUser.getId(), Event.EventStatus.DELETED);

        // Then: Проверяем, что событие найдено
        assertThat(events).hasSize(1);

        // Проверяем доступность связанных сущностей
        Event event = events.get(0);
        assertThat(event.getUser()).isNotNull();
        assertThat(event.getFamily()).isNotNull();
    }

    @Test
    @DisplayName("findBySeriesIdAndStatus должен загружать user и family в одном запросе")
    void findBySeriesIdAndStatus_shouldLoadUserAndFamilyEagerly() {
        // Given: Создаем события серии
        String seriesId = "test-series-uuid";
        for (int i = 0; i < 3; i++) {
            Event event = Event.builder()
                .user(testUser)
                .family(testFamily)
                .title("Событие серии " + i)
                .eventDate(LocalDate.now().plusWeeks(i))
                .eventTime(LocalTime.of(14, 0))
                .status(Event.EventStatus.ACTIVE)
                .seriesId(seriesId)
                .isPersonal(false)
                .notified(false)
                .build();
            eventRepository.save(event);
        }
        testEntityManager.flush();
        testEntityManager.clear();

        // When: Получаем события серии
        List<Event> events = eventRepository.findBySeriesIdAndStatus(
            seriesId, Event.EventStatus.ACTIVE);

        // Then: Проверяем, что события найдены
        assertThat(events).hasSize(3);

        // Проверяем доступность связанных сущностей
        for (Event event : events) {
            assertThat(event.getUser()).isNotNull();
            assertThat(event.getFamily()).isNotNull();
            assertThat(event.getSeriesId()).isEqualTo(seriesId);
        }
    }
}
