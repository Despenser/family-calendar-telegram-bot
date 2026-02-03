package ru.golubyatnikov.family.calendar.bot.service.dispatcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.golubyatnikov.family.calendar.bot.config.BotConfig;
import ru.golubyatnikov.family.calendar.bot.service.authorization.AuthorizationMetricsService;
import ru.golubyatnikov.family.calendar.bot.service.formatting.MessageFormatter;
import ru.golubyatnikov.family.calendar.bot.service.telegram.MessageRetryService;

/**
 * Сервис для обработки callback queries и редактирования сообщений.
 * 
 * <p>CallbackQueryService отвечает за обработку callback queries от inline кнопок,
 * редактирование и удаление сообщений. Основные функции:</p>
 * <ul>
 *   <li>Ответы на callback queries</li>
 *   <li>Редактирование текста сообщений</li>
 *   <li>Удаление сообщений</li>
 *   <li>Обработка устаревших callback queries</li>
 *   <li>Обработка удаленных/старых сообщений</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Service
@Slf4j
public class CallbackQueryService extends DefaultAbsSender {

    private final BotConfig botConfig;
    private final MessageFormatter messageFormatter;
    private final MessageRetryService retryService;
    private final AuthorizationMetricsService metricsService;

    /**
     * Конструктор для инициализации сервиса.
     * 
     * @param botConfig конфигурация бота
     * @param messageFormatter форматтер сообщений
     * @param retryService сервис retry логики
     * @param metricsService сервис метрик
     */
    public CallbackQueryService(BotConfig botConfig, MessageFormatter messageFormatter, 
                               MessageRetryService retryService, AuthorizationMetricsService metricsService) {
        super(new DefaultBotOptions());
        this.botConfig = botConfig;
        this.messageFormatter = messageFormatter;
        this.retryService = retryService;
        this.metricsService = metricsService;
        log.debug("CallbackQueryService инициализирован");
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    /**
     * Отправляет ответ на callback query от inline кнопки.
     * 
     * @param callbackQueryId ID callback query для ответа
     * @param text текст для отображения пользователю
     * @throws TelegramApiException если отправка не удалась
     */
    public void answerCallbackQuery(String callbackQueryId, String text) 
            throws TelegramApiException {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            throw new IllegalArgumentException("CallbackQueryId не может быть пустым");
        }
        
        log.debug("Отправка ответа на callback query: callbackQueryId={}", callbackQueryId);
        
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text != null ? text : "")
                .build();
        
        try {
            execute(answer);
            log.debug("Ответ на callback query успешно отправлен: callbackQueryId={}", callbackQueryId);
            
        } catch (TelegramApiRequestException e) {
            // Проверяем, не устарел ли callback query
            if (e.getMessage() != null && e.getMessage().contains("query is too old")) {
                log.info("Callback query устарел (старше 30 секунд): callbackQueryId={}", callbackQueryId);
                recordMetric("stale_callback_query");
                return;
            }
            
            retryService.handleCallbackQueryError(e, callbackQueryId);
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Ошибка при ответе на callback query: callbackQueryId={}, error={}", 
                    callbackQueryId, e.getMessage());
            throw e;
        }
    }

    /**
     * Редактирует текст существующего сообщения.
     * 
     * @param chatId ID чата, где находится сообщение
     * @param messageId ID сообщения для редактирования
     * @param newText новый текст сообщения
     * @param replyMarkup новая inline клавиатура
     * @throws TelegramApiException если редактирование не удалось
     */
    public void editMessageText(Long chatId, Integer messageId, String newText, 
                               InlineKeyboardMarkup replyMarkup) throws TelegramApiException {
        if (chatId == null) {
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (messageId == null) {
            throw new IllegalArgumentException("MessageId не может быть null");
        }
        
        if (newText == null || newText.isBlank()) {
            throw new IllegalArgumentException("Новый текст не может быть пустым");
        }
        
        log.debug("Редактирование сообщения: chatId={}, messageId={}", chatId, messageId);
        
        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(newText)
                .parseMode("MarkdownV2")
                .replyMarkup(replyMarkup)
                .build();
        
        try {
            execute(editMessage);
            log.debug("Сообщение успешно отредактировано: chatId={}, messageId={}", chatId, messageId);
            
        } catch (TelegramApiRequestException e) {
            retryService.handleTelegramApiError(e, chatId, newText);
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Ошибка при редактировании сообщения: chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());
            throw e;
        }
    }

    /**
     * Пытается отредактировать сообщение с обработкой ошибок удаленных сообщений.
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param newText новый текст
     * @param replyMarkup новая клавиатура
     * @return true если редактирование успешно, false если сообщение не найдено
     * @throws TelegramApiException при других ошибках
     */
    public boolean tryEditMessageText(Long chatId, Integer messageId, String newText, 
                                      InlineKeyboardMarkup replyMarkup) throws TelegramApiException {
        try {
            editMessageText(chatId, messageId, newText, replyMarkup);
            return true;
            
        } catch (TelegramApiRequestException e) {
            if (messageFormatter.isMessageNotFoundError(e)) {
                log.info("Сообщение не найдено или удалено: chatId={}, messageId={}", chatId, messageId);
                return false;
            }
            
            if (messageFormatter.isMessageTooOldError(e)) {
                log.info("Сообщение слишком старое для редактирования: chatId={}, messageId={}", 
                        chatId, messageId);
                return false;
            }
            
            if (messageFormatter.isMessageNotModifiedError(e)) {
                log.debug("Сообщение не изменилось: chatId={}, messageId={}", chatId, messageId);
                return true;
            }
            
            throw e;
        }
    }

    /**
     * Удаляет сообщение и возвращает результат операции.
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения для удаления
     * @return true если удаление успешно, false если сообщение не найдено
     * @throws TelegramApiException если удаление не удалось
     */
    public boolean deleteMessage(Long chatId, Integer messageId) throws TelegramApiException {
        if (chatId == null) {
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (messageId == null) {
            throw new IllegalArgumentException("MessageId не может быть null");
        }
        
        log.debug("Удаление сообщения: chatId={}, messageId={}", chatId, messageId);
        
        DeleteMessage deleteMessage = DeleteMessage.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .build();
        
        try {
            execute(deleteMessage);
            log.debug("Сообщение успешно удалено: chatId={}, messageId={}", chatId, messageId);
            return true;
            
        } catch (TelegramApiRequestException e) {
            if (messageFormatter.isMessageDeleteNotFoundError(e)) {
                log.info("Сообщение для удаления не найдено: chatId={}, messageId={}", 
                        chatId, messageId);
                return false;
            }
            
            log.error("Ошибка при удалении сообщения: chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Сетевая ошибка при удалении сообщения: chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());
            throw e;
        }
    }

    /**
     * Удаляет сообщение без выброса исключений (silent mode).
     * 
     * <p>Этот метод используется когда удаление сообщения желательно, но не критично.
     * Все ошибки логируются, но не пробрасываются дальше.</p>
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения для удаления
     */
    public void deleteMessageSilently(Long chatId, Integer messageId) {
        if (chatId == null) {
            log.error("Попытка удалить сообщение с null chatId");
            return;
        }
        
        if (messageId == null) {
            log.error("Попытка удалить сообщение с null messageId: chatId={}", chatId);
            return;
        }
        
        try {
            deleteMessage(chatId, messageId);
            log.debug("Сообщение успешно удалено (silent): chatId={}, messageId={}", chatId, messageId);
        } catch (TelegramApiException e) {
            log.warn("Не удалось удалить сообщение (silent mode): chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());
        }
    }

    /**
     * Пытается удалить сообщение с обработкой ошибок.
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @return true если удаление успешно, false если сообщение не найдено
     */
    public boolean tryDeleteMessage(Long chatId, Integer messageId) {
        try {
            return deleteMessage(chatId, messageId);
        } catch (TelegramApiException e) {
            log.warn("Не удалось удалить сообщение: chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());
            return false;
        }
    }

    private void recordMetric(String errorType) {
        if (metricsService != null) {
            metricsService.recordMessageSendError(errorType);
        }
    }
}
