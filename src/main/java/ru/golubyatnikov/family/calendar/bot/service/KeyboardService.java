package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;

import java.time.LocalDate;
import java.util.List;

/**
 * Фасад для работы с клавиатурами Telegram.
 * 
 * <p>KeyboardService делегирует создание клавиатур специализированным сервисам:
 * ReplyKeyboardService, InlineKeyboardService и KeyboardLayoutService.</p>
 * 
 * <p><b>Требования:</b> 1.1</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeyboardService {

    private final ReplyKeyboardService replyKeyboardService;
    private final InlineKeyboardService inlineKeyboardService;
    private final KeyboardLayoutService keyboardLayoutService;

    // ==================== ReplyKeyboard методы ====================

    /**
     * Создает клавиатуру для авторизованного пользователя.
     */
    public ReplyKeyboardMarkup createAuthorizedUserKeyboard() {
        return replyKeyboardService.createAuthorizedUserKeyboard();
    }

    /**
     * Создает клавиатуру для неавторизованного пользователя.
     */
    public ReplyKeyboardMarkup createUnauthorizedUserKeyboard() {
        return replyKeyboardService.createUnauthorizedUserKeyboard();
    }

    /**
     * Преобразует текст кнопки в команду.
     */
    public String buttonTextToCommand(String buttonText) {
        return replyKeyboardService.buttonTextToCommand(buttonText);
    }

    // ==================== InlineKeyboard методы ====================

    /**
     * Создает inline клавиатуру для управления событием.
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Long eventId) {
        return inlineKeyboardService.createEventActionsKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для управления событием с учетом прав.
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Event event, Long userId) {
        return inlineKeyboardService.createEventActionsKeyboard(event, userId);
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления.
     */
    public InlineKeyboardMarkup createDeleteConfirmationKeyboard(Long eventId) {
        return inlineKeyboardService.createDeleteConfirmationKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру с кнопкой "Пропустить".
     */
    public InlineKeyboardMarkup createSkipDescriptionKeyboard() {
        return inlineKeyboardService.createSkipDescriptionKeyboard();
    }

    /**
     * Создает inline клавиатуру для выбора типа события.
     */
    public InlineKeyboardMarkup createEventTypeSelectionKeyboard() {
        return inlineKeyboardService.createEventTypeSelectionKeyboard();
    }

    /**
     * Создает inline клавиатуру меню редактирования события.
     */
    public InlineKeyboardMarkup createEditEventMenuKeyboard(Long eventId) {
        return inlineKeyboardService.createEditEventMenuKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для выбора поля редактирования.
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId) {
        return inlineKeyboardService.createEditFieldSelectionKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для завершения редактирования.
     */
    public InlineKeyboardMarkup createEditCompletionKeyboard(Long eventId) {
        return inlineKeyboardService.createEditCompletionKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для настройки напоминаний.
     */
    public InlineKeyboardMarkup createReminderSettingsKeyboard(Long eventId) {
        return inlineKeyboardService.createReminderSettingsKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру меню повторения.
     */
    public InlineKeyboardMarkup createRecurrenceMenuKeyboard(Long eventId) {
        return inlineKeyboardService.createRecurrenceMenuKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для действия с серией.
     */
    public InlineKeyboardMarkup createSeriesActionKeyboard(Long eventId) {
        return inlineKeyboardService.createSeriesActionKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для действий с датой.
     */
    public InlineKeyboardMarkup createDateActionsKeyboard(LocalDate date) {
        return inlineKeyboardService.createDateActionsKeyboard(date);
    }

    /**
     * Создает inline клавиатуру для вложений.
     */
    public InlineKeyboardMarkup createAttachmentKeyboard(Long eventId) {
        return inlineKeyboardService.createAttachmentKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для чек-листа.
     */
    public InlineKeyboardMarkup createChecklistKeyboard(Long eventId) {
        return inlineKeyboardService.createChecklistKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для фильтрации.
     */
    public InlineKeyboardMarkup createFilterKeyboard() {
        return inlineKeyboardService.createFilterKeyboard();
    }

    /**
     * Создает inline клавиатуру для корзины.
     */
    public InlineKeyboardMarkup createTrashActionsKeyboard(Long eventId) {
        return inlineKeyboardService.createTrashActionsKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для списка вложений.
     */
    public InlineKeyboardMarkup createAttachmentsListKeyboard(Long eventId, List<Attachment> attachments, boolean isCreator) {
        return inlineKeyboardService.createAttachmentsListKeyboard(eventId, attachments, isCreator);
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления вложения.
     */
    public InlineKeyboardMarkup createDeleteAttachmentConfirmationKeyboard(Long eventId, Long attachmentId) {
        return inlineKeyboardService.createDeleteAttachmentConfirmationKeyboard(eventId, attachmentId);
    }

    /**
     * Создает inline клавиатуру для просмотра файла.
     */
    public InlineKeyboardMarkup createFileViewKeyboard(Long eventId) {
        return inlineKeyboardService.createFileViewKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для загрузки вложения.
     */
    public InlineKeyboardMarkup createAttachmentUploadKeyboard(Long eventId) {
        return inlineKeyboardService.createAttachmentUploadKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для заметки о завершении.
     */
    public InlineKeyboardMarkup createCompletionNoteKeyboard(Long eventId) {
        return inlineKeyboardService.createCompletionNoteKeyboard(eventId);
    }

    // ==================== KeyboardLayout методы ====================

    /**
     * Создает календарь для выбора даты.
     */
    public InlineKeyboardMarkup createCalendarKeyboard(int year, int month, User user) {
        return keyboardLayoutService.createCalendarKeyboard(year, month, user);
    }

    /**
     * Создает клавиатуру для выбора часа.
     */
    public InlineKeyboardMarkup createHourSelectionKeyboard() {
        return keyboardLayoutService.createHourSelectionKeyboard();
    }

    /**
     * Создает клавиатуру для выбора минут.
     */
    public InlineKeyboardMarkup createMinuteSelectionKeyboard(int selectedHour) {
        return keyboardLayoutService.createMinuteSelectionKeyboard(selectedHour);
    }

    /**
     * Создает фильтрованную клавиатуру для выбора часа.
     */
    public InlineKeyboardMarkup createFilteredHourSelectionKeyboard(LocalDate selectedDate, User user) {
        return keyboardLayoutService.createFilteredHourSelectionKeyboard(selectedDate, user);
    }

    /**
     * Создает фильтрованную клавиатуру для выбора минут.
     */
    public InlineKeyboardMarkup createFilteredMinuteSelectionKeyboard(int selectedHour, LocalDate selectedDate, User user) {
        return keyboardLayoutService.createFilteredMinuteSelectionKeyboard(selectedHour, selectedDate, user);
    }
}
