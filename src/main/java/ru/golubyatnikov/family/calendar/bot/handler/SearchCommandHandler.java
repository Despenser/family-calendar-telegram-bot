package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.SearchService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.EventFormatter;

import java.util.ArrayList;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /search для поиска событий по тексту.
 * 
 * <p>Этот обработчик позволяет пользователю искать события по названию
 * или описанию. Поиск выполняется по событиям семьи, включая семейные
 * события и персональные события пользователя.</p>
 * 
 * <p>Команда работает в два этапа:</p>
 * <ol>
 *   <li>Запрос текста для поиска</li>
 *   <li>Отображение результатов поиска</li>
 * </ol>
 * 
 * <p>При отсутствии результатов пользователю предлагаются кликабельные
 * команды для просмотра событий (/today, /week).</p>
 * 
 * <p><b>Требования:</b> 28.3, 28.4, 7.1, 7.2, 7.3, 7.4, 8.2</p>
 * 
 * @see CommandHandler
 * @see SearchService
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchCommandHandler implements CommandHandler, CallbackHandler {
    
    private final SearchService searchService;
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    
    /**
     * Обрабатывает команду /search.
     * 
     * <p>Отправляет пользователю запрос на ввод текста для поиска и сохраняет message_id
     * для последующего редактирования сообщения с результатами поиска.</p>
     * 
     * <p>Фактический поиск будет выполнен при получении текстового сообщения.</p>
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, User user) {
        log.debug("Обработка команды /search для пользователя ID={}", user.getId());
        
        try {
            Long chatId = message.getChatId();
            
            // Формируем сообщение с запросом текста для поиска
            String responseMessage = "🔍 " + bold("Поиск событий") + "\n\n" +
                                   escape("Введите текст для поиска в названии или описании событий.") + "\n\n" +
                                   italic("Например: день рождения, встреча, поездка");
            
            // Отправляем сообщение и получаем его ID
            Message sentMessage = messageService.sendMessageAndGet(chatId, responseMessage);
            
            // Сохраняем message_id для последующего редактирования
            conversationStateService.setAwaitingSearchQuery(
                user.getId(), 
                chatId, 
                sentMessage.getMessageId()
            );
            
            log.info("Пользователю ID={} отправлен запрос на ввод текста для поиска, messageId={}", 
                    user.getId(), sentMessage.getMessageId());
            
            // Возвращаем null, так как сообщение уже отправлено
            return null;
            
        } catch (Exception e) {
            log.error("Ошибка при отправке запроса на поиск пользователю ID={}", user.getId(), e);
            return "❌ " + escape("Произошла ошибка при отображении формы поиска");
        }
    }
    
    /**
     * Выполняет поиск событий по запросу пользователя.
     * 
     * <p>Этот метод вызывается из UpdateProcessor при получении текстового
     * сообщения после команды /search. Метод редактирует исходное сообщение
     * с результатами поиска и удаляет сообщение пользователя с запросом.</p>
     * 
     * @param chatId идентификатор чата для отправки результатов
     * @param user пользователь, выполняющий поиск
     * @param query текст поискового запроса
     * @param userMessageId идентификатор сообщения пользователя для удаления
     */
    public void performSearch(Long chatId, User user, String query, Integer userMessageId) {
        log.debug("Выполнение поиска для пользователя ID={} по запросу: '{}'", user.getId(), query);
        
        try {
            // Удаляем сообщение пользователя с поисковым запросом
            if (userMessageId != null) {
                try {
                    messageService.deleteMessage(chatId, userMessageId);
                    log.debug("Удалено сообщение пользователя ID={}", userMessageId);
                } catch (Exception e) {
                    log.warn("Не удалось удалить сообщение пользователя ID={}: {}", userMessageId, e.getMessage());
                }
            }
            
            // Валидация запроса
            if (query == null || query.trim().length() < 2) {
                String errorMessage = "❌ " + escape("Поисковый запрос должен содержать минимум 2 символа.") + "\n\n" +
                                    "🔍 " + bold("Поиск событий") + "\n\n" +
                                    escape("Введите текст для поиска в названии или описании событий.") + "\n\n" +
                                    italic("Например: день рождения, встреча, поездка");
                
                // Пытаемся отредактировать сообщение
                ConversationStateService.SearchQueryContext context = 
                    conversationStateService.getSearchQueryContext(user.getId());
                
                if (context != null) {
                    boolean edited = messageService.tryEditMessageText(
                        context.getChatId(), 
                        context.getMessageId(), 
                        errorMessage, 
                        null
                    );
                    
                    if (!edited) {
                        log.info("Не удалось отредактировать сообщение поиска, отправка нового");
                        messageService.sendMessage(chatId, errorMessage);
                    }
                } else {
                    messageService.sendMessage(chatId, errorMessage);
                }
                
                return;
            }
            
            // Выполнение поиска
            List<Event> results = searchService.searchEvents(
                user.getFamily().getId(), 
                user.getId(), 
                query.trim()
            );
            
            // Формирование сообщения с результатами
            String resultMessage = buildSearchResultMessage(query, results);
            
            // Создание кнопки "Найти заново"
            InlineKeyboardMarkup keyboard = createSearchAgainKeyboard();
            
            // Получаем контекст поиска для редактирования сообщения
            ConversationStateService.SearchQueryContext context = 
                conversationStateService.getSearchQueryContext(user.getId());
            
            if (context != null) {
                // Пытаемся отредактировать исходное сообщение
                boolean edited = messageService.tryEditMessageText(
                    context.getChatId(), 
                    context.getMessageId(), 
                    resultMessage, 
                    keyboard
                );
                
                if (edited) {
                    // Обновляем контекст с тем же message_id для кнопки "Найти заново"
                    conversationStateService.setAwaitingSearchQuery(
                        user.getId(), 
                        chatId, 
                        context.getMessageId()
                    );
                    
                    log.info("Пользователю ID={} отредактировано сообщение с {} результатами поиска", 
                            user.getId(), results.size());
                } else {
                    // Fallback: отправляем новое сообщение
                    log.info("Не удалось отредактировать сообщение поиска, отправка нового");
                    Message newMessage = messageService.sendMessageWithInlineKeyboardAndGet(
                        chatId, 
                        resultMessage, 
                        keyboard
                    );
                    
                    // Обновляем контекст с новым message_id
                    conversationStateService.setAwaitingSearchQuery(
                        user.getId(), 
                        chatId, 
                        newMessage.getMessageId()
                    );
                    
                    log.info("Пользователю ID={} отправлено новое сообщение с {} результатами поиска", 
                            user.getId(), results.size());
                }
            } else {
                // Контекст не найден, отправляем новое сообщение
                log.warn("Контекст поиска не найден для пользователя ID={}, отправка нового сообщения", user.getId());
                Message newMessage = messageService.sendMessageWithInlineKeyboardAndGet(
                    chatId, 
                    resultMessage, 
                    keyboard
                );
                
                // Сохраняем новый контекст
                conversationStateService.setAwaitingSearchQuery(
                    user.getId(), 
                    chatId, 
                    newMessage.getMessageId()
                );
                
                log.info("Пользователю ID={} отправлено {} результатов поиска", user.getId(), results.size());
            }
            
        } catch (Exception e) {
            log.error("Ошибка при выполнении поиска для пользователя ID={}", user.getId(), e);
            try {
                String errorMessage = "❌ " + escape("Произошла ошибка при поиске событий. Попробуйте позже.");
                
                // Пытаемся отредактировать сообщение
                ConversationStateService.SearchQueryContext context = 
                    conversationStateService.getSearchQueryContext(user.getId());
                
                if (context != null) {
                    boolean edited = messageService.tryEditMessageText(
                        context.getChatId(), 
                        context.getMessageId(), 
                        errorMessage, 
                        null
                    );
                    
                    if (!edited) {
                        messageService.sendMessage(chatId, errorMessage);
                    }
                } else {
                    messageService.sendMessage(chatId, errorMessage);
                }
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Выполняет поиск событий по запросу пользователя (без удаления сообщения).
     * 
     * <p>Метод для обратной совместимости. Вызывает основной метод performSearch
     * с userMessageId = null.</p>
     * 
     * @param chatId идентификатор чата для отправки результатов
     * @param user пользователь, выполняющий поиск
     * @param query текст поискового запроса
     */
    public void performSearch(Long chatId, User user, String query) {
        performSearch(chatId, user, query, null);
    }
    
    /**
     * Формирует сообщение с результатами поиска.
     * 
     * @param query поисковый запрос
     * @param results список найденных событий
     * @return отформатированное сообщение
     */
    private String buildSearchResultMessage(String query, List<Event> results) {
        StringBuilder messageBuilder = new StringBuilder();
        
        // Заголовок результатов поиска
        messageBuilder.append("🔍 ").append(bold("Результаты поиска")).append("\n\n");
        messageBuilder.append(italic("Запрос: " + escape("\"") + escape(query) + escape("\""))).append("\n\n");
        
        if (results.isEmpty()) {
            // Сообщение об отсутствии результатов
            messageBuilder.append(escape("По запросу \"")).append(escape(query)).append(escape("\" ничего не найдено.")).append("\n\n");
            messageBuilder.append(italic("Попробуйте изменить запрос или использовать другие ключевые слова.")).append("\n\n");
            messageBuilder.append(escape("Вы можете использовать ")).append(escape("/today")).append(escape(" или ")).append(escape("/week"))
                         .append(escape(" для просмотра событий."));
        } else {
            // Форматирование событий с использованием EventFormatter.formatSearchResult()
            // Получаем первого пользователя из результатов для форматирования
            User eventUser = results.get(0).getUser();
            
            for (int i = 0; i < results.size(); i++) {
                Event event = results.get(i);
                messageBuilder.append(EventFormatter.formatSearchResult(event, eventUser));
                
                // Добавляем разделитель между событиями (но не после последнего)
                if (i < results.size() - 1) {
                    messageBuilder.append(escape("\n"));  // Пустая строка ПЕРЕД разделителем
                    messageBuilder.append(EventFormatter.formatDaySeparator());
                    messageBuilder.append(escape("\n\n")); // Пустая строка ПОСЛЕ разделителя
                }
            }
            
            // Пустая строка перед счетчиком
            messageBuilder.append(escape("\n"));
            
            // Счетчик результатов
            messageBuilder.append(italic("Найдено событий: " + results.size()));
        }
        
        return messageBuilder.toString();
    }
    
    /**
     * Создает клавиатуру с кнопкой "Найти заново".
     * 
     * @return InlineKeyboardMarkup с кнопкой повторного поиска
     */
    private InlineKeyboardMarkup createSearchAgainKeyboard() {
        InlineKeyboardButton searchAgainButton = new InlineKeyboardButton();
        searchAgainButton.setText("🔍 Найти заново");
        searchAgainButton.setCallbackData(CallbackPrefix.SEARCH_AGAIN.getPrefix());
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(searchAgainButton);
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(row);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }
    
    // ===== Реализация CallbackHandler =====
    
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
     * <p>Редактирует сообщение с результатами поиска, возвращая его к состоянию
     * запроса текста для поиска. Устанавливает состояние ожидания нового поискового запроса.</p>
     * 
     * @param callbackQuery объект callback query от Telegram
     * @param user авторизованный пользователь
     * @throws Exception если произошла ошибка при обработке callback
     */
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
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
                    "❌ Произошла ошибка. Попробуйте использовать команду /search заново."
                );
            } catch (Exception ex) {
                log.error("Ошибка при отправке ответа на callback query: {}", ex.getMessage(), ex);
            }
            
            throw e;
        }
    }
    
    // ===== Реализация CommandHandler =====
    
    @Override
    public String getCommand() {
        return "/search";
    }
    
    @Override
    public String getDescription() {
        return "Поиск событий по тексту";
    }
    
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
