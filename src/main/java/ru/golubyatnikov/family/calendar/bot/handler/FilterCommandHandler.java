package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.SearchService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
 *   <li>Предстоящие события</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 28.5</p>
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
public class FilterCommandHandler implements CommandHandler {
    
    private final SearchService searchService;
    private final TelegramMessageService messageService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * Обрабатывает команду /filter.
     * 
     * <p>Отправляет пользователю inline-клавиатуру с вариантами фильтрации.</p>
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, User user) {
        log.debug("Обработка команды /filter для пользователя ID={}", user.getId());
        
        try {
            String responseMessage = String.format("🔎 %s\n\nВыберите тип событий для отображения:",
                    bold("Фильтрация событий"));
            
            // Отправляем сообщение с inline-клавиатурой напрямую, так как нужна клавиатура
            InlineKeyboardMarkup keyboard = createFilterKeyboard();
            messageService.sendMessage(message.getChatId(), responseMessage, keyboard);
            log.info("Пользователю ID={} отправлено меню фильтрации", user.getId());
            // Возвращаем null, так как сообщение уже отправлено
            return null;
        } catch (Exception e) {
            log.error("Ошибка при отправке меню фильтрации пользователю ID={}", user.getId(), e);
            return "❌ " + escape("Произошла ошибка при отображении меню фильтрации");
        }
    }
    
    /**
     * Создает inline-клавиатуру с вариантами фильтрации.
     * 
     * @return объект InlineKeyboardMarkup с кнопками фильтров
     */
    private InlineKeyboardMarkup createFilterKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Кнопка "Все события"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton allButton = new InlineKeyboardButton();
        allButton.setText("📋 Все события");
        allButton.setCallbackData("filter_all");
        row1.add(allButton);
        keyboard.add(row1);
        
        // Кнопка "Семейные события"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton familyButton = new InlineKeyboardButton();
        familyButton.setText("👨‍👩‍👧‍👦 Семейные события");
        familyButton.setCallbackData("filter_family");
        row2.add(familyButton);
        keyboard.add(row2);
        
        // Кнопка "Персональные события"
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton personalButton = new InlineKeyboardButton();
        personalButton.setText("🔒 Персональные события");
        personalButton.setCallbackData("filter_personal");
        row3.add(personalButton);
        keyboard.add(row3);
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
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
            messageBuilder.append(String.format("🔎 %s\n\n", bold(filterName)));
            
            if (results.isEmpty()) {
                messageBuilder.append(escape("Событий не найдено.")).append("\n\n");
                messageBuilder.append(italic("Попробуйте другой фильтр или создайте новое событие."));
            } else {
                for (Event event : results) {
                    messageBuilder.append(formatEvent(event, user));
                    messageBuilder.append("\n");
                }
                messageBuilder.append(String.format("\n%s", italic("Найдено событий: " + results.size())));
            }
            
            messageService.sendMessage(chatId, messageBuilder.toString());
            log.info("Пользователю ID={} отправлено {} событий по фильтру {}", 
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
    
    /**
     * Форматирует событие для отображения в результатах фильтрации.
     * 
     * @param event событие для форматирования
     * @param user текущий пользователь (для определения персональных событий)
     * @return отформатированная строка с информацией о событии
     */
    private String formatEvent(Event event, User user) {
        StringBuilder sb = new StringBuilder();
        
        // Иконка типа события
        if (event.getIsPersonal()) {
            sb.append("🔒 ");
        } else {
            sb.append("👨‍👩‍👧‍👦 ");
        }
        
        // Дата события
        sb.append(bold(event.getEventDate().format(DATE_FORMATTER)));
        
        // Время события
        if (event.getEventTime() != null) {
            sb.append(escape(" в ")).append(bold(event.getEventTime().format(TIME_FORMATTER)));
            
            // Временной интервал
            if (event.getEndTime() != null) {
                sb.append(escape(" - ")).append(bold(event.getEndTime().format(TIME_FORMATTER)));
            }
        }
        
        sb.append("\n   ");
        
        // Название события
        sb.append(escape(event.getTitle()));
        
        // Создатель события
        if (!event.belongsToUser(user.getId())) {
            sb.append(escape(" (")).append(escape(event.getUser().getFirstName())).append(escape(")"));
        }
        
        return sb.toString();
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
