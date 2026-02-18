package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.message.SearchMessageService;
import ru.golubyatnikov.family.calendar.bot.service.domain.search.SearchQueryValidator;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.SearchResultFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.domain.search.SearchService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /search для поиска событий по тексту.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchCommandHandler implements CommandHandler, CallbackHandler {
    
    private final SearchService searchService;
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final SearchMessageService searchMessageService;
    private final SearchQueryValidator searchQueryValidator;
    private final SearchResultFormattingService searchResultFormattingService;
    
    /**
     * Обрабатывает команду /search.
     *
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     *
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, @NonNull User user) {
        log.debug("Обработка команды /search для пользователя ID={}", user.getId());
        
        try {
            Long chatId = message.getChatId();
            String responseMessage = "🔍 " + bold("Поиск событий") + "\n\n" +
                                   escape("Введите текст для поиска в названии или описании событий.") + "\n\n" +
                                   italic("Например: день рождения, встреча, поездка");
            

            Message sentMessage = messageService.sendMessageAndGet(chatId, responseMessage);

            conversationStateService.setAwaitingSearchQuery(
                user.getId(), 
                chatId, 
                sentMessage.getMessageId()
            );
            
            log.info("Пользователю ID={} отправлен запрос на ввод текста для поиска, messageId={}", 
                    user.getId(), sentMessage.getMessageId());

            return null;
            
        } catch (Exception e) {
            log.error("Ошибка при отправке запроса на поиск пользователю ID={}", user.getId(), e);
            return "❌ " + escape("Произошла ошибка при отображении формы поиска");
        }
    }
    
    /**
     * Выполняет поиск событий по запросу пользователя.
     *
     * @param chatId идентификатор чата для отправки результатов
     * @param user пользователь, выполняющий поиск
     * @param query текст поискового запроса
     * @param userMessageId идентификатор сообщения пользователя для удаления
     */
    public void performSearch(Long chatId, @NonNull User user, String query, Integer userMessageId) {
        log.debug("Выполнение поиска для пользователя ID={} по запросу: '{}'", user.getId(), query);
        
        try {
            searchMessageService.deleteUserMessage(chatId, userMessageId);
            
            if (!searchQueryValidator.isValid(query)) {
                handleInvalidQuery(user.getId(), chatId);
                return;
            }
            
            List<Event> results = searchService.searchEvents(
                user.getFamily().getId(),
                user.getId(),
                query.trim()
            );
            
            sendSearchResults(user.getId(), chatId, query, results);
            
        } catch (Exception e) {
            log.error("Ошибка при выполнении поиска для пользователя ID={}", user.getId(), e);
            handleSearchError(user.getId(), chatId);
        }
    }
    
    /**
     * Обрабатывает невалидный поисковый запрос.
     */
    private void handleInvalidQuery(@NonNull Long userId, @NonNull Long chatId) {
        String errorMessage = searchQueryValidator.getValidationErrorMessage();
        searchMessageService.sendOrEditSearchMessage(userId, chatId, errorMessage, null, false);
    }
    
    /**
     * Отправляет результаты поиска пользователю.
     */
    private void sendSearchResults(@NonNull Long userId,
                                   @NonNull Long chatId,
                                   @NonNull String query,
                                   @NonNull List<Event> results) {

        String resultMessage = searchResultFormattingService.formatSearchResults(query, results);
        InlineKeyboardMarkup keyboard = createSearchAgainKeyboard();
        
        searchMessageService.sendOrEditSearchMessage(userId, chatId, resultMessage, keyboard, true);
        
        log.info("Пользователю ID={} отправлено {} результатов поиска", userId, results.size());
    }
    
    /**
     * Обрабатывает ошибку при выполнении поиска.
     */
    private void handleSearchError(@NonNull Long userId, @NonNull Long chatId) {
        try {
            String errorMessage = "❌ " + escape("Произошла ошибка при поиске событий. Попробуйте позже.");
            searchMessageService.sendOrEditSearchMessage(userId, chatId, errorMessage, null, false);

        } catch (Exception ex) {
            log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage(), ex);
        }
    }
    
    /**
     * Создает клавиатуру с кнопкой "Найти заново".
     * 
     * @return InlineKeyboardMarkup с кнопкой повторного поиска
     */
    private @NonNull InlineKeyboardMarkup createSearchAgainKeyboard() {
        InlineKeyboardButton searchAgainButton = InlineKeyboardButton.builder()
                .text("🔍 Найти заново")
                .callbackData(CallbackPrefix.SEARCH_AGAIN.getPrefix())
                .build();
        
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow(
                        searchAgainButton))
                .build();
    }
    
    /**
     * Возвращает префикс callback data для обработки кнопки "Найти заново".
     * 
     * @return CallbackPrefix.SEARCH_AGAIN
     */
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.SEARCH_AGAIN;
    }
    
    /**
     * Обрабатывает callback query от кнопки "Найти заново".
     *
     * @param callbackQuery объект callback query от Telegram
     * @param user авторизованный пользователь
     *
     * @throws Exception если произошла ошибка при обработке callback
     */
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        log.debug("Обработка callback 'search_again' для пользователя ID={}", user.getId());
        
        try {
            Long chatId = callbackQuery.getMessage().getChatId();
            Integer messageId = callbackQuery.getMessage().getMessageId();
            
            // Формируем сообщение с запросом текста для поиска
            String searchPromptMessage = "🔍 " + bold("Поиск событий") + "\n\n" +
                                       escape("Введите текст для поиска в названии или описании событий.") + "\n\n" +
                                       italic("Например: день рождения, встреча, поездка");
            
            // Редактируем сообщение, возвращая к запросу текста
            messageService.editMessageText(chatId, messageId, searchPromptMessage, null);
            
            // Устанавливаем состояние ожидания нового поискового запроса
            conversationStateService.setAwaitingSearchQuery(user.getId(), chatId, messageId);
            
            // Отвечаем на callback query
            messageService.answerCallbackQuery(callbackQuery.getId(), null);
            
            log.info("Пользователь ID={} инициировал повторный поиск", user.getId());
            
        } catch (Exception e) {
            log.error("Ошибка при обработке callback 'search_again' для пользователя ID={}", user.getId(), e);
            
            // Отвечаем на callback query с сообщением об ошибке
            try {
                messageService.answerCallbackQuery(
                    callbackQuery.getId(), 
                    "❌ Произошла ошибка. Попробуйте использовать команду 🔍 " + escape("/search") + " заново."
                );
            } catch (Exception ex) {
                log.error("Ошибка при отправке ответа на callback query: {}", ex.getMessage(), ex);
            }
            
            throw e;
        }
    }
    
    @Override
    public String getCommand() {
        return "/search";
    }
    
    @Override
    public String getDescription() {
        return "Поиск событий по тексту";
    }
}
