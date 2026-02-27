package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.MyEventsPageFormattingService;

import java.util.ArrayList;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.ARROW_LEFT;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.ARROW_RIGHT;

/**
 * Сервис для создания клавиатур постраничного списка событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-26
 */
@Service
@RequiredArgsConstructor
public class MyEventsPageKeyboardService {
    
    private final MyEventsPageFormattingService formattingService;
    private final KeyboardService keyboardService;
    
    /**
     * Создает клавиатуру для страницы со списком событий.
     * 
     * @param events список событий на текущей странице
     * @param currentPage текущая страница (начиная с 0)
     * @param totalPages общее количество страниц
     *
     * @return клавиатура со списком событий и навигацией
     */
    public InlineKeyboardMarkup createEventsPageKeyboard(@NonNull List<Event> events, 
                                                         int currentPage, 
                                                         int totalPages) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        // Добавляем кнопки событий (по одной в ряд)
        for (Event event : events) {
            String buttonText = formattingService.buildEventButtonText(event);
            String callbackData = CallbackPrefix.MY_EVENTS_VIEW.withPayload(
                    event.getId() + "_" + currentPage
            );

            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData(callbackData)
                    .build());

            keyboard.add(row);
        }
        
        // Добавляем навигационные кнопки
        if (totalPages > 1) {
            keyboard.add(createNavigationRow(currentPage, totalPages));
        }
        
        return InlineKeyboardMarkup.builder()
            .keyboard(keyboard)
            .build();
    }
    
    /**
     * Создает ряд с навигационными кнопками.
     * 
     * @param currentPage текущая страница (начиная с 0)
     * @param totalPages общее количество страниц
     *
     * @return ряд с кнопками навигации
     */
    private @NonNull InlineKeyboardRow createNavigationRow(int currentPage, int totalPages) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        
        // Кнопка "Назад"
        if (currentPage > 0) {
            row.add(InlineKeyboardButton.builder()
                .text(ARROW_LEFT + " Назад")
                .callbackData(CallbackPrefix.MY_EVENTS_PAGE.withPayload(String.valueOf(currentPage - 1)))
                .build());
        }
        
        // Индикатор текущей страницы
        row.add(InlineKeyboardButton.builder()
            .text("Страница " + (currentPage + 1) + " из " + totalPages)
            .callbackData("calendar_ignore")
            .build());
        
        // Кнопка "Вперёд"
        if (currentPage < totalPages - 1) {
            row.add(InlineKeyboardButton.builder()
                .text("Вперёд " + ARROW_RIGHT)
                .callbackData(CallbackPrefix.MY_EVENTS_PAGE.withPayload(String.valueOf(currentPage + 1)))
                .build());
        }
        
        return row;
    }
    
    /**
     * Создает клавиатуру для детального просмотра события из постраничного списка.
     * Использует специальную клавиатуру с контекстом страницы, которая сохраняет
     * связь с /my_events при любых действиях (уведомления, вложения и т.д.).
     * 
     * @param event событие
     * @param userId идентификатор пользователя
     * @param page номер страницы для возврата
     *
     * @return клавиатура с действиями над событием и кнопкой возврата к списку
     */
    public InlineKeyboardMarkup createEventDetailsKeyboardWithBackToList(@NonNull Event event, Long userId, int page) {
        return keyboardService.createEventActionsKeyboardWithContext(event, userId, page);
    }
}
