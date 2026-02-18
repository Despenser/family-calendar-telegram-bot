package ru.golubyatnikov.family.calendar.bot.service.presentation.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.handler.command.SearchCommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.italic;

/**
 * Обработчик поисковых запросов.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchQueryMessageHandler {

    private final SearchCommandHandler searchCommandHandler;
    private final ConversationStateService conversationStateService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;

    /**
     * Обрабатывает текстовое сообщение как поисковый запрос.
     * 
     * @param message сообщение с поисковым запросом
     * @param user пользователь, выполняющий поиск
     */
    public void handle(Message message, User user) {
        try {
            String query = message.getText();
            Long chatId = message.getChatId();
            Integer messageId = message.getMessageId();
            Long userId = user.getId();
            Long telegramId = user.getTelegramId();
            
            searchCommandHandler.performSearch(chatId, user, query, messageId);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке поискового запроса: userId={}, telegramId={}, error={}", 
                     user.getId(), user.getTelegramId(), e.getMessage(), e);
            
            handleError(message.getChatId(), user);
        }
    }

    /**
     * Обрабатывает ошибку.
     */
    private void handleError(Long chatId, User user) {
        try {
            conversationStateService.clearAwaitingSearchQuery(user.getId());
            
            String response = "❌ " + bold("Произошла ошибка при обработке поискового запроса") + "\\. " +
                            italic("Попробуйте еще раз\\.");

            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);

        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: telegramId={}, error={}", 
                    user.getTelegramId(), ex.getMessage(), ex);
        }
    }
}
