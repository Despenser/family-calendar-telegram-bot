package ru.golubyatnikov.family.calendar.bot.service.presentation.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.model.context.DateSelectionContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;

/**
 * Сервис для обработки навигации по календарю и возврата из создания событий.
 * Централизует логику принятия решений о том, куда вернуть пользователя.
 * 
 * @author Golubyatnikov Aleksey
 * @since 2026-02-13
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarNavigationService {
    
    private final EventService eventService;
    private final ConversationService conversationService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final TelegramMessageService messageService;
    
    /**
     * Обрабатывает возврат из выбора времени к выбору даты.
     * Если создание началось из /calendar, отменяет черновик и возвращает к календарю просмотра.
     * Если создание началось из /add_event, возвращает к календарю выбора даты.
     * 
     * @param user пользователь, который возвращается к выбору даты
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при возврате к выбору даты
     */
    public void handleBackFromEventCreation(User user,
                                            Long chatId,
                                            Integer messageId,
                                            String callbackQueryId) {

        try {
            Event draft = conversationService.getActiveDraft(user.getId());
            
            if (draft == null) {
                returnToCurrentMonthCalendar(user, chatId, messageId, callbackQueryId);
                return;
            }
            
            // Проверяем, началось ли создание из /add_event по флагу
            boolean isFromAddEventCommand = Boolean.TRUE.equals(draft.getIsFromAddEventCommand());
            LocalDate draftDate = draft.getEventDate() != null 
                ? draft.getEventDate() 
                : user.getCurrentDate();
            
            if (isFromAddEventCommand) {
                // Возвращаемся к календарю выбора даты для /add_event
                returnToDateSelectionCalendar(user, draftDate, chatId, messageId, callbackQueryId);

            } else {
                // Создание началось из /calendar - кнопка "Добавить"
                // Отменяем черновик и возвращаемся к экрану управления событиями на эту дату
                conversationService.cancelEventCreation(user.getId());
                
                // Проверяем, есть ли события на эту дату
                DateSelectionContext context = buildContext(user, draftDate, false, false);
                navigateBasedOnContext(context, chatId, messageId, callbackQueryId);
            }
            
        } catch (Exception e) {
            log.error("Ошибка при возврате из создания события: userId={}, error={}", 
                     user.getId(), e.getMessage());

            throw new RuntimeException("Ошибка при возврате из создания события", e);
        }
    }

    /**
     * Обрабатывает отмену создания события.
     * Удаляет черновик и либо показывает сообщение об отмене (если создание из /add_event),
     * либо возвращает к календарю или экрану с событиями (если создание из /calendar).
     * 
     * @param user пользователь, который отменяет создание события
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при отмене создания события
     */
    public void handleCancelEventCreation(User user,
                                         Long chatId,
                                         Integer messageId,
                                         String callbackQueryId) {

        try {
            Event draft = conversationService.getActiveDraft(user.getId());
            boolean isFromAddEvent = draft.getIsFromAddEventCommand() != null && draft.getIsFromAddEventCommand();
            LocalDate selectedDate = draft.getEventDate();

            conversationService.cancelEventCreation(user.getId());

            if (isFromAddEvent) {
                // Если создание началось из /add_event, показываем сообщение об отмене
                messageService.deleteMessage(chatId, messageId);
                String cancelMessage = ERROR + " Создание события было отменено";
                messageService.sendMessage(chatId, cancelMessage);

            } else {
                // Если создание началось из /calendar
                if (selectedDate != null) {
                    // Проверяем, есть ли события на выбранную дату
                    List<Event> eventsOnDate = eventService.getEventsByDateIncludingCompleted(
                        user.getFamily().getId(), 
                        selectedDate
                    );

                    // Фильтруем персональные события других пользователей
                    eventsOnDate = eventsOnDate.stream()
                        .filter(e -> !e.getIsPersonal() || e.getUser().getId().equals(user.getId()))
                        .collect(Collectors.toList());

                    if (!eventsOnDate.isEmpty()) {
                        // Если есть события, показываем экран с действиями для даты
                        String message = botMessageFormattingService.buildDateEventsManagementMessage(
                            selectedDate, eventsOnDate);

                        InlineKeyboardMarkup keyboard = keyboardService.createDateEventsManagementKeyboard(
                            selectedDate, eventsOnDate, user);

                        messageService.editMessageText(chatId, messageId, message, keyboard);
                        messageService.answerCallbackQuery(callbackQueryId, "Создание отменено");
                        return;
                    }
                }

                // Если событий нет или дата не выбрана, возвращаем к календарю
                LocalDate today = LocalDate.now(user.getZoneId());
                InlineKeyboardMarkup calendarKeyboard = keyboardService.createViewCalendarKeyboard(
                    today.getYear(), 
                    today.getMonthValue(), 
                    user
                );
                String message = botMessageFormattingService.buildCalendarViewMessage();

                messageService.editMessageText(chatId, messageId, message, calendarKeyboard);
            }

            // Отвечаем на callback query
            try {
                messageService.answerCallbackQuery(callbackQueryId, "Создание отменено");

            } catch (TelegramApiException e) {
                log.warn("Не удалось ответить на callback query: callbackQueryId={}, error={}", 
                        callbackQueryId, e.getMessage());
            }

        } catch (Exception e) {
            log.error("Ошибка при отмене создания события: userId={}, error={}", 
                     user.getId(), e.getMessage());

            throw new RuntimeException("Ошибка при отмене создания события", e);
        }
    }
    
    /**
     * Обрабатывает выбор даты в режиме просмотра календаря.
     * 
     * @param selectedDate выбранная дата
     * @param user пользователь, который выбрал дату
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при просмотре даты в календаре
     */
    public void handleDateSelectionForCalendarView(LocalDate selectedDate,
                                                   User user,
                                                   Long chatId,
                                                   Integer messageId,
                                                   String callbackQueryId) {

        try {
            DateSelectionContext context = buildContext(user, selectedDate, false, false);
            navigateBasedOnContext(context, chatId, messageId, callbackQueryId);
            
        } catch (Exception e) {
            log.error("Ошибка при просмотре даты в календаре: userId={}, error={}", user.getId(), e.getMessage());
            throw new RuntimeException("Ошибка при просмотре даты в календаре", e);
        }
    }
    
    /**
     * Строит контекст выбора даты.
     * 
     * @param user пользователь, для которого строится контекст
     * @param selectedDate выбранная дата
     * @param isCreatingEvent флаг создания нового события
     * @param isEditingEvent флаг редактирования существующего события
     *
     * @return контекст выбора даты с информацией о событиях и состоянии
     */
    private DateSelectionContext buildContext(@NonNull User user,
                                              @NonNull LocalDate selectedDate,
                                              boolean isCreatingEvent,
                                              boolean isEditingEvent) {

        LocalDate today = user.getCurrentDate();
        
        // Всегда загружаем все события включая завершенные
        List<Event> events = eventService.getEventsByDateIncludingCompleted(
            user.getFamily().getId(), selectedDate);
        
        // Фильтруем персональные события других пользователей
        events = events.stream()
            .filter(event -> !event.getIsPersonal() || event.belongsToUser(user.getId()))
            .collect(Collectors.toList());
        
        return DateSelectionContext.builder()
            .user(user)
            .selectedDate(selectedDate)
            .today(today)
            .events(events)
            .isCreatingEvent(isCreatingEvent)
            .isEditingEvent(isEditingEvent)
            .build();
    }
    
    /**
     * Определяет правильную навигацию на основе контекста.
     * 
     * @param context контекст выбора даты с информацией о событиях
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    private void navigateBasedOnContext(@NonNull DateSelectionContext context,
                                        Long chatId,
                                        Integer messageId,
                                        String callbackQueryId) {

        if (context.isPastDate() && !context.hasEvents()) {
            returnToMonthCalendar(context, chatId, messageId, callbackQueryId);

        } else if (context.isPastDate()) {
            showEventsList(context, chatId, messageId, callbackQueryId);

        } else if (context.isToday() && !context.hasEvents()) {
            showCreateEventPrompt(context, chatId, messageId, callbackQueryId);

        } else if (context.isToday()) {
            showEventsManagement(context, chatId, messageId, callbackQueryId);

        } else if (context.isFutureDate() && !context.hasEvents()) {
            showCreateEventPrompt(context, chatId, messageId, callbackQueryId);

        } else {
            showEventsManagement(context, chatId, messageId, callbackQueryId);
        }
    }
    
    /**
     * Возвращает к календарю текущего месяца.
     * Не отменяет создание события - это должен делать вызывающий код при необходимости.
     * 
     * @param user пользователь
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    private void returnToCurrentMonthCalendar(@NonNull User user,
                                              Long chatId,
                                              Integer messageId,
                                              String callbackQueryId) {

        LocalDate currentDate = user.getCurrentDate();
        InlineKeyboardMarkup keyboard = keyboardService.createViewCalendarKeyboard(
            currentDate.getYear(), currentDate.getMonthValue(), user);

        String message = botMessageFormattingService.buildCalendarViewMessage();
        
        updateMessage(chatId, messageId, message, keyboard, callbackQueryId);
    }
    
    /**
     * Возвращает к календарю выбора даты для создания события.
     * 
     * @param user пользователь
     * @param date дата, месяц которой нужно показать
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    private void returnToDateSelectionCalendar(@NonNull User user,
                                               @NonNull LocalDate date,
                                               Long chatId,
                                               Integer messageId,
                                               String callbackQueryId) {
        
        InlineKeyboardMarkup keyboard = keyboardService.createCalendarKeyboard(
                date.getYear(), date.getMonthValue(), user);

        String message = botMessageFormattingService.buildSelectDateMessageWithHeader();
        updateMessage(chatId, messageId, message, keyboard, callbackQueryId);
    }
    
    /**
     * Возвращает к календарю месяца.
     * 
     * @param context контекст выбора даты
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    private void returnToMonthCalendar(@NonNull DateSelectionContext context,
                                       Long chatId,
                                       Integer messageId,
                                       String callbackQueryId) {

        InlineKeyboardMarkup keyboard = keyboardService.createViewCalendarKeyboard(
            context.selectedDate().getYear(),
            context.selectedDate().getMonthValue(),
            context.user());

        String message = botMessageFormattingService.buildCalendarViewMessage();
        
        updateMessage(chatId, messageId, message, keyboard, callbackQueryId);
    }
    
    /**
     * Показывает список событий на дату.
     * 
     * @param context контекст выбора даты
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    private void showEventsList(@NonNull DateSelectionContext context,
                                Long chatId,
                                Integer messageId,
                                String callbackQueryId) {

        String message = botMessageFormattingService.buildDateEventsListMessage(
            context.selectedDate(), context.events());

        InlineKeyboardMarkup keyboard = keyboardService.createDateEventsListKeyboard(
            context.selectedDate(), context.events());
        
        updateMessage(chatId, messageId, message, keyboard, callbackQueryId);
    }
    
    /**
     * Показывает предложение создать событие.
     * 
     * @param context контекст выбора даты
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    private void showCreateEventPrompt(@NonNull DateSelectionContext context,
                                       Long chatId,
                                       Integer messageId,
                                       String callbackQueryId) {

        String message = botMessageFormattingService.buildCreateEventOnDateMessage(context.selectedDate());
        InlineKeyboardMarkup keyboard = keyboardService.createCreateEventOnDateKeyboard(
            context.selectedDate());
        
        updateMessage(chatId, messageId, message, keyboard, callbackQueryId);
    }
    
    /**
     * Показывает управление событиями на дату.
     * 
     * @param context контекст выбора даты
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param callbackQueryId идентификатор callback query для ответа
     */
    private void showEventsManagement(@NonNull DateSelectionContext context,
                                      Long chatId,
                                      Integer messageId,
                                      String callbackQueryId) {

        String message = botMessageFormattingService.buildDateEventsManagementMessage(
            context.selectedDate(), context.events());

        InlineKeyboardMarkup keyboard = keyboardService.createDateEventsManagementKeyboard(
            context.selectedDate(), context.events(), context.user());
        
        updateMessage(chatId, messageId, message, keyboard, callbackQueryId);
    }
    
    /**
     * Обновляет сообщение в чате.
     * 
     * @param chatId идентификатор чата Telegram
     * @param messageId идентификатор сообщения для редактирования
     * @param message текст сообщения
     * @param keyboard клавиатура
     * @param callbackQueryId идентификатор callback query для ответа
     *
     * @throws RuntimeException если произошла ошибка при обновлении сообщения
     */
    private void updateMessage(Long chatId,
                               Integer messageId,
                               String message,
                               InlineKeyboardMarkup keyboard,
                               String callbackQueryId) {
        try {
            messageService.safeEditMessageAndAnswer(chatId, messageId, message,
                    keyboard, callbackQueryId, "");

        } catch (TelegramApiException e) {
            log.error("Ошибка при обновлении сообщения: error={}", e.getMessage());
            throw new RuntimeException("Ошибка при обновлении сообщения", e);
        }
    }
}
