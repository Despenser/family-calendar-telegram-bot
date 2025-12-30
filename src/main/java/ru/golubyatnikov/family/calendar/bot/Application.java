package ru.golubyatnikov.family.calendar.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Главный класс приложения Family Calendar Bot.
 * 
 * <p>Это Spring Boot приложение для управления семейным календарем через Telegram бота.
 * Приложение использует Webhook для получения обновлений от Telegram API,
 * PostgreSQL для хранения данных и поддерживает scheduled задачи для отправки уведомлений.</p>
 * 
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Создание и управление событиями в семейном календаре</li>
 *   <li>Просмотр предстоящих событий</li>
 *   <li>Автоматические уведомления о событиях</li>
 *   <li>Авторизация пользователей по Telegram ID</li>
 * </ul>
 * 
 * <p>Конфигурация:</p>
 * <ul>
 *   <li>@EnableScheduling - включает поддержку scheduled задач для уведомлений</li>
 *   <li>@EnableAsync - включает асинхронную обработку webhook обновлений</li>
 *   <li>@EnableRetry - включает поддержку retry механизма для отправки сообщений</li>
 * </ul>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableRetry
public class Application {

    /**
     * Точка входа в приложение.
     * 
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
