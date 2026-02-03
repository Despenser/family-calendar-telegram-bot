package ru.golubyatnikov.family.calendar.bot.service.authorization;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.MessageCategory;

import java.util.concurrent.TimeUnit;

/**
 * Сервис для сбора метрик авторизации и обработки неавторизованных пользователей.
 * 
 * <p>AuthorizationMetricsService предоставляет централизованный механизм для
 * отслеживания и мониторинга всех аспектов работы системы авторизации:</p>
 * <ul>
 *   <li>Попытки доступа неавторизованных пользователей</li>
 *   <li>Отправленные сообщения об ограничении доступа</li>
 *   <li>Время обработки проверки авторизации</li>
 *   <li>Ошибки при отправке сообщений</li>
 * </ul>
 * 
 * <p><b>Метрики:</b></p>
 * <ul>
 *   <li><b>unauthorized_access_attempts_total</b> - счетчик попыток доступа
 *       <ul>
 *         <li>Теги: command (имя команды)</li>
 *         <li>Тип: Counter</li>
 *       </ul>
 *   </li>
 *   <li><b>unauthorized_messages_sent_total</b> - счетчик отправленных сообщений
 *       <ul>
 *         <li>Теги: category (категория сообщения)</li>
 *         <li>Тип: Counter</li>
 *       </ul>
 *   </li>
 *   <li><b>authorization_check_duration_seconds</b> - время проверки авторизации
 *       <ul>
 *         <li>Теги: result (authorized/unauthorized)</li>
 *         <li>Тип: Timer (Histogram)</li>
 *       </ul>
 *   </li>
 *   <li><b>message_send_errors_total</b> - счетчик ошибок отправки
 *       <ul>
 *         <li>Теги: error_type (тип ошибки)</li>
 *         <li>Тип: Counter</li>
 *       </ul>
 *   </li>
 * </ul>
 * 
 * <p><b>Использование метрик:</b></p>
 * <ul>
 *   <li>Мониторинг интереса к боту от незарегистрированных пользователей</li>
 *   <li>Выявление популярных команд среди неавторизованных пользователей</li>
 *   <li>Отслеживание производительности системы авторизации</li>
 *   <li>Обнаружение проблем с отправкой сообщений</li>
 *   <li>Выявление потенциальных атак или злоупотреблений</li>
 * </ul>
 * 
 * <p><b>Интеграция с мониторингом:</b></p>
 * <p>Метрики доступны через Spring Boot Actuator endpoint /actuator/metrics
 * и могут быть экспортированы в системы мониторинга:</p>
 * <ul>
 *   <li>Prometheus</li>
 *   <li>Grafana</li>
 *   <li>CloudWatch</li>
 *   <li>Datadog</li>
 * </ul>
 * 
 * <p><b>Требования:</b> Design - Monitoring</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * // Запись попытки доступа
 * metricsService.recordUnauthorizedAccessAttempt("/add_event");
 * 
 * // Запись отправленного сообщения
 * metricsService.recordMessageSent(MessageCategory.EVENT_CREATION);
 * 
 * // Измерение времени проверки авторизации
 * long startTime = System.nanoTime();
 * Optional<User> user = checkAuthorization();
 * metricsService.recordAuthorizationCheckDuration(
 *     System.nanoTime() - startTime,
 *     user.isPresent()
 * );
 * 
 * // Запись ошибки отправки
 * metricsService.recordMessageSendError("telegram_api_error");
 * }</pre>
 * 
 * @see MeterRegistry
 * @see Counter
 * @see Timer
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-12
 */
@Service
@Slf4j
public class AuthorizationMetricsService {
    
    private final MeterRegistry meterRegistry;
    
    // Счетчики
    private static final String UNAUTHORIZED_ACCESS_METRIC = "unauthorized_access_attempts_total";
    private static final String MESSAGES_SENT_METRIC = "unauthorized_messages_sent_total";
    private static final String MESSAGE_ERRORS_METRIC = "message_send_errors_total";
    
    // Таймер
    private static final String AUTH_CHECK_DURATION_METRIC = "authorization_check_duration_seconds";
    
