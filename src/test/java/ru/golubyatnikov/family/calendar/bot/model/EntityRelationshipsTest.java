package ru.golubyatnikov.family.calendar.bot.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit тесты для проверки корректности JPA Entity классов и их relationships.
 * 
 * <p>Проверяет:</p>
 * <ul>
 *   <li>Создание entity объектов</li>
 *   <li>Корректность relationships (@ManyToOne, @OneToMany)</li>
 *   <li>Работу вспомогательных методов</li>
 *   <li>Автоматическую установку created_at через @PrePersist</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@DisplayName("Entity Relationships Tests")
class EntityRelationshipsTest {

    @Test
    @DisplayName("Должен создать Family entity с корректными полями")
    void shouldCreateFamilyEntity() {
        // Given
        Family family = Family.builder()
                .name("Тестовая семья")
                .build();
        
        // When
        family.onCreate();
        
        // Then
        assertNotNull(family);
        assertEquals("Тестовая семья", family.getName());
        assertNotNull(family.getMembers());
        assertTrue(family.getMembers().isEmpty());
        assertNotNull(family.getCreatedAt());
    }

    @Test
    @DisplayName("Должен создать User entity с корректными полями")
    void shouldCreateUserEntity() {
        // Given
        User user = User.builder()
                .telegramId(123456789L)
                .username("testuser")
                .firstName("Иван")
                .lastName("Иванов")
                .build();
        
        // When
        user.onCreate();
        
        // Then
        assertNotNull(user);
        assertEquals(123456789L, user.getTelegramId());
        assertEquals("testuser", user.getUsername());
        assertEquals("Иван", user.getFirstName());
        assertEquals("Иванов", user.getLastName());
        assertNotNull(user.getCreatedAt());
        assertNull(user.getFamily());
    }

    @Test
    @DisplayName("Должен создать Event entity с корректными полями")
    void shouldCreateEventEntity() {
        // Given
        LocalDate eventDate = LocalDate.of(2025, 12, 31);
        LocalTime eventTime = LocalTime.of(18, 0);
        
        Event event = Event.builder()
                .title("Новогодний ужин")
                .description("Семейный ужин в честь Нового года")
                .eventDate(eventDate)
                .eventTime(eventTime)
                .notified(false)
                .build();
        
        // When
        event.onCreate();
        
        // Then
        assertNotNull(event);
        assertEquals("Новогодний ужин", event.getTitle());
        assertEquals("Семейный ужин в честь Нового года", event.getDescription());
        assertEquals(eventDate, event.getEventDate());
        assertEquals(eventTime, event.getEventTime());
        assertFalse(event.getNotified());
        assertNotNull(event.getCreatedAt());
    }

    @Test
    @DisplayName("Должен установить двустороннюю связь между Family и User")
    void shouldEstablishBidirectionalRelationshipBetweenFamilyAndUser() {
        // Given
        Family family = Family.builder()
                .name("Семья Ивановых")
                .build();
        family.onCreate();
        
        User user = User.builder()
                .telegramId(123456789L)
                .firstName("Иван")
                .build();
        user.onCreate();
        
        // When
        family.addMember(user);
        
        // Then
        assertTrue(family.getMembers().contains(user));
        assertEquals(family, user.getFamily());
        assertEquals(1, family.getMembers().size());
    }

    @Test
    @DisplayName("Должен удалить связь между Family и User")
    void shouldRemoveBidirectionalRelationshipBetweenFamilyAndUser() {
        // Given
        Family family = Family.builder()
                .name("Семья Ивановых")
                .build();
        family.onCreate();
        
        User user = User.builder()
                .telegramId(123456789L)
                .firstName("Иван")
                .build();
        user.onCreate();
        
        family.addMember(user);
        
        // When
        family.removeMember(user);
        
        // Then
        assertFalse(family.getMembers().contains(user));
        assertNull(user.getFamily());
        assertEquals(0, family.getMembers().size());
    }

