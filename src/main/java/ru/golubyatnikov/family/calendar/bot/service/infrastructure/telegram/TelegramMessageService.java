package ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.dispatcher.TelegramApiService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.MessageFormatter;

/**
 * Фасад для отправки сообщений через Telegram Bot API.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramMessageService {

    private final MessageSender messageSender;
    private final TelegramApiService telegramApiService;
    private final MessageFormatter messageFormatter;

    /**
     * Отправляет текстовое сообщение пользователю.
     *
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает MarkdownV2)
     *
     * @throws TelegramApiException если все попытки отправки не удались
     */
    public void sendMessage(Long telegramId, String text) throws TelegramApiException {
        messageSender.sendMessage(telegramId, text);
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
    public void sendMessage(Long telegramId,
                            String text,
                            InlineKeyboardMarkup replyMarkup) throws TelegramApiException {

        messageSender.sendMessage(telegramId, text, replyMarkup);
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
    public void sendMessage(Long telegramId,
                            String text,
                            ReplyKeyboardMarkup keyboard) throws TelegramApiException {

        messageSender.sendMessage(telegramId, text, keyboard);
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
    public Message sendMessageAndGet(Long chatId, String text) throws TelegramApiException {
        return messageSender.sendMessageAndGet(chatId, text);
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

        return messageSender.sendMessageAndGet(telegramId, text, replyMarkup);
    }

    /**
     * Отправляет сообщение с inline клавиатурой.
     *
     * @param chatId ID чата
     * @param text текст сообщения
     * @param keyboard inline клавиатура
     *
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendMessageWithInlineKeyboard(Long chatId,
                                              String text,
                                              InlineKeyboardMarkup keyboard) throws TelegramApiException {

        messageSender.sendMessage(chatId, text, keyboard);
    }

    /**
     * Отправляет сообщение с inline клавиатурой и возвращает объект Message.
     *
     * @param chatId Telegram ID пользователя
     * @param text текст сообщения
     * @param keyboard inline клавиатура
     *
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    public Message sendMessageWithInlineKeyboardAndGet(Long chatId,
                                                       String text,
                                                       InlineKeyboardMarkup keyboard) throws TelegramApiException {

        return messageSender.sendMessageAndGet(chatId, text, keyboard);
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

        return messageSender.sendMessageWithoutFormattingAndGet(chatId, text, keyboard);
    }

    /**
     * Редактирует текст существующего сообщения.
     *
     * @param chatId ID чата
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

        telegramApiService.editMessageText(chatId, messageId, newText, replyMarkup);
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

        return telegramApiService.tryEditMessageText(chatId, messageId, newText, replyMarkup);
    }

    /**
     * Безопасно обновляет сообщение и отвечает на callback query.
     * Обрабатывает ошибку "message is not modified", когда содержимое сообщения не изменилось.
     *
     * @param chatId ID чата
     * @param messageId ID сообщения для редактирования
     * @param text новый текст сообщения
     * @param keyboard новая inline клавиатура (может быть null)
     * @param callbackQueryId ID callback query для ответа
     * @param callbackText текст для отображения в callback query
     *
     * @throws TelegramApiException если произошла ошибка, отличная от "message is not modified"
     */
    public void safeEditMessageAndAnswer(Long chatId,
                                         Integer messageId,
                                         String text,
                                         InlineKeyboardMarkup keyboard,
                                         String callbackQueryId,
                                         String callbackText) throws TelegramApiException {
        try {
            editMessageText(chatId, messageId, text, keyboard);
            answerCallbackQuery(callbackQueryId, callbackText);

        } catch (TelegramApiException e) {
            if (e instanceof TelegramApiRequestException requestException
                && messageFormatter.isMessageNotModifiedError(requestException)) {

                try {
                    answerCallbackQuery(callbackQueryId, callbackText);

                } catch (TelegramApiException ex) {
                    log.warn("Не удалось ответить на callback query: {}", ex.getMessage());
                }
            } else {
                throw new RuntimeException("Ошибка при обновлении сообщения", e);
            }
        }
    }

    /**
     * Отправляет ответ на callback query от inline кнопки.
     *
     * @param callbackQueryId ID callback query для ответа
     * @param text текст для отображения пользователю
     *
     * @throws TelegramApiException если отправка не удалась
     */
    public void answerCallbackQuery(String callbackQueryId, String text) throws TelegramApiException {
        telegramApiService.answerCallbackQuery(callbackQueryId, text);
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
    public Message sendFileWithKeyboardAndGet(Long chatId,
                                              String fileId,
                                              String fileType,
                                              String caption,
                                              InlineKeyboardMarkup keyboard) throws TelegramApiException {

        return messageSender.sendFileWithKeyboardAndGet(chatId, fileId, fileType, caption, keyboard);
    }

    /**
     * Удаляет сообщение и возвращает результат операции.
     *
     * @param chatId ID чата
     * @param messageId ID сообщения для удаления
     *
     * @return true, если удаление успешно, false, если сообщение не найдено
     * @throws TelegramApiException если удаление не удалось
     */
    public boolean deleteMessage(Long chatId, Integer messageId) throws TelegramApiException {
        return telegramApiService.deleteMessage(chatId, messageId);
    }

    /**
     * Удаляет сообщение без выброса исключений (silent mode).
     *
     * @param chatId ID чата
     * @param messageId ID сообщения для удаления
     */
    public void deleteMessageSilently(Long chatId, Integer messageId) {
        telegramApiService.deleteMessageSilently(chatId, messageId);
    }
}
