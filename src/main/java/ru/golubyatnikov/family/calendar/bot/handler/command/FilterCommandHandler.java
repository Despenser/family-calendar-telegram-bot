package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.search.SearchService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /filter для фильтрации событий по типу.
 * 
 * <p>Этот обработчик позволяет пользователю фильтровать события по различным
 * критериям с использованием inline-клавиатуры:</p>
 * <ul>
 *   <li>Все события (семейные + персональные)</li>
 *   <li>Только семейные события</li>
 *   <li>Только персональные события</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 3.1, 3.2, 3.3, 3.5</p>
 * 
 * @see CommandHandler
 * @see KeyboardService
 * @see SearchService
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FilterCommandHandler implements CommandHandler {
    
    private final KeyboardService keyboardService;
    private final SearchService searchService;
    private final TelegramMessageService messageService;
    private final BotMessageBuilder botMessageBuilder;
    
    /**
     * Обрабатывает команду /filter.
     * 
     * <p>Отправляет пользователю inline-клавиатуру с вариантами фильтрации.
     * Использует KeyboardService для создания клавиатуры и применяет корректное
     * экранирование MarkdownV2 к тексту сообщения.</p>
     * 
     * <p><b>Требования:</b> 3.1, 3.2, 3.3, 3.5</p>
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, User user) {
        log.debug("Обработка команды /filter для пользователя ID={}", user.getId());
        
        try {
            // Создаем inline-клавиатуру через KeyboardService
            InlineKeyboardMarkup keyboard = keyboardService.createFilterKeyboard();
            
            // Формируем текст сообщения с корректным экранированием
            String messageText = "🔍 " + bold("Выберите тип событий для отображения") + "\n\n" +
                    escape("Используйте кнопки ниже для фильтрации событий по категориям.");
            
            // Отправляем сообщение с inline-клавиатурой напрямую
            messageService.sendMessage(message.getChatId(), messageText, keyboard);
            
            log.debug("Пользователю ID={} отправлено меню фильтрации", user.getId());
            
            // Возвращаем null, так как сообщение уже отправлено
            return null;
            
        } catch (Exception e) {
            log.error("Ошибка при отправке меню фильтрации пользователю ID={}", user.getId(), e);
            return "❌ " + escape("Произошла ошибка при отображении меню фильтрации");
        }
    }
    
    /**
     * Обрабатывает callback query от inline-кнопок фильтрации.
     * 
     * <p>Этот метод вызывается из UpdateProcessor при нажатии на кнопку фильтра.</p>
     * 
     * @param callbackQuery callback query от Telegram
     * @param user пользователь, нажавший кнопку
     */
    public void handleFilterCallback(CallbackQuery callbackQuery, User user) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        
        log.debug("Обработка callback фильтрации '{}' для пользователя ID={}", data, user.getId());
        
        try {
            SearchService.EventFilter filter = parseFilter(data);
            
            if (filter == null) {
                log.warn("Неизвестный фильтр: {}", data);
                return;
            }
            
            // Выполнение фильтрации
            List<Event> results = searchService.filterEvents(
                user.getFamily().getId(),
                user.getId(),
                filter
            );
            
            // Формирование сообщения с результатами
            String filterName = getFilterName(filter);
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(formatMessage("🔎 %s\n\n", filterName));
            
            if (results.isEmpty()) {
                messageBuilder.append(escape("Событий не найдено.")).append("\n\n");
                messageBuilder.append(italic("Попробуйте другой фильтр или создайте новое событие."));
            } else {
                // Используем BotMessageBuilder для форматирования каждого события
                for (int i = 0; i < results.size(); i++) {
                    Event event = results.get(i);
                    messageBuilder.append(botMessageBuilder.buildEventMessage(event));
                    
                    // Добавляем разделитель между событиями (но не после последнего)
                    if (i < results.size() - 1) {
                        messageBuilder.append("\n\n")  // Пустая строка ПЕРЕД разделителем
                                      .append(escape("─────────────────────"))
                                      .append("\n\n");  // Пустая строка ПОСЛЕ разделителя
                    }
                }
                messageBuilder.append(String.format("\n\n%s", italic("Найдено событий: " + results.size())));
            }
            
            messageService.sendMessage(chatId, messageBuilder.toString());
            log.debug("Пользователю ID={} отправлено {} событий по фильтру {}", 
                     user.getId(), results.size(), filter);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке фильтрации для пользователя ID={}", user.getId(), e);
            try {
                messageService.sendMessage(chatId, 
                    "❌ " + escape("Произошла ошибка при фильтрации событий. Попробуйте позже."));
            } catch (Exception ex) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage(), ex);
            }
        }
    }
    
    /**
     * Преобразует callback data в тип фильтра.
     * 
     * @param data callback data от кнопки
     * @return тип фильтра или null если неизвестный
     */
    private SearchService.EventFilter parseFilter(String data) {
        return switch (data) {
            case "filter_all" -> SearchService.EventFilter.ALL;
            case "filter_family" -> SearchService.EventFilter.FAMILY_ONLY;
            case "filter_personal" -> SearchService.EventFilter.PERSONAL_ONLY;
            default -> null;
        };
    }
    
    /**
     * Возвращает название фильтра для отображения.
     * 
     * @param filter тип фильтра
     * @return название фильтра
     */
    private String getFilterName(SearchService.EventFilter filter) {
        return switch (filter) {
            case ALL -> "Все события";
            case FAMILY_ONLY -> "Семейные события";
            case PERSONAL_ONLY -> "Персональные события";
            default -> "Неизвестный фильтр";
        };
    }
    
    @Override
    public String getCommand() {
        return "/filter";
    }
    
    @Override
    public String getDescription() {
        return "Фильтрация событий по типу";
    }
    
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
