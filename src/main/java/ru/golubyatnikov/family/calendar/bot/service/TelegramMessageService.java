package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Фасад для отправки сообщений через Telegram Bot API.
 * 
 * <p>TelegramMessageService является фасадом, который делегирует операции
 * специализированным сервисам для обеспечения разделения ответственности:</p>
 * <ul>
 *   <li>{@link MessageSender} - базовая отправка сообщений</li>
 *   <li>{@link CallbackQueryService} - обработка callback queries и редактирование</li>
 *   <li>{@link MessageFormatter} - форматирование и проверка ошибок</li>
 *   <li>{@link MessageRetryService} - retry логика с exponential backoff</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1, 6.4, 9.4</p>
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
 * @author Family Calendar Bot Team
 * @since 2025-12-30
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramMessageService {

    private final MessageSender messageSender;
    private final CallbackQueryService callbackQueryService;
    private final MessageFormatter messageFormatter;

    /**
     * Отправляет текстовое сообщение пользователю.
     * 
     * @param telegramId Telegram ID пользователя-получателя
     * @param text текст сообщения (поддерживает MarkdownV2)
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
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendMessage(Long telegramId, String text, InlineKeyboardMarkup replyMarkup) 
            throws TelegramApiException {
        messageSender.sendMessage(telegramId, text, replyMarkup);
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
        messageSender.sendMessage(telegramId, text, keyboard);
    }

    /**
     * Отправляет сообщение и возвращает объект Message.
     * 
     * @param chatId Telegram ID пользователя
     * @param text текст сообщения
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
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    public Message sendMessageAndGet(Long telegramId, String text, InlineKeyboardMarkup replyMarkup) 
            throws TelegramApiException {
        return messageSender.sendMessageAndGet(telegramId, text, replyMarkup);
    }

    /**
     * Отправляет сообщение с inline клавиатурой.
     * 
     * @param chatId ID чата
     * @param text текст сообщения
     * @param keyboard inline клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendMessageWithInlineKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
        messageSender.sendMessage(chatId, text, keyboard);
    }

    /**
     * Отправляет сообщение с inline клавиатурой и возвращает объект Message.
     * 
     * @param chatId Telegram ID пользователя
     * @param text текст сообщения
     * @param keyboard inline клавиатура
     * @return отправленное сообщение
     * @throws TelegramApiException если отправка не удалась
     */
    public Message sendMessageWithInlineKeyboardAndGet(Long chatId, String text, InlineKeyboardMarkup keyboard) 
            throws TelegramApiException {
        return messageSender.sendMessageAndGet(chatId, text, keyboard);
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
        messageSender.sendMessageWithoutFormatting(chatId, text, keyboard);
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
        return messageSender.sendMessageWithoutFormattingAndGet(chatId, text, keyboard);
    }

    /**
     * Редактирует текст существующего сообщения.
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения для редактирования
     * @param newText новый текст сообщения
     * @param replyMarkup новая inline клавиатура
     * @throws TelegramApiException если редактирование не удалось
     */
    public void editMessageText(Long chatId, Integer messageId, String newText, 
                               InlineKeyboardMarkup replyMarkup) throws TelegramApiException {
        callbackQueryService.editMessageText(chatId, messageId, newText, replyMarkup);
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
        return callbackQueryService.tryEditMessageText(chatId, messageId, newText, replyMarkup);
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
        callbackQueryService.answerCallbackQuery(callbackQueryId, text);
    }

    /**
     * Отправляет файл пользователю по Telegram file_id.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id файла
     * @param fileType тип файла (document, photo, video, audio)
     * @param caption подпись к файлу
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendFile(Long chatId, String fileId, String fileType, String caption) 
            throws TelegramApiException {
        messageSender.sendFile(chatId, fileId, fileType, caption);
    }

    /**
     * Отправляет файл с inline клавиатурой.
     * 
     * @param chatId идентификатор чата
     * @param fileId Telegram file_id файла
     * @param fileType тип файла
     * @param caption подпись к файлу
     * @param keyboard inline клавиатура
     * @throws TelegramApiException если отправка не удалась
     */
    public void sendFileWithKeyboard(Long chatId, String fileId, String fileType, String caption, 
                                     InlineKeyboardMarkup keyboard) throws TelegramApiException {
        messageSender.sendFileWithKeyboard(chatId, fileId, fileType, caption, keyboard);
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
    public Message sendFileWithKeyboardAndGet(Long chatId, String fileId, String fileType, String caption, 
                                              InlineKeyboardMarkup keyboard) throws TelegramApiException {
        return messageSender.sendFileWithKeyboardAndGet(chatId, fileId, fileType, caption, keyboard);
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
        return callbackQueryService.deleteMessage(chatId, messageId);
    }

    /**
     * Удаляет сообщение без выброса исключений (silent mode).
     * 
     * @param chatId ID чата
     * @param messageId ID сообщения для удаления
     */
    public void deleteMessageSilently(Long chatId, Integer messageId) {
        callbackQueryService.deleteMessageSilently(chatId, messageId);
    }
}