    @Test
    @DisplayName("Должен установить связь между Event, User и Family")
    void shouldEstablishRelationshipsBetweenEventUserAndFamily() {
        // Given
        Family family = Family.builder()
                .name("Семья Ивановых")
                .build();
        family.onCreate();
        
        User user = User.builder()
                .telegramId(123456789L)
                .firstName("Иван")
                .family(family)
                .build();
        user.onCreate();
        
        Event event = Event.builder()
                .title("День рождения")
                .eventDate(LocalDate.of(2025, 12, 31))
                .eventTime(LocalTime.of(18, 0))
                .user(user)
                .family(family)
                .build();
        event.onCreate();
        
        // Then
        assertEquals(user, event.getUser());
        assertEquals(family, event.getFamily());
        assertEquals(family, user.getFamily());
    }

    @Test
    @DisplayName("Должен корректно работать метод User.getFullName()")
    void shouldReturnFullNameForUser() {
        // Given
        User userWithLastName = User.builder()
                .firstName("Иван")
                .lastName("Иванов")
                .build();
        
        User userWithoutLastName = User.builder()
                .firstName("Петр")
                .build();
        
        // When & Then
        assertEquals("Иван Иванов", userWithLastName.getFullName());
        assertEquals("Петр", userWithoutLastName.getFullName());
    }

    @Test
    @DisplayName("Должен корректно работать метод User.hasFamily()")
    void shouldCheckIfUserHasFamily() {
        // Given
        Family family = Family.builder()
                .name("Семья")
                .build();
        
        User userWithFamily = User.builder()
                .firstName("Иван")
                .family(family)
                .build();
        
        User userWithoutFamily = User.builder()
                .firstName("Петр")
                .build();
        
        // When & Then
        assertTrue(userWithFamily.hasFamily());
        assertFalse(userWithoutFamily.hasFamily());
    }

    @Test
    @DisplayName("Должен корректно работать метод Event.getEventDateTime()")
    void shouldReturnEventDateTime() {
        // Given
        LocalDate date = LocalDate.of(2025, 12, 31);
        LocalTime time = LocalTime.of(18, 30);
        
        Event event = Event.builder()
                .eventDate(date)
                .eventTime(time)
                .build();
        
        // When
        LocalDateTime eventDateTime = event.getEventDateTime();
        
        // Then
        assertEquals(LocalDateTime.of(2025, 12, 31, 18, 30), eventDateTime);
    }

    @Test
    @DisplayName("Должен корректно форматировать дату и время события")
    void shouldFormatEventDateAndTime() {
        // Given
        Event event = Event.builder()
                .eventDate(LocalDate.of(2025, 12, 31))
                .eventTime(LocalTime.of(18, 30))
                .build();
        
        // When & Then
        assertEquals("31.12.2025", event.getFormattedDate());
        assertEquals("18:30", event.getFormattedTime());
    }

    @Test
    @DisplayName("Должен корректно определять, что событие в будущем")
    void shouldDetermineEventIsInFuture() {
        // Given
        Event futureEvent = Event.builder()
                .eventDate(LocalDate.now().plusDays(1))
                .eventTime(LocalTime.of(12, 0))
                .build();
        
        // When & Then
        assertTrue(futureEvent.isFuture());
        assertFalse(futureEvent.isPast());
    }

    @Test
    @DisplayName("Должен корректно определять, что событие в прошлом")
    void shouldDetermineEventIsInPast() {
        // Given
        Event pastEvent = Event.builder()
                .eventDate(LocalDate.now().minusDays(1))
                .eventTime(LocalTime.of(12, 0))
                .build();
        
        // When & Then
        assertTrue(pastEvent.isPast());
        assertFalse(pastEvent.isFuture());
    }

    @Test
    @DisplayName("Должен корректно проверять принадлежность события пользователю")
    void shouldCheckIfEventBelongsToUser() {
        // Given
        User user = User.builder()
                .id(1L)
                .firstName("Иван")
                .build();
        
        Event event = Event.builder()
                .user(user)
                .build();
        
        // When & Then
        assertTrue(event.belongsToUser(1L));
        assertFalse(event.belongsToUser(2L));
    }

    @Test
    @DisplayName("Должен корректно устанавливать значение по умолчанию для notified")
    void shouldSetDefaultValueForNotified() {
        // Given & When
        Event event = Event.builder()
                .title("Тест")
                .eventDate(LocalDate.now())
                .eventTime(LocalTime.now())
                .build();
        
        // Then
        assertFalse(event.getNotified());
    }
}
