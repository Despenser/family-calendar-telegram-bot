package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.service.keyboard.AttachmentInlineKeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.keyboard.ConfirmationInlineKeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.keyboard.EventInlineKeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.keyboard.NavigationInlineKeyboardFactory;
import ru.golubyatnikov.family.calendar.bot.service.keyboard.ReminderInlineKeyboardFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Фасад для создания inline клавиатур (InlineKeyboardMarkup) в Telegram.
 * 
 * <p>InlineKeyboardService делегирует создание клавиатур специализированным фабрикам,
 * предоставляя единый интерфейс для всех типов inline клавиатур.</p>
 * 
 * <p><b>Требования:</b> 1.1, 1.3</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InlineKeyboardService {

    private final EventInlineKeyboardFactory eventFactory;
    private final AttachmentInlineKeyboardFactory attachmentFactory;
    private final ReminderInlineKeyboardFactory reminderFactory;
    private final NavigationInlineKeyboardFactory navigationFactory;
    private final ConfirmationInlineKeyboardFactory confirmationFactory;

    // ========== Методы для событий (делегирование к EventInlineKeyboardFactory) ==========

    /**
     * Создает inline клавиатуру для управления событием.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Long eventId) {
        return eventFactory.createEventActionsKeyboard(eventId);
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
        return eventFactory.createEventActionsKeyboard(event, userId);
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления события.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createDeleteConfirmationKeyboard(Long eventId) {
        return eventFactory.createDeleteConfirmationKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для выбора типа события.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createEventTypeSelectionKeyboard() {
        return eventFactory.createEventTypeSelectionKeyboard();
    }

    /**
     * Создает inline клавиатуру меню редактирования события.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createEditEventMenuKeyboard(Long eventId) {
        return eventFactory.createEditEventMenuKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру с действиями для выбранной даты.
     * 
     * @param date выбранная дата
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createDateActionsKeyboard(LocalDate date) {
        return eventFactory.createDateActionsKeyboard(date);
    }

    /**
     * Создает inline клавиатуру для управления событием в корзине.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createTrashActionsKeyboard(Long eventId) {
        return eventFactory.createTrashActionsKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для добавления заметки к завершённому событию.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createCompletionNoteKeyboard(Long eventId) {
        return eventFactory.createCompletionNoteKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для выбора поля редактирования события.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId) {
        return eventFactory.createEditFieldSelectionKeyboard(eventId);
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
        return eventFactory.createEditFieldSelectionKeyboard(eventId, userId);
    }

    /**
     * Создает inline клавиатуру для завершения редактирования события.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditCompletionKeyboard(Long eventId) {
        return eventFactory.createEditCompletionKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для фильтрации событий.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createFilterKeyboard() {
        return eventFactory.createFilterKeyboard();
    }

    // ========== Методы для вложений (делегирование к AttachmentInlineKeyboardFactory) ==========

    /**
     * Создает inline клавиатуру для прикрепления файла к событию.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createAttachmentKeyboard(Long eventId) {
        return attachmentFactory.createAttachmentKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для списка вложений события.
     * 
     * @param eventId идентификатор события
     * @param attachments список вложений
     * @param isCreator является ли пользователь создателем
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentsListKeyboard(Long eventId, List<Attachment> attachments, boolean isCreator) {
        return attachmentFactory.createAttachmentsListKeyboard(eventId, attachments, isCreator);
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления вложения.
     * 
     * @param eventId идентификатор события
     * @param attachmentId идентификатор вложения
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createDeleteAttachmentConfirmationKeyboard(Long eventId, Long attachmentId) {
        return attachmentFactory.createDeleteAttachmentConfirmationKeyboard(eventId, attachmentId);
    }

    /**
     * Создает inline клавиатуру для просмотра файла вложения.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createFileViewKeyboard(Long eventId) {
        return attachmentFactory.createFileViewKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для режима загрузки вложения.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentUploadKeyboard(Long eventId) {
        return attachmentFactory.createAttachmentUploadKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для добавления чек-листа к событию.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createChecklistKeyboard(Long eventId) {
        return attachmentFactory.createChecklistKeyboard(eventId);
    }

    // ========== Методы для напоминаний (делегирование к ReminderInlineKeyboardFactory) ==========

    /**
     * Создает inline клавиатуру для настройки напоминаний.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createReminderSettingsKeyboard(Long eventId) {
        return reminderFactory.createReminderSettingsKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру меню настройки повторения события.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createRecurrenceMenuKeyboard(Long eventId) {
        return reminderFactory.createRecurrenceMenuKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для выбора действия с серией событий.
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createSeriesActionKeyboard(Long eventId) {
        return reminderFactory.createSeriesActionKeyboard(eventId);
    }

    // ========== Методы для навигации (делегирование к NavigationInlineKeyboardFactory) ==========

    /**
     * Создает inline клавиатуру с кнопкой "Пропустить" для описания события.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createSkipDescriptionKeyboard() {
        return navigationFactory.createSkipDescriptionKeyboard();
    }
    
    // ========== Методы для календаря просмотра ==========
    
    /**
     * Создает inline клавиатуру со списком событий на дату (для прошлых дат).
     */
    public InlineKeyboardMarkup createDateEventsListKeyboard(java.time.LocalDate date, 
                                                             java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> events, 
                                                             ru.golubyatnikov.family.calendar.bot.model.User user) {
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
        
        // Кнопки с событиями
        for (ru.golubyatnikov.family.calendar.bot.model.Event event : events) {
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> row = new java.util.ArrayList<>();
            
            String buttonText = String.format("%s - %s", 
                event.getEventTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                event.getTitle());
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(buttonText);
            button.setCallbackData("view_event_" + event.getId());
            row.add(button);
            rows.add(row);
        }
        
        // Кнопка "Назад к календарю"
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> backRow = new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton backButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("🔙 Назад к календарю");
        // Возвращаемся к календарю месяца, а не к выбору даты
        backButton.setCallbackData(String.format("calendar_%d-%02d", date.getYear(), date.getMonthValue()));
        backRow.add(backButton);
        rows.add(backRow);
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Создает inline клавиатуру для создания события на дату (для будущих дат без событий).
     */
    public InlineKeyboardMarkup createCreateEventOnDateKeyboard(java.time.LocalDate date) {
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
        
        // Кнопка "Создать событие"
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> createRow = new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton createButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("➕ Создать событие");
        createButton.setCallbackData("create_event_on_date_" + date.toString());
        createRow.add(createButton);
        rows.add(createRow);
        
        // Кнопка "Назад к календарю"
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> backRow = new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton backButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("🔙 Назад к календарю");
        backButton.setCallbackData(String.format("calendar_%d-%02d", date.getYear(), date.getMonthValue()));
        backRow.add(backButton);
        rows.add(backRow);
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    
    /**
     * Создает inline клавиатуру для управления событиями на дату (для будущих дат с событиями).
     */
    public InlineKeyboardMarkup createDateEventsManagementKeyboard(java.time.LocalDate date, 
                                                                   java.util.List<ru.golubyatnikov.family.calendar.bot.model.Event> events, 
                                                                   ru.golubyatnikov.family.calendar.bot.model.User user) {
        java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
        
        // Проверяем, есть ли у пользователя свои события на эту дату
        boolean hasOwnEvents = events.stream()
            .anyMatch(event -> event.getUser().getId().equals(user.getId()));
        
        // Первый ряд: Добавить | Просмотреть
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> firstRow = new java.util.ArrayList<>();
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton createButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("➕ Добавить");
        createButton.setCallbackData("create_event_on_date_" + date.toString());
        firstRow.add(createButton);
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton viewButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("👁 Просмотреть");
        viewButton.setCallbackData("view_events_on_date_" + date.toString());
        firstRow.add(viewButton);
        
        rows.add(firstRow);
        
        // Второй ряд: Редактировать | Удалить (только если есть свои события)
        if (hasOwnEvents) {
            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> secondRow = new java.util.ArrayList<>();
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton editButton = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("✏️ Редактировать");
            editButton.setCallbackData("edit_my_events_on_date_" + date.toString());
            secondRow.add(editButton);
            
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton deleteButton = 
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("🗑 Удалить");
            deleteButton.setCallbackData("delete_my_events_on_date_" + date.toString());
            secondRow.add(deleteButton);
            
            rows.add(secondRow);
        }
        
        // Третий ряд: Назад к календарю
        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> backRow = new java.util.ArrayList<>();
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton backButton = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton("🔙 Назад к календарю");
        backButton.setCallbackData(String.format("calendar_%d-%02d", date.getYear(), date.getMonthValue()));
        backRow.add(backButton);
        rows.add(backRow);
        
        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup keyboard = 
            new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }
}
