package ru.golubyatnikov.family.calendar.bot.util;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import java.util.UUID;

/**
 * Утилитный класс для управления correlation ID в MDC.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-04
 */
public final class CorrelationIdUtil {
    
    /**
     * Ключ для хранения correlation ID в MDC.
     */
    public static final String CORRELATION_ID_KEY = "correlationId";
    
    /**
     * Приватный конструктор для предотвращения создания экземпляров.
     *
     * @throws UnsupportedOperationException всегда, так как это утилитный класс
     */
    private CorrelationIdUtil() {
        throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
    }
    
    /**
     * Генерирует новый correlation ID и устанавливает его в MDC.
     */
    public static void generateAndSet() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }
    
    /**
     * Устанавливает заданный correlation ID в MDC.
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
     */
    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
    }
    
    /**
     * Выполняет код с автоматическим управлением correlation ID.
     * 
     * @param runnable код для выполнения с correlation ID
     * @throws RuntimeException если выполнение кода завершилось с ошибкой
     */
    public static void executeWithCorrelationId(@NonNull Runnable runnable) {
        generateAndSet();

        try {
            runnable.run();
        } finally {
            clear();
        }
    }
}
