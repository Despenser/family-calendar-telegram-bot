package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Фасад для создания inline клавиатур (InlineKeyboardMarkup) в Telegram.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InlineKeyboardService {

    private final EventInlineKeyboardFactory eventFactory;
    private final AttachmentInlineKeyboardFactory attachmentFactory;
    private final NavigationInlineKeyboardFactory navigationFactory;
    private final KeyboardFactory keyboardFactory;

    /**
     * Создает inline клавиатуру для управления событием.
     * 
     * @param eventId идентификатор события
     *
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
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Event event, Long userId) {
        return eventFactory.createEventActionsKeyboard(event, userId);
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
     * Создает inline клавиатуру для управления событием в корзине.
     * 
     * @param eventId идентификатор события
     *
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
     *
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
     *
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
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId, Long userId) {
        return eventFactory.createEditFieldSelectionKeyboard(eventId, userId);
    }

    /**
     * Создает inline клавиатуру для фильтрации событий.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createFilterKeyboard() {
        return eventFactory.createFilterKeyboard();
    }

    /**
     * Создает inline клавиатуру для списка вложений события.
     * 
     * @param eventId идентификатор события
     * @param attachments список вложений
     * @param isCreator является ли пользователь создателем
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentsListKeyboard(Long eventId,
                                                              List<Attachment> attachments,
                                                              boolean isCreator) {

        return attachmentFactory.createAttachmentsListKeyboard(eventId, attachments, isCreator);
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления вложения.
     * 
     * @param eventId идентификатор события
     * @param attachmentId идентификатор вложения
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createDeleteAttachmentConfirmationKeyboard(Long eventId,
                                                                           Long attachmentId) {

        return attachmentFactory.createDeleteAttachmentConfirmationKeyboard(eventId, attachmentId);
    }

    /**
     * Создает inline клавиатуру для просмотра файла вложения.
     * 
     * @param eventId идентификатор события
     *
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
     *
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentUploadKeyboard(Long eventId) {
        return attachmentFactory.createAttachmentUploadKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру с кнопкой "Пропустить" для описания события.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createSkipDescriptionKeyboard() {
        return navigationFactory.createSkipDescriptionKeyboard();
    }
    
    /**
     * Создает inline клавиатуру с кнопкой "Отменить создание" для этапа ввода названия.
     * 
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createCancelCreationKeyboard() {
        return navigationFactory.createCancelCreationKeyboard();
    }
    
    /**
     * Создает inline клавиатуру со списком событий на дату (для прошлых дат).
     * 
     * @param date дата для отображения событий
     * @param events список событий на эту дату
     *
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createDateEventsListKeyboard(@NonNull LocalDate date,
                                                             @NonNull List<Event> events) {

        List<InlineKeyboardRow> rows = keyboardFactory.createEventButtonRows(events, 
                event -> CallbackPrefix.VIEW_EVENT.withPayload(event.getId().toString()));

        rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton("🔙 Назад к календарю",
                        CallbackPrefix.CALENDAR.withPayload(String.format("%d-%02d", date.getYear(), date.getMonthValue()))
                )
        ));

        return keyboardFactory.createMarkup(rows);
    }
    
    /**
     * Создает inline клавиатуру для создания события на дату (для будущих дат без событий).
     * 
     * @param date дата для создания события
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createCreateEventOnDateKeyboard(@NonNull LocalDate date) {
        return keyboardFactory.createMarkup(
            keyboardFactory.createRow(
                keyboardFactory.createButton("➕ Создать событие", CallbackPrefix.CREATE_EVENT_ON_DATE.withPayload(date.toString()))
            ),
            keyboardFactory.createRow(
                keyboardFactory.createButton("🔙 Назад к календарю",
                        CallbackPrefix.CALENDAR.withPayload(String.format("%d-%02d", date.getYear(), date.getMonthValue()))
                )
            )
        );
    }
    
    /**
     * Создает inline клавиатуру для управления событиями на дату (для будущих дат с событиями).
     * 
     * @param date дата для управления событиями
     * @param events список событий на эту дату
     * @param user пользователь для проверки прав доступа
     *
     * @return настроенная InlineKeyboardMarkup
     */
    public InlineKeyboardMarkup createDateEventsManagementKeyboard(@NonNull LocalDate date,
                                                                   @NonNull List<Event> events,
                                                                   User user) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        
        // Проверяем, есть ли у пользователя активные события на эту дату
        boolean hasActiveOwnEvents = events.stream()
            .filter(event -> event.getUser().getId().equals(user.getId()))
            .anyMatch(event -> event.getStatus() == EventStatus.ACTIVE);
        
        // Первый ряд: Добавить | Просмотреть
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("➕ Добавить", CallbackPrefix.CREATE_EVENT_ON_DATE.withPayload(date.toString())),
            keyboardFactory.createButton("👁 Просмотреть", CallbackPrefix.VIEW_EVENTS_ON_DATE.withPayload(date.toString()))
        ));
        
        // Второй ряд: Редактировать | Удалить (только если есть активные события)
        if (hasActiveOwnEvents) {
            rows.add(keyboardFactory.createRow(
                keyboardFactory.createButton("✏️ Редактировать", CallbackPrefix.EDIT_MY_EVENTS_ON_DATE.withPayload(date.toString())),
                keyboardFactory.createButton("🗑 Удалить", CallbackPrefix.DELETE_MY_EVENTS_ON_DATE.withPayload(date.toString()))
            ));
        }
        
        // Третий ряд: Назад - возвращаемся к календарю месяца
        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад", CallbackPrefix.BACK_TO_CALENDAR.withPayload(
                    String.format("%d-%02d", date.getYear(), date.getMonthValue())))
        ));
        
        return keyboardFactory.createMarkup(rows);
    }
    
    /**
     * Создает клавиатуру для выбора события пользователя для редактирования.
     */
    public InlineKeyboardMarkup createMyEventsEditKeyboard(@NonNull LocalDate selectedDate,
                                                           @NonNull List<Event> myEvents) {

        List<InlineKeyboardRow> rows = keyboardFactory.createEventButtonRows(myEvents,
                event -> CallbackPrefix.EDIT_EVENT_FROM_CALENDAR.withPayload(event.getId() + "_" + selectedDate));

        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад", CallbackPrefix.CALENDAR.withPayload(selectedDate.toString()))
        ));

        return keyboardFactory.createMarkup(rows);
    }
    
    /**
     * Создает клавиатуру для выбора события пользователя для удаления.
     */
    public InlineKeyboardMarkup createMyEventsDeleteKeyboard(@NonNull LocalDate selectedDate,
                                                             @NonNull List<Event> myEvents) {

        List<InlineKeyboardRow> rows = keyboardFactory.createEventButtonRows(myEvents,
                event -> CallbackPrefix.DELETE_EVENT.withPayload(event.getId().toString()));

        rows.add(keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад", CallbackPrefix.CALENDAR.withPayload(selectedDate.toString()))
        ));

        return keyboardFactory.createMarkup(rows);
    }
}
