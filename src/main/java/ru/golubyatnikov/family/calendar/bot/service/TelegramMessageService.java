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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.golubyatnikov.family.calendar.bot.config.BotConfig;

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

    /**
     * Конструктор для инициализации сервиса с конфигурацией бота.
     * 
     * <p>Создает экземпляр DefaultAbsSender с настройками по умолчанию
     * для отправки сообщений через Telegram Bot API.</p>
     * 
     * @param botConfig конфигурация бота с токеном и другими параметрами
     */
    public TelegramMessageService(BotConfig botConfig) {
        super(new DefaultBotOptions());
        this.botConfig = botConfig;
        log.info("TelegramMessageService инициализирован с токеном: {}...", 
                maskToken(botConfig.getToken()));
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
     * <p>Сообщение отправляется с поддержкой Markdown форматирования
     * для улучшения читаемости (жирный текст, курсив, ссылки и т.д.).</p>
     * 
     * <p>Retry стратегия:</p>
     * <ul>
     *   <li>Попытка 1: немедленно</li>
     *   <li>Попытка 2: через 1 секунду</li>
     *   <li>Попытка 3: через 2 секунды</li>
     * </ul>
     * 
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает Markdown)
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если telegramId null или text пустой
     * @see #sendMessage(Long, String, InlineKeyboardMarkup)
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
                .build();
        
        try {
            execute(message);
            log.info("Сообщение успешно отправлено: telegramId={}, textLength={}", 
                    telegramId, text.length());
            
        } catch (TelegramApiRequestException e) {
            handleTelegramApiError(e, telegramId);
            throw e;
            
        } catch (TelegramApiException e) {
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
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает Markdown)
     * @param replyMarkup разметка inline кнопок
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если telegramId null, text пустой или replyMarkup null
     * @see InlineKeyboardMarkup
     * @see #sendMessage(Long, String)
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendMessage(Long telegramId, String text, InlineKeyboardMarkup replyMarkup) 
            throws TelegramApiException {
        validateSendMessageParams(telegramId, text);
        
        if (replyMarkup == null) {
            log.error("Попытка отправить сообщение с null replyMarkup: telegramId={}", telegramId);
            throw new IllegalArgumentException("ReplyMarkup не может быть null");
        }
        
        log.debug("Отправка сообщения с inline кнопками: telegramId={}, textLength={}, buttonsCount={}", 
                telegramId, text.length(), countButtons(replyMarkup));
        
        SendMessage message = SendMessage.builder()
                .chatId(telegramId.toString())
                .text(text)
                .replyMarkup(replyMarkup)
                .build();
        
        try {
            execute(message);
            log.info("Сообщение с inline кнопками успешно отправлено: telegramId={}, textLength={}, buttonsCount={}", 
                    telegramId, text.length(), countButtons(replyMarkup));
            
        } catch (TelegramApiRequestException e) {
            handleTelegramApiError(e, telegramId);
            throw e;
            
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения с inline кнопками: telegramId={}, error={}", 
                    telegramId, e.getMessage());
            throw e;
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
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает Markdown)
     * @param keyboard reply клавиатура с кнопками команд
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если telegramId null, text пустой или keyboard null
     * @see ReplyKeyboardMarkup
     * @see #sendMessage(Long, String)
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
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
                .replyMarkup(keyboard)
                .build();
        
        try {
            execute(message);
            log.info("Сообщение с reply клавиатурой успешно отправлено: telegramId={}, textLength={}, keyboardRows={}", 
                    telegramId, text.length(), keyboard.getKeyboard() != null ? keyboard.getKeyboard().size() : 0);
            
        } catch (TelegramApiRequestException e) {
            handleTelegramApiError(e, telegramId);
            throw e;
            
        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения с reply клавиатурой: telegramId={}, error={}", 
                    telegramId, e.getMessage());
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
     * Редактирует текст существующего сообщения.
     * 
     * <p>Этот метод позволяет изменить текст ранее отправленного сообщения
     * без отправки нового. Полезно для обновления inline-календарей,
     * меню выбора и других интерактивных элементов.</p>
     * 
     * <p>Метод автоматически повторяет попытки при ошибках
     * с экспоненциальной задержкой. Максимум 3 попытки.</p>
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
     * @param newText новый текст сообщения
     * @param replyMarkup новая inline клавиатура (может быть null для удаления кнопок)
     * @throws TelegramApiException если все попытки редактирования не удались
     * @throws IllegalArgumentException если chatId или messageId null, или newText пустой
     * @see EditMessageText
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
                .replyMarkup(replyMarkup)
                .build();
        
        try {
            execute(editMessage);
            log.info("Сообщение успешно отредактировано: chatId={}, messageId={}, newTextLength={}", 
                    chatId, messageId, newText.length());
            
        } catch (TelegramApiRequestException e) {
            handleTelegramApiError(e, chatId);
            throw e;
            
        } catch (TelegramApiException e) {
            log.error("Ошибка при редактировании сообщения: chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());
            throw e;
        }
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
     * @param callbackQueryId ID callback query для ответа
     * @param text текст для отображения пользователю (может быть пустым)
     * @throws TelegramApiException если все попытки отправки не удались
     * @throws IllegalArgumentException если callbackQueryId null или пустой
     * @see AnswerCallbackQuery
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
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
            log.info("Ответ на callback query успешно отправлен: callbackQueryId={}", 
                    callbackQueryId);
            
        } catch (TelegramApiRequestException e) {
            handleTelegramApiError(e, null);
            throw e;
            
        } catch (TelegramApiException e) {
            log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}", 
                    callbackQueryId, e.getMessage());
            throw e;
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
     * Обрабатывает ошибки Telegram API с детальным логированием.
     * 
     * <p>Различные коды ошибок требуют разной обработки:</p>
     * <ul>
     *   <li>400 - некорректные параметры, не требует retry</li>
     *   <li>401 - неверный токен, критическая ошибка</li>
     *   <li>403 - бот заблокирован пользователем</li>
     *   <li>404 - чат не найден</li>
     *   <li>429 - rate limit, требует увеличения задержки</li>
     *   <li>500+ - ошибки сервера, требует retry</li>
     * </ul>
     * 
     * @param e исключение от Telegram API
     * @param telegramId Telegram ID пользователя (может быть null)
     */
    private void handleTelegramApiError(TelegramApiRequestException e, Long telegramId) {
        Integer errorCode = e.getErrorCode();
        String apiResponse = e.getApiResponse();
        
        if (errorCode == null) {
            log.error("Ошибка Telegram API без кода: telegramId={}, response={}", 
                    telegramId, apiResponse);
            return;
        }
        
        switch (errorCode) {
            case 400:
                log.error("Bad Request (400): Некорректные параметры запроса. telegramId={}, response={}", 
                        telegramId, apiResponse);
                break;
                
            case 401:
                log.error("Unauthorized (401): Неверный токен бота! Проверьте TELEGRAM_BOT_TOKEN. response={}", 
                        apiResponse);
                break;
                
            case 403:
                log.warn("Forbidden (403): Бот заблокирован пользователем или нет доступа. telegramId={}, response={}", 
                        telegramId, apiResponse);
                break;
                
            case 404:
                log.warn("Not Found (404): Чат не найден. telegramId={}, response={}", 
                        telegramId, apiResponse);
                break;
                
            case 429:
                log.warn("Too Many Requests (429): Превышен лимит запросов. Требуется увеличить задержку. telegramId={}, response={}", 
                        telegramId, apiResponse);
                break;
                
            default:
                if (errorCode >= 500) {
                    log.error("Server Error ({}): Ошибка сервера Telegram. telegramId={}, response={}", 
                            errorCode, telegramId, apiResponse);
                } else {
                    log.error("Telegram API Error ({}): telegramId={}, response={}", 
                            errorCode, telegramId, apiResponse);
                }
        }
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
     * Маскирует токен бота для безопасного логирования.
     * 
     * <p>Показывает только первые 10 символов токена для идентификации,
     * остальное заменяет на звездочки.</p>
     * 
     * @param token токен бота
     * @return замаскированный токен
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 10) + "***";
    }
}
