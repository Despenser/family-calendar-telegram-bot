package ru.golubyatnikov.family.calendar.bot.service;

import jakarta.validation.ConstraintViolationException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.model.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.FamilyRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;
import ru.golubyatnikov.family.calendar.bot.service.event.EventService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based тест для проверки Bean Validation в EventService.
 * 
 * <p><b>Property 7: Bean Validation Enforcement</b></p>
 * <p>*For any* невалидные входные данные (пустой title, null eventDate, слишком длинное description),
 * сервис SHALL выбрасывать ConstraintViolationException.</p>
 * 
 * <p><b>Validates: Requirements 8.2</b></p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
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
class EventServiceBeanValidationPropertyTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private EventRepository eventRepository;

    private User testUser;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        // Создаем тестовую семью
        Family testFamily = Family.builder()
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

        // Создаем тестовое событие для тестов обновления
        testEvent = Event.builder()
            .user(testUser)
            .family(testFamily)
            .title("Тестовое событие")
            .description("Описание")
            .eventDate(LocalDate.now().plusDays(1))
            .eventTime(LocalTime.of(10, 0))
            .status(EventStatus.ACTIVE)
            .isPersonal(false)
            .notified(false)
            .build();
        testEvent = eventRepository.save(testEvent);
    }

    // ==================== Property 7: Bean Validation Enforcement ====================

    @Test
    @DisplayName("Property 7: createEvent должен выбрасывать ConstraintViolationException при null userId")
    void createEvent_shouldThrowConstraintViolationException_whenUserIdIsNull() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        LocalDateTime futureDateTime = LocalDateTime.now().plusDays(1);
        
        assertThatThrownBy(() -> 
            eventService.createEvent(null, "Название", "Описание", futureDateTime))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("userId не может быть null");
    }

    @Test
    @DisplayName("Property 7: createEvent должен выбрасывать ConstraintViolationException при пустом title")
    void createEvent_shouldThrowConstraintViolationException_whenTitleIsBlank() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        LocalDateTime futureDateTime = LocalDateTime.now().plusDays(1);
        
        assertThatThrownBy(() -> 
            eventService.createEvent(testUser.getId(), "", "Описание", futureDateTime))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Название события не может быть пустым");
    }

    @Test
    @DisplayName("Property 7: createEvent должен выбрасывать ConstraintViolationException при null title")
    void createEvent_shouldThrowConstraintViolationException_whenTitleIsNull() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        LocalDateTime futureDateTime = LocalDateTime.now().plusDays(1);
        
        assertThatThrownBy(() -> 
            eventService.createEvent(testUser.getId(), null, "Описание", futureDateTime))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Название события не может быть пустым");
    }

    @Test
    @DisplayName("Property 7: createEvent должен выбрасывать ConstraintViolationException при null eventDateTime")
    void createEvent_shouldThrowConstraintViolationException_whenEventDateTimeIsNull() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        assertThatThrownBy(() -> 
            eventService.createEvent(testUser.getId(), "Название", "Описание", null))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Дата и время события не могут быть null");
    }

    @Test
    @DisplayName("Property 7: createEvent должен выбрасывать ConstraintViolationException при слишком длинном title")
    void createEvent_shouldThrowConstraintViolationException_whenTitleTooLong() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        String longTitle = "A".repeat(256); // 256 символов, максимум 255
        LocalDateTime futureDateTime = LocalDateTime.now().plusDays(1);
        
        assertThatThrownBy(() -> 
            eventService.createEvent(testUser.getId(), longTitle, "Описание", futureDateTime))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Название события не может превышать 255 символов");
    }

    @Test
    @DisplayName("Property 7: createEvent должен выбрасывать ConstraintViolationException при слишком длинном description")
    void createEvent_shouldThrowConstraintViolationException_whenDescriptionTooLong() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        String longDescription = "A".repeat(2001); // 2001 символ, максимум 2000
        LocalDateTime futureDateTime = LocalDateTime.now().plusDays(1);
        
        assertThatThrownBy(() -> 
            eventService.createEvent(testUser.getId(), "Название", longDescription, futureDateTime))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Описание события не может превышать 2000 символов");
    }

    @Test
    @DisplayName("Property 7: updateEventTitle должен выбрасывать ConstraintViolationException при пустом title")
    void updateEventTitle_shouldThrowConstraintViolationException_whenTitleIsBlank() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        assertThatThrownBy(() -> 
            eventService.updateEventTitle(testEvent.getId(), testUser.getId(), ""))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Название события не может быть пустым");
    }

    @Test
    @DisplayName("Property 7: updateEventTitle должен выбрасывать ConstraintViolationException при слишком длинном title")
    void updateEventTitle_shouldThrowConstraintViolationException_whenTitleTooLong() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        String longTitle = "A".repeat(256);
        
        assertThatThrownBy(() -> 
            eventService.updateEventTitle(testEvent.getId(), testUser.getId(), longTitle))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Название события не может превышать 255 символов");
    }

    @Test
    @DisplayName("Property 7: updateEventDate должен выбрасывать ConstraintViolationException при null date")
    void updateEventDate_shouldThrowConstraintViolationException_whenDateIsNull() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        assertThatThrownBy(() -> 
            eventService.updateEventDate(testEvent.getId(), testUser.getId(), null))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Дата события не может быть null");
    }

    @Test
    @DisplayName("Property 7: updateEventTime должен выбрасывать ConstraintViolationException при null time")
    void updateEventTime_shouldThrowConstraintViolationException_whenTimeIsNull() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        assertThatThrownBy(() -> 
            eventService.updateEventTime(testEvent.getId(), testUser.getId(), null))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Время события не может быть null");
    }

    @Test
    @DisplayName("Property 7: updateEventDescription должен выбрасывать ConstraintViolationException при слишком длинном description")
    void updateEventDescription_shouldThrowConstraintViolationException_whenDescriptionTooLong() {
        // Feature: code-quality-refactoring, Property 7: Bean Validation Enforcement
        // Validates: Requirements 8.2
        
        String longDescription = "A".repeat(2001);
        
        assertThatThrownBy(() -> 
            eventService.updateEventDescription(testEvent.getId(), testUser.getId(), longDescription))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("Описание события не может превышать 2000 символов");
    }

    // ==================== Позитивные тесты для валидных данных ====================

    @Test
    @DisplayName("createEvent должен успешно создавать событие с валидными данными")
    void createEvent_shouldSucceed_withValidData() {
        LocalDateTime futureDateTime = LocalDateTime.now().plusDays(1);
        
        Event event = eventService.createEvent(
            testUser.getId(), 
            "Валидное название", 
            "Валидное описание", 
            futureDateTime
        );
        
        assertThat(event).isNotNull();
        assertThat(event.getId()).isNotNull();
        assertThat(event.getTitle()).isEqualTo("Валидное название");
    }

    @Test
    @DisplayName("createEvent должен успешно создавать событие с максимально допустимой длиной title")
    void createEvent_shouldSucceed_withMaxLengthTitle() {
        String maxTitle = "A".repeat(255); // Ровно 255 символов
        LocalDateTime futureDateTime = LocalDateTime.now().plusDays(1);
        
        Event event = eventService.createEvent(
            testUser.getId(), 
            maxTitle, 
            "Описание", 
            futureDateTime
        );
        
        assertThat(event).isNotNull();
        assertThat(event.getTitle()).hasSize(255);
    }

    @Test
    @DisplayName("createEvent должен успешно создавать событие с максимально допустимой длиной description")
    void createEvent_shouldSucceed_withMaxLengthDescription() {
        String maxDescription = "A".repeat(2000); // Ровно 2000 символов
        LocalDateTime futureDateTime = LocalDateTime.now().plusDays(1);
        
        Event event = eventService.createEvent(
            testUser.getId(), 
            "Название", 
            maxDescription, 
            futureDateTime
        );
        
        assertThat(event).isNotNull();
        assertThat(event.getDescription()).hasSize(2000);
    }

    @Test
    @DisplayName("createEvent должен успешно создавать событие с null description")
    void createEvent_shouldSucceed_withNullDescription() {
        LocalDateTime futureDateTime = LocalDateTime.now().plusDays(1);
        
        Event event = eventService.createEvent(
            testUser.getId(), 
            "Название", 
            null, 
            futureDateTime
        );
        
        assertThat(event).isNotNull();
        assertThat(event.getDescription()).isNull();
    }
}
