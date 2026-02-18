package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.context.EditingContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.service.domain.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.domain.reminder.ReminderSchedulingService;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.conversation.ConversationStateService;
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
    private final ConversationStateService conversationStateService;
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
            log.error("Попытка создать клавиатуру с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для события ID={}", eventId);
        
        long attachmentsCount = attachmentService.countEventAttachments(eventId);
        String attachmentsButtonText = attachmentsCount > 0 
            ? "📎 Вложения (" + attachmentsCount + ")" 
            : "📎 Вложения";
        
        log.debug("Inline клавиатура для события ID={} создана", eventId);
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("✏️ Редактировать", "edit_event_" + eventId),
                keyboardFactory.createButton("🗑️ Удалить", "delete_event_" + eventId)
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton(attachmentsButtonText, "attach_file_list_" + eventId)
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
            log.error("Попытка создать клавиатуру с некорректным event");
            throw new IllegalArgumentException("Event и Event ID не могут быть null");
        }
        
        if (userId == null || userId <= 0) {
            log.error("Попытка создать клавиатуру с некорректным userId: {}", userId);
            throw new IllegalArgumentException("UserId должен быть положительным числом");
        }
        
        Long eventId = event.getId();
        log.debug("Создание inline клавиатуры для события ID={} с учетом прав пользователя ID={}", 
                eventId, userId);
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        
        // Первый ряд: кнопки редактирования и удаления
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("✏️ Редактировать", "edit_event_" + eventId),
            keyboardFactory.createButton("🗑️ Удалить", "delete_event_" + eventId)
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
                remindersBtn = keyboardFactory.createButton("🔕 Откл. напоминания", "disable_reminders_" + eventId);
            } else {
                remindersBtn = keyboardFactory.createButton("🔔 Вкл. напоминания", "enable_reminders_" + eventId);
            }
            
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(attachmentsButtonText, "attach_file_list_" + eventId),
                remindersBtn
            ));
        } else {
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton(attachmentsButtonText, "attach_file_list_" + eventId)
            ));
        }
        
        // Третий ряд: кнопка завершения (только для активных событий создателя)
        if (isActive && isOwner) {
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton("✅ Завершить", "complete_event_" + eventId)
            ));
        }
        
        InlineKeyboardMarkup keyboard = keyboardFactory.createMarkup(rows);
        
        log.debug("Inline клавиатуру для события ID={} создана с {} рядами", eventId, rows.size());
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления события.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createDeleteConfirmationKeyboard(Long eventId) {
        log.debug("Создание inline клавиатуры подтверждения удаления для события {}", eventId);
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("✅ Да, удалить", "confirm_delete_" + eventId),
                keyboardFactory.createButton("❌ Отмена", "cancel_delete_" + eventId)
            )
        );
    }

    /**
     * Создает inline клавиатуру для выбора типа события.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createEventTypeSelectionKeyboard() {
        log.debug("Создание inline клавиатуры для выбора типа события");
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("👨‍👩‍👧‍👦 Семейное событие", "event_type_family")
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton("👤 Персональное событие", "event_type_personal")
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
            log.error("Попытка создать клавиатуру корзины с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для события в корзине ID={}", eventId);
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("♻️ Восстановить", "trash_restore_" + eventId),
                keyboardFactory.createButton("❌ Удалить навсегда", "trash_delete_" + eventId)
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
            log.error("Попытка создать клавиатуру заметки о завершении с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для добавления заметки к завершённому событию ID={}", eventId);
        
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
            log.error("Попытка создать клавиатуру выбора поля с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры выбора поля для редактирования события ID={}, userId={}", eventId, userId);
        
        // Проверяем, редактируется ли событие из календаря
        boolean isFromCalendar = false;
        if (userId != null) {
            EditingContext context = conversationStateService.getEditingContext(userId);
            isFromCalendar = context != null && context.getSourceDate() != null;
        }

        InlineKeyboardButton cancelBtn;
        
        if (isFromCalendar) {
            cancelBtn = keyboardFactory.createButton("🔙 Назад", "edit_cancel_" + eventId);
            log.debug("Создана кнопка 'Назад' для редактирования из календаря: eventId={}, userId={}", eventId, userId);
        } else {
            cancelBtn = keyboardFactory.createButton("❌ Отмена", "edit_cancel_" + eventId);
            log.debug("Создана кнопка 'Отмена' для обычного редактирования: eventId={}, userId={}", eventId, userId);
        }
        
        InlineKeyboardMarkup keyboard = keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("📝 Название", "edit_field_title_" + eventId),
                keyboardFactory.createButton("📅 Дата", "edit_field_date_" + eventId)
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton("🕐 Время", "edit_field_time_" + eventId),
                keyboardFactory.createButton("📄 Описание", "edit_field_description_" + eventId)
            ),
            keyboardFactory.createRow(cancelBtn)
        );
        
        log.debug("Inline клавиатура выбора поля для события ID={} создана: 3 ряда", eventId);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для фильтрации событий.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createFilterKeyboard() {
        log.debug("Создание inline клавиатуры для фильтрации событий");
        
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("📋 Все события", "filter_all")
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton("👨‍👩‍👧‍👦 Семейные", "filter_family"),
                keyboardFactory.createButton("👤 Личные", "filter_personal")
            )
        );
    }
}
