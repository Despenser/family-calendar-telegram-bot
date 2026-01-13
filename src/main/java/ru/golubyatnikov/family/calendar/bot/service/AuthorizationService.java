package ru.golubyatnikov.family.calendar.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.time.Instant;
import java.util.Optional;

/**
 * Сервис для централизованной проверки авторизации пользователей.
 * 
 * <p>AuthorizationService предоставляет единый механизм проверки авторизации
 * для всех команд бота, требующих регистрации пользователя. Сервис автоматически
 * отправляет информативные сообщения неавторизованным пользователям и логирует
 * попытки доступа для мониторинга и анализа.</p>
 * 
 * <p>Сервис выполняет следующие функции:</p>
 * <ul>
 *   <li>Проверка наличия пользователя в базе данных по Telegram ID</li>
 *   <li>Автоматическая отправка сообщений об ограничении доступа</li>
 *   <li>Логирование попыток доступа неавторизованных пользователей</li>
 *   <li>Поддержка категоризированных сообщений для разных типов команд</li>
 *   <li>Обработка ошибок при отправке сообщений</li>
 * </ul>
 * 
 * <p><b>Архитектурный паттерн:</b> Service Layer + Facade Pattern</p>
 * <p><b>Требования:</b> 1.1, 1.2, 2.1, 2.2, 2.3, 2.4, 2.5, 6.1, 6.2, 6.3, 6.4</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * // В обработчике команды
 * Optional<User> userOpt = authorizationService.checkAuthorizationAndNotify(
 *     telegramId, 
 *     chatId, 
 *     MessageCategory.EVENT_CREATION,
 *     "/add_event",
 *     username
 * );
 * 
 * if (userOpt.isEmpty()) {
 *     return; // Пользователь не авторизован, сообщение уже отправлено
 * }
 * 
 * User user = userOpt.get();
 * // Продолжить обработку команды
 * }</pre>
 * 
 * @see UserService
 * @see UnauthorizedMessageService
 * @see TelegramMessageService
 * @see MessageCategory
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-12
 */
@Service
@Slf4j
public class AuthorizationService {
    
    private final UserService userService;
    private final UnauthorizedMessageService messageService;
    private final TelegramMessageService telegramMessageService;
    private final AuthorizationMetricsService metricsService;
    
    /**
     * Конструктор для внедрения зависимостей.
     * 
     * @param userService сервис для работы с пользователями
     * @param messageService сервис для формирования сообщений об ограничении доступа
     * @param telegramMessageService сервис для отправки сообщений через Telegram API
     * @param metricsService сервис для сбора метрик авторизации
     */
    public AuthorizationService(
            UserService userService,
            UnauthorizedMessageService messageService,
            TelegramMessageService telegramMessageService,
            AuthorizationMetricsService metricsService) {
        this.userService = userService;
        this.messageService = messageService;
        this.telegramMessageService = telegramMessageService;
        this.metricsService = metricsService;
        
        log.info("AuthorizationService инициализирован");
    }
    
