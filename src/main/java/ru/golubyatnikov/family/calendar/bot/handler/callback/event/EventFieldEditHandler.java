package ru.golubyatnikov.family.calendar.bot.handler.callback.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.golubyatnikov.family.calendar.bot.handler.callback.CallbackHandler;
import ru.golubyatnikov.family.calendar.bot.model.context.CallbackQueryContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.enums.EditField;
import ru.golubyatnikov.family.calendar.bot.service.domain.event.EventService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.parsing.CallbackDataExtractionService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.CallbackQueryService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram.TelegramMessageService;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard.KeyboardService;
import ru.golubyatnikov.family.calendar.bot.util.CallbackMessages;
import java.time.LocalDate;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.BACK;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Status.ERROR;

/**
 * Обработчик редактирования конкретного поля события.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventFieldEditHandler implements CallbackHandler {
    
    private final TelegramMessageService messageService;
    private final ConversationStateService conversationStateService;
    private final KeyboardService keyboardService;
    private final EventService eventService;
    private final KeyboardFactory keyboardFactory;
    private final CallbackDataExtractionService callbackDataExtractionService;
    private final CallbackQueryService callbackQueryService;
    
    @Override
    public CallbackPrefix getPrefix() {
        return CallbackPrefix.EDIT_FIELD;
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return CallbackPrefix.EDIT_FIELD.matches(callbackData);
    }
    
    @Override
    public void handle(@NonNull CallbackQuery callbackQuery, User user) throws Exception {
        CallbackQueryContext context = callbackDataExtractionService.extractContext(callbackQuery, user);
        handleEditField(context);
    }
    
    /**
     * Обрабатывает редактирование конкретного поля события.
     */
    private void handleEditField(@NonNull CallbackQueryContext context) {
        Long userId = context.getUserId();
        try {
            String payload = CallbackPrefix.EDIT_FIELD.extractPayload(context.callbackData());
            
            String[] parts = payload.split("_");
            
            if (parts.length < 2) {
                handleInvalidFormat(context);
                return;
            }
            
            String field = parts[0];
            Long eventId = parseEventId(parts[1], context);
            Integer page = parts.length > 2
                    ? Integer.parseInt(parts[2])
                    : null;
            
            if (eventId == null) {
                return;
            }
            
            updateEditingContext(userId, eventId, context.chatId(), context.messageId(), field, page);
            
            String message = buildMessageForField(field);
            InlineKeyboardMarkup keyboard = buildKeyboardForField(field, context.user(), eventId, page);
            
            messageService.editMessageText(context.chatId(), context.messageId(), message, keyboard);
            callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
            
        } catch (TelegramApiException e) {
            log.error("Ошибка Telegram API при редактировании поля: userId={}, callbackData='{}', error={}", 
                     userId, context.callbackData(), e.getMessage(), e);

            throw new RuntimeException("Ошибка при редактировании поля", e);
        }
    }
    
    /**
     * Обрабатывает некорректный формат callback data.
     */
    private void handleInvalidFormat(@NonNull CallbackQueryContext context) throws TelegramApiException {
        messageService.editMessageText(context.chatId(), context.messageId(),
                ERROR + " Произошла ошибка при обработке запроса", null);

        callbackQueryService.answerCallback(context, CallbackMessages.EMPTY);
    }
    
    /**
     * Парсит ID события из строки.
     */
    private @Nullable Long parseEventId(String eventIdStr, @NonNull CallbackQueryContext context) {
        try {
            return Long.parseLong(eventIdStr);

        } catch (NumberFormatException e) {
            log.error("Некорректный eventId в callback data: eventId='{}', callbackData='{}', " +
                     "userId={}, error={}", eventIdStr, context.callbackData(), context.getUserId(), e.getMessage());

            try {
                handleInvalidFormat(context);

            } catch (TelegramApiException ex) {
                log.error("Ошибка при отправке сообщения об ошибке: {}", ex.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Обновляет контекст редактирования.
     */
    private void updateEditingContext(Long userId,
                                      Long eventId,
                                      Long chatId,
                                      Integer messageId,
                                      String field,
                                      Integer page) {

        if (!conversationStateService.isEditingEvent(userId)) {
            if (page != null) {
                conversationStateService.startEventEditing(userId, eventId, chatId, messageId, page);

            } else {
                conversationStateService.startEventEditing(userId, eventId, chatId, messageId);
            }
        }
        
        EditField editField = mapToEditField(field);
        if (editField != null) {
            conversationStateService.setEditingField(userId, editField);
        }
    }
    
    /**
     * Формирует сообщение для редактирования поля.
     */
    private @NonNull String buildMessageForField(@NonNull String field) {
        return switch (field) {
            case "date" -> DATE + " Редактирование даты\n\nВыберите новую дату из календаря:";
            case "time" -> TIME + " Редактирование времени\n\nВыберите новое время:";
            case "title" -> DESCRIPTION + " Редактирование названия\n\nОтправьте новое название события:";
            case "description" -> NOTE + " Редактирование описания\n\nОтправьте новое описание события:";
            default -> ERROR + " Неизвестное поле для редактирования";
        };
    }
    
    /**
     * Формирует клавиатуру для редактирования поля.
     */
    private @Nullable InlineKeyboardMarkup buildKeyboardForField(@NonNull String field,
                                                                 User user,
                                                                 Long eventId,
                                                                 Integer page) {
        return switch (field) {
            case "date" -> {
                LocalDate now = user.getCurrentDate();
                yield keyboardService.createCalendarKeyboard(
                    now.getYear(), 
                    now.getMonthValue(), 
                    user,
                    eventId
                );
            }
            case "time" -> {
                Event event = eventService.getEventById(eventId);
                LocalDate eventDate = event.getEventDate();
                yield keyboardService.createFilteredHourSelectionKeyboard(eventDate, user, eventId);
            }
            case "title", "description" -> createCancelOnlyKeyboard(eventId, page);
            default -> null;
        };
    }
    
    /**
     * Создает клавиатуру только с кнопкой "Назад".
     */
    private @NonNull InlineKeyboardMarkup createCancelOnlyKeyboard(@NonNull Long eventId, Integer page) {
        String payload = page != null ? eventId + "_" + page : eventId.toString();
        InlineKeyboardButton button = keyboardFactory.createButton(BACK + " Назад",
                CallbackPrefix.EDIT_BACK.withPayload(payload));

        InlineKeyboardRow row = keyboardFactory.createRow(button);

        return keyboardFactory.createMarkup(row);
    }
    
    /**
     * Преобразует строковое представление поля в EditField enum.
     */
    private @Nullable EditField mapToEditField(@NonNull String fieldName) {
        return switch (fieldName) {
            case "date" -> EditField.DATE;
            case "time" -> EditField.TIME;
            case "title" -> EditField.TITLE;
            case "description" -> EditField.DESCRIPTION;
            default -> null;
        };
    }
}
