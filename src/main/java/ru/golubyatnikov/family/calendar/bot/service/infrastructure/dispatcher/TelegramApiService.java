package ru.golubyatnikov.family.calendar.bot.service.infrastructure.dispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.MessageFormatter;

/**
 * Низкоуровневый сервис для работы с Telegram Bot API.
 * Предоставляет методы для работы с callback queries, редактирования и удаления сообщений.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-15
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramApiService {

    private final TelegramClient telegramClient;
    private final MessageFormatter messageFormatter;

    /**
     * Отправляет ответ на callback query от inline кнопки.
     * 
     * @param callbackQueryId ID callback query для ответа
     * @param text текст для отображения пользователю
     *
     * @throws TelegramApiException если отправка не удалась
     */
    public void answerCallbackQuery(String callbackQueryId, String text) throws TelegramApiException {

        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            throw new IllegalArgumentException("CallbackQueryId не может быть пустым");
        }
        
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text != null ? text : "")
                .build();
        
        try {
            telegramClient.execute(answer);
            
        } catch (TelegramApiException e) {
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
     *
     * @throws TelegramApiException если редактирование не удалось
     */
    public void editMessageText(Long chatId,
                                Integer messageId,
                                String newText,
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
        
        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text(newText)
                .parseMode("MarkdownV2")
                .replyMarkup(replyMarkup)
                .build();
        
        try {
            telegramClient.execute(editMessage);

        } catch (TelegramApiException e) {
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
     *
     * @return true, если редактирование успешно, false, если сообщение не найдено
     * @throws TelegramApiException при других ошибках
     */
    public boolean tryEditMessageText(Long chatId,
                                      Integer messageId,
                                      String newText,
                                      InlineKeyboardMarkup replyMarkup) throws TelegramApiException {

        try {
            editMessageText(chatId, messageId, newText, replyMarkup);
            return true;
            
        } catch (TelegramApiRequestException e) {
            if (messageFormatter.isMessageNotFoundError(e)) {
                return false;
            }
            
            if (messageFormatter.isMessageTooOldError(e)) {
                return false;
            }
            
            if (messageFormatter.isMessageNotModifiedError(e)) {
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
     *
     * @throws TelegramApiException если удаление не удалось
     */
    public void deleteMessage(Long chatId, Integer messageId) throws TelegramApiException {
        if (chatId == null) {
            throw new IllegalArgumentException("ChatId не может быть null");
        }
        
        if (messageId == null) {
            throw new IllegalArgumentException("MessageId не может быть null");
        }
        
        DeleteMessage deleteMessage = DeleteMessage.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .build();
        
        try {
            telegramClient.execute(deleteMessage);

        } catch (TelegramApiRequestException e) {
            if (messageFormatter.isMessageDeleteNotFoundError(e)) {
                return;
            }
            
            log.error("Ошибка при удалении сообщения: chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());

            throw e;
            
        } catch (TelegramApiException e) {
            log.error("Сетевая ошибка при удалении сообщения: chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());

            throw e;
        }
    }

    /**
     * Удаляет сообщение без выброса исключений (silent mode).
     *
     * @param chatId ID чата
     * @param messageId ID сообщения для удаления
     */
    public void deleteMessageSilently(Long chatId, Integer messageId) {
        if (chatId == null) {
            return;
        }
        
        if (messageId == null) {
            return;
        }
        
        try {
            deleteMessage(chatId, messageId);

        } catch (TelegramApiException e) {
            log.warn("Не удалось удалить сообщение (silent mode): chatId={}, messageId={}, error={}", 
                    chatId, messageId, e.getMessage());
        }
    }
}
