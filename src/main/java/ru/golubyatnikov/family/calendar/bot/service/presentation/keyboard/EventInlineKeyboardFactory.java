package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.service.domain.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import java.util.ArrayList;
import java.util.List;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Event.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.EventType.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.ALL_EVENTS;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Reminders.*;

/**
 * Фабрика для создания inline клавиатур, связанных с событиями.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventInlineKeyboardFactory {

    private final AttachmentService attachmentService;
    private final ReminderSchedulingService reminderSchedulingService;
    private final KeyboardFactory keyboardFactory;

    /**
     * Создает inline клавиатуру для управления событием.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     *
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        long attachmentsCount = attachmentService.countEventAttachments(eventId);
        String attachmentsButtonText = attachmentsCount > 0 
            ? ATTACHMENT + " Вложения (" + attachmentsCount + ")" 
            : ATTACHMENT + " Вложения";
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(EDIT + " Редактировать", CallbackPrefix.EDIT_EVENT.withPayload(eventId.toString())),
                keyboardFactory.createButton(DELETE + " Удалить", CallbackPrefix.DELETE_EVENT.withPayload(eventId.toString()))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton(attachmentsButtonText, CallbackPrefix.ATTACH_FILE.withPayload("list_" + eventId))
            )
        );
    }

    /**
     * Создает inline клавиатуру для управления событием с контекстом постраничного списка.
     * Добавляет кнопку возврата к списку и размещает её в одном ряду с кнопкой завершения.
     *
     * @param event событие
     * @param userId идентификатор пользователя
     * @param page номер страницы для возврата к списку
     *
     * @return настроенная InlineKeyboardMarkup с кнопкой возврата к списку
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createEventActionsKeyboardWithContext(Event event, Long userId, Integer page) {
        validateEventAndUserId(event, userId);

        Long eventId = event.getId();
        List<InlineKeyboardRow> rows = new ArrayList<>();

        // Первый ряд: кнопки редактирования и удаления
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton(EDIT + " Редактировать", CallbackPrefix.EDIT_EVENT.withPayload(eventId + "_" + page)),
            keyboardFactory.createButton(DELETE + " Удалить", CallbackPrefix.DELETE_EVENT.withPayload(eventId + "_" + page))
        ));

        boolean isActive = event.getStatus() == EventStatus.ACTIVE;
        boolean isOwner = event.belongsToUser(userId);

        // Второй ряд: кнопка вложений и кнопка управления напоминаниями
        long attachmentsCount = attachmentService.countEventAttachments(event.getId());
        String attachmentsButtonText = attachmentsCount > 0
            ? ATTACHMENT + " Вложения (" + attachmentsCount + ")"
            : ATTACHMENT + " Вложения";

        if (isActive && isOwner) {
            boolean hasReminders = reminderSchedulingService.hasActiveReminders(eventId);

            InlineKeyboardButton remindersBtn;
            if (hasReminders) {
                remindersBtn = keyboardFactory.createButton(DISABLED + " Откл. напоминания",
                    CallbackPrefix.DISABLE_REMINDERS.withPayload(eventId + "_" + page));

            } else {
                remindersBtn = keyboardFactory.createButton(ENABLED + " Вкл. напоминания",
                    CallbackPrefix.ENABLE_REMINDERS.withPayload(eventId + "_" + page));
            }

            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(attachmentsButtonText, CallbackPrefix.ATTACH_FILE.withPayload("list_" + eventId + "_" + page)),
                remindersBtn
            ));

        } else {
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(attachmentsButtonText, CallbackPrefix.ATTACH_FILE.withPayload("list_" + eventId + "_" + page))
            ));
        }

        // Третий ряд: кнопка возврата к списку и кнопка завершения (в одном ряду)
        if (page != null) {
            InlineKeyboardButton backButton = keyboardFactory.createButton(
                BACK + " К списку",
                CallbackPrefix.MY_EVENTS_BACK.withPayload(String.valueOf(page))
            );

            if (isActive && isOwner) {
                // Кнопки "К списку" и "Завершить" в одном ряду
                rows.add(keyboardFactory.createRow(
                    backButton,
                    keyboardFactory.createButton(COMPLETE + " Завершить", CallbackPrefix.COMPLETE_EVENT.withPayload(eventId + "_" + page))
                ));
            } else {
                // Только кнопка "К списку"
                rows.add(keyboardFactory.createRow(backButton));
            }
        } else if (isActive && isOwner) {
            // Если нет контекста страницы, но есть возможность завершить - показываем только кнопку завершения
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(COMPLETE + " Завершить", CallbackPrefix.COMPLETE_EVENT.withPayload(eventId.toString()))
            ));
        }

        return keyboardFactory.createMarkup(rows);
    }


    /**
     * Создает inline клавиатуру для управления событием с учетом статуса и прав доступа.
     * 
     * @param event событие
     * @param userId идентификатор пользователя
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Event event, Long userId) {
        validateEventAndUserId(event, userId);
        
        Long eventId = event.getId();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        
        // Первый ряд: кнопки редактирования и удаления
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton(EDIT + " Редактировать", CallbackPrefix.EDIT_EVENT.withPayload(eventId.toString())),
            keyboardFactory.createButton(DELETE + " Удалить", CallbackPrefix.DELETE_EVENT.withPayload(eventId.toString()))
        ));
        
        boolean isActive = event.getStatus() == EventStatus.ACTIVE;
        boolean isOwner = event.belongsToUser(userId);
        
        // Второй ряд: кнопка вложений и кнопка управления напоминаниями
        long attachmentsCount = attachmentService.countEventAttachments(event.getId());
        String attachmentsButtonText = attachmentsCount > 0 
            ? ATTACHMENT + " Вложения (" + attachmentsCount + ")" 
            : ATTACHMENT + " Вложения";
        
        if (isActive && isOwner) {
            boolean hasReminders = reminderSchedulingService.hasActiveReminders(eventId);
            
            InlineKeyboardButton remindersBtn;
            if (hasReminders) {
                remindersBtn = keyboardFactory.createButton(DISABLED + " Откл. напоминания", 
                    CallbackPrefix.DISABLE_REMINDERS.withPayload(eventId.toString()));

            } else {
                remindersBtn = keyboardFactory.createButton(ENABLED + " Вкл. напоминания", 
                    CallbackPrefix.ENABLE_REMINDERS.withPayload(eventId.toString()));
            }
            
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(attachmentsButtonText, CallbackPrefix.ATTACH_FILE.withPayload("list_" + eventId)),
                remindersBtn
            ));

        } else {
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(attachmentsButtonText, CallbackPrefix.ATTACH_FILE.withPayload("list_" + eventId))
            ));
        }
        
        // Третий ряд: кнопка завершения (только для активных событий создателя)
        if (isActive && isOwner) {
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(COMPLETE + " Завершить", CallbackPrefix.COMPLETE_EVENT.withPayload(eventId.toString()))
            ));
        }

        return keyboardFactory.createMarkup(rows);
    }

    /**
     * Создает inline клавиатуру для выбора типа события.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createEventTypeSelectionKeyboard() {
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(FAMILY + " Семейное событие", CallbackPrefix.EVENT_TYPE.withPayload("family"))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton(PERSONAL + " Персональное событие", CallbackPrefix.EVENT_TYPE.withPayload("personal"))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton(BACK + " Назад", CallbackPrefix.TYPE_BACK_TO_TIME.withPayload("")),
                keyboardFactory.createButton(CANCEL + " Отменить создание", CallbackPrefix.TYPE_CANCEL.withPayload(""))
            )
        );
    }

    /**
     * Создает inline клавиатуру для управления событием в корзине.
     * 
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createTrashActionsKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(RESTORE + " Восстановить", CallbackPrefix.TRASH_RESTORE.withPayload(eventId.toString())),
                keyboardFactory.createButton(CANCEL + " Удалить навсегда", CallbackPrefix.TRASH_DELETE.withPayload(eventId.toString()))
            )
        );
    }

    /**
     * Создает inline клавиатуру для добавления заметки к завершённому событию.
     * 
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createCompletionNoteKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(DESCRIPTION + " Добавить заметку", CallbackPrefix.ADD_COMPLETION_NOTE.withPayload(eventId.toString()))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton(SKIP + " Пропустить", CallbackPrefix.SKIP_COMPLETION_NOTE.withPayload(""))
            )
        );
    }

    /**
     * Создает inline клавиатуру для выбора поля редактирования события с контекстом страницы.
     * 
     * @param eventId идентификатор события
     * @param page номер страницы для возврата к списку (может быть null)
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId, Integer page) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }

        // Формируем payload для кнопок с учетом контекста страницы
        String titlePayload = page != null ? "title_" + eventId + "_" + page : "title_" + eventId;
        String datePayload = page != null ? "date_" + eventId + "_" + page : "date_" + eventId;
        String timePayload = page != null ? "time_" + eventId + "_" + page : "time_" + eventId;
        String descPayload = page != null ? "description_" + eventId + "_" + page : "description_" + eventId;
        String cancelPayload = page != null ? eventId + "_" + page : eventId.toString();

        InlineKeyboardButton cancelBtn = keyboardFactory.createButton(BACK + " Назад", 
            CallbackPrefix.EDIT_CANCEL.withPayload(cancelPayload));

        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(DESCRIPTION + " Название", CallbackPrefix.EDIT_FIELD.withPayload(titlePayload)),
                keyboardFactory.createButton(DATE + " Дата", CallbackPrefix.EDIT_FIELD.withPayload(datePayload))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton(TIME + " Время", CallbackPrefix.EDIT_FIELD.withPayload(timePayload)),
                keyboardFactory.createButton(NOTE + " Описание", CallbackPrefix.EDIT_FIELD.withPayload(descPayload))
            ),
            keyboardFactory.createRow(cancelBtn)
        );
    }

    /**
     * Создает inline клавиатуру для фильтрации событий.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createFilterKeyboard() {
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton(ALL_EVENTS + " Все события", CallbackPrefix.FILTER.withPayload("all"))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton(FAMILY + " Семейные", CallbackPrefix.FILTER.withPayload("family")),
                keyboardFactory.createButton(PERSONAL + " Личные", CallbackPrefix.FILTER.withPayload("personal"))
            )
        );
    }
    
    /**
     * Валидирует параметры события и пользователя.
     * 
     * @param event событие
     * @param userId идентификатор пользователя
     * @throws IllegalArgumentException если параметры некорректны
     */
    private void validateEventAndUserId(Event event, Long userId) {
        if (event == null || event.getId() == null) {
            throw new IllegalArgumentException("Event и Event ID не могут быть null");
        }
        
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("UserId должен быть положительным числом");
        }
    }
}
