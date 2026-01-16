package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

/**
 * Обработчик callback queries для работы с вложениями к событиям.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>attach_file_ - прикрепление файлов к событию</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.3, 2.5</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentCallbackHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.ATTACH_FILE;
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback вложения: data='{}', userId={}", 
                callbackData, user.getId());
        
        handleAttachFile(callbackData, user.getId(), chatId, messageId, callbackQueryId);
    }
    
    /**
     * Обрабатывает прикрепление файлов к событию.
     * 
     * @param callbackData данные callback (формат: attach_file_{action}_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleAttachFile(String callbackData, Long userId, Long chatId, 
                                  Integer messageId, String callbackQueryId) 
            throws Exception {
        log.debug("Пользователь {} начал прикрепление файла", userId);
        
        String message = "📎 Прикрепление файла\n\n" +
                       "Отправьте файл, документ или изображение для прикрепления к событию.\n\n" +
                       "_Максимальный размер файла: 20 МБ_";
        
        // TODO: Установить контекст ожидания файла
        
        messageService.editMessageText(chatId, messageId, message, null);
        messageService.answerCallbackQuery(callbackQueryId, "");
    }
}
