package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;

/**
 * Обработчик callback queries для работы с комментариями к событиям.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>comment_ - добавление комментария к событию</li>
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
public class CommentCallbackHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.COMMENT;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.COMMENT.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback комментария: data='{}', userId={}", 
                callbackData, user.getId());
        
        if (CallbackPrefix.COMMENT.matches(callbackData)) {
            handleComment(callbackData, user.getId(), chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Обрабатывает добавление комментария к событию.
     * 
     * @param callbackData данные callback (формат: comment_{action}_{eventId})
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     * @param callbackQueryId идентификатор callback query
     */
    private void handleComment(String callbackData, Long userId, Long chatId, 
                              Integer messageId, String callbackQueryId) {
        log.info("Пользователь {} начал добавление комментария", userId);
        
        String message = "💬 Добавление комментария\n\n" +
                       "Отправьте текст комментария:";
        
        // TODO: Установить контекст ожидания комментария
        
        try {
            messageService.editMessageText(chatId, messageId, message, null);
            messageService.answerCallbackQuery(callbackQueryId, CallbackMessages.EMPTY);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при отправке сообщения о комментарии: userId={}, error={}", 
                     userId, e.getMessage());
            throw new RuntimeException("Ошибка при отправке сообщения", e);
        }
    }
}
