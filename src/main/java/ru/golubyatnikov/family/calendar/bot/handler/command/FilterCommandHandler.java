package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Обработчик команды /filter для фильтрации событий по типу.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FilterCommandHandler implements CommandHandler {
    
    private final KeyboardService keyboardService;
    private final TelegramMessageService messageService;
    
    /**
     * Обрабатывает команду /filter.
     *
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     *
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, @NonNull User user) {
        try {
            InlineKeyboardMarkup keyboard = keyboardService.createFilterKeyboard();

            String messageText = "🔍 " + bold("Выберите тип событий для отображения") + "\n\n" +
                    escape("Используйте кнопки ниже для фильтрации событий по категориям.");

            messageService.sendMessage(message.getChatId(), messageText, keyboard);
            
            return null;
            
        } catch (Exception e) {
            log.error("Ошибка при отправке меню фильтрации пользователю ID={}", user.getId(), e);
            return "❌ " + escape("Произошла ошибка при отображении меню фильтрации");
        }
    }
    
    @Override
    public String getCommand() {
        return "/filter";
    }
    
    @Override
    public String getDescription() {
        return "Фильтрация событий по типу";
    }
}
