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
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;

import java.time.LocalDate;

/**
 * Обработчик команды /calendar для просмотра календаря событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2025-12-30
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalendarCommandHandler implements CommandHandler {

    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;

    @Override
    public String getCommand() {
        return "/calendar";
    }

    @Override
    public String getDescription() {
        return "Просмотр календаря событий";
    }

    @Override
    public String handle(@NonNull Message message, @NonNull User user) {
        Long chatId = message.getChatId();
        
        log.info("Пользователь {} вызвал команду /calendar", user.getId());
        
        try {
            // Получаем текущую дату пользователя
            LocalDate currentDate = user.getCurrentDate();
            int year = currentDate.getYear();
            int month = currentDate.getMonthValue();
            
            // Создаем календарь просмотра с возможностью выбора прошлых дат
            InlineKeyboardMarkup keyboard = keyboardService.createViewCalendarKeyboard(year, month, user);
            
            String messageText = botMessageFormattingService.buildCalendarViewMessage();
            
            messageService.sendMessageWithInlineKeyboard(chatId, messageText, keyboard);
            
            log.debug("Календарь просмотра отправлен пользователю {}", user.getId());
            
            return "";

        } catch (Exception e) {
            log.error("Ошибка при отправке календаря пользователю {}: {}", user.getId(), e.getMessage(), e);
            return "Произошла ошибка при загрузке календаря. Попробуйте позже.";
        }
    }
}