    /**
     * Проверяет авторизацию пользователя и отправляет сообщение при отсутствии доступа.
     * 
     * <p>Метод выполняет следующие действия:</p>
     * <ol>
     *   <li>Проверяет наличие пользователя в базе данных по Telegram ID</li>
     *   <li>Если пользователь не найден:
     *     <ul>
     *       <li>Логирует попытку доступа с уровнем INFO</li>
     *       <li>Формирует сообщение об ограничении доступа для указанной категории</li>
     *       <li>Отправляет сообщение пользователю через Telegram API</li>
     *       <li>Возвращает пустой Optional</li>
     *     </ul>
     *   </li>
     *   <li>Если пользователь найден, возвращает Optional с пользователем</li>
     * </ol>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Ошибки доступа к БД логируются и пробрасываются выше</li>
     *   <li>Ошибки отправки сообщений логируются, но не прерывают выполнение</li>
     *   <li>Retry логика для отправки сообщений реализована в TelegramMessageService</li>
     * </ul>
     * 
     * <p><b>Логирование:</b></p>
     * <ul>
     *   <li>Уровень INFO для попыток доступа неавторизованных пользователей</li>
     *   <li>Включает telegram_id, username (если доступен), команду и timestamp</li>
     *   <li>Структурированный формат для удобства анализа</li>
     * </ul>
     * 
     * @param telegramId Telegram ID пользователя для проверки авторизации
     * @param chatId ID чата для отправки сообщения об ограничении доступа
     * @param category категория команды для формирования специфичного сообщения
     * @param commandName имя команды для логирования (например, "/add_event")
     * @param username имя пользователя в Telegram (может быть null)
     * @return Optional содержащий пользователя, если он авторизован, иначе пустой Optional
     * @throws IllegalArgumentException если telegramId, chatId, category или commandName равны null
     * @throws org.springframework.dao.DataAccessException если возникла ошибка доступа к БД
     */
    public Optional<User> checkAuthorizationAndNotify(
            Long telegramId, 
            Long chatId, 
            MessageCategory category,
            String commandName,
            String username) {
        
        validateParams(telegramId, chatId, category, commandName);
        
        log.debug("Проверка авторизации: telegramId={}, command={}", telegramId, commandName);
        
        // Начинаем измерение времени проверки авторизации
        long startTime = System.nanoTime();
        
        Optional<User> userOpt;
        try {
            userOpt = userService.findByTelegramId(telegramId);
        } catch (Exception e) {
            log.error("Ошибка доступа к БД при проверке авторизации: telegramId={}, command={}, error={}", 
                    telegramId, commandName, e.getMessage(), e);
            
            // Записываем метрику времени проверки (неудачная)
            metricsService.recordAuthorizationCheckDuration(
                    System.nanoTime() - startTime, 
                    false
            );
            
            // Отправляем сообщение о временной недоступности
            sendTemporaryUnavailableMessage(chatId);
            
            // Возвращаем пустой Optional, чтобы не блокировать работу бота
            return Optional.empty();
        }
        
        // Записываем метрику времени проверки
        metricsService.recordAuthorizationCheckDuration(
                System.nanoTime() - startTime, 
                userOpt.isPresent()
        );
        
        if (userOpt.isEmpty()) {
            // Записываем метрику попытки доступа
            metricsService.recordUnauthorizedAccessAttempt(commandName);
            
            logUnauthorizedAccess(telegramId, username, commandName);
            sendUnauthorizedMessage(chatId, category);
        } else {
            log.debug("Пользователь авторизован: telegramId={}, userId={}", 
                    telegramId, userOpt.get().getId());
        }
        
        return userOpt;
    }
    
    /**
     * Логирует попытку доступа неавторизованного пользователя.
     * 
     * <p>Создает структурированную лог-запись уровня INFO, содержащую:</p>
     * <ul>
     *   <li>Telegram ID пользователя</li>
     *   <li>Username пользователя в Telegram (если доступен)</li>
     *   <li>Имя команды, к которой пытался получить доступ пользователь</li>
     *   <li>Timestamp попытки доступа</li>
     * </ul>
     * 
     * <p>Логи используются для:</p>
     * <ul>
     *   <li>Мониторинга интереса к боту</li>
     *   <li>Выявления потенциальных проблем безопасности</li>
     *   <li>Анализа популярности функций</li>
     *   <li>Планирования расширения пользовательской базы</li>
     * </ul>
     * 
     * <p><b>Важно:</b> В production окружении рекомендуется не логировать username
     * для соблюдения конфиденциальности. Используйте только telegram_id.</p>
     * 
     * <p><b>Обработка ошибок:</b> Ошибки логирования не должны прерывать основной flow.
     * Если логирование не удается, ошибка логируется на уровне ERROR, но выполнение продолжается.</p>
     * 
     * <p><b>Требования:</b> 2.5, 6.1, 6.2, 6.3, 6.4</p>
     * 
     * @param telegramId Telegram ID пользователя
     * @param username имя пользователя в Telegram (может быть null)
     * @param commandName имя команды
     */
    private void logUnauthorizedAccess(Long telegramId, String username, String commandName) {
        try {
            // Формируем структурированное сообщение с учетом возможного отсутствия username
            if (username != null && !username.isBlank()) {
                log.info("Unauthorized access attempt: telegramId={}, username={}, command={}, timestamp={}", 
                        telegramId, username, commandName, Instant.now());
            } else {
                log.info("Unauthorized access attempt: telegramId={}, username=<not_provided>, command={}, timestamp={}", 
                        telegramId, commandName, Instant.now());
            }
        } catch (Exception e) {
            // Ошибка логирования не должна прерывать основной flow
            log.error("Ошибка при логировании попытки доступа: telegramId={}, command={}, error={}", 
                    telegramId, commandName, e.getMessage());
        }
    }
    
