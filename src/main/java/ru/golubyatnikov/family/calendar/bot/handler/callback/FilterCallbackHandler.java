package ru.golubyatnikov.family.calendar.bot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.EventFilter;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.EventService;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.UserService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;

import java.util.List;

/**
 * Обработчик callback queries для фильтрации событий.
 * 
 * <p>Обрабатывает следующие типы callback:</p>
 * <ul>
 *   <li>filter_all - показать все события (семейные и личные)</li>
 *   <li>filter_family - показать только семейные события</li>
 *   <li>filter_personal - показать только личные события</li>
 * </ul>
 * 
 * <p>При обработке callback:</p>
 * <ol>
 *   <li>Парсит тип фильтра из callback data</li>
 *   <li>Сохраняет выбор пользователя в базу данных</li>
 *   <li>Получает отфильтрованные события</li>
 *   <li>Форматирует и отправляет ответ с применением экранирования MarkdownV2</li>
 *   <li>Отвечает на callback query с подтверждением</li>
 * </ol>
 * 
 * <p><b>Требования:</b> 2.1, 2.2, 2.3, 2.4, 2.5, 3.4</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FilterCallbackHandler implements CallbackHandler {
    
    private final UserService userService;
    private final EventService eventService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder botMessageBuilder;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.FILTER;
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(CallbackQuery callbackQuery, User user) throws Exception {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String callbackQueryId = callbackQuery.getId();
        
        log.debug("Обработка callback фильтрации: data='{}', userId={}", 
                callbackData, user.getId());
        
        // Парсим тип фильтра из callback data
        EventFilter filter = parseFilter(callbackData);
        
        log.info("Пользователь ID={} выбрал фильтр: {}", user.getId(), filter);
        
        // Сохраняем выбор пользователя
        userService.setEventFilter(user.getId(), filter);
        
        // Получаем отфильтрованные события
        List<Event> events = eventService.getFilteredEvents(user, filter);
        
        log.debug("Найдено {} событий для фильтра {}", events.size(), filter);
        
        // Форматируем и отправляем ответ
        String messageText = formatFilteredEvents(events, filter);
        
        try {
            messageService.editMessageText(
                chatId, 
                messageId, 
                messageText, 
                keyboardService.createFilterKeyboard()
            );
            
            // Отвечаем на callback query с подтверждением
            String confirmationText = "Фильтр применен: " + filter.getDisplayName();
            messageService.answerCallbackQuery(callbackQueryId, confirmationText);
            
            log.info("Фильтр {} успешно применен для пользователя ID={}", filter, user.getId());
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            log.error("Ошибка при обработке фильтрации: userId={}, filter={}, error={}", 
                     user.getId(), filter, e.getMessage());
            throw new RuntimeException("Ошибка при применении фильтра", e);
        }
    }
    
    /**
     * Парсит тип фильтра из callback data.
     * 
     * <p>Поддерживаемые значения:</p>
     * <ul>
     *   <li>"filter_all" → EventFilter.ALL</li>
     *   <li>"filter_family" → EventFilter.FAMILY</li>
     *   <li>"filter_personal" → EventFilter.PERSONAL</li>
     * </ul>
     * 
     * <p>Если callback data не распознан, возвращается EventFilter.ALL по умолчанию.</p>
     * 
     * @param callbackData строка callback data (формат: filter_{type})
     * @return соответствующий EventFilter
     */
    private EventFilter parseFilter(String callbackData) {
        String filterType = CallbackPrefix.FILTER.extractPayload(callbackData);
        
        return switch (filterType) {
            case "all" -> EventFilter.ALL;
            case "family" -> EventFilter.FAMILY;
            case "personal" -> EventFilter.PERSONAL;
            default -> {
                log.warn("Неизвестный тип фильтра: '{}', используется ALL по умолчанию", filterType);
                yield EventFilter.ALL;
            }
        };
    }
    
    /**
     * Форматирует список отфильтрованных событий для отображения.
     * 
     * <p>Формат сообщения:</p>
     * <pre>
     * 🔍 Фильтр: [Название фильтра]
     * 
     * [Список событий или сообщение об отсутствии событий]
     * </pre>
     * 
     * <p>Каждое событие форматируется через BotMessageBuilder с применением экранирования MarkdownV2.</p>
     * События разделяются горизонтальными линиями.
     * 
     * @param events список событий для форматирования
     * @param filter примененный фильтр
     * @return отформатированный текст сообщения с экранированием MarkdownV2
     */
    private String formatFilteredEvents(List<Event> events, EventFilter filter) {
        StringBuilder sb = new StringBuilder();
        
        // Заголовок с типом фильтра
        sb.append(MarkdownFormatter.escapeMarkdownV2("🔍 Фильтр: "))
          .append("*")
          .append(MarkdownFormatter.escapeMarkdownV2(filter.getDisplayName()))
          .append("*\n\n");
        
        if (events.isEmpty()) {
            sb.append(MarkdownFormatter.escapeMarkdownV2("Нет событий для отображения"));
        } else {
            sb.append(MarkdownFormatter.escapeMarkdownV2("Найдено событий: "))
              .append("*")
              .append(events.size())
              .append("*\n\n");
            
            // Используем BotMessageBuilder для форматирования каждого события
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                sb.append(botMessageBuilder.buildEventMessage(event));
                
                // Добавляем разделитель между событиями (но не после последнего)
                if (i < events.size() - 1) {
                    sb.append("\n\n")  // Пустая строка ПЕРЕД разделителем
                      .append(MarkdownFormatter.escapeMarkdownV2("─────────────────────"))
                      .append("\n\n");  // Пустая строка ПОСЛЕ разделителя
                }
            }
        }
        
        return sb.toString();
    }
}
