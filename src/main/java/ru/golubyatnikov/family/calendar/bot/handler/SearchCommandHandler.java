package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.SearchService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.EventFormatter;

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
public class SearchCommandHandler implements CommandHandler {
    
    private final SearchService searchService;
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    
    /**
     * Обрабатывает команду /search.
     * 
     * <p>Отправляет пользователю запрос на ввод текста для поиска.
     * Фактический поиск будет выполнен при получении текстового сообщения.</p>
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, User user) {
        log.debug("Обработка команды /search для пользователя ID={}", user.getId());
        
        try {
            // Устанавливаем состояние ожидания поискового запроса
            conversationStateService.setAwaitingSearchQuery(user.getId(), message.getChatId());
            
            String responseMessage = "🔍 " + bold("Поиск событий") + "\n\n" +
                                   escape("Введите текст для поиска в названии или описании событий.") + "\n\n" +
                                   italic("Например: день рождения, встреча, поездка");
            
            log.info("Пользователю ID={} будет отправлен запрос на ввод текста для поиска", user.getId());
            return responseMessage;
        } catch (Exception e) {
            log.error("Ошибка при отправке запроса на поиск пользователю ID={}", user.getId(), e);
            return "❌ " + escape("Произошла ошибка при отображении формы поиска");
        }
    }
    
    /**
     * Выполняет поиск событий по запросу пользователя.
     * 
     * <p>Этот метод вызывается из UpdateProcessor при получении текстового
     * сообщения после команды /search.</p>
     * 
     * @param chatId идентификатор чата для отправки результатов
     * @param user пользователь, выполняющий поиск
     * @param query текст поискового запроса
     */
    public void performSearch(Long chatId, User user, String query) {
        log.debug("Выполнение поиска для пользователя ID={} по запросу: '{}'", user.getId(), query);
        
        try {
            // Валидация запроса
            if (query == null || query.trim().length() < 2) {
                messageService.sendMessage(chatId, 
                    "❌ " + escape("Поисковый запрос должен содержать минимум 2 символа."));
                return;
            }
            
            // Выполнение поиска
            List<Event> results = searchService.searchEvents(
                user.getFamily().getId(), 
                user.getId(), 
                query.trim()
            );
            
            if (results.isEmpty()) {
                String responseMessage = "🔍 " + bold("Результаты поиска") + "\n\n" +
                                       escape("По запросу \"") + escape(query) + escape("\" ничего не найдено.") + "\n\n" +
                                       italic("Попробуйте изменить запрос или использовать другие ключевые слова.") + "\n\n" +
                                       escape("Вы можете использовать ") + escape("/today") + escape(" или ") + escape("/week") + 
                                       escape(" для просмотра событий.");
                messageService.sendMessage(chatId, responseMessage);
                log.info("Поиск для пользователя ID={} не дал результатов", user.getId());
                return;
            }
            
            // Формирование сообщения с результатами
            StringBuilder messageBuilder = new StringBuilder();
            
            // Заголовок результатов поиска
            messageBuilder.append("🔍 ").append(bold("Результаты поиска")).append("\n\n");
            messageBuilder.append(italic("Запрос: " + escape("\"") + query + escape("\""))).append("\n\n");
            
            // Форматирование событий с использованием EventFormatter.formatSearchResult()
            for (int i = 0; i < results.size(); i++) {
                Event event = results.get(i);
                messageBuilder.append(EventFormatter.formatSearchResult(event, user));
                
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
            
            messageService.sendMessage(chatId, messageBuilder.toString());
            log.info("Пользователю ID={} отправлено {} результатов поиска", user.getId(), results.size());
            
        } catch (Exception e) {
            log.error("Ошибка при выполнении поиска для пользователя ID={}", user.getId(), e);
            try {
                messageService.sendMessage(chatId, 
                    "❌ " + escape("Произошла ошибка при поиске событий. Попробуйте позже."));
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage(), ex);
            }
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
    
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
