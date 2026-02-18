package ru.golubyatnikov.family.calendar.bot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Конфигурация кэширования с использованием Caffeine.
 * 
 * Настраивает кэш-менеджер для оптимизации производительности приложения
 * путем кэширования часто запрашиваемых данных.
 * 
 * Параметры конфигурации задаются в application.yml под префиксом app.cache
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-19
 */
@Slf4j
@Configuration
@EnableCaching
@ConfigurationProperties(prefix = "app.cache")
@Getter
@Setter
public class CacheConfig {

    /**
     * Максимальное количество записей в кэше
     */
    private int maximumSize = 1000;

    /**
     * Время жизни записи после создания/обновления (в минутах)
     */
    private int expireAfterWriteMinutes = 60;

    /**
     * Время жизни записи после последнего доступа (в минутах)
     */
    private int expireAfterAccessMinutes = 30;

    /**
     * Включить статистику кэша для мониторинга
     */
    private boolean recordStats = true;

    /**
     * Список имен кэшей для создания
     */
    private List<String> names = List.of("upcomingEvents", "userEvents");

    /**
     * Создает и настраивает CacheManager с использованием Caffeine.
     * 
     * Кэшируемые данные:
     * - upcomingEvents: предстоящие события семьи (ключ: familyId_days_zoneId)
     * - userEvents: события пользователя (ключ: userId_ACTIVE)
     *
     * @return настроенный CacheManager
     */
    @Bean
    public CacheManager cacheManager() {
        log.info("Инициализация Caffeine Cache Manager");
        log.info("Настройки кэша: maximumSize={}, expireAfterWrite={}min, expireAfterAccess={}min, recordStats={}", 
                maximumSize, expireAfterWriteMinutes, expireAfterAccessMinutes, recordStats);
        log.info("Создаваемые кэши: {}", names);

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(names);
        cacheManager.setCaffeine(caffeineCacheBuilder());
        
        return cacheManager;
    }

    /**
     * Создает builder для настройки Caffeine кэша на основе параметров конфигурации.
     *
     * @return настроенный Caffeine builder
     */
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
                .expireAfterAccess(expireAfterAccessMinutes, TimeUnit.MINUTES);

        if (recordStats) {
            builder.recordStats();
        }

        return builder;
    }
}
