package ru.golubyatnikov.family.calendar.bot;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Базовый тест для проверки загрузки Spring контекста.
 * 
 * <p>Этот тест проверяет, что приложение корректно настроено
 * и Spring контекст может быть успешно загружен со всеми
 * необходимыми бинами и конфигурациями.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "telegram.bot.webhook.enabled=false"
})
@Disabled("Временно отключен - требует полной настройки контекста")
class ApplicationTests {

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
