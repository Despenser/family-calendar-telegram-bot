package ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.golubyatnikov.family.calendar.bot.util.TelegramExceptionUtil;

/**
 * TODO нужен рефакторинг много дублирующихся методов
 * Сервис для базовой отправки сообщений через Telegram Bot API.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageSender {

    private final TelegramClient telegramClient;

    /**
     * Отправляет текстовое сообщение пользователю.
     * 
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает MarkdownV2)
     *
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
            telegramClient.execute(message);
            log.debug("Сообщение успешно отправлено: telegramId={}", telegramId);

        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения: telegramId={}, error={}", telegramId, e.getMessage());
            throw e;
        }
    }

    /**
     * Отправляет текстовое сообщение с inline клавиатурой.
     * 
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения
     * @param replyMarkup inline клавиатура
     *
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendMessage(Long telegramId, String text, InlineKeyboardMarkup replyMarkup) throws TelegramApiException {
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
            telegramClient.execute(message);
            log.debug("Сообщение с inline кнопками успешно отправлено: telegramId={}", telegramId);

        } catch (TelegramApiRequestException e) {
            if (TelegramExceptionUtil.isParseError(e)) {
                log.warn("Ошибка парсинга MarkdownV2, переключаемся на plain text: telegramId={}", telegramId);
                
                try {
                    sendMessageWithoutFormatting(telegramId, text, replyMarkup);
                    log.info("Сообщение успешно отправлено без форматирования (fallback): telegramId={}", telegramId);

                } catch (TelegramApiException fallbackException) {
                    log.error("Fallback на plain text также не удался: telegramId={}, error={}", 
                            telegramId, fallbackException.getMessage());

                    throw fallbackException;
                }
            }
            else throw e;
        }
    }

    /**
     * Отправляет текстовое сообщение с reply клавиатурой.
     * 
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения
     * @param keyboard reply клавиатура
     *
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

        telegramClient.execute(message);
        log.debug("Сообщение с reply клавиатурой успешно отправлено: telegramId={}", telegramId);

    }

    /**
     * Отправляет сообщение и возвращает объект Message.
     * 
     * @param chatId Telegram ID пользователя
     * @param text текст сообщения
     *
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
        
        return telegramClient.execute(message);
    }

    /**
     * Отправляет сообщение с inline клавиатурой и возвращает объект Message.
     * 
     * @param telegramId Telegram ID пользователя
     * @param text текст сообщения
     * @param replyMarkup inline клавиатура
     *
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    public Message sendMessageAndGet(Long telegramId,
                                     String text,
                                     InlineKeyboardMarkup replyMarkup) throws TelegramApiException {

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
        
        return telegramClient.execute(message);
    }

    /**
     * Отправляет сообщение без форматирования.
     * 
     * @param chatId ID чата
     * @param text текст сообщения
     * @param keyboard inline клавиатура
     *
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendMessageWithoutFormatting(Long chatId,
                                             String text,
                                             InlineKeyboardMarkup keyboard) throws TelegramApiException {

        validateSendMessageParams(chatId, text);
        
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(keyboard)
                .build();
        
        telegramClient.execute(message);
    }

    /**
     * Отправляет сообщение без форматирования и возвращает объект Message.
     * 
     * @param chatId ID чата
     * @param text текст сообщения
     * @param keyboard inline клавиатура
     *
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    public Message sendMessageWithoutFormattingAndGet(Long chatId,
                                                      String text,
                                                      InlineKeyboardMarkup keyboard) throws TelegramApiException {

        validateSendMessageParams(chatId, text);
        
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(keyboard)
                .build();
        
        return telegramClient.execute(message);
    }

    private void validateSendMessageParams(Long telegramId, String text) {
        if (telegramId == null) {
            throw new IllegalArgumentException("TelegramId не может быть null");
        }
        
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Текст сообщения не может быть пустым");
        }
    }


    /**
     * Отправляет файл пользователю по Telegram file_id.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id файла
     * @param fileType тип файла (document, photo, video, audio)
     * @param caption подпись к файлу (может быть null)
     *
     * @throws TelegramApiException если отправка не удалась
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendFile(Long chatId, String fileId, String fileType, String caption) throws TelegramApiException {
        validateFileParams(chatId, fileId, fileType);
        
        log.debug("Отправка файла: chatId={}, fileId={}, fileType={}", chatId, fileId, fileType);

        switch (fileType.toLowerCase()) {
            case "document" -> sendDocument(chatId, fileId, caption);
            case "photo" -> sendPhoto(chatId, fileId, caption);
            case "video" -> sendVideo(chatId, fileId, caption);
            case "audio" -> sendAudio(chatId, fileId, caption);
            default -> throw new IllegalArgumentException(
                    "Неподдерживаемый тип файла: " + fileType + ". Поддерживаются: document, photo, video, audio");
        }

        log.debug("Файл успешно отправлен: chatId={}, fileType={}", chatId, fileType);

    }

    /**
     * Отправляет файл с inline клавиатурой.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id файла
     * @param fileType тип файла (document, photo, video, audio)
     * @param caption подпись к файлу
     * @param keyboard inline клавиатура
     *
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

        switch (fileType.toLowerCase()) {
            case "document" -> sendDocumentWithKeyboard(chatId, fileId, caption, keyboard);
            case "photo" -> sendPhotoWithKeyboard(chatId, fileId, caption, keyboard);
            case "video" -> sendVideoWithKeyboard(chatId, fileId, caption, keyboard);
            case "audio" -> sendAudioWithKeyboard(chatId, fileId, caption, keyboard);
            default -> throw new IllegalArgumentException(
                    "Неподдерживаемый тип файла: " + fileType);
        }

        log.debug("Файл с клавиатурой успешно отправлен: chatId={}, fileType={}", chatId, fileType);

    }

    /**
     * Отправляет файл с клавиатурой и возвращает отправленное сообщение.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id файла
     * @param fileType тип файла
     * @param caption подпись к файлу
     * @param keyboard inline клавиатура
     *
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    @Retryable(
        retryFor = TelegramApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public Message sendFileWithKeyboardAndGet(Long chatId,
                                              String fileId,
                                              String fileType,
                                              String caption,
                                              InlineKeyboardMarkup keyboard) throws TelegramApiException {

        validateFileParams(chatId, fileId, fileType);
        
        log.debug("Отправка файла с клавиатурой (с возвратом Message): chatId={}, fileType={}", 
                chatId, fileType);

        Message sentMessage = switch (fileType.toLowerCase()) {
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

    }

    // Приватные методы для отправки разных типов файлов

    private void sendDocument(@NonNull Long chatId, String fileId, String caption) throws TelegramApiException {

        var sendDocument = SendDocument.builder()
                .chatId(chatId.toString())
                .document(new InputFile(fileId))
                .build();

        if (caption != null && !caption.isBlank()) {
            sendDocument.setCaption(caption);
            sendDocument.setParseMode("MarkdownV2");
        }
        
        telegramClient.execute(sendDocument);
    }
    
    private void sendPhoto(@NonNull Long chatId, String fileId, String caption) throws TelegramApiException {

        var sendPhoto =  SendPhoto.builder()
                .chatId(chatId.toString())
                .photo(new InputFile(fileId))
                .build();
        
        if (caption != null && !caption.isBlank()) {
            sendPhoto.setCaption(caption);
            sendPhoto.setParseMode("MarkdownV2");
        }
        
        telegramClient.execute(sendPhoto);
    }
    
    private void sendVideo(@NonNull Long chatId, String fileId, String caption)
            throws TelegramApiException {

        var sendVideo = SendVideo.builder()
                .chatId(chatId.toString())
                .video(new InputFile(fileId))
                .build();
        
        if (caption != null && !caption.isBlank()) {
            sendVideo.setCaption(caption);
            sendVideo.setParseMode("MarkdownV2");
        }
        
        telegramClient.execute(sendVideo);
    }
    
    private void sendAudio(@NonNull Long chatId, String fileId, String caption) throws TelegramApiException {

        var sendAudio = SendAudio.builder()
                .chatId(chatId.toString())
                .audio(new InputFile(fileId))
                .build();

        if (caption != null && !caption.isBlank()) {
            sendAudio.setCaption(caption);
            sendAudio.setParseMode("MarkdownV2");
        }
        
        telegramClient.execute(sendAudio);
    }

    private void sendDocumentWithKeyboard(@NonNull Long chatId,
                                          String fileId,
                                          String caption,
                                          InlineKeyboardMarkup keyboard) throws TelegramApiException {

        var sendDocument = SendDocument.builder()
                .chatId(chatId.toString())
                .document(new InputFile(fileId))
                .build();
        
        if (caption != null && !caption.isBlank()) {
            sendDocument.setCaption(caption);
            sendDocument.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendDocument.setReplyMarkup(keyboard);
        }
        
        telegramClient.execute(sendDocument);
    }
    
    private void sendPhotoWithKeyboard(Long chatId, String fileId, String caption, 
                                       InlineKeyboardMarkup keyboard)
            throws TelegramApiException {


        var sendPhoto =  SendPhoto.builder()
                .chatId(chatId.toString())
                .photo(new InputFile(fileId))
                .build();

        if (caption != null && !caption.isBlank()) {
            sendPhoto.setCaption(caption);
            sendPhoto.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendPhoto.setReplyMarkup(keyboard);
        }
        
        telegramClient.execute(sendPhoto);
    }
    
    private void sendVideoWithKeyboard(Long chatId, String fileId, String caption, 
                                       InlineKeyboardMarkup keyboard)
            throws TelegramApiException {

        var sendVideo = SendVideo.builder()
                .chatId(chatId.toString())
                .video(new InputFile(fileId))
                .build();

        if (caption != null && !caption.isBlank()) {
            sendVideo.setCaption(caption);
            sendVideo.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendVideo.setReplyMarkup(keyboard);
        }
        
        telegramClient.execute(sendVideo);
    }
    
    private void sendAudioWithKeyboard(Long chatId, String fileId, String caption, 
                                       InlineKeyboardMarkup keyboard)
            throws TelegramApiException {

        var sendAudio = SendAudio.builder()
                .chatId(chatId.toString())
                .audio(new InputFile(fileId))
                .build();
        
        if (caption != null && !caption.isBlank()) {
            sendAudio.setCaption(caption);
            sendAudio.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendAudio.setReplyMarkup(keyboard);
        }
        
        telegramClient.execute(sendAudio);
    }

    private Message sendDocumentWithKeyboardAndGet(
            Long chatId, String fileId, String caption, 
            InlineKeyboardMarkup keyboard) throws TelegramApiException {

        var sendDocument = SendDocument.builder()
                .chatId(chatId.toString())
                .document(new InputFile(fileId))
                .build();
        
        if (caption != null && !caption.isBlank()) {
            sendDocument.setCaption(caption);
            sendDocument.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendDocument.setReplyMarkup(keyboard);
        }
        
        return telegramClient.execute(sendDocument);
    }
    
    private Message sendPhotoWithKeyboardAndGet(
            Long chatId, String fileId, String caption, 
            InlineKeyboardMarkup keyboard) throws TelegramApiException {

        var sendPhoto =  SendPhoto.builder()
                .chatId(chatId.toString())
                .photo(new InputFile(fileId))
                .build();
        
        if (caption != null && !caption.isBlank()) {
            sendPhoto.setCaption(caption);
            sendPhoto.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendPhoto.setReplyMarkup(keyboard);
        }
        
        return telegramClient.execute(sendPhoto);
    }
    
    private org.telegram.telegrambots.meta.api.objects.message.Message sendVideoWithKeyboardAndGet(
            Long chatId, String fileId, String caption, 
            InlineKeyboardMarkup keyboard) throws TelegramApiException {

        var sendVideo = SendVideo.builder()
                .chatId(chatId.toString())
                .video(new InputFile(fileId))
                .build();
        
        if (caption != null && !caption.isBlank()) {
            sendVideo.setCaption(caption);
            sendVideo.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendVideo.setReplyMarkup(keyboard);
        }
        
        return telegramClient.execute(sendVideo);
    }
    
    private org.telegram.telegrambots.meta.api.objects.message.Message sendAudioWithKeyboardAndGet(
            Long chatId, String fileId, String caption, 
            InlineKeyboardMarkup keyboard) throws TelegramApiException {

        var sendAudio = SendAudio.builder()
                .chatId(chatId.toString())
                .audio(new InputFile(fileId))
                .build();
        
        if (caption != null && !caption.isBlank()) {
            sendAudio.setCaption(caption);
            sendAudio.setParseMode("MarkdownV2");
        }
        
        if (keyboard != null) {
            sendAudio.setReplyMarkup(keyboard);
        }
        
        return telegramClient.execute(sendAudio);
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
}
