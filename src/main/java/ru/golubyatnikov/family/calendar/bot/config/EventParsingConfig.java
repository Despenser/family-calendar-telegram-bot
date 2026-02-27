package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация для парсинга событий через AI.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-27
 */
@Configuration
@ConfigurationProperties(prefix = "app.event-parsing")
@Getter
@Setter
public class EventParsingConfig {

    /**
     * Время жизни сессии парсинга в минутах
     */
    private int sessionTimeoutMinutes = 30;

    /**
     * Интервал очистки устаревших сессий в миллисекундах
     */
    private long cleanupIntervalMs = 600000; // 10 минут

    /**
     * Максимальное количество сообщений в истории диалога
     */
    private int maxConversationHistorySize = 10;

    /**
     * Системный промпт для агента-парсера
     */
    private String parserSystemPrompt = """
            Ты - ассистент для извлечения информации о событиях календаря из текста пользователя.
            Твоя задача - извлечь название события, дату и время.
            
            Правила:
            1. Дата должна быть в формате dd.MM.yyyy
            2. Время должно быть в формате HH:mm
            3. Если пользователь указывает относительную дату (сегодня, завтра, послезавтра), преобразуй её в конкретную дату
            4. Если какие-то данные отсутствуют, задай уточняющий вопрос пользователю
            5. Название события должно быть кратким и понятным
            6. Всегда отвечай на русском языке
            
            Текущая дата: %s
            Завтрашняя дата: %s
            """;

    /**
     * Системный промпт для агента-валидатора (Judge)
     */
    private String judgeSystemPrompt = """
            Ты - валидатор извлеченных данных о событии календаря.
            Твоя задача - проверить корректность и полноту данных, извлеченных другим агентом.
            
            Проверь:
            1. Наличие всех обязательных полей: название, дата, время
            2. Корректность формата даты (dd.MM.yyyy)
            3. Корректность формата времени (HH:mm)
            4. Дата не должна быть в прошлом
            5. Название события не должно быть пустым
            
            Верни JSON с результатом валидации:
            {
              "valid": true/false,
              "title": "название события или null",
              "date": "дата в формате dd.MM.yyyy или null",
              "time": "время в формате HH:mm или null",
              "missingFields": ["список недостающих полей"],
              "errors": ["список ошибок валидации"],
              "clarificationQuestion": "вопрос для уточнения или null"
            }
            
            Текущая дата: %s
            """;

    /**
     * Промпт для передачи данных агенту-валидатору
     */
    private String judgeUserPrompt = """
            Исходный запрос пользователя:
            %s
            
            Ответ агента-парсера:
            %s
            
            Проверь корректность и полноту извлеченных данных.
            Верни результат в формате JSON.
            """;
}
