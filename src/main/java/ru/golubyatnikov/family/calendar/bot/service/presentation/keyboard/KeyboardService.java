package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import ru.golubyatnikov.family.calendar.bot.model.entity.Attachment;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import java.time.LocalDate;
import java.util.List;

/**
 * Фасад для работы с клавиатурами Telegram.
 * 
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeyboardService {

    private final ReplyKeyboardService replyKeyboardService;
    private final InlineKeyboardService inlineKeyboardService;
    private final KeyboardLayoutService keyboardLayoutService;

    /**
     * Создает обычную клавиатуру для авторизованного пользователя.
     *
     * @return настроенная ReplyKeyboardMarkup с полным набором команд
     */
    public ReplyKeyboardMarkup createAuthorizedUserKeyboard() {
        return replyKeyboardService.createAuthorizedUserKeyboard();
    }

    /**
     * Создает обычную клавиатуру для неавторизованного пользователя.
     *
     * @return настроенная ReplyKeyboardMarkup с ограниченным набором команд
     */
    public ReplyKeyboardMarkup createUnauthorizedUserKeyboard() {
        return replyKeyboardService.createUnauthorizedUserKeyboard();
    }

    /**
     * Преобразует текст кнопки обычной клавиатуры в команду бота.
     *
     * @param buttonText текст кнопки для преобразования
     *
     * @return соответствующая команда или исходный текст, если преобразование невозможно
     * @throws IllegalArgumentException если buttonText равен null
     */
    public String buttonTextToCommand(String buttonText) {
        return replyKeyboardService.buttonTextToCommand(buttonText);
    }

    /**
     * Создает inline клавиатуру для управления событием.
     *
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup с действиями над событием
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Long eventId) {
        return inlineKeyboardService.createEventActionsKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для управления событием с учетом прав доступа.
     *
     * @param event событие для управления
     * @param userId идентификатор пользователя
     *
     * @return настроенная InlineKeyboardMarkup с доступными действиями
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Event event, Long userId) {
        return inlineKeyboardService.createEventActionsKeyboard(event, userId);
    }

    /**
     * Создает inline клавиатуру для выбора типа события при создании.
     *
     * @return настроенная InlineKeyboardMarkup с типами событий
     */
    public InlineKeyboardMarkup createEventTypeSelectionKeyboard() {
        return inlineKeyboardService.createEventTypeSelectionKeyboard();
    }

    /**
     * Создает inline клавиатуру для выбора поля редактирования события.
     *
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup с полями для редактирования
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId) {
        return inlineKeyboardService.createEditFieldSelectionKeyboard(eventId);
    }
    
    /**
     * Создает inline клавиатуру для выбора поля редактирования события.
     *
     * @param eventId идентификатор события
     * @param userId идентификатор пользователя (для проверки контекста редактирования)
     *
     * @return настроенная InlineKeyboardMarkup с доступными полями
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId, Long userId) {
        return inlineKeyboardService.createEditFieldSelectionKeyboard(eventId, userId);
    }

    /**
     * Создает inline клавиатуру для управления событием в корзине.
     *
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup для управления в корзине
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createTrashActionsKeyboard(Long eventId) {
        return inlineKeyboardService.createTrashActionsKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для добавления заметки к завершённому событию.
     *
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup для добавления заметки
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createCompletionNoteKeyboard(Long eventId) {
        return inlineKeyboardService.createCompletionNoteKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для фильтрации событий.
     *
     * @return настроенная InlineKeyboardMarkup с опциями фильтрации
     */
    public InlineKeyboardMarkup createFilterKeyboard() {
        return inlineKeyboardService.createFilterKeyboard();
    }

    /**
     * Создает inline клавиатуру со списком вложений события.
     *
     * @param eventId идентификатор события
     * @param attachments список вложений для отображения
     * @param isCreator является ли пользователь создателем события
     *
     * @return настроенная InlineKeyboardMarkup со списком вложений
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentsListKeyboard(Long eventId,
                                                              List<Attachment> attachments,
                                                              boolean isCreator) {

        return inlineKeyboardService.createAttachmentsListKeyboard(eventId, attachments, isCreator);
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления вложения.
     *
     * @param eventId идентификатор события
     * @param attachmentId идентификатор вложения
     *
     * @return настроенная InlineKeyboardMarkup для подтверждения удаления
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createDeleteAttachmentConfirmationKeyboard(Long eventId, Long attachmentId) {
        return inlineKeyboardService.createDeleteAttachmentConfirmationKeyboard(eventId, attachmentId);
    }

    /**
     * Создает inline клавиатуру для просмотра файла вложения.
     *
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup для просмотра файла
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createFileViewKeyboard(Long eventId) {
        return inlineKeyboardService.createFileViewKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру для режима загрузки вложения.
     *
     * @param eventId идентификатор события
     *
     * @return настроенная InlineKeyboardMarkup для загрузки
     * @throws IllegalArgumentException если eventId некорректен
     */
    public InlineKeyboardMarkup createAttachmentUploadKeyboard(Long eventId) {
        return inlineKeyboardService.createAttachmentUploadKeyboard(eventId);
    }

    /**
     * Создает inline клавиатуру с кнопкой "Пропустить".
     *
     * @return настроенная InlineKeyboardMarkup с кнопкой пропуска
     */
    public InlineKeyboardMarkup createSkipDescriptionKeyboard() {
        return inlineKeyboardService.createSkipDescriptionKeyboard();
    }

    /**
     * Создает календарь для выбора даты при создании события.
     *
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     *
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createCalendarKeyboard(int year, int month, User user) {
        return keyboardLayoutService.createCalendarKeyboard(year, month, user);
    }

    /**
     * Создает календарь для выбора даты с учетом контекста редактирования.
     *
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     * @param editingEventId ID редактируемого события (null для создания нового)
     *
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createCalendarKeyboard(int year,
                                                       int month,
                                                       User user,
                                                       Long editingEventId) {

        return keyboardLayoutService.createCalendarKeyboard(year, month, user, editingEventId);
    }
    
    /**
     * Создает календарь для просмотра событий с возможностью выбора прошлых дат.
     *
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     *
     * @return настроенная InlineKeyboardMarkup с календарем просмотра
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createViewCalendarKeyboard(int year, int month, User user) {
        return keyboardLayoutService.createViewCalendarKeyboard(year, month, user);
    }

    /**
     * Создает фильтрованную клавиатуру для выбора часа.
     *
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     *
     * @return настроенная InlineKeyboardMarkup с доступными часами
     */
    public InlineKeyboardMarkup createFilteredHourSelectionKeyboard(LocalDate selectedDate, User user) {
        return keyboardLayoutService.createFilteredHourSelectionKeyboard(selectedDate, user);
    }

    /**
     * Создает фильтрованную клавиатуру для выбора часа с учетом редактирования.
     *
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @param editingEventId ID редактируемого события (null для создания нового)
     *
     * @return настроенная InlineKeyboardMarkup с доступными часами
     */
    public InlineKeyboardMarkup createFilteredHourSelectionKeyboard(LocalDate selectedDate,
                                                                    User user,
                                                                    Long editingEventId) {
        return keyboardLayoutService.createFilteredHourSelectionKeyboard(selectedDate, user, editingEventId);
    }
    
    /**
     * Создает фильтрованную клавиатуру для выбора часа для создания нового события.
     *
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @param isFromAddEventCommand true если создание началось из команды /add_event
     *
     * @return настроенная InlineKeyboardMarkup с доступными часами
     */
    public InlineKeyboardMarkup createFilteredHourSelectionKeyboard(LocalDate selectedDate,
                                                                    User user,
                                                                    boolean isFromAddEventCommand) {

        return keyboardLayoutService.createFilteredHourSelectionKeyboard(selectedDate, user, isFromAddEventCommand);
    }

    /**
     * Создает фильтрованную клавиатуру для выбора минут с учетом редактирования.
     *
     * @param selectedHour выбранный час
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @param editingEventId ID редактируемого события (null для создания нового)
     *
     * @return настроенная InlineKeyboardMarkup с доступными минутами
     */
    public InlineKeyboardMarkup createFilteredMinuteSelectionKeyboard(int selectedHour,
                                                                      LocalDate selectedDate,
                                                                      User user,
                                                                      Long editingEventId) {
        return keyboardLayoutService.createFilteredMinuteSelectionKeyboard(selectedHour, selectedDate, user, editingEventId);
    }
    
    /**
     * Создает фильтрованную клавиатуру для выбора минут для создания нового события.
     *
     * @param selectedHour выбранный час
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @param isFromAddEventCommand true если создание началось из команды /add_event
     *
     * @return настроенная InlineKeyboardMarkup с доступными минутами
     */
    public InlineKeyboardMarkup createFilteredMinuteSelectionKeyboard(int selectedHour,
                                                                      LocalDate selectedDate,
                                                                      User user,
                                                                      boolean isFromAddEventCommand) {
                                                                        
        return keyboardLayoutService.createFilteredMinuteSelectionKeyboard(selectedHour, selectedDate, user, isFromAddEventCommand);
    }
    
    /**
     * Создает клавиатуру со списком событий на выбранную дату.
     *
     * @param date дата для отображения событий
     * @param events список событий на эту дату
     *
     * @return настроенная InlineKeyboardMarkup со списком событий
     */
    public InlineKeyboardMarkup createDateEventsListKeyboard(LocalDate date, List<Event> events) {
        return inlineKeyboardService.createDateEventsListKeyboard(date, events);
    }
    
    /**
     * Создает клавиатуру для создания события на выбранную дату.
     *
     * @param date дата для создания события
     * @return настроенная InlineKeyboardMarkup с кнопкой создания
     */
    public InlineKeyboardMarkup createCreateEventOnDateKeyboard(LocalDate date) {
        return inlineKeyboardService.createCreateEventOnDateKeyboard(date);
    }

    /**
     * Создает клавиатуру для управления событиями на выбранную дату.
     *
     * @param date дата для управления событиями
     * @param events список событий на эту дату
     * @param user пользователь для проверки прав доступа
     *
     * @return настроенная InlineKeyboardMarkup с опциями управления
     */
    public InlineKeyboardMarkup createDateEventsManagementKeyboard(LocalDate date,
                                                                   List<Event> events,
                                                                   User user) {

        return inlineKeyboardService.createDateEventsManagementKeyboard(date, events, user);
    }
    
    /**
     * Создает клавиатуру для выбора события пользователя для редактирования.
     */
    public InlineKeyboardMarkup createMyEventsEditKeyboard(LocalDate selectedDate, List<Event> myEvents) {
        return inlineKeyboardService.createMyEventsEditKeyboard(selectedDate, myEvents);
    }
    
    /**
     * Создает клавиатуру для выбора события пользователя для удаления.
     */
    public InlineKeyboardMarkup createMyEventsDeleteKeyboard(LocalDate selectedDate, List<Event> myEvents) {
        return inlineKeyboardService.createMyEventsDeleteKeyboard(selectedDate, myEvents);
    }
}
