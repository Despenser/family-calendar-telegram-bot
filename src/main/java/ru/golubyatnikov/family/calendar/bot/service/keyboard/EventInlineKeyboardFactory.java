package ru.golubyatnikov.family.calendar.bot.service.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.service.attachment.AttachmentService;
import ru.golubyatnikov.family.calendar.bot.service.conversation.ConversationStateService;
import ru.golubyatnikov.family.calendar.bot.service.reminder.ReminderService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Фабрика для создания inline клавиатур, связанных с событиями.
 * 
 * <p>Отвечает за создание клавиатур для управления событиями, редактирования,
 * удаления и завершения событий.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.3</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-03
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventInlineKeyboardFactory {

    private final AttachmentService attachmentService;
    private final ReminderService reminderService;
    private final ConversationStateService conversationStateService;

    /**
     * Создает inline клавиатуру для управления событием.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            log.error("Попытка создать клавиатуру с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для события ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Первый ряд: кнопки редактирования и удаления
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton editBtn = new InlineKeyboardButton("✏️ Редактировать");
        editBtn.setCallbackData("edit_event_" + eventId);
        row1.add(editBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️ Удалить");
        deleteBtn.setCallbackData("delete_event_" + eventId);
        row1.add(deleteBtn);
        
        rows.add(row1);
        
        // Второй ряд: кнопка вложений
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        long attachmentsCount = attachmentService.countEventAttachments(eventId);
        String attachmentsButtonText = attachmentsCount > 0 
            ? "📎 Вложения (" + attachmentsCount + ")" 
            : "📎 Вложения";
        
        InlineKeyboardButton attachmentsBtn = new InlineKeyboardButton(attachmentsButtonText);
        attachmentsBtn.setCallbackData("attach_file_list_" + eventId);
        row2.add(attachmentsBtn);
        
        rows.add(row2);
        keyboard.setKeyboard(rows);
        
        log.debug("Inline клавиатура для события ID={} создана", eventId);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для управления событием с учетом статуса и прав доступа.
     * 
     * @param event событие
     * @param userId идентификатор пользователя
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
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Первый ряд: кнопки редактирования и удаления
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton editBtn = new InlineKeyboardButton("✏️ Редактировать");
        editBtn.setCallbackData("edit_event_" + eventId);
        row1.add(editBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️ Удалить");
        deleteBtn.setCallbackData("delete_event_" + eventId);
        row1.add(deleteBtn);
        
        rows.add(row1);
        
        boolean isActive = event.getStatus() == Event.EventStatus.ACTIVE;
        boolean isOwner = event.belongsToUser(userId);
        
        // Второй ряд: кнопка вложений и кнопка управления напоминаниями
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        long attachmentsCount = attachmentService.countEventAttachments(event.getId());
        String attachmentsButtonText = attachmentsCount > 0 
            ? "📎 Вложения (" + attachmentsCount + ")" 
            : "📎 Вложения";
        
        InlineKeyboardButton attachmentsBtn = new InlineKeyboardButton(attachmentsButtonText);
        attachmentsBtn.setCallbackData("attach_file_list_" + eventId);
        row2.add(attachmentsBtn);
        
        if (isActive && isOwner) {
            boolean hasReminders = reminderService.hasActiveReminders(eventId);
            
            InlineKeyboardButton remindersBtn;
            if (hasReminders) {
                remindersBtn = new InlineKeyboardButton("🔕 Откл. напоминания");
                remindersBtn.setCallbackData("disable_reminders_" + eventId);
            } else {
                remindersBtn = new InlineKeyboardButton("🔔 Вкл. напоминания");
                remindersBtn.setCallbackData("enable_reminders_" + eventId);
            }
            
            row2.add(remindersBtn);
        }
        
        rows.add(row2);
        
        // Третий ряд: кнопка завершения (только для активных событий создателя)
        if (isActive && isOwner) {
            List<InlineKeyboardButton> row3 = new ArrayList<>();
            
            InlineKeyboardButton completeBtn = new InlineKeyboardButton("✅ Завершить");
            completeBtn.setCallbackData("complete_event_" + eventId);
            row3.add(completeBtn);
            
            rows.add(row3);
        }
        
        keyboard.setKeyboard(rows);
        
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
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton("✅ Да, удалить");
        confirmBtn.setCallbackData("confirm_delete_" + eventId);
        row1.add(confirmBtn);
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("cancel_delete_" + eventId);
        row1.add(cancelBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для выбора типа события.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createEventTypeSelectionKeyboard() {
        log.debug("Создание inline клавиатуры для выбора типа события");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton familyBtn = new InlineKeyboardButton("👨‍👩‍👧‍👦 Семейное событие");
        familyBtn.setCallbackData("event_type_family");
        row1.add(familyBtn);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton personalBtn = new InlineKeyboardButton("👤 Персональное событие");
        personalBtn.setCallbackData("event_type_personal");
        row2.add(personalBtn);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру меню редактирования события.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createEditEventMenuKeyboard(Long eventId) {
        log.debug("Создание inline клавиатуры меню редактирования для события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton dateBtn = new InlineKeyboardButton("📅 Изменить дату");
        dateBtn.setCallbackData("edit_field_date_" + eventId);
        row1.add(dateBtn);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton timeBtn = new InlineKeyboardButton("🕐 Изменить время");
        timeBtn.setCallbackData("edit_field_time_" + eventId);
        row2.add(timeBtn);
        rows.add(row2);
        
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton titleBtn = new InlineKeyboardButton("✏️ Изменить название");
        titleBtn.setCallbackData("edit_field_title_" + eventId);
        row3.add(titleBtn);
        rows.add(row3);
        
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру с действиями для выбранной даты.
     * 
     * @param date выбранная дата
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createDateActionsKeyboard(LocalDate date) {
        log.debug("Создание inline клавиатуры действий для даты {}", date);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        String dateStr = date.toString();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton viewBtn = new InlineKeyboardButton("👀 Посмотреть события");
        viewBtn.setCallbackData("date_actions_view_" + dateStr);
        row1.add(viewBtn);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton createBtn = new InlineKeyboardButton("➕ Создать новое");
        createBtn.setCallbackData("date_actions_create_" + dateStr);
        row2.add(createBtn);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для управления событием в корзине.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createTrashActionsKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            log.error("Попытка создать клавиатуру корзины с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для события в корзине ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton restoreBtn = new InlineKeyboardButton("♻️ Восстановить");
        restoreBtn.setCallbackData("trash_restore_" + eventId);
        row1.add(restoreBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("❌ Удалить навсегда");
        deleteBtn.setCallbackData("trash_delete_" + eventId);
        row1.add(deleteBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для добавления заметки к завершённому событию.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createCompletionNoteKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            log.error("Попытка создать клавиатуру заметки о завершении с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры для добавления заметки к завершённому событию ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton addNoteButton = new InlineKeyboardButton("📝 Добавить заметку");
        addNoteButton.setCallbackData(CallbackPrefix.ADD_COMPLETION_NOTE.withPayload(eventId.toString()));
        row1.add(addNoteButton);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton skipButton = new InlineKeyboardButton("⏭️ Пропустить");
        skipButton.setCallbackData(CallbackPrefix.SKIP_COMPLETION_NOTE.withPayload(""));
        row2.add(skipButton);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для выбора поля редактирования события.
     * 
     * @param eventId идентификатор события
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
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId, Long userId) {
        if (eventId == null || eventId <= 0) {
            log.error("Попытка создать клавиатуру выбора поля с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры выбора поля для редактирования события ID={}, userId={}", eventId, userId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Ряд 1: Название и Дата
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton titleBtn = new InlineKeyboardButton("📝 Название");
        titleBtn.setCallbackData("edit_field_title_" + eventId);
        row1.add(titleBtn);
        
        InlineKeyboardButton dateBtn = new InlineKeyboardButton("📅 Дата");
        dateBtn.setCallbackData("edit_field_date_" + eventId);
        row1.add(dateBtn);
        rows.add(row1);
        
        // Ряд 2: Время и Описание
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton timeBtn = new InlineKeyboardButton("🕐 Время");
        timeBtn.setCallbackData("edit_field_time_" + eventId);
        row2.add(timeBtn);
        
        InlineKeyboardButton descBtn = new InlineKeyboardButton("📄 Описание");
        descBtn.setCallbackData("edit_field_description_" + eventId);
        row2.add(descBtn);
        rows.add(row2);
        
        // Ряд 3: Отмена или Назад (в зависимости от контекста)
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        
        // Проверяем, редактируется ли событие из календаря
        boolean isFromCalendar = false;
        if (userId != null) {
            ConversationStateService.EditingContext context = conversationStateService.getEditingContext(userId);
            isFromCalendar = context != null && context.getSourceDate() != null;
        }
        
        if (isFromCalendar) {
            cancelBtn.setText("🔙 Назад");
            log.debug("Создана кнопка 'Назад' для редактирования из календаря: eventId={}, userId={}", eventId, userId);
        } else {
            cancelBtn.setText("❌ Отмена");
            log.debug("Создана кнопка 'Отмена' для обычного редактирования: eventId={}, userId={}", eventId, userId);
        }
        
        cancelBtn.setCallbackData("edit_cancel_" + eventId);
        row3.add(cancelBtn);
        rows.add(row3);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline клавиатура выбора поля для события ID={} создана: {} рядов", eventId, rows.size());
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для завершения редактирования события.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditCompletionKeyboard(Long eventId) {
        if (eventId == null || eventId <= 0) {
            log.error("Попытка создать клавиатуру завершения с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом");
        }
        
        log.debug("Создание inline клавиатуры завершения редактирования для события ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton moreBtn = new InlineKeyboardButton("✏️ Редактировать еще");
        moreBtn.setCallbackData("edit_more_" + eventId);
        row1.add(moreBtn);
        
        InlineKeyboardButton completeBtn = new InlineKeyboardButton("✅ Завершить");
        completeBtn.setCallbackData("edit_complete_" + eventId);
        row1.add(completeBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        log.debug("Inline клавиатура завершения для события ID={} создана", eventId);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для фильтрации событий.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createFilterKeyboard() {
        log.debug("Создание inline клавиатуры для фильтрации событий");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton allBtn = new InlineKeyboardButton("📋 Все события");
        allBtn.setCallbackData("filter_all");
        row1.add(allBtn);
        rows.add(row1);
        
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        InlineKeyboardButton familyBtn = new InlineKeyboardButton("👨‍👩‍👧‍👦 Семейные");
        familyBtn.setCallbackData("filter_family");
        row2.add(familyBtn);
        
        InlineKeyboardButton personalBtn = new InlineKeyboardButton("👤 Личные");
        personalBtn.setCallbackData("filter_personal");
        row2.add(personalBtn);
        
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }
}