    /**
     * Отправляет сообщение об ограничении доступа пользователю.
     * 
     * <p>Формирует и отправляет информативное сообщение, объясняющее причину
     * ограничения доступа и способы получения доступа к функционалу бота.</p>
     * 
     * <p>Ошибки отправки сообщений логируются, но не прерывают выполнение,
     * так как основная цель метода - проверка авторизации, а не гарантированная
     * доставка сообщения.</p>
     * 
     * @param chatId ID чата для отправки сообщения
     * @param category категория команды для формирования специфичного сообщения
     */
    private void sendUnauthorizedMessage(Long chatId, MessageCategory category) {
        try {
            String message = messageService.getMessage(category);
            telegramMessageService.sendMessage(chatId, message);
            
            // Записываем метрику успешной отправки сообщения
            metricsService.recordMessageSent(category);
            
            log.debug("Сообщение об ограничении доступа отправлено: chatId={}, category={}", 
                    chatId, category);
            
        } catch (TelegramApiException e) {
            // Записываем метрику ошибки отправки
            metricsService.recordMessageSendError("telegram_api_error");
            
            log.error("Ошибка при отправке сообщения об ограничении доступа: chatId={}, category={}, error={}", 
                    chatId, category, e.getMessage());
            // Не пробрасываем исключение, так как основная цель - проверка авторизации
        } catch (Exception e) {
            // Записываем метрику ошибки отправки
            metricsService.recordMessageSendError("unknown_error");
            
            log.error("Неожиданная ошибка при отправке сообщения об ограничении доступа: chatId={}, category={}, error={}", 
                    chatId, category, e.getMessage(), e);
            // Не пробрасываем исключение
        }
    }
    
    /**
     * Отправляет сообщение о временной недоступности сервиса.
     * 
     * <p>Используется когда возникает ошибка доступа к БД или другие
     * технические проблемы, которые не позволяют проверить авторизацию.</p>
     * 
     * <p>Сообщение информирует пользователя о временных технических проблемах
     * и предлагает повторить попытку позже.</p>
     * 
     * @param chatId ID чата для отправки сообщения
     */
    private void sendTemporaryUnavailableMessage(Long chatId) {
        try {
            String message = "⚠️ Временные технические проблемы\\. Пожалуйста, попробуйте позже\\.";
            telegramMessageService.sendMessage(chatId, message);
            
            log.debug("Сообщение о временной недоступности отправлено: chatId={}", chatId);
            
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения о временной недоступности: chatId={}, error={}", 
                    chatId, e.getMessage());
            // Не пробрасываем исключение - это последняя попытка уведомить пользователя
        }
    }
    
    /**
     * Валидирует параметры метода checkAuthorizationAndNotify.
     * 
     * @param telegramId Telegram ID пользователя
     * @param chatId ID чата
     * @param category категория сообщения
     * @param commandName имя команды
     * @throws IllegalArgumentException если какой-либо параметр некорректен
     */
    private void validateParams(Long telegramId, Long chatId, MessageCategory category, String commandName) {
        if (telegramId == null) {
            log.error("Попытка проверить авторизацию с null telegramId");
            throw new IllegalArgumentException("TelegramId не может быть null");
        }
        
        if (chatId == null) {
            log.error("Попытка проверить авторизацию с null chatId: telegramId={}", telegramId);
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (category == null) {
            log.error("Попытка проверить авторизацию с null category: telegramId={}", telegramId);
            throw new IllegalArgumentException("Категория сообщения не может быть null");
        }
        
        if (commandName == null || commandName.isBlank()) {
            log.error("Попытка проверить авторизацию с пустым commandName: telegramId={}", telegramId);
            throw new IllegalArgumentException("Имя команды не может быть пустым");
        }
    }
}
