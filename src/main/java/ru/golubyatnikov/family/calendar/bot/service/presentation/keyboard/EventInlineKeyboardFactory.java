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
            ? "📎 Вложения (" + attachmentsCount + ")" 
            : "📎 Вложения";
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("✏️ Редактировать", CallbackPrefix.EDIT_EVENT.withPayload(eventId.toString())),
                keyboardFactory.createButton("🗑️ Удалить", CallbackPrefix.DELETE_EVENT.withPayload(eventId.toString()))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton(attachmentsButtonText, CallbackPrefix.ATTACH_FILE.withPayload("list_" + eventId))
            )
        );
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
        if (event == null || event.getId() == null) {
            throw new IllegalArgumentException("Event и Event ID не могут быть null");
        }
        
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("UserId должен быть положительным числом");
        }
        
        Long eventId = event.getId();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        
        // Первый ряд: кнопки редактирования и удаления
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("✏️ Редактировать", CallbackPrefix.EDIT_EVENT.withPayload(eventId.toString())),
            keyboardFactory.createButton("🗑️ Удалить", CallbackPrefix.DELETE_EVENT.withPayload(eventId.toString()))
        ));
        
        boolean isActive = event.getStatus() == EventStatus.ACTIVE;
        boolean isOwner = event.belongsToUser(userId);
        
        // Второй ряд: кнопка вложений и кнопка управления напоминаниями
        long attachmentsCount = attachmentService.countEventAttachments(event.getId());
        String attachmentsButtonText = attachmentsCount > 0 
            ? "📎 Вложения (" + attachmentsCount + ")" 
            : "📎 Вложения";
        
        if (isActive && isOwner) {
            boolean hasReminders = reminderSchedulingService.hasActiveReminders(eventId);
            
            InlineKeyboardButton remindersBtn;
            if (hasReminders) {
                remindersBtn = keyboardFactory.createButton("🔕 Откл. напоминания", 
                    CallbackPrefix.DISABLE_REMINDERS.withPayload(eventId.toString()));

            } else {
                remindersBtn = keyboardFactory.createButton("🔔 Вкл. напоминания", 
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
                keyboardFactory.createButton("✅ Завершить", CallbackPrefix.COMPLETE_EVENT.withPayload(eventId.toString()))
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
                keyboardFactory.createButton("👨‍👩‍👧‍👦 Семейное событие", CallbackPrefix.EVENT_TYPE.withPayload("family"))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton("👤 Персональное событие", CallbackPrefix.EVENT_TYPE.withPayload("personal"))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton("🔙 Назад", CallbackPrefix.TYPE_BACK_TO_TIME.withPayload("")),
                keyboardFactory.createButton("✖️ Отменить создание", CallbackPrefix.TYPE_CANCEL.withPayload(""))
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
                keyboardFactory.createButton("♻️ Восстановить", CallbackPrefix.TRASH_RESTORE.withPayload(eventId.toString())),
                keyboardFactory.createButton("❌ Удалить навсегда", CallbackPrefix.TRASH_DELETE.withPayload(eventId.toString()))
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
                keyboardFactory.createButton("📝 Добавить заметку", CallbackPrefix.ADD_COMPLETION_NOTE.withPayload(eventId.toString()))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton("⏭️ Пропустить", CallbackPrefix.SKIP_COMPLETION_NOTE.withPayload(""))
            )
        );
    }

    /**
     * Создает inline клавиатуру для выбора поля редактирования события.
     * 
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId) {
        return createEditFieldSelectionKeyboard(eventId, null);
    }

    /**
     * Создает inline клавиатуру для выбора поля редактирования события.
     * 
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя (для проверки контекста редактирования)
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId, Long userId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }

        InlineKeyboardButton cancelBtn = keyboardFactory.createButton("🔙 Назад", 
            CallbackPrefix.EDIT_CANCEL.withPayload(eventId.toString()));

        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("📝 Название", CallbackPrefix.EDIT_FIELD.withPayload("title_" + eventId)),
                keyboardFactory.createButton("📅 Дата", CallbackPrefix.EDIT_FIELD.withPayload("date_" + eventId))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton("🕐 Время", CallbackPrefix.EDIT_FIELD.withPayload("time_" + eventId)),
                keyboardFactory.createButton("📄 Описание", CallbackPrefix.EDIT_FIELD.withPayload("description_" + eventId))
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
                keyboardFactory.createButton("📋 Все события", CallbackPrefix.FILTER.withPayload("all"))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton("👨‍👩‍👧‍👦 Семейные", CallbackPrefix.FILTER.withPayload("family")),
                keyboardFactory.createButton("👤 Личные", CallbackPrefix.FILTER.withPayload("personal"))
            )
        );
    }
}
