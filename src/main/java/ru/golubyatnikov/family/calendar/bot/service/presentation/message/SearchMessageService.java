package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.context.SearchQueryContext;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;

/**
 * Сервис для отправки и редактирования сообщений в контексте поиска.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchMessageService {
    
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    
    /**
     * Отправляет или редактирует сообщение в контексте поиска.
     * Пытается отредактировать существующее сообщение, если есть контекст.
     * Если редактирование не удалось, отправляет новое сообщение.
     *
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param message текст сообщения
     * @param keyboard клавиатура (может быть null)
     * @param clearContext нужно ли очистить контекст после отправки
     */
    public void sendOrEditSearchMessage(@NonNull Long userId,
                                        @NonNull Long chatId,
                                        @NonNull String message,
                                        InlineKeyboardMarkup keyboard,
                                        boolean clearContext) {

        SearchQueryContext context = conversationStateService.getSearchQueryContext(userId);
        
        if (context != null) {
            try {
                boolean edited = messageService.tryEditMessageText(
                    context.getChatId(),
                    context.getMessageId(),
                    message,
                    keyboard
                );
                
                if (edited) {
                    log.debug("Сообщение поиска отредактировано для пользователя ID={}", userId);
                    if (clearContext) {
                        conversationStateService.clearAwaitingSearchQuery(userId);
                    }
                } else {
                    log.info("Не удалось отредактировать сообщение поиска, отправка нового");
                    sendNewSearchMessage(userId, chatId, message, keyboard, clearContext);
                }
            } catch (TelegramApiException e) {
                log.warn("Ошибка при редактировании сообщения поиска для пользователя ID={}: {}", 
                        userId, e.getMessage());

                sendNewSearchMessage(userId, chatId, message, keyboard, clearContext);
            }
        } else {
            log.warn("Контекст поиска не найден для пользователя ID={}, отправка нового сообщения", userId);
            sendNewSearchMessage(userId, chatId, message, keyboard, clearContext);
        }
    }
    
    /**
     * Отправляет новое сообщение в контексте поиска.
     *
     * @param userId идентификатор пользователя
     * @param chatId идентификатор чата
     * @param message текст сообщения
     * @param keyboard клавиатура (может быть null)
     * @param clearContext нужно ли очистить контекст после отправки
     */
    private void sendNewSearchMessage(@NonNull Long userId,
                                      @NonNull Long chatId,
                                      @NonNull String message,
                                      InlineKeyboardMarkup keyboard,
                                      boolean clearContext) {
        try {
            if (keyboard != null) {
                messageService.sendMessageWithInlineKeyboard(chatId, message, keyboard);
            } else {
                messageService.sendMessage(chatId, message);
            }
            
            if (clearContext) {
                conversationStateService.clearAwaitingSearchQuery(userId);
            }
            
            log.debug("Отправлено новое сообщение поиска для пользователя ID={}", userId);

        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке нового сообщения поиска для пользователя ID={}: {}", userId, e.getMessage(), e);

            if (clearContext) {
                conversationStateService.clearAwaitingSearchQuery(userId);
            }
        }
    }
    
    /**
     * Удаляет сообщение пользователя с поисковым запросом.
     *
     * @param chatId идентификатор чата
     * @param messageId идентификатор сообщения
     */
    public void deleteUserMessage(@NonNull Long chatId, Integer messageId) {
        if (messageId == null) {
            return;
        }
        
        try {
            messageService.deleteMessage(chatId, messageId);
            log.debug("Удалено сообщение пользователя ID={}", messageId);

        } catch (TelegramApiException e) {
            log.warn("Не удалось удалить сообщение пользователя ID={}: {}", messageId, e.getMessage());
        }
    }
}
