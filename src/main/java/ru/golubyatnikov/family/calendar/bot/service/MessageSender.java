package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.golubyatnikov.family.calendar.bot.config.BotConfig;

/**
 * Сервис для базовой отправки сообщений через Telegram Bot API.
 * 
 * <p>MessageSender отвечает за низкоуровневую отправку сообщений пользователям
 * с автоматическими повторными попытками при ошибках. Основные функции:</p>
 * <ul>
 *   <li>Отправка текстовых сообщений</li>
 *   <li>Отправка сообщений с inline клавиатурами</li>
 *   <li>Отправка сообщений с reply клавиатурами</li>
 *   <li>Автоматические повторные попытки с экспоненциальной задержкой</li>
 *   <li>Валидация параметров отправки</li>
 * </ul>
 * 
 * <p><b>Retry механизм:</b></p>
 * <ul>
 *   <li>Максимум 3 попытки отправки</li>
 *   <li>Экспоненциальная задержка: 1с, 2с, 4с</li>
 *   <li>Автоматический retry для TelegramApiException</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Service
@Slf4j
public class MessageSender extends DefaultAbsSender {

    private final BotConfig botConfig;
    private final AuthorizationMetricsService metricsService;

    /**
     * Конструктор для инициализации сервиса с конфигурацией бота.
     * 
     * @param botConfig конфигурация бота с токеном
     * @param metricsService сервис для сбора метрик
     */
    public MessageSender(BotConfig botConfig, AuthorizationMetricsService metricsService) {
        super(new DefaultBotOptions());
        this.botConfig = botConfig;
        this.metricsService = metricsService;
        log.debug("MessageSender инициализирован");
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    /**
     * Отправляет текстовое сообщение пользователю.
     * 
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает MarkdownV2)
     * @throws TelegramApiException если все попытки отправки не удались
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendMessage(Long telegramId, String text) throws TelegramApiException {
        validateSendMessageParams(telegramId, text);
        
        log.debug("Отправка сообщения: telegramId={}, textLength={}", 
                telegramId, text.length());
        
        SendMessage message = SendMessage.builder()
                .chatId(telegramId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .build();
        
        try {
            execute(message);
            log.debug("Сообщение успешно отправлено: telegramId={}", telegramId);
            
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            throw e;
            
        } catch (TelegramApiException e) {
            recordMetric("network_error");
            log.error("Ошибка при отправке сообщения: telegramId={}, error={}", 
                    telegramId, e.getMessage());
            throw e;
        }
    }

    /**
     * Отправляет текстовое сообщение с inline клавиатурой.
     * 
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения
     * @param replyMarkup inline клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendMessage(Long telegramId, String text, InlineKeyboardMarkup replyMarkup) 
            throws TelegramApiException {
        validateSendMessageParams(telegramId, text);
        
        if (replyMarkup == null) {
            throw new IllegalArgumentException("ReplyMarkup не может быть null");
        }
        
        log.debug("Отправка сообщения с inline кнопками: telegramId={}", telegramId);
        
        SendMessage message = SendMessage.builder()
                .chatId(telegramId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .replyMarkup(replyMarkup)
                .build();
        
        try {
            execute(message);
            log.debug("Сообщение с inline кнопками успешно отправлено: telegramId={}", telegramId);
        } catch (TelegramApiRequestException e) {
            // Если это ошибка парсинга, пробуем fallback на plain text
            if (isParseError(e)) {
                log.warn("Ошибка парсинга MarkdownV2, переключаемся на plain text: telegramId={}", telegramId);
                recordMetric("markdown_parse_error_fallback");
                
                try {
                    sendMessageWithoutFormatting(telegramId, text, replyMarkup);
                    log.info("Сообщение успешно отправлено без форматирования (fallback): telegramId={}", telegramId);
                } catch (TelegramApiException fallbackException) {
                    log.error("Fallback на plain text также не удался: telegramId={}, error={}", 
                            telegramId, fallbackException.getMessage());
                    throw fallbackException;
                }
            } else {
                recordMetricForTelegramError(e);
                throw e;
            }
        }
    }

    /**
     * Отправляет текстовое сообщение с reply клавиатурой.
     * 
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения
     * @param keyboard reply клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendMessage(Long telegramId, String text, ReplyKeyboardMarkup keyboard) 
            throws TelegramApiException {
        validateSendMessageParams(telegramId, text);
        
        if (keyboard == null) {
            throw new IllegalArgumentException("Keyboard не может быть null");
        }
        
        log.debug("Отправка сообщения с reply клавиатурой: telegramId={}", telegramId);
        
        SendMessage message = SendMessage.builder()
                .chatId(telegramId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .replyMarkup(keyboard)
                .build();
        
        try {
            execute(message);
            log.debug("Сообщение с reply клавиатурой успешно отправлено: telegramId={}", telegramId);
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            throw e;
        }
    }

    /**
     * Отправляет сообщение и возвращает объект Message.
     * 
     * @param chatId Telegram ID пользователя
     * @param text текст сообщения
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public Message sendMessageAndGet(Long chatId, String text) throws TelegramApiException {
        validateSendMessageParams(chatId, text);
        
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .build();
        
        return execute(message);
    }

    /**
     * Отправляет сообщение с inline клавиатурой и возвращает объект Message.
     * 
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения
     * @param replyMarkup inline клавиатура
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    public Message sendMessageAndGet(Long telegramId, String text, InlineKeyboardMarkup replyMarkup) 
            throws TelegramApiException {
        validateSendMessageParams(telegramId, text);
        
        if (replyMarkup == null) {
            throw new IllegalArgumentException("ReplyMarkup не может быть null");
        }
        
        SendMessage message = SendMessage.builder()
                .chatId(telegramId.toString())
                .text(text)
                .parseMode("MarkdownV2")
                .replyMarkup(replyMarkup)
                .build();
        
        return execute(message);
    }

    /**
     * Отправляет сообщение без форматирования.
     * 
     * @param chatId ID чата
     * @param text текст сообщения
     * @param keyboard inline клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendMessageWithoutFormatting(Long chatId, String text, InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
        validateSendMessageParams(chatId, text);
        
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(keyboard)
                .build();
        
        execute(message);
    }

    /**
     * Отправляет сообщение без форматирования и возвращает объект Message.
     * 
     * @param chatId ID чата
     * @param text текст сообщения
     * @param keyboard inline клавиатура
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    public Message sendMessageWithoutFormattingAndGet(Long chatId, String text, InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
        validateSendMessageParams(chatId, text);
        
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(keyboard)
                .build();
        
        return execute(message);
    }

    @Recover
    public void recoverSendMessage(TelegramApiException e, Long telegramId, String text) {
        log.error("Все попытки отправки сообщения исчерпаны: telegramId={}, error={}", 
                telegramId, e.getMessage());
    }

    private void validateSendMessageParams(Long telegramId, String text) {
        if (telegramId == null) {
            throw new IllegalArgumentException("TelegramId не может быть null");
        }
        
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Текст сообщения не может быть пустым");
        }
    }

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
    
    private void recordMetric(String errorType) {
        if (metricsService != null) {
            metricsService.recordMessageSendError(errorType);
        }
    }

    /**
     * Отправляет файл пользователю по Telegram file_id.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id файла
     * @param fileType тип файла (document, photo, video, audio)
     * @param caption подпись к файлу (может быть null)
     * @throws TelegramApiException если отправка не удалась
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendFile(Long chatId, String fileId, String fileType, String caption) 
            throws TelegramApiException {
        validateFileParams(chatId, fileId, fileType);
        
        log.debug("Отправка файла: chatId={}, fileId={}, fileType={}", chatId, fileId, fileType);
        
        try {
            switch (fileType.toLowerCase()) {
                case "document" -> sendDocument(chatId, fileId, caption);
                case "photo" -> sendPhoto(chatId, fileId, caption);
                case "video" -> sendVideo(chatId, fileId, caption);
                case "audio" -> sendAudio(chatId, fileId, caption);
                default -> throw new IllegalArgumentException(
                        "Неподдерживаемый тип файла: " + fileType + 
                        ". Поддерживаются: document, photo, video, audio");
            }
            
            log.debug("Файл успешно отправлен: chatId={}, fileType={}", chatId, fileType);
            
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            throw e;
        }
    }

    /**
     * Отправляет файл с inline клавиатурой.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id файла
     * @param fileType тип файла (document, photo, video, audio)
     * @param caption подпись к файлу
     * @param keyboard inline клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendFileWithKeyboard(Long chatId, String fileId, String fileType, String caption, 
                                     org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
        validateFileParams(chatId, fileId, fileType);
        
        log.debug("Отправка файла с клавиатурой: chatId={}, fileType={}", chatId, fileType);
        
        try {
            switch (fileType.toLowerCase()) {
                case "document" -> sendDocumentWithKeyboard(chatId, fileId, caption, keyboard);
                case "photo" -> sendPhotoWithKeyboard(chatId, fileId, caption, keyboard);
                case "video" -> sendVideoWithKeyboard(chatId, fileId, caption, keyboard);
                case "audio" -> sendAudioWithKeyboard(chatId, fileId, caption, keyboard);
                default -> throw new IllegalArgumentException(
                        "Неподдерживаемый тип файла: " + fileType);
            }
            
            log.debug("Файл с клавиатурой успешно отправлен: chatId={}, fileType={}", chatId, fileType);
            
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            throw e;
        }
    }

    /**
     * Отправляет файл с клавиатурой и возвращает отправленное сообщение.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id файла
     * @param fileType тип файла
     * @param caption подпись к файлу
     * @param keyboard inline клавиатура
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public org.telegram.telegrambots.meta.api.objects.Message sendFileWithKeyboardAndGet(
            Long chatId, String fileId, String fileType, String caption, 
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
        validateFileParams(chatId, fileId, fileType);
        
        log.debug("Отправка файла с клавиатурой (с возвратом Message): chatId={}, fileType={}", 
                chatId, fileType);
        
        try {
            org.telegram.telegrambots.meta.api.objects.Message sentMessage = switch (fileType.toLowerCase()) {
                case "document" -> sendDocumentWithKeyboardAndGet(chatId, fileId, caption, keyboard);
                case "photo" -> sendPhotoWithKeyboardAndGet(chatId, fileId, caption, keyboard);
                case "video" -> sendVideoWithKeyboardAndGet(chatId, fileId, caption, keyboard);
                case "audio" -> sendAudioWithKeyboardAndGet(chatId, fileId, caption, keyboard);
                default -> throw new IllegalArgumentException(
                        "Неподдерживаемый тип файла: " + fileType);
            };
            
            log.debug("Файл с клавиатурой успешно отправлен (с возвратом Message): chatId={}, fileType={}, messageId={}", 
                    chatId, fileType, sentMessage.getMessageId());
            
            return sentMessage;
            
        } catch (TelegramApiRequestException e) {
            recordMetricForTelegramError(e);
            throw e;
        }
    }

    // Приватные методы для отправки разных типов файлов

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

    private void sendDocumentWithKeyboard(Long chatId, String fileId, String caption, 
                                          org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
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
    
    private void sendPhotoWithKeyboard(Long chatId, String fileId, String caption, 
                                       org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
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
    
    private void sendVideoWithKeyboard(Long chatId, String fileId, String caption, 
                                       org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
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
    
    private void sendAudioWithKeyboard(Long chatId, String fileId, String caption, 
                                       org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
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

    private org.telegram.telegrambots.meta.api.objects.Message sendDocumentWithKeyboardAndGet(
            Long chatId, String fileId, String caption, 
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
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
        
        return execute(sendDocument);
    }
    
    private org.telegram.telegrambots.meta.api.objects.Message sendPhotoWithKeyboardAndGet(
            Long chatId, String fileId, String caption, 
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
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
        
        return execute(sendPhoto);
    }
    
    private org.telegram.telegrambots.meta.api.objects.Message sendVideoWithKeyboardAndGet(
            Long chatId, String fileId, String caption, 
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
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
        
        return execute(sendVideo);
    }
    
    private org.telegram.telegrambots.meta.api.objects.Message sendAudioWithKeyboardAndGet(
            Long chatId, String fileId, String caption, 
            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
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
        
        return execute(sendAudio);
    }

    private void validateFileParams(Long chatId, String fileId, String fileType) {
        if (chatId == null) {
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("FileId не может быть пустым");
        }
        
        if (fileType == null || fileType.isBlank()) {
            throw new IllegalArgumentException("FileType не может быть пустым");
        }
    }

    private boolean isParseError(TelegramApiRequestException e) {
        if (e.getErrorCode() == null || e.getErrorCode() != 400) {
            return false;
        }
        
        String message = e.getMessage();
        String apiResponse = e.getApiResponse();
        
        return (message != null && message.contains("can't parse entities")) ||
               (apiResponse != null && apiResponse.contains("can't parse entities"));
    }
}