    /**
     * Конструктор для внедрения зависимостей.
     * 
     * @param meterRegistry реестр метрик Micrometer
     */
    public AuthorizationMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("AuthorizationMetricsService инициализирован");
    }
    
    /**
     * Записывает попытку доступа неавторизованного пользователя.
     * 
     * <p>Увеличивает счетчик попыток доступа с тегом команды.
     * Это позволяет отслеживать, какие команды наиболее популярны
     * среди незарегистрированных пользователей.</p>
     * 
     * <p><b>Метрика:</b> unauthorized_access_attempts_total</p>
     * <p><b>Теги:</b></p>
     * <ul>
     *   <li>command - имя команды (например, "/add_event")</li>
     * </ul>
     * 
     * <p><b>Примеры использования метрики:</b></p>
     * <ul>
     *   <li>Определение популярных функций для приоритизации регистрации</li>
     *   <li>Выявление потенциальных атак (множественные попытки)</li>
     *   <li>Анализ интереса к боту</li>
     * </ul>
     * 
     * @param commandName имя команды (например, "/add_event")
     * @throws IllegalArgumentException если commandName null или пустой
     */
    public void recordUnauthorizedAccessAttempt(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            log.warn("Попытка записать метрику с пустым commandName");
            return;
        }
        
        try {
            Counter.builder(UNAUTHORIZED_ACCESS_METRIC)
                    .tag("command", commandName)
                    .description("Количество попыток доступа неавторизованных пользователей")
                    .register(meterRegistry)
                    .increment();
            
            log.debug("Метрика записана: {} для команды {}", UNAUTHORIZED_ACCESS_METRIC, commandName);
            
        } catch (Exception e) {
            log.error("Ошибка при записи метрики попытки доступа: command={}, error={}", 
                    commandName, e.getMessage());
        }
    }
    
    /**
     * Записывает отправку сообщения об ограничении доступа.
     * 
     * <p>Увеличивает счетчик отправленных сообщений с тегом категории.
     * Это позволяет отслеживать, какие типы сообщений отправляются чаще всего.</p>
     * 
     * <p><b>Метрика:</b> unauthorized_messages_sent_total</p>
     * <p><b>Теги:</b></p>
     * <ul>
     *   <li>category - категория сообщения (например, "EVENT_CREATION")</li>
     * </ul>
     * 
     * <p><b>Примеры использования метрики:</b></p>
     * <ul>
     *   <li>Мониторинг эффективности системы уведомлений</li>
     *   <li>Анализ распределения попыток по категориям</li>
     *   <li>Оптимизация текстов сообщений для популярных категорий</li>
     * </ul>
     * 
     * @param category категория сообщения
     * @throws IllegalArgumentException если category null
     */
    public void recordMessageSent(MessageCategory category) {
        if (category == null) {
            log.warn("Попытка записать метрику с null category");
            return;
        }
        
        try {
            Counter.builder(MESSAGES_SENT_METRIC)
                    .tag("category", category.name())
                    .description("Количество отправленных сообщений об ограничении доступа")
                    .register(meterRegistry)
                    .increment();
            
            log.debug("Метрика записана: {} для категории {}", MESSAGES_SENT_METRIC, category);
            
        } catch (Exception e) {
            log.error("Ошибка при записи метрики отправки сообщения: category={}, error={}", 
                    category, e.getMessage());
        }
    }
    
    /**
     * Записывает время обработки проверки авторизации.
     * 
     * <p>Записывает длительность проверки авторизации в наносекундах
     * с тегом результата (authorized/unauthorized). Это позволяет
     * отслеживать производительность системы авторизации.</p>
     * 
     * <p><b>Метрика:</b> authorization_check_duration_seconds</p>
     * <p><b>Теги:</b></p>
     * <ul>
     *   <li>result - результат проверки ("authorized" или "unauthorized")</li>
     * </ul>
     * 
     * <p><b>Примеры использования метрики:</b></p>
     * <ul>
     *   <li>Мониторинг производительности проверки авторизации</li>
     *   <li>Выявление узких мест в системе</li>
     *   <li>Сравнение времени для авторизованных и неавторизованных</li>
     *   <li>Настройка алертов при деградации производительности</li>
     * </ul>
     * 
     * <p><b>Рекомендуемые пороги для алертов:</b></p>
     * <ul>
     *   <li>Warning: p95 > 50ms</li>
     *   <li>Critical: p95 > 100ms</li>
     * </ul>
     * 
     * @param durationNanos длительность проверки в наносекундах
     * @param isAuthorized true если пользователь авторизован, false иначе
     */
    public void recordAuthorizationCheckDuration(long durationNanos, boolean isAuthorized) {
        if (durationNanos < 0) {
            log.warn("Попытка записать отрицательную длительность: {}", durationNanos);
            return;
        }
        
        try {
            String result = isAuthorized ? "authorized" : "unauthorized";
            
            Timer.builder(AUTH_CHECK_DURATION_METRIC)
                    .tag("result", result)
                    .description("Время обработки проверки авторизации")
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
            
            log.debug("Метрика записана: {} = {}ms для result={}", 
                    AUTH_CHECK_DURATION_METRIC, 
                    TimeUnit.NANOSECONDS.toMillis(durationNanos),
                    result);
            
        } catch (Exception e) {
            log.error("Ошибка при записи метрики длительности проверки: duration={}, authorized={}, error={}", 
                    durationNanos, isAuthorized, e.getMessage());
        }
    }
    
    /**
     * Записывает ошибку при отправке сообщения.
     * 
     * <p>Увеличивает счетчик ошибок отправки с тегом типа ошибки.
     * Это позволяет отслеживать проблемы с доставкой сообщений
     * и выявлять паттерны ошибок.</p>
     * 
     * <p><b>Метрика:</b> message_send_errors_total</p>
     * <p><b>Теги:</b></p>
     * <ul>
     *   <li>error_type - тип ошибки (например, "telegram_api_error", "network_error")</li>
     * </ul>
     * 
     * <p><b>Типы ошибок:</b></p>
     * <ul>
     *   <li>telegram_api_error - ошибки Telegram API (400, 403, 404 и т.д.)</li>
     *   <li>network_error - сетевые ошибки (timeout, connection refused)</li>
     *   <li>rate_limit_error - превышение лимита запросов (429)</li>
     *   <li>unknown_error - неизвестные ошибки</li>
     * </ul>
     * 
     * <p><b>Примеры использования метрики:</b></p>
     * <ul>
     *   <li>Мониторинг надежности отправки сообщений</li>
     *   <li>Выявление проблем с Telegram API</li>
     *   <li>Настройка алертов при росте ошибок</li>
     *   <li>Анализ типов ошибок для улучшения обработки</li>
     * </ul>
     * 
     * <p><b>Рекомендуемые пороги для алертов:</b></p>
     * <ul>
     *   <li>Warning: error rate > 1% за 5 минут</li>
     *   <li>Critical: error rate > 5% за 5 минут</li>
     * </ul>
     * 
     * @param errorType тип ошибки (например, "telegram_api_error")
     * @throws IllegalArgumentException если errorType null или пустой
     */
    public void recordMessageSendError(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            log.warn("Попытка записать метрику с пустым errorType");
            errorType = "unknown_error";
        }
        
        try {
            Counter.builder(MESSAGE_ERRORS_METRIC)
                    .tag("error_type", errorType)
                    .description("Количество ошибок при отправке сообщений")
                    .register(meterRegistry)
                    .increment();
            
            log.debug("Метрика записана: {} для типа ошибки {}", MESSAGE_ERRORS_METRIC, errorType);
            
        } catch (Exception e) {
            log.error("Ошибка при записи метрики ошибки отправки: errorType={}, error={}", 
                    errorType, e.getMessage());
        }
    }
    
    /**
     * Получает текущее значение счетчика попыток доступа для команды.
     * 
     * <p>Этот метод полезен для тестирования и отладки.
     * В production рекомендуется использовать системы мониторинга
     * для просмотра метрик.</p>
     * 
     * @param commandName имя команды
     * @return текущее значение счетчика или 0.0 если метрика не найдена
     */
    public double getUnauthorizedAccessCount(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return 0.0;
        }
        
        try {
            Counter counter = meterRegistry.find(UNAUTHORIZED_ACCESS_METRIC)
                    .tag("command", commandName)
                    .counter();
            
            return counter != null ? counter.count() : 0.0;
            
        } catch (Exception e) {
            log.error("Ошибка при получении значения метрики: command={}, error={}", 
                    commandName, e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Получает текущее значение счетчика отправленных сообщений для категории.
     * 
     * <p>Этот метод полезен для тестирования и отладки.
     * В production рекомендуется использовать системы мониторинга
     * для просмотра метрик.</p>
     * 
     * @param category категория сообщения
     * @return текущее значение счетчика или 0.0 если метрика не найдена
     */
    public double getMessagesSentCount(MessageCategory category) {
        if (category == null) {
            return 0.0;
        }
        
        try {
            Counter counter = meterRegistry.find(MESSAGES_SENT_METRIC)
                    .tag("category", category.name())
                    .counter();
            
            return counter != null ? counter.count() : 0.0;
            
        } catch (Exception e) {
            log.error("Ошибка при получении значения метрики: category={}, error={}", 
                    category, e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Получает текущее значение счетчика ошибок для типа ошибки.
     * 
     * <p>Этот метод полезен для тестирования и отладки.
     * В production рекомендуется использовать системы мониторинга
     * для просмотра метрик.</p>
     * 
     * @param errorType тип ошибки
     * @return текущее значение счетчика или 0.0 если метрика не найдена
     */
    public double getMessageErrorsCount(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return 0.0;
        }
        
        try {
            Counter counter = meterRegistry.find(MESSAGE_ERRORS_METRIC)
                    .tag("error_type", errorType)
                    .counter();
            
            return counter != null ? counter.count() : 0.0;
            
        } catch (Exception e) {
            log.error("Ошибка при получении значения метрики: errorType={}, error={}", 
                    errorType, e.getMessage());
            return 0.0;
        }
    }
}
