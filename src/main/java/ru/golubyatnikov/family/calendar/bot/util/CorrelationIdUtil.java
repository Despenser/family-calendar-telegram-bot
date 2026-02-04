package ru.golubyatnikov.family.calendar.bot.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Утилитный класс для управления correlation ID в MDC.
 * 
 * <p>Correlation ID используется для трейсинга операций через всю систему
 * и связывания логов. Этот класс предоставляет методы для работы с correlation ID
 * в контекстах, где нет HTTP запроса (schedulers, async методы, тесты).</p>
 * 
 * <p>Основные методы:</p>
 * <ul>
 *   <li>{@link #generateAndSet()} - генерирует новый correlation ID и устанавливает в MDC</li>
 *   <li>{@link #set(String)} - устанавливает заданный correlation ID в MDC</li>
 *   <li>{@link #get()} - получает текущий correlation ID из MDC</li>
 *   <li>{@link #clear()} - удаляет correlation ID из MDC</li>
 *   <li>{@link #executeWithCorrelationId(Runnable)} - выполняет код с автоматическим управлением correlation ID</li>
 * </ul>
 * 
 * <p><b>Пример использования в scheduler:</b></p>
 * <pre>{@code
 * @Scheduled(fixedRate = 60000)
 * public void scheduledTask() {
 *     CorrelationIdUtil.executeWithCorrelationId(() -> {
 *         // Весь код здесь будет иметь correlation ID в логах
 *         log.info("Выполнение задачи");
 *         service.doWork();
 *     });
 * }
 * }</pre>
 * 
 * <p><b>Пример использования в async методе:</b></p>
 * <pre>{@code
 * @Async
 * public void asyncMethod() {
 *     String correlationId = CorrelationIdUtil.generateAndSet();
 *     try {
 *         log.info("Async операция");
 *         // Работа...
 *     } finally {
 *         CorrelationIdUtil.clear();
 *     }
 * }
 * }</pre>
 * 
 * @author Golubyatnikov Aleksey
 * @since 2026-02-04
 */
public final class CorrelationIdUtil {
    
    /**
     * Ключ для хранения correlation ID в MDC.
     */
    private static final String CORRELATION_ID_KEY = "correlationId";
    
    /**
     * Приватный конструктор для предотвращения создания экземпляров.
     */
    private CorrelationIdUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Генерирует новый correlation ID и устанавливает его в MDC.
     * 
     * <p>Correlation ID генерируется на основе UUID для обеспечения уникальности.</p>
     * 
     * @return сгенерированный correlation ID
     */
    public static String generateAndSet() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
        return correlationId;
    }
    
    /**
     * Устанавливает заданный correlation ID в MDC.
     * 
     * <p>Используется когда correlation ID уже известен (например, передан из другого контекста).</p>
     * 
     * @param correlationId correlation ID для установки
     * @throws IllegalArgumentException если correlationId null или пустой
     */
    public static void set(String correlationId) {
        if (correlationId == null || correlationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Correlation ID не может быть null или пустым");
        }
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }
    
    /**
     * Получает текущий correlation ID из MDC.
     * 
     * @return текущий correlation ID или null, если не установлен
     */
    public static String get() {
        return MDC.get(CORRELATION_ID_KEY);
    }
    
    /**
     * Удаляет correlation ID из MDC.
     * 
     * <p>Должен вызываться в блоке finally для предотвращения утечки памяти
     * и загрязнения MDC в многопоточных средах.</p>
     */
    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
    }
    
    /**
     * Выполняет код с автоматическим управлением correlation ID.
     * 
     * <p>Этот метод:</p>
     * <ol>
     *   <li>Генерирует новый correlation ID</li>
     *   <li>Устанавливает его в MDC</li>
     *   <li>Выполняет переданный код</li>
     *   <li>Автоматически очищает MDC в блоке finally</li>
     * </ol>
     * 
     * <p>Рекомендуется использовать этот метод в schedulers и async методах
     * для автоматического управления жизненным циклом correlation ID.</p>
     * 
     * @param runnable код для выполнения с correlation ID
     * @throws RuntimeException если выполнение кода завершилось с ошибкой
     */
    public static void executeWithCorrelationId(Runnable runnable) {
        generateAndSet();
        try {
            runnable.run();
        } finally {
            clear();
        }
    }
    
    /**
     * Выполняет код с заданным correlation ID.
     * 
     * <p>Используется когда нужно продолжить трейсинг с существующим correlation ID
     * (например, при передаче контекста между потоками).</p>
     * 
     * @param correlationId correlation ID для использования
     * @param runnable код для выполнения
     * @throws IllegalArgumentException если correlationId null или пустой
     * @throws RuntimeException если выполнение кода завершилось с ошибкой
     */
    public static void executeWithCorrelationId(String correlationId, Runnable runnable) {
        set(correlationId);
        try {
            runnable.run();
        } finally {
            clear();
        }
    }
}
