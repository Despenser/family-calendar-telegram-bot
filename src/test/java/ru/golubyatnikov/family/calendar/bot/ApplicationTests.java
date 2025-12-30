package ru.golubyatnikov.family.calendar.bot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.FamilyRepository;
import ru.golubyatnikov.family.calendar.bot.repository.UserRepository;

/**
 * Базовый тест для проверки загрузки Spring контекста.
 * 
 * <p>Этот тест проверяет, что приложение корректно настроено
 * и Spring контекст может быть успешно загружен со всеми
 * необходимыми бинами и конфигурациями.</p>
 * 
 * <p>На данном этапе тест отключает автоконфигурацию БД,
 * так как миграции и конфигурация будут добавлены позже.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
    "telegram.bot.webhook.enabled=false"
})
class ApplicationTests {

    @MockBean
    private EventRepository eventRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private FamilyRepository familyRepository;

    /**
     * Проверяет, что Spring контекст загружается без ошибок.
     * 
     * <p>Если этот тест проходит успешно, это означает, что:</p>
     * <ul>
     *   <li>Все необходимые зависимости присутствуют</li>
     *   <li>Конфигурация приложения корректна</li>
     *   <li>Все Spring бины могут быть созданы</li>
     * </ul>
     */
    @Test
    void contextLoads() {
        // Тест проходит, если контекст загружается без исключений
    }
}
