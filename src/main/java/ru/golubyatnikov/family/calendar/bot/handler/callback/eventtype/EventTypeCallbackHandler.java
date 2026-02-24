package ru.golubyatnikov.family.calendar.bot.handler.callback.eventtype;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.annotation.HandleCallbackErrors;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.BotMessageFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.*;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик callback queries для выбора типа события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventTypeCallbackHandler implements CallbackHandler {
    
    private final ConversationService conversationService;
    private final TelegramMessageService messageService;
    private final KeyboardService keyboardService;
    private final BotMessageFormattingService botMessageFormattingService;
    private final EventService eventService;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final CallbackQueryService callbackQueryService;
    private final DateTimeFormattingService dateTimeFormattingService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.EVENT_TYPE;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        if (callbackData == null) {
            return false;
        }
        
        return CallbackPrefix.EVENT_TYPE.matches(callbackData) ||
               CallbackPrefix.SKIP_DESCRIPTION.matches(callbackData) ||
               CallbackPrefix.TYPE_BACK_TO_TIME.matches(callbackData) ||
               CallbackPrefix.TITLE_BACK.matches(callbackData) ||
               CallbackPrefix.DESC_BACK_TO_TITLE.matches(callbackData) ||
               CallbackPrefix.TYPE_CANCEL.matches(callbackData);
    }
    
    @Override
    @HandleCallbackErrors
    public void handle(@NonNull CallbackQuery callbackQuery, @NonNull User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        
        if (CallbackPrefix.EVENT_TYPE.matches(context.callbackData())) {
            handleEventTypeSelection(context);

        } else if (CallbackPrefix.SKIP_DESCRIPTION.matches(context.callbackData())) {
            handleSkipDescription(context);
            
        } else if (CallbackPrefix.TYPE_BACK_TO_TIME.matches(context.callbackData())) {
            handleBackToTime(context, user);
            
        } else if (CallbackPrefix.TITLE_BACK.matches(context.callbackData())) {
            handleBackToTypeSelection(context);
            
        } else if (CallbackPrefix.DESC_BACK_TO_TITLE.matches(context.callbackData())) {
            handleBackToTitleInput(context);
            
        } else if (CallbackPrefix.TYPE_CANCEL.matches(context.callbackData())) {
            handleCancelCreation(context, user);
        }
    }
    
    /**
     * Обрабатывает выбор типа события (семейное/персональное).
     * 
     * @param context контекст callback query
     */
    private void handleEventTypeSelection(@NonNull CallbackQueryContext context) {
        String eventType = CallbackPrefix.EVENT_TYPE.extractPayload(context.callbackData());
        boolean isPersonal = eventType.equals("personal");

        conversationService.updateEventType(context.getUserId(), isPersonal);

        String message = botMessageFormattingService.buildEventTypeSelectedMessage(isPersonal) + 
                        "\n\n" + bold("Теперь отправьте название для вашего события:");
        
        InlineKeyboardMarkup cancelKeyboard = keyboardService.createCancelCreationKeyboard();
        callbackQueryService.editMessageAndAnswer(context, message, cancelKeyboard, CallbackMessages.SELECTED);
    }
    
    /**
     * Обрабатывает пропуск описания события.
     * Завершает создание события без описания.
     * 
     * @param context контекст callback query
     */
    private void handleSkipDescription(@NonNull CallbackQueryContext context) {
        Event completedEvent = conversationService.completeEventCreation(context.getUserId(), null);
        
        sendEventCreatedNotification(completedEvent, context.chatId());
        callbackQueryService.answerCallback(context.callbackQueryId(), CallbackMessages.CREATED);
        
    }
    
    /**
     * Отправляет уведомление о созданном событии.
     * В случае ошибки отправляет упрощённое сообщение.
     * 
     * @param event созданное событие
     * @param chatId идентификатор чата
     */
    private void sendEventCreatedNotification(Event event, Long chatId) {
        try {
            eventService.sendOrUpdateEventMessage(event, chatId);

        } catch (TelegramApiException e) {
            log.error("Ошибка при отправке сообщения о созданном событии: eventId={}, error={}", 
                    event.getId(), e.getMessage());

            sendFallbackEventMessage(event, chatId);
        }
    }
    
    /**
     * Отправляет упрощённое сообщение о созданном событии в случае ошибки основного метода.
     * 
     * @param event созданное событие
     * @param chatId идентификатор чата
     */
    private void sendFallbackEventMessage(@NonNull Event event, Long chatId) {
        try {
            String response = formatMessage(
                    """
                            %s *Событие успешно создано!*
                            
                            %s Дата: %s
                            %s Время: %s
                            %s Название: %s""",
                SUCCESS,
                DATE,
                dateTimeFormattingService.formatDate(event.getEventDate()),
                TIME,
                dateTimeFormattingService.formatTime(event.getEventTime()),
                DESCRIPTION,
                event.getTitle()
            );

            ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
            messageService.sendMessage(chatId, response, keyboard);

        } catch (TelegramApiException e) {
            log.error("Критическая ошибка при отправке fallback сообщения: eventId={}, error={}", 
                    event.getId(), e.getMessage());
        }
    }
    
    /**
     * Обрабатывает возврат к выбору времени (минут).
     * Восстанавливает клавиатуру выбора минут с учетом уже выбранных даты и часа.
     * 
     * @param context контекст callback query
     * @param user пользователь
     */
    private void handleBackToTime(@NonNull CallbackQueryContext context, @NonNull User user) {
        try {
            Event draft = conversationService.getActiveDraft(context.getUserId());
            
            if (draft.getEventTime() == null) {
                log.warn("Попытка вернуться к выбору времени, но время не было выбрано: userId={}", context.getUserId());
                callbackQueryService.answerCallback(context.callbackQueryId(), ERROR + " Ошибка: время не было выбрано");
                return;
            }
            
            int selectedHour = draft.getEventTime().getHour();
            LocalDate selectedDate = draft.getEventDate();

            InlineKeyboardMarkup keyboard = keyboardService.createFilteredMinuteSelectionKeyboard(
                selectedHour, selectedDate, user);
            
            String message = botMessageFormattingService.buildHourSelectedMessage(selectedHour);
            
            messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
            callbackQueryService.answerCallback(context.callbackQueryId(), CallbackMessages.EMPTY);
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к выбору времени: userId={}, error={}", context.getUserId(), e.getMessage());
            throw new RuntimeException("Ошибка при возврате к выбору времени", e);
        }
    }
    
    /**
     * Обрабатывает отмену создания события.
     * Удаляет черновик и либо показывает сообщение об отмене (если создание из /add_event),
     * либо возвращает к календарю или экрану с событиями (если создание из /calendar).
     * 
     * @param context контекст callback query
     * @param user пользователь
     */
    private void handleCancelCreation(@NonNull CallbackQueryContext context, @NonNull User user) {
        try {
            Event draft = conversationService.getActiveDraft(context.getUserId());
            boolean isFromAddEvent = draft.getIsFromAddEventCommand() != null && draft.getIsFromAddEventCommand();
            LocalDate selectedDate = draft.getEventDate();
            
            conversationService.cancelEventCreation(context.getUserId());
            
            if (isFromAddEvent) {
                // Если создание началось из /add_event, показываем сообщение об отмене
                String message = escape(ERROR + " Создание события было отменено");
                messageService.editMessageText(context.chatId(), context.messageId(), message, null);
                callbackQueryService.answerCallback(context.callbackQueryId(), "Создание отменено");
                
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
                        String message = botMessageFormattingService.buildDateEventsManagementMessage(selectedDate, eventsOnDate);
                        InlineKeyboardMarkup keyboard = keyboardService.createDateEventsManagementKeyboard(selectedDate, eventsOnDate, user);
                        
                        messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
                        callbackQueryService.answerCallback(context.callbackQueryId(), "Создание отменено");
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
                
                messageService.editMessageText(context.chatId(), context.messageId(), message, calendarKeyboard);
                callbackQueryService.answerCallback(context.callbackQueryId(), "Создание отменено");
            }
            
        } catch (Exception e) {
            log.error("Ошибка при отмене создания события: userId={}, error={}", context.getUserId(), e.getMessage());
            throw new RuntimeException("Ошибка при отмене создания события", e);
        }
    }
    
    /**
     * Обрабатывает возврат к выбору типа события (с этапа ввода названия).
     * Восстанавливает клавиатуру выбора типа события.
     * 
     * @param context контекст callback query
     */
    private void handleBackToTypeSelection(@NonNull CallbackQueryContext context) {
        try {
            Event draft = conversationService.getActiveDraft(context.getUserId());
            
            String formattedTime = dateTimeFormattingService.formatTime(draft.getEventTime());
            InlineKeyboardMarkup typeKeyboard = keyboardService.createEventTypeSelectionKeyboard();
            String message = botMessageFormattingService.buildTimeSelectedMessage(formattedTime);
            
            messageService.editMessageText(context.chatId(), context.messageId(), message, typeKeyboard);
            callbackQueryService.answerCallback(context.callbackQueryId(), CallbackMessages.EMPTY);
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к выбору типа: userId={}, error={}", context.getUserId(), e.getMessage());
            throw new RuntimeException("Ошибка при возврате к выбору типа", e);
        }
    }
    
    /**
     * Обрабатывает возврат к вводу названия (с этапа ввода описания).
     * Восстанавливает сообщение с запросом названия.
     * 
     * @param context контекст callback query
     */
    private void handleBackToTitleInput(@NonNull CallbackQueryContext context) {
        try {
            log.debug("Возврат к вводу названия: userId={}", context.getUserId());
            
            // Очищаем название и описание через сервис (с сохранением в БД)
            conversationService.clearTitleAndDescription(context.getUserId());
            
            Event draft = conversationService.getActiveDraft(context.getUserId());
            log.debug("После очистки: title={}, description={}", draft.getTitle(), draft.getDescription());
            
            boolean isPersonal = draft.getIsPersonal() != null && draft.getIsPersonal();
            String message = botMessageFormattingService.buildEventTypeSelectedMessage(isPersonal) + 
                            "\n\n" + bold("Теперь отправьте название для вашего события:");
            
            InlineKeyboardMarkup cancelKeyboard = keyboardService.createCancelCreationKeyboard();
            
            messageService.editMessageText(context.chatId(), context.messageId(), message, cancelKeyboard);
            callbackQueryService.answerCallback(context.callbackQueryId(), CallbackMessages.EMPTY);
            
        } catch (Exception e) {
            log.error("Ошибка при возврате к вводу названия: userId={}, error={}", context.getUserId(), e.getMessage());
            throw new RuntimeException("Ошибка при возврате к вводу названия", e);
        }
    }
}
