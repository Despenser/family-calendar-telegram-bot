package ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;

/**
 * Сервис для работы с callback queries.
 * Предоставляет высокоуровневые операции над callback от Telegram.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-16
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackQueryService {
    
    private final TelegramMessageService messageService;
    
    /**
     * Редактирует сообщение в контексте callback query.
     * 
     * @param context контекст callback query
     * @param text новый текст сообщения
     * @param keyboard клавиатура для отображения (может быть null)
     *
     * @throws RuntimeException если произошла ошибка при редактировании
     */
    public void editMessage(@NonNull CallbackQueryContext context,
                           @NonNull String text,
                           InlineKeyboardMarkup keyboard) {
        try {
            messageService.editMessageText(context.chatId(), context.messageId(), text, keyboard);
            
        } catch (TelegramApiException e) {
            log.error("Ошибка при редактировании сообщения: userId={}, chatId={}, messageId={}, error={}",
                     context.getUserId(), context.chatId(), context.messageId(), e.getMessage());
            
            throw new RuntimeException("Ошибка при редактировании сообщения", e);
        }
    }
    
    /**
     * Отправляет ответ на callback query.
     * 
     * @param callbackQueryId идентификатор callback query
     * @param text текст ответа (может быть пустым)
     */
    public void answerCallback(@NonNull String callbackQueryId, @NonNull String text) {
        try {
            messageService.answerCallbackQuery(callbackQueryId, text);
            
        } catch (TelegramApiException e) {
            log.warn("Не удалось ответить на callback query: callbackQueryId={}, error={}", 
                    callbackQueryId, e.getMessage());
        }
    }
    
    /**
     * Отправляет ответ на callback query, используя контекст.
     * 
     * @param context контекст callback query
     * @param text текст ответа (может быть пустым)
     */
    public void answerCallback(@NonNull CallbackQueryContext context, @NonNull String text) {
        answerCallback(context.callbackQueryId(), text);
    }
    
    /**
     * Редактирует сообщение и отправляет ответ на callback query.
     * Удобный метод для типичного сценария обработки callback.
     * 
     * @param context контекст callback query
     * @param text новый текст сообщения
     * @param keyboard клавиатура для отображения (может быть null)
     * @param callbackAnswer текст ответа на callback (может быть пустым)
     */
    public void editMessageAndAnswer(@NonNull CallbackQueryContext context,
                                     @NonNull String text,
                                     InlineKeyboardMarkup keyboard,
                                     @NonNull String callbackAnswer) {
        editMessage(context, text, keyboard);
        answerCallback(context.callbackQueryId(), callbackAnswer);
    }
}
