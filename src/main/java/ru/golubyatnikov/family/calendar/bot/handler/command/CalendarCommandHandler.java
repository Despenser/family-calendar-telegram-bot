package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.util.BotMessageBuilder;

import java.time.LocalDate;

/**
 * Обработчик команды /calendar для просмотра календаря событий.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CalendarCommandHandler implements CommandHandler {

    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageBuilder messageBuilder;

    @Override
    public String getCommand() {
        return "/calendar";
    }

    @Override
    public String getDescription() {
        return "Просмотр календаря событий";
    }

    @Override
    public String handle(Message message, User user) {
        Long chatId = message.getChatId();
        
        log.info("Пользователь {} вызвал команду /calendar", user.getId());
        
        try {
            // Получаем текущую дату пользователя
            LocalDate currentDate = user.getCurrentDate();
            int year = currentDate.getYear();
            int month = currentDate.getMonthValue();
            
            // Создаем календарь просмотра с возможностью выбора прошлых дат
            InlineKeyboardMarkup keyboard = keyboardService.createViewCalendarKeyboard(year, month, user);
            
            String messageText = messageBuilder.buildCalendarViewMessage();
            
            messageService.sendMessageWithInlineKeyboard(chatId, messageText, keyboard);
            
            log.debug("Календарь просмотра отправлен пользователю {}", user.getId());
            
            return ""; // Возвращаем пустую строку, так как сообщение уже отправлено
        } catch (Exception e) {
            log.error("Ошибка при отправке календаря пользователю {}: {}", user.getId(), e.getMessage(), e);
            return "Произошла ошибка при загрузке календаря. Попробуйте позже.";
        }
    }
}
