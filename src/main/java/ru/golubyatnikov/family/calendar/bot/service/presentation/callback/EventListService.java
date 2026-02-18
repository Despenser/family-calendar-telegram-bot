package ru.golubyatnikov.family.calendar.bot.service.presentation.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис для работы со списками событий на дату.
 * Обрабатывает просмотр, редактирование и удаление событий.
 * 
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventListService {
    
    private final EventService eventService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final TelegramMessageService messageService;
    
    /**
     * Показывает список событий на дату.
     * 
     * @param selectedDate выбранная дата для просмотра событий
     * @param user пользователь, запросивший список событий
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при просмотре событий на дату
     */
    public void viewEventsOnDate(LocalDate selectedDate,
                                 User user,
                                 Long chatId,
                                 Integer messageId,
                                 String callbackQueryId) {

        try {
            LocalDate today = user.getCurrentDate();
            boolean isPastOrToday = selectedDate.isBefore(today) || selectedDate.equals(today);
            
            List<Event> events = isPastOrToday
                ? eventService.getEventsByDateIncludingCompleted(user.getFamily().getId(), selectedDate)
                : eventService.getEventsByDate(user.getFamily().getId(), selectedDate);
            
            events = filterPersonalEvents(events, user.getId());
            
            String message = botMessageFormattingService.buildDateEventsListMessage(selectedDate, events);
            InlineKeyboardMarkup keyboard = keyboardService.createDateEventsListKeyboard(selectedDate, events);
            
            updateMessageSafe(chatId, messageId, message, keyboard, callbackQueryId);

        } catch (Exception e) {
            log.error("Ошибка при просмотре событий на дату: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при просмотре событий на дату", e);
        }
    }
    
    /**
     * Показывает список событий пользователя для редактирования.
     * 
     * @param selectedDate выбранная дата
     * @param user пользователь, чьи события отображаются
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при редактировании событий на дату
     */
    public void showMyEventsForEdit(LocalDate selectedDate,
                                    User user,
                                    Long chatId,
                                    Integer messageId,
                                    String callbackQueryId) {

        try {
            List<Event> allEvents = eventService.getEventsByDate(user.getFamily().getId(), selectedDate);
            List<Event> myEvents = allEvents.stream()
                .filter(event -> event.getUser().getId().equals(user.getId()))
                .collect(Collectors.toList());
            
            InlineKeyboardMarkup keyboard = keyboardService.createMyEventsEditKeyboard(selectedDate, myEvents);
            String message = "✏️ Выберите событие для редактирования:";
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");

        } catch (Exception e) {
            log.error("Ошибка при редактировании событий на дату: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при редактировании событий на дату", e);
        }
    }
    
    /**
     * Показывает список событий пользователя для удаления.
     * 
     * @param selectedDate выбранная дата
     * @param user пользователь, чьи события отображаются
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при удалении событий на дату
     */
    public void showMyEventsForDelete(LocalDate selectedDate,
                                      User user,
                                      Long chatId,
                                      Integer messageId,
                                      String callbackQueryId) {

        try {
            List<Event> allEvents = eventService.getEventsByDate(user.getFamily().getId(), selectedDate);
            List<Event> myEvents = allEvents.stream()
                .filter(event -> event.getUser().getId().equals(user.getId()))
                .collect(Collectors.toList());
            
            InlineKeyboardMarkup keyboard = keyboardService.createMyEventsDeleteKeyboard(selectedDate, myEvents);
            String message = "🗑 Выберите событие для удаления:";
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");

        } catch (Exception e) {
            log.error("Ошибка при удалении событий на дату: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при удалении событий на дату", e);
        }
    }
    
    /**
     * Фильтрует персональные события других пользователей.
     * 
     * @param events список событий для фильтрации
     * @param userId идентификатор пользователя
     *
     * @return отфильтрованный список событий
     */
    private List<Event> filterPersonalEvents(@NonNull List<Event> events, Long userId) {
        return events.stream()
            .filter(event -> !event.getIsPersonal() || event.belongsToUser(userId))
            .collect(Collectors.toList());
    }
    
    /**
     * Безопасно обновляет сообщение, обрабатывая ошибку "message is not modified".
     * 
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param message текст сообщения
     * @param keyboard клавиатура
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при обновлении сообщения
     */
    private void updateMessageSafe(Long chatId,
                                   Integer messageId,
                                   String message,
                                   InlineKeyboardMarkup keyboard,
                                   String callbackQueryId) {

        try {
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");

        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                try {
                    messageService.answerCallbackQuery(callbackQueryId, "");

                } catch (TelegramApiException ex) {
                    log.warn("Не удалось ответить на callback query: {}", ex.getMessage());
                }
                } else {
                throw new RuntimeException("Ошибка при обновлении сообщения", e);
            }
        }
    }
}
