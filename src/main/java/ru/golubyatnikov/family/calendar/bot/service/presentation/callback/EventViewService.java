package ru.golubyatnikov.family.calendar.bot.service.presentation.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для просмотра и управления событиями из календаря.
 * Обрабатывает просмотр событий, создание новых и повторение существующих.
 * 
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventViewService {
    
    private final EventService eventService;
    private final ConversationService conversationService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final TelegramMessageService messageService;
    private final EventRepository eventRepository;
    private final KeyboardFactory keyboardFactory;
    
    /**
     * Показывает детали события из календаря.
     * 
     * @param eventId идентификатор события для просмотра
     * @param user пользователь, запросивший просмотр события
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при просмотре события
     */
    public void viewEvent(Long eventId,
                          User user,
                          Long chatId,
                          Integer messageId,
                          String callbackQueryId) {

        try {
            Event event = eventService.getEventById(eventId);
            int eventCount = eventService.getActiveEventsCount(event.getUser().getId());
            String eventMessage = botMessageFormattingService.buildEventMessageWithHeader(event, eventCount);
            
            LocalDate eventDate = event.getEventDate();
            InlineKeyboardMarkup keyboard = buildEventViewKeyboard(event, eventDate);
            
            messageService.editMessageText(chatId, messageId, eventMessage, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "");

        } catch (Exception e) {
            log.error("Ошибка при просмотре события: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при просмотре события", e);
        }
    }
    
    /**
     * Создает событие на выбранную дату.
     * 
     * @param selectedDate выбранная дата для создания события
     * @param user пользователь, создающий событие
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при создании события на дату
     */
    public void createEventOnDate(LocalDate selectedDate, User user, Long chatId, 
                                  Integer messageId, String callbackQueryId) {
        try {
            if (!conversationService.hasActiveDraft(user.getId())) {
                conversationService.startEventCreation(user.getId());
                log.info("Создан черновик события для пользователя {} при выборе даты в календаре", user.getId());
            }
            
            showTimeSelectionForDate(selectedDate, user, chatId, messageId, callbackQueryId);
            
            log.info("Пользователь {} выбрал дату {} для создания события", user.getId(), selectedDate);

        } catch (Exception e) {
            log.error("Ошибка при создании события на дату: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при создании события на дату", e);
        }
    }
    
    /**
     * Показывает выбор времени для выбранной даты.
     * Общий метод для создания и редактирования событий.
     * 
     * @param selectedDate выбранная дата
     * @param user пользователь
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при показе выбора времени
     */
    public void showTimeSelectionForDate(LocalDate selectedDate,
                                         User user,
                                         Long chatId,
                                         Integer messageId,
                                         String callbackQueryId) {
        try {
            conversationService.updateEventDate(user.getId(), selectedDate);
            conversationService.setCreationMessageId(user.getId(), messageId.longValue());
            
            InlineKeyboardMarkup keyboard = keyboardService.createFilteredHourSelectionKeyboard(selectedDate, user);
            String message = botMessageFormattingService.buildSelectTimeMessage(selectedDate);
            
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Дата выбрана");

        } catch (Exception e) {
            log.error("Ошибка при показе выбора времени: userId={}, date={}, error={}", 
                     user.getId(), selectedDate, e.getMessage());

            throw new RuntimeException("Ошибка при показе выбора времени", e);
        }
    }
    
    /**
     * Повторяет событие с новой датой и временем.
     * 
     * @param eventId идентификатор события для повторения
     * @param user пользователь, повторяющий событие
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при повторении события
     */
    public void repeatEvent(Long eventId,
                            User user,
                            Long chatId,
                            Integer messageId,
                            String callbackQueryId) {

        try {
            Event originalEvent = eventService.getEventById(eventId);
            
            log.info("Пользователь {} начал повторение события ID={}", user.getId(), eventId);
            
            if (!hasAccessToEvent(originalEvent, user)) {
                log.warn("Попытка повторить чужое событие: userId={}, eventId={}", user.getId(), eventId);
                messageService.answerCallbackQuery(callbackQueryId, "У вас нет доступа к этому событию");
                return;
            }
            
            conversationService.cancelEventCreation(user.getId());
            Event draft = conversationService.startEventCreation(user.getId());
            
            copyEventData(draft, originalEvent);
            eventRepository.save(draft);
            
            log.debug("Создан черновик для повторения события: draftId={}, originalEventId={}", 
                     draft.getId(), eventId);
            
            showDateSelectionForRepeat(originalEvent, user, chatId, messageId, callbackQueryId);
            
        } catch (Exception e) {
            log.error("Ошибка при повторении события: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при повторении события", e);
        }
    }
    
    /**
     * Строит клавиатуру для просмотра события.
     * 
     * @param event событие для просмотра
     * @param eventDate дата события
     *
     * @return клавиатура с кнопками действий
     */
    private @NonNull InlineKeyboardMarkup buildEventViewKeyboard(@NonNull Event event, LocalDate eventDate) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        
        if (event.isCompleted()) {
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton("🔄 Повторить событие",
                        "repeat_event_" + event.getId())
            ));
        }
        
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад к списку",
                    "view_events_on_date_" + eventDate.toString())
        ));
        
        return keyboardFactory.createMarkup(rows);
    }
    
    /**
     * Проверяет доступ пользователя к событию.
     * 
     * @param event событие для проверки
     * @param user пользователь
     *
     * @return true, если пользователь имеет доступ к событию
     */
    private boolean hasAccessToEvent(@NonNull Event event, @NonNull User user) {
        return event.belongsToUser(user.getId()) || 
               (user.getFamily() != null && event.getFamily().getId().equals(user.getFamily().getId()));
    }
    
    /**
     * Копирует данные события в черновик.
     * 
     * @param draft черновик события
     * @param originalEvent оригинальное событие
     */
    private void copyEventData(@NonNull Event draft, @NonNull Event originalEvent) {
        draft.setTitle(originalEvent.getTitle());
        draft.setDescription(originalEvent.getDescription());
        draft.setIsPersonal(originalEvent.getIsPersonal());
    }
    
    /**
     * Показывает выбор даты для повторения события.
     * 
     * @param originalEvent оригинальное событие
     * @param user пользователь
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при показе выбора даты
     */
    private void showDateSelectionForRepeat(Event originalEvent,
                                            @NonNull User user,
                                            Long chatId,
                                            Integer messageId,
                                            String callbackQueryId) {

        LocalDate currentDate = user.getCurrentDate();
        InlineKeyboardMarkup keyboard = keyboardService.createCalendarKeyboard(
            currentDate.getYear(), currentDate.getMonthValue(), user
        );

        String message = botMessageFormattingService.buildRepeatEventSelectDateMessage(originalEvent);
        
        try {
            messageService.editMessageText(chatId, messageId, message, keyboard);
            messageService.answerCallbackQuery(callbackQueryId, "Данные скопированы. Выберите новую дату");
            
            log.info("Пользователь {} начал выбор новой даты для повторения события ID={}", 
                    user.getId(), originalEvent.getId());

        } catch (TelegramApiException e) {
            log.error("Ошибка при показе выбора даты для повторения: userId={}, error={}", 
                     user.getId(), e.getMessage());

            throw new RuntimeException("Ошибка при показе выбора даты", e);
        }
    }
}
