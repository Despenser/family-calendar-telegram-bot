package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.myevents.MyEventsPageService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.MyEventsPageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.MyEventsPageKeyboardService;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;

/**
 * Координатор команды /my_events для Telegram бота семейного календаря.
 * Отвечает за обработку команды отображения постраничного списка событий пользователя.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlannerCommandHandler implements CommandHandler {

    private final MyEventsPageService pageService;
    private final MyEventsPageFormattingService formattingService;
    private final MyEventsPageKeyboardService keyboardService;
    private final TelegramMessageService messageService;

    /**
     * Обрабатывает команду /my_events от пользователя.
     * Отображает первую страницу списка событий.
     *
     * @param message входящее сообщение от Telegram
     * @param user пользователь из базы данных
     *
     * @return null, так как все сообщения отправляются внутри метода
     */
    @Override
    public String handle(Message message, User user) {
        if (message == null || user == null) {
            throw new IllegalArgumentException("Сообщение и пользователь не могут быть null");
        }

        Long chatId = message.getChatId();
        try {
            // Получаем первую страницу событий
            Page<Event> eventsPage = pageService.getEventsPage(user.getId(), 0);
            
            if (eventsPage.isEmpty()) {
                sendNoEventsMessage(chatId);
                return null;
            }

            sendEventsPage(chatId, eventsPage);
            return null;

        } catch (Exception e) {
            log.error("Ошибка при обработке команды /my_events для пользователя ID={}: {}", 
                    user.getId(), e.getMessage(), e);

            sendErrorMessage(chatId);
            return null;
        }
    }

    @Override
    public String getCommand() {
        return "/my_events";
    }

    @Override
    public String getDescription() {
        return "Управление моими событиями";
    }

    /**
     * Отправляет сообщение об отсутствии событий.
     * 
     * @param chatId идентификатор чата
     */
    private void sendNoEventsMessage(Long chatId) {
        try {
            String noEventsMessage = formattingService.buildNoEventsMessage();
            messageService.sendMessage(chatId, noEventsMessage);

        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения об отсутствии событий: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправляет страницу со списком событий.
     * 
     * @param chatId идентификатор чата
     * @param eventsPage страница событий
     */
    private void sendEventsPage(Long chatId, Page<Event> eventsPage) {
        try {
            String headerMessage = formattingService.buildPageHeader(
                eventsPage.getTotalElements(),
                eventsPage.getNumber() + 1,
                eventsPage.getTotalPages()
            );
            
            InlineKeyboardMarkup keyboard = keyboardService.createEventsPageKeyboard(
                eventsPage.getContent(),
                eventsPage.getNumber(),
                eventsPage.getTotalPages()
            );
            
            messageService.sendMessage(chatId, headerMessage, keyboard);

        } catch (Exception e) {
            log.error("Ошибка при отправке страницы событий: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправляет сообщение об ошибке.
     * 
     * @param chatId идентификатор чата
     */
    private void sendErrorMessage(Long chatId) {
        try {
            String errorMessage = ERROR + " Произошла ошибка при получении списка событий. Попробуйте позже.";
            messageService.sendMessage(chatId, errorMessage);

        } catch (Exception sendError) {
            log.error("Не удалось отправить сообщение об ошибке: {}", sendError.getMessage());
        }
    }
}
