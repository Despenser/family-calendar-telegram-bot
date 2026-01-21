package ru.golubyatnikov.family.calendar.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.golubyatnikov.family.calendar.bot.config.BotConfig;
import ru.golubyatnikov.family.calendar.bot.util.SensitiveDataMasker;

/**
 * Сервис для отправки сообщений через Telegram Bot API.
 * 
 * <p>TelegramMessageService предоставляет надежный механизм отправки сообщений
 * пользователям Telegram с автоматическими повторными попытками при ошибках.
 * Он выполняет следующие функции:</p>
 * <ul>
 *   <li>Отправка текстовых сообщений пользователям</li>
 *   <li>Отправка сообщений с inline кнопками</li>
 *   <li>Ответы на callback queries от inline кнопок</li>
 *   <li>Автоматические повторные попытки с экспоненциальной задержкой</li>
 *   <li>Обработка различных типов ошибок Telegram API</li>
 *   <li>Подробное логирование всех операций</li>
 * </ul>
 * 
 * <p><b>Retry механизм:</b></p>
 * <ul>
 *   <li>Максимум 3 попытки отправки</li>
 *   <li>Экспоненциальная задержка: 1с, 2с, 4с</li>
 *   <li>Множитель задержки: 2.0</li>
 *   <li>Автоматический retry для TelegramApiException</li>
 * </ul>
 * 
 * <p><b>Обработка ошибок:</b></p>
 * <ul>
 *   <li>400 Bad Request - некорректные параметры запроса</li>
 *   <li>401 Unauthorized - неверный токен бота</li>
 *   <li>403 Forbidden - бот заблокирован пользователем</li>
 *   <li>404 Not Found - чат не найден</li>
 *   <li>429 Too Many Requests - превышен лимит запросов</li>
 *   <li>500+ Server Errors - ошибки сервера Telegram</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 6.4, 9.4</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * // Простая отправка сообщения
 * messageService.sendMessage(123456789L, "Привет!");
 * 
 * // Отправка с inline кнопками
 * InlineKeyboardMarkup keyboard = createKeyboard();
 * messageService.sendMessage(123456789L, "Выберите действие:", keyboard);
 * 
 * // Ответ на callback query
 * messageService.answerCallbackQuery("callback_id", "Обработано!");
 * }</pre>
 * 
 * @see BotConfig
 * @see TelegramApiException
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@Service
@Slf4j
public class TelegramMessageService extends DefaultAbsSender {

    private final BotConfig botConfig;
    private final AuthorizationMetricsService metricsService;

    /**
     * Конструктор для инициализации сервиса с конфигурацией бота.
     * 
     * <p>Создает экземпляр DefaultAbsSender с настройками по умолчанию
     * для отправки сообщений через Telegram Bot API.</p>
     * 
     * @param botConfig конфигурация бота с токеном и другими параметрами
     * @param metricsService сервис для сбора метрик (может быть null для тестов)
     */
    public TelegramMessageService(BotConfig botConfig, AuthorizationMetricsService metricsService) {
        super(new DefaultBotOptions());
        this.botConfig = botConfig;
        this.metricsService = metricsService;
        log.debug("TelegramMessageService инициализирован с токеном: {}...", 
                SensitiveDataMasker.maskToken(botConfig.getToken()));
    }

    /**
     * Возвращает токен бота для аутентификации в Telegram API.
     * 
     * <p>Этот метод требуется интерфейсом DefaultAbsSender для
     * аутентификации всех запросов к Telegram Bot API.</p>
     * 
     * @return токен бота
     */
    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    /**
     * Отправляет текстовое сообщение пользователю.
     * 
     * <p>Метод автоматически повторяет попытки отправки при ошибках
     * с экспоненциальной задержкой. Максимум 3 попытки.</p>
     * 
     * <p>Сообщение отправляется с поддержкой MarkdownV2 форматирования
     * для улучшения читаемости (жирный текст, курсив, моноширинный текст и т.д.).
     * Все специальные символы MarkdownV2 должны быть экранированы с помощью
     * {@link ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter}.</p>
     * 
     * <p>Retry стратегия:</p>
     * <ul>
     *   <li>Попытка 1: немедленно</li>
     *   <li>Попытка 2: через 1 секунду</li>
     *   <li>Попытка 3: через 2 секунды</li>
     * </ul>
     * 
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает MarkdownV2, требует экранирования)
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если telegramId null или text пустой
     * @see #sendMessage(Long, String, InlineKeyboardMarkup)
     * @see ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendMessage(Long telegramId, String text) throws TelegramApiException {
        validateSendMessageParams(telegramId, text);
        
        log.debug("Отправка сообщения пользователю: telegramId={}, textLength={}", 
                telegramId, text.length());
        
        SendMessage message = SendMessage.builder()
                .chatId(telegramId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .build();
        
        try {
            execute(message);
            log.debug("Сообщение успешно отправлено: telegramId={}, textLength={}", 
                    telegramId, text.length());
            
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            handleTelegramApiError(e, telegramId, text);
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Ошибка при отправке сообщения: telegramId={}, error={}", 
                    telegramId, e.getMessage());
            throw e;
        }
    }

    /**
     * Отправляет текстовое сообщение с inline кнопками пользователю.
     * 
     * <p>Метод автоматически повторяет попытки отправки при ошибках
     * с экспоненциальной задержкой. Максимум 3 попытки.</p>
     * 
     * <p>Inline кнопки позволяют пользователю выполнять действия
     * прямо в чате без отправки дополнительных сообщений.</p>
     * 
     * <p>Сообщение отправляется с поддержкой MarkdownV2 форматирования.
     * Все специальные символы MarkdownV2 должны быть экранированы с помощью
     * {@link ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter}.</p>
     * 
     * <p>Примеры использования inline кнопок:</p>
     * <ul>
     *   <li>Редактирование события</li>
     *   <li>Удаление события</li>
     *   <li>Подтверждение действий</li>
     *   <li>Навигация по спискам</li>
     * </ul>
     * 
     * <p>Retry стратегия:</p>
     * <ul>
     *   <li>Попытка 1: немедленно</li>
     *   <li>Попытка 2: через 1 секунду</li>
     *   <li>Попытка 3: через 2 секунды</li>
     * </ul>
     * 
     * <p><b>Обработка ошибок парсинга:</b></p>
     * <ul>
     *   <li>При ошибке парсинга MarkdownV2 (400 Bad Request) попытки прекращаются</li>
     *   <li>Автоматический fallback на отправку без форматирования (plain text)</li>
     *   <li>Детальное логирование для диагностики проблем с экранированием</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 4.3, 4.4, 4.5</p>
     * 
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает MarkdownV2, требует экранирования)
     * @param replyMarkup разметка inline кнопок
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если telegramId null, text пустой или replyMarkup null
     * @see InlineKeyboardMarkup
     * @see #sendMessage(Long, String)
     * @see ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter
     */
    public void sendMessage(Long telegramId, String text, InlineKeyboardMarkup replyMarkup) 
            throws TelegramApiException {
        validateSendMessageParams(telegramId, text);
        
        if (replyMarkup == null) {
            log.error("Попытка отправить сообщение с null replyMarkup: telegramId={}", telegramId);
            throw new IllegalArgumentException("ReplyMarkup не может быть null");
        }
        
        log.debug("Отправка сообщения с inline кнопками: telegramId={}, textLength={}, buttonsCount={}", 
                telegramId, text.length(), countButtons(replyMarkup));
        
        // Попытка отправки с MarkdownV2
        SendMessage message = SendMessage.builder()
                .chatId(telegramId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .replyMarkup(replyMarkup)
                .build();
        
        try {
            executeWithRetry(message, telegramId, text, 3);
            log.debug("Сообщение с inline кнопками успешно отправлено: telegramId={}, textLength={}, buttonsCount={}", 
                    telegramId, text.length(), countButtons(replyMarkup));
            
        } catch (TelegramApiRequestException e) {
            // Если это ошибка парсинга, пробуем fallback на plain text
            if (isParseError(e)) {
                log.warn("Ошибка парсинга MarkdownV2, переключаемся на plain text: telegramId={}", 
                        telegramId);
                recordMetric("markdown_parse_error_fallback");
                
                try {
                    sendMessageWithoutFormatting(telegramId, text, replyMarkup);
                    log.info("Сообщение успешно отправлено без форматирования (fallback): telegramId={}", 
                            telegramId);
                } catch (TelegramApiException fallbackException) {
                    log.error("Fallback на plain text также не удался: telegramId={}, error={}", 
                            telegramId, fallbackException.getMessage());
                    throw fallbackException;
                }
            } else {
                throw e;
            }
        }
    }

    /**
     * Отправляет текстовое сообщение с reply клавиатурой пользователю.
     * 
     * <p>Метод автоматически повторяет попытки отправки при ошибках
     * с экспоненциальной задержкой. Максимум 3 попытки.</p>
     * 
     * <p>Reply клавиатура заменяет стандартную клавиатуру Telegram и позволяет
     * пользователю быстро отправлять команды одним нажатием кнопки.</p>
     * 
     * <p>Сообщение отправляется с поддержкой MarkdownV2 форматирования.
     * Все специальные символы MarkdownV2 должны быть экранированы с помощью
     * {@link ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter}.</p>
     * 
     * <p>Примеры использования reply клавиатуры:</p>
     * <ul>
     *   <li>Основные команды бота (Предстоящие события, Добавить событие и т.д.)</li>
     *   <li>Быстрый доступ к часто используемым функциям</li>
     *   <li>Упрощение взаимодействия для пользователей</li>
     * </ul>
     * 
     * <p>Retry стратегия:</p>
     * <ul>
     *   <li>Попытка 1: немедленно</li>
     *   <li>Попытка 2: через 1 секунду</li>
     *   <li>Попытка 3: через 2 секунды</li>
     * </ul>
     * 
     * <p><b>Обработка ошибок парсинга:</b></p>
     * <ul>
     *   <li>При ошибке парсинга MarkdownV2 (400 Bad Request) попытки прекращаются</li>
     *   <li>Детальное логирование для диагностики проблем с экранированием</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 4.3, 4.4, 4.5</p>
     * 
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает MarkdownV2, требует экранирования)
     * @param keyboard reply клавиатура с кнопками команд
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если telegramId null, text пустой или keyboard null
     * @see ReplyKeyboardMarkup
     * @see #sendMessage(Long, String)
     * @see ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter
     */
    public void sendMessage(Long telegramId, String text, ReplyKeyboardMarkup keyboard) 
            throws TelegramApiException {
        validateSendMessageParams(telegramId, text);
        
        if (keyboard == null) {
            log.error("Попытка отправить сообщение с null keyboard: telegramId={}", telegramId);
            throw new IllegalArgumentException("Keyboard не может быть null");
        }
        
        log.debug("Отправка сообщения с reply клавиатурой: telegramId={}, textLength={}, keyboardRows={}", 
                telegramId, text.length(), keyboard.getKeyboard() != null ? keyboard.getKeyboard().size() : 0);
        
        SendMessage message = SendMessage.builder()
                .chatId(telegramId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .replyMarkup(keyboard)
                .build();
        
        try {
            executeWithRetry(message, telegramId, text, 3);
            log.debug("Сообщение с reply клавиатурой успешно отправлено: telegramId={}, textLength={}, keyboardRows={}", 
                    telegramId, text.length(), keyboard.getKeyboard() != null ? keyboard.getKeyboard().size() : 0);
            
        } catch (TelegramApiRequestException e) {
            // Для reply клавиатуры не делаем fallback, так как она не поддерживает inline кнопки
            recordMetricForTelegramError(e);
            handleTelegramApiError(e, telegramId, text);
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Ошибка при отправке сообщения с reply клавиатурой: telegramId={}, error={}", 
                    telegramId, e.getMessage());
            throw e;
        }
    }

    /**
     * Отправляет текстовое сообщение с inline кнопками и возвращает отправленное сообщение.
     * 
     * <p>Этот метод аналогичен {@link #sendMessage(Long, String, InlineKeyboardMarkup)},
     * но возвращает объект Message, содержащий messageId и другую информацию
     * об отправленном сообщении.</p>
     * 
     * <p>Метод автоматически обрабатывает ошибки парсинга MarkdownV2 с fallback
     * на отправку без форматирования (plain text).</p>
     * 
     * <p><b>Обработка ошибок парсинга:</b></p>
     * <ul>
     *   <li>При ошибке парсинга MarkdownV2 (400 Bad Request) автоматически переключается на plain text</li>
     *   <li>Детальное логирование для диагностики проблем с экранированием</li>
     *   <li>Метрика "markdown_parse_error_fallback" для мониторинга</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 1.2</p>
     * 
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает MarkdownV2)
     * @param replyMarkup разметка inline кнопок
     * @return отправленное сообщение с messageId
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если telegramId null, text пустой или replyMarkup null
     */
    public Message sendMessageAndGet(Long telegramId, String text, InlineKeyboardMarkup replyMarkup) 
            throws TelegramApiException {
        validateSendMessageParams(telegramId, text);
        
        if (replyMarkup == null) {
            log.error("Попытка отправить сообщение с null replyMarkup: telegramId={}", telegramId);
            throw new IllegalArgumentException("ReplyMarkup не может быть null");
        }
        
        log.debug("Отправка сообщения с inline кнопками (с возвратом Message): telegramId={}, textLength={}, buttonsCount={}", 
                telegramId, text.length(), countButtons(replyMarkup));
        
        SendMessage message = SendMessage.builder()
                .chatId(telegramId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .replyMarkup(replyMarkup)
                .build();
        
        try {
            Message sentMessage = execute(message);
            log.debug("Сообщение успешно отправлено: telegramId={}, messageId={}", 
                    telegramId, sentMessage.getMessageId());
            return sentMessage;
            
        } catch (TelegramApiRequestException e) {
            // Обработка ошибок парсинга с fallback
            if (isParseError(e)) {
                log.warn("Ошибка парсинга MarkdownV2, переключаемся на plain text: telegramId={}", 
                        telegramId);
                recordMetric("markdown_parse_error_fallback");
                
                SendMessage plainMessage = SendMessage.builder()
                        .chatId(telegramId.toString())
                        .text(text)
                        .replyMarkup(replyMarkup)
                        .build();
                
                Message sentMessage = execute(plainMessage);
                log.info("Сообщение успешно отправлено без форматирования (fallback): telegramId={}, messageId={}", 
                        telegramId, sentMessage.getMessageId());
                return sentMessage;
            }
            
            recordMetricForTelegramError(e);
            handleTelegramApiError(e, telegramId, text);
            throw e;
        }
    }

    /**
     * Отправляет текстовое сообщение с inline кнопками пользователю.
     * 
     * <p>Это удобный метод-обертка для {@link #sendMessage(Long, String, InlineKeyboardMarkup)}
     * с более явным названием для отправки сообщений с inline клавиатурой.</p>
     * 
     * @param chatId ID чата для отправки сообщения
     * @param text текст сообщения (поддерживает Markdown)
     * @param keyboard inline клавиатура с кнопками
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если chatId null, text пустой или keyboard null
     * @see #sendMessage(Long, String, InlineKeyboardMarkup)
     */
    public void sendMessageWithInlineKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
        sendMessage(chatId, text, keyboard);
    }

    /**
     * Отправляет текстовое сообщение с inline кнопками без форматирования.
     * 
     * <p>Этот метод используется как fallback механизм, когда отправка
     * с MarkdownV2 форматированием не удается из-за ошибки 400 (Bad Request).
     * Сообщение отправляется с parseMode=null, что означает отсутствие
     * какого-либо форматирования.</p>
     * 
     * <p>Метод автоматически повторяет попытки отправки при ошибках
     * с экспоненциальной задержкой. Максимум 3 попытки.</p>
     * 
     * <p><b>Требования:</b> 4.4, 5.2</p>
     * 
     * @param chatId ID чата для отправки сообщения
     * @param text текст сообщения (без форматирования)
     * @param keyboard inline клавиатура с кнопками
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если chatId null, text пустой или keyboard null
     */
    public void sendMessageWithoutFormatting(Long chatId, String text, InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
        validateSendMessageParams(chatId, text);
        
        if (keyboard == null) {
            log.error("Попытка отправить сообщение с null keyboard: chatId={}", chatId);
            throw new IllegalArgumentException("Keyboard не может быть null");
        }
        
        log.debug("Отправка сообщения без форматирования с inline кнопками: chatId={}, textLength={}, buttonsCount={}", 
                chatId, text.length(), countButtons(keyboard));
        
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                // parseMode не устанавливается - отправка без форматирования
                .replyMarkup(keyboard)
                .build();
        
        try {
            execute(message);
            log.debug("Сообщение без форматирования с inline кнопками успешно отправлено: chatId={}, textLength={}, buttonsCount={}", 
                    chatId, text.length(), countButtons(keyboard));
            
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            
            // Детальное логирование клавиатуры при ошибке
            if (e.getErrorCode() != null && e.getErrorCode() == 400) {
                log.error("Ошибка 400 при отправке без форматирования с inline кнопками. Детали клавиатуры: " +
                         "chatId={}, buttonsCount={}, keyboardDetails={}", 
                         chatId, countButtons(keyboard), getKeyboardDetails(keyboard));
            }
            
            handleTelegramApiError(e, chatId, text);
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Ошибка при отправке сообщения без форматирования с inline кнопками: chatId={}, error={}", 
                    chatId, e.getMessage());
            throw e;
        }
    }

    /**
     * Редактирует текст существующего сообщения.
     * 
     * <p>Этот метод позволяет изменить текст ранее отправленного сообщения
     * без отправки нового. Полезно для обновления inline-календарей,
     * меню выбора и других интерактивных элементов.</p>
     * 
     * <p>Метод автоматически повторяет попытки при ошибках
     * с экспоненциальной задержкой. Максимум 3 попытки.</p>
     * 
     * <p>Сообщение редактируется с поддержкой MarkdownV2 форматирования.
     * Все специальные символы MarkdownV2 должны быть экранированы с помощью
     * {@link ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter}.</p>
     * 
     * <p>Ограничения:</p>
     * <ul>
     *   <li>Можно редактировать только сообщения, отправленные ботом</li>
     *   <li>Сообщение должно быть не старше 48 часов</li>
     *   <li>Новый текст должен отличаться от старого</li>
     * </ul>
     * 
     * @param chatId ID чата, где находится сообщение
     * @param messageId ID сообщения для редактирования
     * @param newText новый текст сообщения (поддерживает MarkdownV2, требует экранирования)
     * @param replyMarkup новая inline клавиатура (может быть null для удаления кнопок)
     * @throws TelegramApiException если все попытки редактирования не удались
     * @throws IllegalArgumentException если chatId или messageId null, или newText пустой
     * @see EditMessageText
     * @see ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void editMessageText(Long chatId, Integer messageId, String newText, 
                               InlineKeyboardMarkup replyMarkup) throws TelegramApiException {
        if (chatId == null) {
            log.error("Попытка редактировать сообщение с null chatId");
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (messageId == null) {
            log.error("Попытка редактировать сообщение с null messageId: chatId={}", chatId);
            throw new IllegalArgumentException("MessageId не может быть null");
        }
        
        if (newText == null || newText.isBlank()) {
            log.error("Попытка редактировать сообщение с пустым текстом: chatId={}, messageId={}", 
                    chatId, messageId);
            throw new IllegalArgumentException("Новый текст не может быть пустым");
        }
        
        log.debug("Редактирование сообщения: chatId={}, messageId={}, newTextLength={}", 
                chatId, messageId, newText.length());
        
        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(newText)
                .parseMode("MarkdownV2")
                .replyMarkup(replyMarkup)
                .build();
        
        try {
            execute(editMessage);
            log.debug("Сообщение успешно отредактировано: chatId={}, messageId={}, newTextLength={}", 
                    chatId, messageId, newText.length());
            
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            handleTelegramApiError(e, chatId, newText);
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Ошибка при редактировании сообщения: chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());
            throw e;
        }
    }

    /**
     * Редактирует текст существующего сообщения с обработкой ошибок удалённых сообщений.
     * 
     * <p>Этот метод пытается отредактировать существующее сообщение и возвращает
     * результат операции. В отличие от {@link #editMessageText}, этот метод не
     * выбрасывает исключение, если сообщение не найдено или слишком старое для
     * редактирования.</p>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Сообщение удалено пользователем - возвращает false</li>
     *   <li>Сообщение слишком старое (>48 часов) - возвращает false</li>
     *   <li>Сообщение не может быть отредактировано - возвращает false</li>
     *   <li>Успешное редактирование - возвращает true</li>
     *   <li>Другие ошибки - выбрасывает TelegramApiException</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 4.1, 4.2, 4.3, 4.4</p>
     * 
     * @param chatId ID чата, где находится сообщение
     * @param messageId ID сообщения для редактирования
     * @param newText новый текст сообщения (поддерживает MarkdownV2)
     * @param replyMarkup новая inline клавиатура
     * @return true если редактирование успешно, false если сообщение не найдено/удалено/старое
     * @throws TelegramApiException при других ошибках (сетевые, парсинга и т.д.)
     * @throws IllegalArgumentException если chatId или messageId null, или newText пустой
     */
    public boolean tryEditMessageText(Long chatId, Integer messageId, String newText, 
                                      InlineKeyboardMarkup replyMarkup) throws TelegramApiException {
        try {
            editMessageText(chatId, messageId, newText, replyMarkup);
            return true;
            
        } catch (TelegramApiRequestException e) {
            // Проверяем, не удалено ли сообщение
            if (isMessageNotFoundError(e)) {
                log.info("Сообщение не найдено или удалено: chatId={}, messageId={}", 
                        chatId, messageId);
                return false;
            }
            
            // Проверяем, не слишком ли старое сообщение
            if (isMessageTooOldError(e)) {
                log.info("Сообщение слишком старое для редактирования: chatId={}, messageId={}", 
                        chatId, messageId);
                return false;
            }
            
            // Проверяем, не идентично ли новое содержимое текущему
            if (isMessageNotModifiedError(e)) {
                log.debug("Сообщение не изменилось (содержимое идентично): chatId={}, messageId={}", 
                        chatId, messageId);
                return true; // Считаем это успехом, так как сообщение уже в нужном состоянии
            }
            
            // Другие ошибки пробрасываем дальше
            throw e;
        }
    }

    /**
     * Проверяет, является ли ошибка "сообщение не найдено".
     * 
     * <p>Эта ошибка возникает когда:</p>
     * <ul>
     *   <li>Пользователь удалил сообщение</li>
     *   <li>Сообщение не существует</li>
     *   <li>Бот не имеет доступа к сообщению</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 4.1, 4.2</p>
     * 
     * @param e исключение от Telegram API
     * @return true если это ошибка "сообщение не найдено", false иначе
     */
    private boolean isMessageNotFoundError(TelegramApiRequestException e) {
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("message to edit not found")) ||
               (message != null && message.contains("message can't be edited")) ||
               (message != null && message.contains("message to delete not found")) ||
               (apiResponse != null && apiResponse.contains("message to edit not found")) ||
               (apiResponse != null && apiResponse.contains("message can't be edited")) ||
               (apiResponse != null && apiResponse.contains("message to delete not found"));
    }

    /**
     * Проверяет, является ли ошибка "сообщение слишком старое".
     * 
     * <p>Telegram позволяет редактировать сообщения только в течение 48 часов
     * после отправки. После этого срока редактирование невозможно.</p>
     * 
     * <p><b>Требования:</b> 4.2</p>
     * 
     * @param e исключение от Telegram API
     * @return true если это ошибка "сообщение слишком старое", false иначе
     */
    private boolean isMessageTooOldError(TelegramApiRequestException e) {
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("message is too old")) ||
               (message != null && message.contains("message can't be edited")) ||
               (apiResponse != null && apiResponse.contains("message is too old")) ||
               (apiResponse != null && apiResponse.contains("message can't be edited"));
    }

    /**
     * Проверяет, является ли ошибка "сообщение не изменилось".
     * 
     * <p>Эта ошибка возникает когда новое содержимое и клавиатура
     * идентичны текущему содержимому сообщения.</p>
     * 
     * <p><b>Требования:</b> 4.3</p>
     * 
     * @param e исключение от Telegram API
     * @return true если это ошибка "сообщение не изменилось", false иначе
     */
    private boolean isMessageNotModifiedError(TelegramApiRequestException e) {
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("message is not modified")) ||
               (apiResponse != null && apiResponse.contains("message is not modified"));
    }

    /**
     * Отправляет ответ на callback query от inline кнопки.
     * 
     * <p>Когда пользователь нажимает на inline кнопку, Telegram отправляет
     * callback query. Бот должен ответить на этот query в течение нескольких
     * секунд, иначе пользователь увидит ошибку "loading...".</p>
     * 
     * <p>Ответ может содержать:</p>
     * <ul>
     *   <li>Текст для отображения в виде всплывающего уведомления</li>
     *   <li>Пустой ответ для простого подтверждения</li>
     * </ul>
     * 
     * <p>Метод автоматически повторяет попытки при ошибках
     * с экспоненциальной задержкой. Максимум 3 попытки.</p>
     * 
     * <p><b>Обработка устаревших запросов:</b></p>
     * <ul>
     *   <li>Если callback query старше 30 секунд, Telegram вернет ошибку "query is too old"</li>
     *   <li>В этом случае метод логирует информационное сообщение и не повторяет попытки</li>
     *   <li>Это нормальное поведение и не является критической ошибкой</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 4.1, 4.2, 4.3, 4.5</p>
     * 
     * @param callbackQueryId ID callback query для ответа
     * @param text текст для отображения пользователю (может быть пустым)
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если callbackQueryId null или пустой
     * @see AnswerCallbackQuery
     */
    public void answerCallbackQuery(String callbackQueryId, String text) 
            throws TelegramApiException {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            log.error("Попытка ответить на callback query с пустым ID");
            throw new IllegalArgumentException("CallbackQueryId не может быть пустым");
        }
        
        log.debug("Отправка ответа на callback query: callbackQueryId={}, text='{}'", 
                callbackQueryId, text);
        
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text != null ? text : "")
                .build();
        
        try {
            execute(answer);
            log.debug("Ответ на callback query успешно отправлен: callbackQueryId={}", 
                    callbackQueryId);
            
        } catch (TelegramApiRequestException e) {
            // Проверяем, не устарел ли callback query
            if (e.getMessage() != null && e.getMessage().contains("query is too old")) {
                log.info("Callback query устарел (старше 30 секунд): callbackQueryId={}", 
                        callbackQueryId);
                recordMetric("stale_callback_query");
                return; // Не повторяем попытки для устаревших запросов
            }
            
            recordMetricForTelegramError(e);
            handleCallbackQueryError(e, callbackQueryId);
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}", 
                    callbackQueryId, e.getMessage());
            throw e;
        }
    }

    /**
     * Отправляет файл пользователю по Telegram file_id с inline клавиатурой.
     * 
     * <p>Этот метод используется для отправки файлов с кнопками навигации,
     * например, кнопкой "Назад к вложениям" при просмотре файла.</p>
     * 
     * <p><b>Поддерживаемые типы файлов:</b></p>
     * <ul>
     *   <li>document - отправляется через sendDocument</li>
     *   <li>photo - отправляется через sendPhoto</li>
     *   <li>video - отправляется через sendVideo</li>
     *   <li>audio - отправляется через sendAudio</li>
     * </ul>
     * 
     * <p>Метод автоматически повторяет попытки отправки при ошибках
     * с экспоненциальной задержкой. Максимум 3 попытки.</p>
     * 
     * <p><b>Требования:</b> 3.1</p>
     * 
     * @param chatId идентификатор чата для отправки файла
     * @param fileId Telegram file_id файла
     * @param fileType тип файла (document, photo, video, audio)
     * @param caption подпись к файлу (может быть null)
     * @param keyboard inline клавиатура с кнопками (может быть null)
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если chatId, fileId или fileType null/пустые, или fileType неподдерживаемый
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendFileWithKeyboard(Long chatId, String fileId, String fileType, String caption, 
                                     InlineKeyboardMarkup keyboard) throws TelegramApiException {
        // Валидация параметров
        if (chatId == null) {
            log.error("Попытка отправить файл с null chatId");
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (fileId == null || fileId.isBlank()) {
            log.error("Попытка отправить файл с пустым fileId: chatId={}", chatId);
            throw new IllegalArgumentException("FileId не может быть пустым");
        }
        
        if (fileType == null || fileType.isBlank()) {
            log.error("Попытка отправить файл с пустым fileType: chatId={}, fileId={}", 
                    chatId, fileId);
            throw new IllegalArgumentException("FileType не может быть пустым");
        }
        
        log.debug("Отправка файла с клавиатурой: chatId={}, fileId={}, fileType={}, caption='{}', hasKeyboard={}", 
                chatId, fileId, fileType, caption, keyboard != null);
        
        try {
            // Выбираем метод отправки в зависимости от типа файла
            switch (fileType.toLowerCase()) {
                case "document" -> sendDocumentWithKeyboard(chatId, fileId, caption, keyboard);
                case "photo" -> sendPhotoWithKeyboard(chatId, fileId, caption, keyboard);
                case "video" -> sendVideoWithKeyboard(chatId, fileId, caption, keyboard);
                case "audio" -> sendAudioWithKeyboard(chatId, fileId, caption, keyboard);
                default -> {
                    log.error("Неподдерживаемый тип файла: chatId={}, fileType={}", 
                            chatId, fileType);
                    throw new IllegalArgumentException(
                            "Неподдерживаемый тип файла: " + fileType + 
                            ". Поддерживаются: document, photo, video, audio");
                }
            }
            
            log.debug("Файл с клавиатурой успешно отправлен: chatId={}, fileId={}, fileType={}", 
                    chatId, fileId, fileType);
            
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            log.error("Ошибка при отправке файла с клавиатурой: chatId={}, fileId={}, fileType={}, " +
                     "errorCode={}, error={}", 
                     chatId, fileId, fileType, e.getErrorCode(), e.getMessage());
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Сетевая ошибка при отправке файла с клавиатурой: chatId={}, fileId={}, fileType={}, error={}", 
                    chatId, fileId, fileType, e.getMessage());
            throw e;
        }
    }

    /**
     * Отправляет файл пользователю по Telegram file_id.
     * 
     * <p>Этот метод используется для отправки файлов, которые уже загружены в Telegram
     * и имеют file_id. Метод автоматически выбирает правильный метод отправки
     * в зависимости от типа файла.</p>
     * 
     * <p><b>Поддерживаемые типы файлов:</b></p>
     * <ul>
     *   <li>document - отправляется через sendDocument</li>
     *   <li>photo - отправляется через sendPhoto</li>
     *   <li>video - отправляется через sendVideo</li>
     *   <li>audio - отправляется через sendAudio</li>
     * </ul>
     * 
     * <p>Метод автоматически повторяет попытки отправки при ошибках
     * с экспоненциальной задержкой. Максимум 3 попытки.</p>
     * 
     * <p><b>Требования:</b> 5.1, 5.2, 5.3, 5.4, 5.5</p>
     * 
     * @param chatId идентификатор чата для отправки файла
     * @param fileId Telegram file_id файла
     * @param fileType тип файла (document, photo, video, audio)
     * @param caption подпись к файлу (может быть null)
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если chatId, fileId или fileType null/пустые, или fileType неподдерживаемый
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendFile(Long chatId, String fileId, String fileType, String caption) 
            throws TelegramApiException {
        // Валидация параметров
        if (chatId == null) {
            log.error("Попытка отправить файл с null chatId");
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (fileId == null || fileId.isBlank()) {
            log.error("Попытка отправить файл с пустым fileId: chatId={}", chatId);
            throw new IllegalArgumentException("FileId не может быть пустым");
        }
        
        if (fileType == null || fileType.isBlank()) {
            log.error("Попытка отправить файл с пустым fileType: chatId={}, fileId={}", 
                    chatId, fileId);
            throw new IllegalArgumentException("FileType не может быть пустым");
        }
        
        log.debug("Отправка файла: chatId={}, fileId={}, fileType={}, caption='{}'", 
                chatId, fileId, fileType, caption);
        
        try {
            // Выбираем метод отправки в зависимости от типа файла
            switch (fileType.toLowerCase()) {
                case "document" -> sendDocument(chatId, fileId, caption);
                case "photo" -> sendPhoto(chatId, fileId, caption);
                case "video" -> sendVideo(chatId, fileId, caption);
                case "audio" -> sendAudio(chatId, fileId, caption);
                default -> {
                    log.error("Неподдерживаемый тип файла: chatId={}, fileType={}", 
                            chatId, fileType);
                    throw new IllegalArgumentException(
                            "Неподдерживаемый тип файла: " + fileType + 
                            ". Поддерживаются: document, photo, video, audio");
                }
            }
            
            log.debug("Файл успешно отправлен: chatId={}, fileId={}, fileType={}", 
                    chatId, fileId, fileType);
            
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            log.error("Ошибка при отправке файла: chatId={}, fileId={}, fileType={}, " +
                     "errorCode={}, error={}", 
                     chatId, fileId, fileType, e.getErrorCode(), e.getMessage());
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Сетевая ошибка при отправке файла: chatId={}, fileId={}, fileType={}, error={}", 
                    chatId, fileId, fileType, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Отправляет документ пользователю.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id документа
     * @param caption подпись к документу
     * @throws TelegramApiException если отправка не удалась
     */
    private void sendDocument(Long chatId, String fileId, String caption) 
            throws TelegramApiException {
        var sendDocument = new org.telegram.telegrambots.meta.api.methods.send.SendDocument();
        sendDocument.setChatId(chatId.toString());
        sendDocument.setDocument(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
        
        if (caption != null && !caption.isBlank()) {
            sendDocument.setCaption(caption);
            sendDocument.setParseMode("MarkdownV2");
        }
        
        execute(sendDocument);
    }
    
    /**
     * Отправляет фотографию пользователю.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id фотографии
     * @param caption подпись к фотографии
     * @throws TelegramApiException если отправка не удалась
     */
    private void sendPhoto(Long chatId, String fileId, String caption) 
            throws TelegramApiException {
        var sendPhoto = new org.telegram.telegrambots.meta.api.methods.send.SendPhoto();
        sendPhoto.setChatId(chatId.toString());
        sendPhoto.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
        
        if (caption != null && !caption.isBlank()) {
            sendPhoto.setCaption(caption);
            sendPhoto.setParseMode("MarkdownV2");
        }
        
        execute(sendPhoto);
    }
    
    /**
     * Отправляет видео пользователю.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id видео
     * @param caption подпись к видео
     * @throws TelegramApiException если отправка не удалась
     */
    private void sendVideo(Long chatId, String fileId, String caption) 
            throws TelegramApiException {
        var sendVideo = new org.telegram.telegrambots.meta.api.methods.send.SendVideo();
        sendVideo.setChatId(chatId.toString());
        sendVideo.setVideo(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
        
        if (caption != null && !caption.isBlank()) {
            sendVideo.setCaption(caption);
            sendVideo.setParseMode("MarkdownV2");
        }
        
        execute(sendVideo);
    }
    
    /**
     * Отправляет аудио пользователю.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id аудио
     * @param caption подпись к аудио
     * @throws TelegramApiException если отправка не удалась
     */
    private void sendAudio(Long chatId, String fileId, String caption) 
            throws TelegramApiException {
        var sendAudio = new org.telegram.telegrambots.meta.api.methods.send.SendAudio();
        sendAudio.setChatId(chatId.toString());
        sendAudio.setAudio(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
        
        if (caption != null && !caption.isBlank()) {
            sendAudio.setCaption(caption);
            sendAudio.setParseMode("MarkdownV2");
        }
        
        execute(sendAudio);
    }

    /**
     * Отправляет документ пользователю с inline клавиатурой.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id документа
     * @param caption подпись к документу
     * @param keyboard inline клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    private void sendDocumentWithKeyboard(Long chatId, String fileId, String caption, 
                                          InlineKeyboardMarkup keyboard) throws TelegramApiException {
        var sendDocument = new org.telegram.telegrambots.meta.api.methods.send.SendDocument();
        sendDocument.setChatId(chatId.toString());
        sendDocument.setDocument(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
        
        if (caption != null && !caption.isBlank()) {
            sendDocument.setCaption(caption);
            sendDocument.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendDocument.setReplyMarkup(keyboard);
        }
        
        execute(sendDocument);
    }
    
    /**
     * Отправляет фотографию пользователю с inline клавиатурой.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id фотографии
     * @param caption подпись к фотографии
     * @param keyboard inline клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    private void sendPhotoWithKeyboard(Long chatId, String fileId, String caption, 
                                       InlineKeyboardMarkup keyboard) throws TelegramApiException {
        var sendPhoto = new org.telegram.telegrambots.meta.api.methods.send.SendPhoto();
        sendPhoto.setChatId(chatId.toString());
        sendPhoto.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
        
        if (caption != null && !caption.isBlank()) {
            sendPhoto.setCaption(caption);
            sendPhoto.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendPhoto.setReplyMarkup(keyboard);
        }
        
        execute(sendPhoto);
    }
    
    /**
     * Отправляет видео пользователю с inline клавиатурой.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id видео
     * @param caption подпись к видео
     * @param keyboard inline клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    private void sendVideoWithKeyboard(Long chatId, String fileId, String caption, 
                                       InlineKeyboardMarkup keyboard) throws TelegramApiException {
        var sendVideo = new org.telegram.telegrambots.meta.api.methods.send.SendVideo();
        sendVideo.setChatId(chatId.toString());
        sendVideo.setVideo(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
        
        if (caption != null && !caption.isBlank()) {
            sendVideo.setCaption(caption);
            sendVideo.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendVideo.setReplyMarkup(keyboard);
        }
        
        execute(sendVideo);
    }
    
    /**
     * Отправляет аудио пользователю с inline клавиатурой.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id аудио
     * @param caption подпись к аудио
     * @param keyboard inline клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    private void sendAudioWithKeyboard(Long chatId, String fileId, String caption, 
                                       InlineKeyboardMarkup keyboard) throws TelegramApiException {
        var sendAudio = new org.telegram.telegrambots.meta.api.methods.send.SendAudio();
        sendAudio.setChatId(chatId.toString());
        sendAudio.setAudio(new org.telegram.telegrambots.meta.api.objects.InputFile(fileId));
        
        if (caption != null && !caption.isBlank()) {
            sendAudio.setCaption(caption);
            sendAudio.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendAudio.setReplyMarkup(keyboard);
        }
        
        execute(sendAudio);
    }

    /**
     * Удаляет сообщение из чата.
     * 
     * <p>Этот метод используется для удаления промежуточных сообщений пользователя,
     * например, текстовых сообщений с новыми значениями полей при редактировании события.
     * Удаление помогает поддерживать чистоту чата.</p>
     * 
     * <p><b>Обработка ошибок:</b></p>
     * <ul>
     *   <li>Сообщение не найдено или удалено - логирует предупреждение, не выбрасывает исключение</li>
     *   <li>Сообщение слишком старое (>48 часов) - логирует предупреждение, не выбрасывает исключение</li>
     *   <li>Нет прав на удаление - логирует предупреждение, не выбрасывает исключение</li>
     *   <li>Другие ошибки - логирует предупреждение, не выбрасывает исключение</li>
     * </ul>
     * 
     * <p>Метод никогда не выбрасывает исключения, чтобы ошибка удаления сообщения
     * не прерывала основной процесс обработки (например, обновление события).</p>
     * 
     * <p><b>Требования:</b> 5.4, 8.1, 8.2, 8.3, 8.4</p>
     * 
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения для удаления
     * @see DeleteMessage
     */
    public void deleteMessage(Long chatId, Integer messageId) {
        if (chatId == null) {
            log.error("Попытка удалить сообщение с null chatId");
            return;
        }
        
        if (messageId == null) {
            log.error("Попытка удалить сообщение с null messageId: chatId={}", chatId);
            return;
        }
        
        log.debug("Удаление сообщения: chatId={}, messageId={}", chatId, messageId);
        
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId.toString());
        deleteMessage.setMessageId(messageId);
        
        try {
            execute(deleteMessage);
            log.debug("Сообщение успешно удалено: chatId={}, messageId={}", chatId, messageId);
            
        } catch (TelegramApiRequestException e) {
            // Проверяем, не удалено ли сообщение уже
            if (isMessageNotFoundError(e)) {
                log.info("Сообщение не найдено или уже удалено: chatId={}, messageId={}", 
                        chatId, messageId);
                return;
            }
            
            // Проверяем, не слишком ли старое сообщение
            if (isMessageTooOldError(e)) {
                log.info("Сообщение слишком старое для удаления: chatId={}, messageId={}", 
                        chatId, messageId);
                return;
            }
            
            // Логируем другие ошибки как предупреждения, но не выбрасываем исключение
            log.warn("Не удалось удалить сообщение: chatId={}, messageId={}, errorCode={}, error={}", 
                    chatId, messageId, e.getErrorCode(), e.getMessage());
            
        } catch (TelegramApiException e) {
            // Логируем сетевые и другие ошибки как предупреждения
            log.warn("Ошибка при удалении сообщения: chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());
        }
    }

    /**
     * Метод восстановления после неудачных попыток отправки сообщения.
     * 
     * <p>Этот метод вызывается Spring Retry, когда все попытки отправки
     * исчерпаны. Он логирует финальную ошибку и может выполнить
     * дополнительные действия (например, сохранить сообщение для
     * повторной отправки позже).</p>
     * 
     * <p>В текущей реализации метод только логирует ошибку.
     * В production можно добавить:</p>
     * <ul>
     *   <li>Сохранение неотправленных сообщений в БД</li>
     *   <li>Отправку уведомлений администраторам</li>
     *   <li>Метрики для мониторинга</li>
     * </ul>
     * 
     * @param e исключение, вызвавшее сбой всех попыток
     * @param telegramId Telegram ID пользователя (может быть null)
     * @param text текст сообщения
     * @see Recover
     */
    @Recover
    public void recoverSendMessage(TelegramApiException e, Long telegramId, String text) {
        log.error("Все попытки отправки сообщения исчерпаны: telegramId={}, textLength={}, error={}", 
                telegramId, text != null ? text.length() : 0, e.getMessage());
        
        // TODO: В production можно добавить сохранение неотправленных сообщений
        // для повторной отправки позже или уведомление администраторов
    }

    /**
     * Метод восстановления после неудачных попыток отправки сообщения с кнопками.
     * 
     * @param e исключение, вызвавшее сбой всех попыток
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения
     * @param replyMarkup разметка inline кнопок
     * @see Recover
     */
    @Recover
    public void recoverSendMessageWithMarkup(TelegramApiException e, Long telegramId, 
                                            String text, InlineKeyboardMarkup replyMarkup) {
        log.error("Все попытки отправки сообщения с кнопками исчерпаны: telegramId={}, textLength={}, error={}", 
                telegramId, text != null ? text.length() : 0, e.getMessage());
    }

    /**
     * Метод восстановления после неудачных попыток отправки сообщения с reply клавиатурой.
     * 
     * @param e исключение, вызвавшее сбой всех попыток
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения
     * @param keyboard reply клавиатура
     * @see Recover
     */
    @Recover
    public void recoverSendMessageWithKeyboard(TelegramApiException e, Long telegramId, 
                                               String text, ReplyKeyboardMarkup keyboard) {
        log.error("Все попытки отправки сообщения с reply клавиатурой исчерпаны: telegramId={}, textLength={}, error={}", 
                telegramId, text != null ? text.length() : 0, e.getMessage());
    }

    /**
     * Метод восстановления после неудачных попыток редактирования сообщения.
     * 
     * @param e исключение, вызвавшее сбой всех попыток
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param newText новый текст
     * @param replyMarkup inline клавиатура
     * @see Recover
     */
    @Recover
    public void recoverEditMessageText(TelegramApiException e, Long chatId, Integer messageId,
                                       String newText, InlineKeyboardMarkup replyMarkup) {
        log.error("Все попытки редактирования сообщения исчерпаны: chatId={}, messageId={}, error={}", 
                chatId, messageId, e.getMessage());
    }

    /**
     * Метод восстановления после неудачных попыток ответа на callback query.
     * 
     * @param e исключение, вызвавшее сбой всех попыток
     * @param callbackQueryId ID callback query
     * @param text текст ответа
     * @see Recover
     */
    @Recover
    public void recoverAnswerCallbackQuery(TelegramApiException e, String callbackQueryId, String text) {
        log.error("Все попытки ответа на callback query исчерпаны: callbackQueryId={}, error={}", 
                callbackQueryId, e.getMessage());
    }

    /**
     * Метод восстановления после неудачных попыток отправки файла.
     * 
     * @param e исключение, вызвавшее сбой всех попыток
     * @param chatId ID чата
     * @param fileId Telegram file_id
     * @param fileType тип файла
     * @param caption подпись к файлу
     * @see Recover
     */
    @Recover
    public void recoverSendFile(TelegramApiException e, Long chatId, String fileId, 
                               String fileType, String caption) {
        log.error("Все попытки отправки файла исчерпаны: chatId={}, fileId={}, fileType={}, error={}", 
                chatId, fileId, fileType, e.getMessage());
    }

    /**
     * Метод восстановления после неудачных попыток отправки файла с клавиатурой.
     * 
     * @param e исключение, вызвавшее сбой всех попыток
     * @param chatId ID чата
     * @param fileId Telegram file_id
     * @param fileType тип файла
     * @param caption подпись к файлу
     * @param keyboard inline клавиатура
     * @see Recover
     */
    @Recover
    public void recoverSendFileWithKeyboard(TelegramApiException e, Long chatId, String fileId, 
                                           String fileType, String caption, InlineKeyboardMarkup keyboard) {
        log.error("Все попытки отправки файла с клавиатурой исчерпаны: chatId={}, fileId={}, fileType={}, error={}", 
                chatId, fileId, fileType, e.getMessage());
    }

    /**
     * Обрабатывает ошибки Telegram API с детальным логированием.
     * 
     * <p>Различные коды ошибок требуют разной обработки:</p>
     * <ul>
     *   <li>400 - некорректные параметры, не требует retry (логирует текст для отладки MarkdownV2)</li>
     *   <li>401 - неверный токен, критическая ошибка</li>
     *   <li>403 - бот заблокирован пользователем</li>
     *   <li>404 - чат не найден</li>
     *   <li>429 - rate limit, требует увеличения задержки</li>
     *   <li>500+ - ошибки сервера, требует retry</li>
     * </ul>
     * 
     * @param e исключение от Telegram API
     * @param telegramId Telegram ID пользователя (может быть null)
     * @param text текст сообщения для логирования при ошибке парсинга (может быть null)
     */
    private void handleTelegramApiError(TelegramApiRequestException e, Long telegramId, String text) {
        Integer errorCode = e.getErrorCode();
        String apiResponse = e.getApiResponse();
        
        if (errorCode == null) {
            log.error("Ошибка Telegram API без кода: telegramId={}, response={}, stackTrace={}", 
                    telegramId, apiResponse, getStackTraceString(e));
            return;
        }
        
        switch (errorCode) {
            case 400:
                // Детальное логирование для ошибки 400 (Bad Request)
                String textPreview = text != null 
                    ? text.substring(0, Math.min(200, text.length())) 
                    : "null";
                
                log.error("Bad Request (400): Ошибка парсинга MarkdownV2. " +
                         "telegramId={}, textPreview='{}', fullTextLength={}, response={}, stackTrace={}", 
                         telegramId, textPreview, text != null ? text.length() : 0, 
                         apiResponse, getStackTraceString(e));
                
                // Логируем полный текст сообщения для детальной диагностики
                if (text != null) {
                    log.debug("Полный текст сообщения при ошибке 400: telegramId={}, fullText='{}'", 
                            telegramId, text);
                }
                break;
                
            case 401:
                log.error("Unauthorized (401): Неверный токен бота! Проверьте TELEGRAM_BOT_TOKEN. " +
                         "response={}, stackTrace={}", 
                         apiResponse, getStackTraceString(e));
                break;
                
            case 403:
                log.warn("Forbidden (403): Бот заблокирован пользователем или нет доступа. " +
                        "telegramId={}, response={}", 
                        telegramId, apiResponse);
                break;
                
            case 404:
                log.warn("Not Found (404): Чат не найден. telegramId={}, response={}", 
                        telegramId, apiResponse);
                break;
                
            case 429:
                log.warn("Too Many Requests (429): Превышен лимит запросов. " +
                        "Требуется увеличить задержку. telegramId={}, response={}", 
                        telegramId, apiResponse);
                break;
                
            default:
                if (errorCode >= 500) {
                    log.error("Server Error ({}): Ошибка сервера Telegram. " +
                            "telegramId={}, response={}, stackTrace={}", 
                            errorCode, telegramId, apiResponse, getStackTraceString(e));
                } else {
                    log.error("Telegram API Error ({}): telegramId={}, response={}, stackTrace={}", 
                            errorCode, telegramId, apiResponse, getStackTraceString(e));
                }
        }
    }

    /**
     * Обрабатывает ошибки при ответе на callback query.
     * 
     * <p>Специализированная обработка ошибок для callback queries,
     * включая детальное логирование с callbackQueryId.</p>
     * 
     * <p><b>Требования:</b> 4.4, 5.2</p>
     * 
     * @param e исключение от Telegram API
     * @param callbackQueryId ID callback query
     */
    private void handleCallbackQueryError(TelegramApiRequestException e, String callbackQueryId) {
        Integer errorCode = e.getErrorCode();
        String apiResponse = e.getApiResponse();
        
        if (errorCode == null) {
            log.error("Ошибка Telegram API при ответе на callback query без кода: " +
                     "callbackQueryId={}, response={}, stackTrace={}", 
                     callbackQueryId, apiResponse, getStackTraceString(e));
            return;
        }
        
        switch (errorCode) {
            case 400:
                log.error("Bad Request (400): Ошибка при ответе на callback query. " +
                         "callbackQueryId={}, response={}, stackTrace={}", 
                         callbackQueryId, apiResponse, getStackTraceString(e));
                break;
                
            case 401:
                log.error("Unauthorized (401): Неверный токен бота! Проверьте TELEGRAM_BOT_TOKEN. " +
                         "callbackQueryId={}, response={}, stackTrace={}", 
                         callbackQueryId, apiResponse, getStackTraceString(e));
                break;
                
            case 403:
                log.warn("Forbidden (403): Нет доступа к callback query. " +
                        "callbackQueryId={}, response={}", 
                        callbackQueryId, apiResponse);
                break;
                
            case 429:
                log.warn("Too Many Requests (429): Превышен лимит запросов. " +
                        "Требуется увеличить задержку. callbackQueryId={}, response={}", 
                        callbackQueryId, apiResponse);
                break;
                
            default:
                if (errorCode >= 500) {
                    log.error("Server Error ({}): Ошибка сервера Telegram при ответе на callback query. " +
                            "callbackQueryId={}, response={}, stackTrace={}", 
                            errorCode, callbackQueryId, apiResponse, getStackTraceString(e));
                } else {
                    log.error("Telegram API Error ({}): callbackQueryId={}, response={}, stackTrace={}", 
                            errorCode, callbackQueryId, apiResponse, getStackTraceString(e));
                }
        }
    }

    /**
     * Выполняет отправку сообщения с ручным управлением повторными попытками.
     * 
     * <p>Этот метод реализует собственную логику retry вместо использования
     * Spring @Retryable, чтобы иметь полный контроль над процессом и
     * возможность прекратить попытки при ошибках парсинга.</p>
     * 
     * <p><b>Стратегия retry:</b></p>
     * <ul>
     *   <li>При ошибках парсинга (400 Bad Request с "can't parse") - прекращаем попытки</li>
     *   <li>При других ошибках - продолжаем до maxAttempts</li>
     *   <li>Экспоненциальная задержка между попытками: 1с, 2с, 4с</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 4.3, 4.4, 5.4</p>
     * 
     * @param message сообщение для отправки
     * @param telegramId ID пользователя для логирования
     * @param text текст сообщения для логирования
     * @param maxAttempts максимальное количество попыток
     * @throws TelegramApiException если все попытки не удались
     */
    private void executeWithRetry(SendMessage message, Long telegramId, String text, int maxAttempts) 
            throws TelegramApiException {
        int attempt = 0;
        TelegramApiException lastException = null;
        
        while (attempt < maxAttempts) {
            attempt++;
            
            try {
                execute(message);
                return; // Успешно отправлено
                
            } catch (TelegramApiRequestException e) {
                lastException = e;
                recordMetricForTelegramError(e);
                
                // Детальное логирование каждой попытки
                String textPreview = text != null 
                    ? text.substring(0, Math.min(50, text.length())) 
                    : "null";
                
                log.error("Bad Request (400): Ошибка парсинга MarkdownV2. " +
                         "telegramId={}, textPreview='{}', attempt={}/{}, response={}", 
                         telegramId, textPreview, attempt, maxAttempts, e.getApiResponse());
                
                // Если это ошибка парсинга, прекращаем попытки
                if (isParseError(e)) {
                    log.error("Критическая ошибка парсинга MarkdownV2, прекращаем попытки: " +
                             "telegramId={}, attempts={}, error={}", 
                             telegramId, attempt, e.getMessage());
                    handleTelegramApiError(e, telegramId, text);
                    throw e;
                }
                
                // Детальное логирование при ошибке 400
                if (e.getErrorCode() != null && e.getErrorCode() == 400) {
                    log.debug("Полный текст сообщения при ошибке 400: telegramId={}, fullText='{}'", 
                            telegramId, text);
                }
                
                handleTelegramApiError(e, telegramId, text);
                
                // Если это не последняя попытка, делаем задержку
                if (attempt < maxAttempts) {
                    try {
                        long delay = (long) (1000 * Math.pow(2, attempt - 1));
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
                
            } catch (TelegramApiException e) {
                lastException = e;
                recordMetric("network_error");
                
                log.error("Ошибка при отправке сообщения: telegramId={}, attempt={}/{}, error={}", 
                        telegramId, attempt, maxAttempts, e.getMessage());
                
                // Если это не последняя попытка, делаем задержку
                if (attempt < maxAttempts) {
                    try {
                        long delay = (long) (1000 * Math.pow(2, attempt - 1));
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
        }
        
        // Все попытки исчерпаны
        log.error("Все попытки отправки сообщения исчерпаны: " +
                 "telegramId={}, textLength={}, attempts={}, error={}, stackTrace={}", 
                 telegramId, text != null ? text.length() : 0, attempt, 
                 lastException != null ? lastException.getMessage() : "unknown",
                 lastException != null ? getStackTraceString(lastException) : "no stack trace");
        
        if (lastException != null) {
            throw lastException;
        }
    }

    /**
     * Проверяет, является ли исключение ошибкой парсинга MarkdownV2.
     * 
     * <p>Ошибки парсинга имеют код 400 (Bad Request) и содержат
     * текст "can't parse entities" в сообщении об ошибке.</p>
     * 
     * <p><b>Требования:</b> 4.3, 4.4</p>
     * 
     * @param e исключение от Telegram API
     * @return true если это ошибка парсинга, false иначе
     */
    private boolean isParseError(TelegramApiRequestException e) {
        if (e.getErrorCode() == null || e.getErrorCode() != 400) {
            return false;
        }
        
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("can't parse entities")) ||
               (apiResponse != null && apiResponse.contains("can't parse entities"));
    }

    /**
     * Валидирует параметры для отправки сообщения.
     * 
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения
     * @throws IllegalArgumentException если параметры некорректны
     */
    private void validateSendMessageParams(Long telegramId, String text) {
        if (telegramId == null) {
            log.error("Попытка отправить сообщение с null telegramId");
            throw new IllegalArgumentException("TelegramId не может быть null");
        }
        
        if (text == null || text.isBlank()) {
            log.error("Попытка отправить пустое сообщение: telegramId={}", telegramId);
            throw new IllegalArgumentException("Текст сообщения не может быть пустым");
        }
        
        if (text.length() > 4096) {
            log.warn("Текст сообщения превышает лимит Telegram (4096 символов): telegramId={}, length={}", 
                    telegramId, text.length());
            // Telegram API автоматически обрежет сообщение или вернет ошибку
        }
    }

    /**
     * Подсчитывает количество кнопок в inline клавиатуре.
     * 
     * @param markup разметка inline клавиатуры
     * @return количество кнопок
     */
    private int countButtons(InlineKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null) {
            return 0;
        }
        
        return markup.getKeyboard().stream()
                .mapToInt(row -> row != null ? row.size() : 0)
                .sum();
    }

    /**
     * Записывает метрику ошибки на основе кода ошибки Telegram API.
     * 
     * @param e исключение от Telegram API
     */
    private void recordMetricForTelegramError(TelegramApiRequestException e) {
        Integer errorCode = e.getErrorCode();
        
        if (errorCode == null) {
            recordMetric("unknown_error");
            return;
        }
        
        String errorType = switch (errorCode) {
            case 400 -> "bad_request";
            case 401 -> "unauthorized";
            case 403 -> "forbidden";
            case 404 -> "not_found";
            case 429 -> "rate_limit_error";
            default -> errorCode >= 500 ? "server_error" : "telegram_api_error";
        };
        
        recordMetric(errorType);
    }
    
    /**
     * Записывает метрику ошибки отправки сообщения.
     * 
     * @param errorType тип ошибки
     */
    private void recordMetric(String errorType) {
        if (metricsService != null) {
            metricsService.recordMessageSendError(errorType);
        }
    }
    
    /**
     * Получает строковое представление стека вызовов исключения.
     * 
     * <p>Используется для детального логирования критических ошибок.</p>
     * 
     * @param e исключение
     * @return строка со стеком вызовов (первые 5 элементов)
     */
    private String getStackTraceString(Exception e) {
        if (e == null || e.getStackTrace() == null || e.getStackTrace().length == 0) {
            return "no stack trace";
        }
        
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = e.getStackTrace();
        int limit = Math.min(5, elements.length);
        
        for (int i = 0; i < limit; i++) {
            sb.append(elements[i].toString());
            if (i < limit - 1) {
                sb.append(" -> ");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Получает детальное описание inline клавиатуры для логирования.
     * 
     * <p>Возвращает информацию о кнопках: текст и callback data.</p>
     * 
     * @param markup разметка inline клавиатуры
     * @return строка с деталями клавиатуры
     */
    private String getKeyboardDetails(InlineKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null || markup.getKeyboard().isEmpty()) {
            return "empty keyboard";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        
        int rowIndex = 0;
        for (var row : markup.getKeyboard()) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            
            sb.append("row").append(rowIndex).append("=[");
            
            int btnIndex = 0;
            for (var button : row) {
                if (button != null) {
                    sb.append("{text='").append(button.getText())
                      .append("', callback='").append(button.getCallbackData())
                      .append("'}");
                    
                    if (btnIndex < row.size() - 1) {
                        sb.append(", ");
                    }
                }
                btnIndex++;
            }
            
            sb.append("]");
            
            if (rowIndex < markup.getKeyboard().size() - 1) {
                sb.append(", ");
            }
            rowIndex++;
        }
        
        sb.append("]");
        return sb.toString();
    }
}
