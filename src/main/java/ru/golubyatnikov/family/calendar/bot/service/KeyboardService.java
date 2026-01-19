package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис для создания клавиатур с кнопками команд в Telegram.
 * 
 * <p>KeyboardService предоставляет удобный интерфейс для создания ReplyKeyboardMarkup
 * с кнопками команд и InlineKeyboardMarkup для интерактивных действий.
 * Это позволяет пользователям выполнять команды одним нажатием без необходимости вводить их вручную.</p>
 * 
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание клавиатуры для авторизованных пользователей с полным набором команд</li>
 *   <li>Создание клавиатуры для неавторизованных пользователей с ограниченным набором команд</li>
 *   <li>Создание inline кнопок для управления событиями (редактирование, удаление)</li>
 *   <li>Создание inline кнопок для подтверждения действий</li>
 *   <li>Преобразование текста кнопки в соответствующую команду для обработки</li>
 *   <li>Автоматическая настройка параметров клавиатуры для удобства использования</li>
 * </ul>
 * 
 * <p><b>Параметры клавиатуры:</b></p>
 * <ul>
 *   <li>resize_keyboard=true - клавиатура автоматически подстраивается под размер экрана</li>
 * </ul>
 * 
 * <p><b>Адаптация к теме:</b></p>
 * <ul>
 *   <li>Все кнопки автоматически адаптируются под светлую/темную тему Telegram пользователя</li>
 *   <li>Эмодзи делают интерфейс более привлекательным и понятным в любой теме</li>
 *   <li>Inline кнопки имеют современный вид и плавные анимации</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 13.1, 13.3, 13.4, 13.5, 14.1, 14.2, 14.3, 14.4, 14.5, 16.1, 16.2, 16.3, 16.5, 16.6</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * // Создание клавиатуры для авторизованного пользователя
 * ReplyKeyboardMarkup keyboard = keyboardService.createAuthorizedUserKeyboard();
 * messageService.sendMessage(chatId, "Выберите действие:", keyboard);
 * 
 * // Создание inline кнопок для события
 * InlineKeyboardMarkup inlineKeyboard = keyboardService.createEventActionsKeyboard(eventId);
 * messageService.sendMessageWithInlineKeyboard(chatId, "Управление событием:", inlineKeyboard);
 * 
 * // Преобразование текста кнопки в команду
 * String buttonText = "📅 Предстоящие события";
 * String command = keyboardService.buttonTextToCommand(buttonText);
 * // command = "/upcoming_events"
 * }</pre>
 * 
 * @see ReplyKeyboardMarkup
 * @see InlineKeyboardMarkup
 * @see KeyboardButton
 * @see InlineKeyboardButton
 * @author Family Calendar Bot Team
 * @version 2.0.0
 * @since 2025-12-30
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeyboardService {

    private final EventRepository eventRepository;

    // Константы для текста кнопок
    private static final String BTN_START = "🚀 Начать";
    private static final String BTN_UPCOMING_EVENTS = "📅 Предстоящие события";
    private static final String BTN_ADD_EVENT = "➕ Добавить событие";
    private static final String BTN_MY_EVENTS = "📋 Мои события";
    private static final String BTN_HELP = "❓ Помощь";
    
    // Маппинг русских букв на надстрочные Unicode символы
    private static final Map<Character, Character> SUPERSCRIPT_MAP = Map.ofEntries(
        Map.entry('А', 'ᴬ'), Map.entry('а', 'ᴬ'),
        Map.entry('Б', 'ᴮ'), Map.entry('б', 'ᴮ'),
        Map.entry('В', 'ⱽ'), Map.entry('в', 'ⱽ'),
        Map.entry('Г', 'ᴳ'), Map.entry('г', 'ᴳ'),
        Map.entry('Д', 'ᴰ'), Map.entry('д', 'ᴰ'),
        Map.entry('Е', 'ᴱ'), Map.entry('е', 'ᴱ'),
        Map.entry('Ж', 'ᴶ'), Map.entry('ж', 'ᴶ'),
        Map.entry('З', 'ᶻ'), Map.entry('з', 'ᶻ'),
        Map.entry('И', 'ᴵ'), Map.entry('и', 'ᴵ'),
        Map.entry('К', 'ᴷ'), Map.entry('к', 'ᴷ'),
        Map.entry('Л', 'ᴸ'), Map.entry('л', 'ᴸ'),
        Map.entry('М', 'ᴹ'), Map.entry('м', 'ᴹ'),
        Map.entry('Н', 'ᴺ'), Map.entry('н', 'ᴺ'),
        Map.entry('О', 'ᴼ'), Map.entry('о', 'ᴼ'),
        Map.entry('П', 'ᴾ'), Map.entry('п', 'ᴾ'),
        Map.entry('Р', 'ᴿ'), Map.entry('р', 'ᴿ'),
        Map.entry('С', 'ˢ'), Map.entry('с', 'ˢ'),
        Map.entry('Т', 'ᵀ'), Map.entry('т', 'ᵀ'),
        Map.entry('У', 'ᵁ'), Map.entry('у', 'ᵁ'),
        Map.entry('Ф', 'ᶠ'), Map.entry('ф', 'ᶠ'),
        Map.entry('Х', 'ˣ'), Map.entry('х', 'ˣ'),
        Map.entry('Ч', 'ᶜ'), Map.entry('ч', 'ᶜ'),
        Map.entry('Ш', 'ᵂ'), Map.entry('ш', 'ᵂ'),
        Map.entry('Ы', 'ʸ'), Map.entry('ы', 'ʸ'),
        Map.entry('Э', 'ᴱ'), Map.entry('э', 'ᴱ'),
        Map.entry('Ю', 'ᵁ'), Map.entry('ю', 'ᵁ'),
        Map.entry('Я', 'ᴬ'), Map.entry('я', 'ᴬ')
    );

    /**
     * Создает клавиатуру для авторизованного пользователя с полным набором команд.
     * 
     * <p>Клавиатура содержит следующие кнопки:</p>
     * <ul>
     *   <li>📅 Предстоящие события - просмотр событий на ближайшие 7 дней</li>
     *   <li>➕ Добавить событие - создание нового события</li>
     *   <li>📋 Мои события - просмотр и управление своими событиями</li>
     *   <li>❓ Помощь - справка по командам</li>
     * </ul>
     * 
     * <p>Кнопки расположены в 2 ряда по 2 кнопки для удобства использования.</p>
     * 
     * <p>Параметры клавиатуры:</p>
     * <ul>
     *   <li>resize_keyboard=true - автоматическая подстройка под размер экрана</li>
     * </ul>
     * 
     * @return настроенная ReplyKeyboardMarkup для авторизованного пользователя
     * @see #createUnauthorizedUserKeyboard()
     */
    public ReplyKeyboardMarkup createAuthorizedUserKeyboard() {
        log.debug("Создание клавиатуры для авторизованного пользователя");
        
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        
        // Первая строка: Предстоящие события | Добавить событие
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BTN_UPCOMING_EVENTS));
        row1.add(new KeyboardButton(BTN_ADD_EVENT));
        rows.add(row1);
        
        // Вторая строка: Мои события | Помощь
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(BTN_MY_EVENTS));
        row2.add(new KeyboardButton(BTN_HELP));
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Клавиатура для авторизованного пользователя создана: {} кнопок в {} рядах", 
                countButtons(rows), rows.size());
        
        return keyboard;
    }

    /**
     * Создает клавиатуру для неавторизованного пользователя с ограниченным набором команд.
     * 
     * <p>Клавиатура содержит следующие кнопки:</p>
     * <ul>
     *   <li>🚀 Начать - регистрация/авторизация в системе</li>
     *   <li>❓ Помощь - справка по командам</li>
     * </ul>
     * 
     * <p>Кнопки расположены в 1 ряд для простоты интерфейса.</p>
     * 
     * <p>Параметры клавиатуры:</p>
     * <ul>
     *   <li>resize_keyboard=true - автоматическая подстройка под размер экрана</li>
     * </ul>
     * 
     * @return настроенная ReplyKeyboardMarkup для неавторизованного пользователя
     * @see #createAuthorizedUserKeyboard()
     */
    public ReplyKeyboardMarkup createUnauthorizedUserKeyboard() {
        log.debug("Создание клавиатуры для неавторизованного пользователя");
        
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        
        List<KeyboardRow> rows = new ArrayList<>();
        
        // Единственная строка: Начать | Помощь
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(BTN_START));
        row.add(new KeyboardButton(BTN_HELP));
        rows.add(row);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Клавиатура для неавторизованного пользователя создана: {} кнопок в {} рядах", 
                countButtons(rows), rows.size());
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для управления событием.
     * 
     * <p>Inline кнопки отображаются непосредственно под сообщением с событием
     * и позволяют выполнять действия без отправки новых сообщений.</p>
     * 
     * <p>Клавиатура содержит следующие кнопки:</p>
     * <ul>
     *   <li>✏️ Редактировать - переход в режим редактирования события</li>
     *   <li>🗑️ Удалить - запрос подтверждения удаления события</li>
     * </ul>
     * 
     * <p>Callback data формируется в формате "action_eventId" для идентификации действия.</p>
     * 
     * <p><b>Преимущества inline кнопок:</b></p>
     * <ul>
     *   <li>Не занимают место основной клавиатуры</li>
     *   <li>Автоматически адаптируются под тему пользователя</li>
     *   <li>Имеют визуальную обратную связь при нажатии</li>
     *   <li>Могут быть динамически обновлены без отправки нового сообщения</li>
     * </ul>
     * 
     * @param eventId идентификатор события для формирования callback data
     * @return настроенная InlineKeyboardMarkup с кнопками управления событием
     * @throws IllegalArgumentException если eventId равен null или не является положительным числом
     * @see #createDeleteConfirmationKeyboard(Long)
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Long eventId) {
        // Валидация eventId
        if (eventId == null) {
            log.error("Попытка создать клавиатуру с null eventId");
            throw new IllegalArgumentException("EventId не может быть null");
        }
        
        if (eventId <= 0) {
            log.error("Попытка создать клавиатуру с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом, получено: " + eventId);
        }
        
        log.debug("Создание inline клавиатуры для события ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопки редактирования и удаления
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton editBtn = new InlineKeyboardButton("✏️ Редактировать");
        String editCallbackData = "edit_event_" + eventId;
        editBtn.setCallbackData(editCallbackData);
        row1.add(editBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️ Удалить");
        String deleteCallbackData = "delete_event_" + eventId;
        deleteBtn.setCallbackData(deleteCallbackData);
        row1.add(deleteBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        // Детальное логирование созданной клавиатуры
        log.debug("Inline клавиатура для события ID={} создана: buttonCount={}, " +
                "editCallback='{}', deleteCallback='{}'", 
                eventId, row1.size(), editCallbackData, deleteCallbackData);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для управления событием с учетом статуса и прав доступа.
     * 
     * <p>Эта перегруженная версия метода добавляет кнопку "Завершить событие" для активных событий,
     * которые принадлежат текущему пользователю. Кнопка размещается перед кнопкой "Удалить".</p>
     * 
     * <p>Клавиатура содержит следующие кнопки:</p>
     * <ul>
     *   <li>✏️ Редактировать - для редактирования события</li>
     *   <li>✅ Завершить событие - только для активных событий создателя</li>
     *   <li>🗑️ Удалить - для удаления события</li>
     * </ul>
     * 
     * <p>Callback data формируется в формате:</p>
     * <ul>
     *   <li>"edit_event_{eventId}" для редактирования</li>
     *   <li>"complete_event_{eventId}" для завершения</li>
     *   <li>"delete_event_{eventId}" для удаления</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 1.1, 4.1, 4.2, 4.3, 4.4</p>
     * 
     * @param event событие для создания клавиатуры
     * @param userId идентификатор пользователя для проверки прав доступа
     * @return настроенная InlineKeyboardMarkup с кнопками управления
     * @throws IllegalArgumentException если event или userId равны null, или userId некорректен
     * @see #createEventActionsKeyboard(Long)
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Event event, Long userId) {
        // Валидация параметров
        if (event == null) {
            log.error("Попытка создать клавиатуру с null event");
            throw new IllegalArgumentException("Event не может быть null");
        }
        
        if (event.getId() == null) {
            log.error("Попытка создать клавиатуру для события с null ID");
            throw new IllegalArgumentException("Event ID не может быть null");
        }
        
        if (userId == null) {
            log.error("Попытка создать клавиатуру с null userId");
            throw new IllegalArgumentException("UserId не может быть null");
        }
        
        if (userId <= 0) {
            log.error("Попытка создать клавиатуру с некорректным userId: {}", userId);
            throw new IllegalArgumentException("UserId должен быть положительным числом, получено: " + userId);
        }
        
        Long eventId = event.getId();
        log.debug("Создание inline клавиатуры для события ID={} с учетом прав пользователя ID={}", 
                eventId, userId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Первый ряд: кнопки редактирования и удаления
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton editBtn = new InlineKeyboardButton("✏️ Редактировать");
        String editCallbackData = "edit_event_" + eventId;
        editBtn.setCallbackData(editCallbackData);
        row1.add(editBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️ Удалить");
        String deleteCallbackData = "delete_event_" + eventId;
        deleteBtn.setCallbackData(deleteCallbackData);
        row1.add(deleteBtn);
        
        rows.add(row1);
        
        // Второй ряд: кнопка "Завершить событие" (только для активных событий создателя)
        boolean isActive = event.getStatus() == Event.EventStatus.ACTIVE;
        boolean isOwner = event.belongsToUser(userId);
        
        if (isActive && isOwner) {
            List<InlineKeyboardButton> row2 = new ArrayList<>();
            
            InlineKeyboardButton completeBtn = new InlineKeyboardButton("✅ Завершить событие");
            String completeCallbackData = "complete_event_" + eventId;
            completeBtn.setCallbackData(completeCallbackData);
            row2.add(completeBtn);
            
            // Вставляем кнопку "Завершить" перед кнопкой "Удалить" (в начало списка)
            rows.add(0, row2);
            
            log.debug("Inline клавиатура для события ID={} создана с кнопкой завершения: " +
                    "buttonCount={}, editCallback='{}', completeCallback='{}', deleteCallback='{}'", 
                    eventId, 3, editCallbackData, completeCallbackData, deleteCallbackData);
        } else {
            log.debug("Inline клавиатура для события ID={} создана без кнопки завершения " +
                    "(isActive={}, isOwner={}): buttonCount={}, editCallback='{}', deleteCallback='{}'", 
                    eventId, isActive, isOwner, 2, editCallbackData, deleteCallbackData);
        }
        
        keyboard.setKeyboard(rows);
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру для подтверждения удаления события.
     * 
     * <p>Эта клавиатура отображается после нажатия кнопки "Удалить" и требует
     * явного подтверждения действия для предотвращения случайного удаления.</p>
     * 
     * <p>Клавиатура содержит следующие кнопки:</p>
     * <ul>
     *   <li>✅ Да, удалить - подтверждение удаления события</li>
     *   <li>❌ Отмена - отмена операции удаления</li>
     * </ul>
     * 
     * <p>Callback data формируется в формате "confirm_delete_eventId" или "cancel_delete_eventId".</p>
     * 
     * @param eventId идентификатор события для формирования callback data
     * @return настроенная InlineKeyboardMarkup с кнопками подтверждения
     * @see #createEventActionsKeyboard(Long)
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
        
        log.debug("Inline клавиатура подтверждения для события {} создана", eventId);
        
        return keyboard;
    }

    /**
     * Преобразует текст кнопки в соответствующую команду для обработки.
     * 
     * <p>Этот метод позволяет обрабатывать нажатия кнопок так же, как текстовые команды.
     * Если текст не соответствует ни одной известной кнопке, он возвращается без изменений.</p>
     * 
     * <p>Поддерживаемые преобразования:</p>
     * <ul>
     *   <li>"🚀 Начать" → "/start"</li>
     *   <li>"📅 Предстоящие события" → "/upcoming_events"</li>
     *   <li>"➕ Добавить событие" → "/add_event"</li>
     *   <li>"📋 Мои события" → "/my_events"</li>
     *   <li>"❓ Помощь" → "/help"</li>
     * </ul>
     * 
     * <p>Если текст не соответствует ни одной кнопке, он возвращается без изменений.
     * Это позволяет пользователям вводить команды вручную, если они предпочитают.</p>
     * 
     * @param buttonText текст кнопки для преобразования
     * @return соответствующая команда или исходный текст, если преобразование невозможно
     * @throws IllegalArgumentException если buttonText равен null
     */
    public String buttonTextToCommand(String buttonText) {
        if (buttonText == null) {
            log.error("Попытка преобразовать null buttonText в команду");
            throw new IllegalArgumentException("ButtonText не может быть null");
        }
        
        log.debug("Преобразование текста кнопки в команду: '{}'", buttonText);
        
        String command = switch (buttonText) {
            case BTN_START -> "/start";
            case BTN_UPCOMING_EVENTS -> "/upcoming_events";
            case BTN_ADD_EVENT -> "/add_event";
            case BTN_MY_EVENTS -> "/my_events";
            case BTN_HELP -> "/help";
            default -> buttonText;
        };
        
        if (!command.equals(buttonText)) {
            log.debug("Текст кнопки '{}' преобразован в команду '{}'", buttonText, command);
        } else {
            log.debug("Текст '{}' не является кнопкой, возвращен без изменений", buttonText);
        }
        
        return command;
    }

    /**
     * Создает inline-календарь для выбора даты события.
     * 
     * <p>Календарь отображает указанный месяц с кнопками для каждого дня.
     * Даты в прошлом отображаются как пустые ячейки без текста.
     * Дни с существующими событиями выделяются визуальным индикатором с инициалом создателя.</p>
     * 
     * <p>Структура календаря:</p>
     * <ul>
     *   <li>Заголовок с названием месяца и года</li>
     *   <li>Строка с днями недели (Пн-Вс)</li>
     *   <li>Дни месяца (с пустыми ячейками для выравнивания)</li>
     *   <li>Кнопки навигации: Предыдущий месяц | Отмена | Следующий месяц</li>
     * </ul>
     * 
     * <p>Callback data формируется в формате:</p>
     * <ul>
     *   <li>"date_YYYY-MM-DD" - для выбора даты</li>
     *   <li>"calendar_YYYY-MM" - для навигации по месяцам</li>
     *   <li>"calendar_cancel" - для отмены выбора</li>
     *   <li>"calendar_ignore" - для неактивных элементов</li>
     * </ul>
     * 
     * <p><b>Визуальные индикаторы:</b></p>
     * <ul>
     *   <li>Прошлые даты: пустая ячейка " "</li>
     *   <li>Дни с событиями: "день📌инициал" (например, "5📌А")</li>
     *   <li>Навигация в прошлое: заблокирована пустой кнопкой "   "</li>
     * </ul>
     * 
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param familyId ID семьи для проверки существующих событий
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если month не в диапазоне 1-12
     */
    public InlineKeyboardMarkup createCalendarKeyboard(int year, int month, Long familyId) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        
        log.debug("Создание inline-календаря для {}-{:02d}, familyId={}", year, month, familyId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        YearMonth yearMonth = YearMonth.of(year, month);
        YearMonth currentYearMonth = YearMonth.now();
        LocalDate firstDay = yearMonth.atDay(1);
        int daysInMonth = yearMonth.lengthOfMonth();
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
        
        // Получаем события семьи за этот месяц для визуального выделения
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        List<Event> monthEvents = eventRepository
            .findByFamilyIdAndEventDateBetweenAndStatus(
                familyId, monthStart, monthEnd, Event.EventStatus.ACTIVE);
        
        // Группируем события по датам и берем первое событие (по времени) для каждой даты
        Map<LocalDate, Event> firstEventByDate = monthEvents.stream()
            .sorted(Comparator.comparing(Event::getEventTime))
            .collect(Collectors.toMap(
                Event::getEventDate,
                Function.identity(),
                (existing, replacement) -> existing, // Оставляем первое (самое раннее)
                LinkedHashMap::new
            ));
        
        log.debug("Найдено {} событий для календаря {}-{:02d}", 
            firstEventByDate.size(), year, month);
        
        // Заголовок с месяцем и годом
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        InlineKeyboardButton headerBtn = new InlineKeyboardButton(
            yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("ru"))));
        headerBtn.setCallbackData("calendar_ignore");
        headerRow.add(headerBtn);
        rows.add(headerRow);
        
        // Дни недели
        List<InlineKeyboardButton> weekDaysRow = new ArrayList<>();
        String[] weekDays = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (String day : weekDays) {
            InlineKeyboardButton dayBtn = new InlineKeyboardButton(day);
            dayBtn.setCallbackData("calendar_ignore");
            weekDaysRow.add(dayBtn);
        }
        rows.add(weekDaysRow);
        
        // Дни месяца
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        
        // Пустые ячейки до первого дня месяца
        for (int i = 1; i < firstDayOfWeek; i++) {
            InlineKeyboardButton emptyBtn = new InlineKeyboardButton(" ");
            emptyBtn.setCallbackData("calendar_ignore");
            currentRow.add(emptyBtn);
        }
        
        // Дни месяца
        LocalDate today = LocalDate.now();
        
        // Подсчитываем количество событий для каждой даты
        Map<LocalDate, Long> eventCountByDate = monthEvents.stream()
            .collect(Collectors.groupingBy(Event::getEventDate, Collectors.counting()));
        
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(year, month, day);
            InlineKeyboardButton dayBtn;
            
            // Даты в прошлом отображаются как пустые ячейки
            if (date.isBefore(today)) {
                dayBtn = new InlineKeyboardButton(" ");
                dayBtn.setCallbackData("calendar_ignore");
            } else {
                // Добавляем визуальный индикатор для дней с событиями
                String dayText = String.valueOf(day);
                
                // Добавляем эмодзи 📍 для текущей даты
                if (date.equals(today)) {
                    dayText = "📍" + day;
                }
                
                // Если на этот день есть событие, добавляем инициал создателя и счетчик
                if (firstEventByDate.containsKey(date)) {
                    Event event = firstEventByDate.get(date);
                    String creatorInitial = event.getUser().getFirstName()
                        .substring(0, 1).toUpperCase();
                    String superscriptInitial = toSuperscript(creatorInitial);
                    
                    // Добавляем счетчик событий если их больше одного
                    long eventCount = eventCountByDate.getOrDefault(date, 0L);
                    if (eventCount > 1) {
                        dayText = day + superscriptInitial + "(" + eventCount + ")";
                    } else {
                        dayText = day + superscriptInitial;
                    }
                    
                    // Если это текущая дата с событиями, добавляем эмодзи в начало
                    if (date.equals(today)) {
                        dayText = "📍" + dayText;
                    }
                }
                
                dayBtn = new InlineKeyboardButton(dayText);
                dayBtn.setCallbackData(String.format("date_%d-%02d-%02d", year, month, day));
            }
            
            currentRow.add(dayBtn);
            
            // Переход на новую строку после воскресенья
            if ((firstDayOfWeek + day - 1) % 7 == 0) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
            }
        }
        
        // Добавляем последнюю строку, если она не пустая
        if (!currentRow.isEmpty()) {
            // Заполняем пустыми ячейками до конца недели
            while (currentRow.size() < 7) {
                InlineKeyboardButton emptyBtn = new InlineKeyboardButton(" ");
                emptyBtn.setCallbackData("calendar_ignore");
                currentRow.add(emptyBtn);
            }
            rows.add(currentRow);
        }
        
        // Кнопки навигации
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        
        // Кнопка "Предыдущий месяц" - блокируем если предыдущий месяц в прошлом
        YearMonth prevMonth = yearMonth.minusMonths(1);
        if (prevMonth.isBefore(currentYearMonth)) {
            // Добавляем пустую кнопку вместо навигации
            InlineKeyboardButton disabledBtn = new InlineKeyboardButton("   ");
            disabledBtn.setCallbackData("calendar_ignore");
            navigationRow.add(disabledBtn);
        } else {
            InlineKeyboardButton prevBtn = new InlineKeyboardButton("◀️ Пред");
            prevBtn.setCallbackData(String.format("calendar_%d-%02d", 
                prevMonth.getYear(), prevMonth.getMonthValue()));
            navigationRow.add(prevBtn);
        }
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("calendar_cancel");
        navigationRow.add(cancelBtn);
        
        InlineKeyboardButton nextBtn = new InlineKeyboardButton("След ▶️");
        YearMonth nextMonth = yearMonth.plusMonths(1);
        nextBtn.setCallbackData(String.format("calendar_%d-%02d", 
            nextMonth.getYear(), nextMonth.getMonthValue()));
        navigationRow.add(nextBtn);
        
        rows.add(navigationRow);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-календарь для {}-{:02d} создан с {} рядами", year, month, rows.size());
        
        return keyboard;
    }

    /**
     * Создает inline-клавиатуру для выбора часа события.
     * 
     * <p>Клавиатура содержит кнопки для выбора часа от 0 до 23,
     * расположенные по 4 кнопки в ряд для удобства выбора.</p>
     * 
     * <p>Callback data формируется в формате:</p>
     * <ul>
     *   <li>"hour_HH" - для выбора часа (например, "hour_09")</li>
     *   <li>"time_cancel" - для отмены выбора</li>
     *   <li>"time_ignore" - для неактивных элементов</li>
     * </ul>
     * 
     * @return настроенная InlineKeyboardMarkup с кнопками выбора часа
     */
    public InlineKeyboardMarkup createHourSelectionKeyboard() {
        log.debug("Создание inline-клавиатуры для выбора часа");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Заголовок
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        InlineKeyboardButton headerBtn = new InlineKeyboardButton("Выберите час:");
        headerBtn.setCallbackData("time_ignore");
        headerRow.add(headerBtn);
        rows.add(headerRow);
        
        // Кнопки часов (по 4 в ряд)
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            InlineKeyboardButton hourBtn = new InlineKeyboardButton(
                String.format("%02d:00", hour));
            hourBtn.setCallbackData(String.format("hour_%02d", hour));
            currentRow.add(hourBtn);
            
            if ((hour + 1) % 4 == 0) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
            }
        }
        
        // Кнопка отмены
        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("time_cancel");
        cancelRow.add(cancelBtn);
        rows.add(cancelRow);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура для выбора часа создана с {} рядами", rows.size());
        
        return keyboard;
    }

    /**
     * Создает inline-клавиатуру для выбора минут события.
     * 
     * <p>Клавиатура содержит кнопки для выбора минут с интервалом 15 минут:
     * 00, 15, 30, 45. Это упрощает выбор и покрывает большинство случаев использования.</p>
     * 
     * <p>Callback data формируется в формате:</p>
     * <ul>
     *   <li>"time_HH:MM" - для выбора времени (например, "time_09:30")</li>
     *   <li>"time_back" - для возврата к выбору часа</li>
     *   <li>"time_cancel" - для отмены выбора</li>
     *   <li>"time_ignore" - для неактивных элементов</li>
     * </ul>
     * 
     * @param selectedHour выбранный час (0-23)
     * @return настроенная InlineKeyboardMarkup с кнопками выбора минут
     * @throws IllegalArgumentException если selectedHour не в диапазоне 0-23
     */
    public InlineKeyboardMarkup createMinuteSelectionKeyboard(int selectedHour) {
        if (selectedHour < 0 || selectedHour > 23) {
            throw new IllegalArgumentException("Selected hour must be between 0 and 23");
        }
        
        log.debug("Создание inline-клавиатуры для выбора минут (час: {:02d})", selectedHour);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Заголовок
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        InlineKeyboardButton headerBtn = new InlineKeyboardButton(
            String.format("Выберите минуты (час: %02d):", selectedHour));
        headerBtn.setCallbackData("time_ignore");
        headerRow.add(headerBtn);
        rows.add(headerRow);
        
        // Кнопки минут
        List<InlineKeyboardButton> minutesRow = new ArrayList<>();
        int[] minutes = {0, 15, 30, 45};
        for (int minute : minutes) {
            InlineKeyboardButton minuteBtn = new InlineKeyboardButton(
                String.format("%02d:%02d", selectedHour, minute));
            minuteBtn.setCallbackData(String.format("time_%02d:%02d", selectedHour, minute));
            minutesRow.add(minuteBtn);
        }
        rows.add(minutesRow);
        
        // Кнопки навигации
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        
        InlineKeyboardButton backBtn = new InlineKeyboardButton("◀️ Назад");
        backBtn.setCallbackData("time_back");
        navigationRow.add(backBtn);
        
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("time_cancel");
        navigationRow.add(cancelBtn);
        
        rows.add(navigationRow);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура для выбора минут создана с {} рядами", rows.size());
        
        return keyboard;
    }

    /**
     * Создает inline клавиатуру с кнопкой "Пропустить" для описания события.
     * 
     * <p>Эта клавиатура отображается при запросе описания события
     * и позволяет пользователю пропустить этот шаг.</p>
     * 
     * @return настроенная InlineKeyboardMarkup с кнопкой "Пропустить"
     */
    public InlineKeyboardMarkup createSkipDescriptionKeyboard() {
        log.debug("Создание inline клавиатуры с кнопкой 'Пропустить'");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton skipBtn = new InlineKeyboardButton("⏭️ Пропустить");
        skipBtn.setCallbackData("skip_description");
        row1.add(skipBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        log.debug("Inline клавиатура с кнопкой 'Пропустить' создана");
        
        return keyboard;
    }

    /**
     * Преобразует строку в надстрочный формат используя Unicode символы.
     * 
     * <p>Для каждого символа в строке пытается найти соответствующий
     * надстрочный Unicode символ. Если надстрочный символ не найден,
     * возвращает исходный символ.</p>
     * 
     * @param text текст для преобразования
     * @return текст с надстрочными символами
     */
    private String toSuperscript(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(SUPERSCRIPT_MAP.getOrDefault(c, c));
        }
        
        return result.toString();
    }

    /**
     * Подсчитывает общее количество кнопок в списке рядов клавиатуры.
     * 
     * <p>Используется для логирования и отладки.</p>
     * 
     * @param rows список рядов клавиатуры
     * @return общее количество кнопок
     */
    private int countButtons(List<KeyboardRow> rows) {
        if (rows == null) {
            return 0;
        }
        
        return rows.stream()
                .mapToInt(row -> row != null ? row.size() : 0)
                .sum();
    }
    
    /**
     * Создает inline-клавиатуру для выбора типа события (семейное/персональное).
     * 
     * <p>Позволяет пользователю выбрать, будет ли событие видно всей семье
     * или только ему самому.</p>
     * 
     * <p><b>Требования:</b> 26.1</p>
     * 
     * @return настроенная InlineKeyboardMarkup с кнопками выбора типа
     */
    public InlineKeyboardMarkup createEventTypeSelectionKeyboard() {
        log.debug("Создание inline-клавиатуры для выбора типа события");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопка "Семейное событие"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton familyBtn = new InlineKeyboardButton("👨‍👩‍👧‍👦 Семейное событие");
        familyBtn.setCallbackData("event_type_family");
        row1.add(familyBtn);
        rows.add(row1);
        
        // Кнопка "Персональное событие"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton personalBtn = new InlineKeyboardButton("🔒 Персональное событие");
        personalBtn.setCallbackData("event_type_personal");
        row2.add(personalBtn);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура выбора типа события создана");
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру меню редактирования события.
     * 
     * <p>Позволяет выбрать, какое поле события нужно изменить.</p>
     * 
     * <p><b>Требования:</b> 18.1</p>
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup с опциями редактирования
     */
    public InlineKeyboardMarkup createEditEventMenuKeyboard(Long eventId) {
        log.debug("Создание inline-клавиатуры меню редактирования для события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопка "Изменить дату"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton dateBtn = new InlineKeyboardButton("📅 Изменить дату");
        dateBtn.setCallbackData("edit_field_date_" + eventId);
        row1.add(dateBtn);
        rows.add(row1);
        
        // Кнопка "Изменить время"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton timeBtn = new InlineKeyboardButton("🕐 Изменить время");
        timeBtn.setCallbackData("edit_field_time_" + eventId);
        row2.add(timeBtn);
        rows.add(row2);
        
        // Кнопка "Изменить название"
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton titleBtn = new InlineKeyboardButton("✏️ Изменить название");
        titleBtn.setCallbackData("edit_field_title_" + eventId);
        row3.add(titleBtn);
        rows.add(row3);
        
        // Кнопка "Изменить описание"
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton descBtn = new InlineKeyboardButton("📝 Изменить описание");
        descBtn.setCallbackData("edit_field_description_" + eventId);
        row4.add(descBtn);
        rows.add(row4);
        
        // Кнопка "Отмена"
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("edit_cancel_" + eventId);
        row5.add(cancelBtn);
        rows.add(row5);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура меню редактирования создана");
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для выбора поля редактирования события.
     * 
     * <p>Клавиатура содержит кнопки для выбора полей: Название, Дата, Время, Описание, Отмена.</p>
     * <p>Кнопки расположены по 2 в ряд для удобства использования.</p>
     * 
     * <p><b>Требования:</b> 3.1, 3.4</p>
     * 
     * @param eventId идентификатор события для формирования callback data
     * @return настроенная InlineKeyboardMarkup с кнопками выбора поля
     * @throws IllegalArgumentException если eventId равен null или не является положительным числом
     */
    public InlineKeyboardMarkup createEditFieldSelectionKeyboard(Long eventId) {
        // Валидация eventId
        if (eventId == null) {
            log.error("Попытка создать клавиатуру выбора поля с null eventId");
            throw new IllegalArgumentException("EventId не может быть null");
        }
        
        if (eventId <= 0) {
            log.error("Попытка создать клавиатуру выбора поля с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом, получено: " + eventId);
        }
        
        log.debug("Создание inline-клавиатуры выбора поля для редактирования события ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Ряд 1: Название и Дата
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("📝 Название", "edit_field_title_" + eventId));
        row1.add(createButton("📅 Дата", "edit_field_date_" + eventId));
        rows.add(row1);
        
        // Ряд 2: Время и Описание
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("🕐 Время", "edit_field_time_" + eventId));
        row2.add(createButton("📄 Описание", "edit_field_description_" + eventId));
        rows.add(row2);
        
        // Ряд 3: Отмена
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createButton("❌ Отменить", "edit_cancel_" + eventId));
        rows.add(row3);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура выбора поля для события ID={} создана: {} рядов, {} кнопок", 
                eventId, rows.size(), rows.stream().mapToInt(List::size).sum());
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для завершения редактирования события.
     * 
     * <p>Клавиатура содержит кнопки: "Редактировать еще" и "Завершить".</p>
     * <p>Позволяет пользователю продолжить редактирование других полей или завершить процесс.</p>
     * 
     * <p><b>Требования:</b> 3.1, 3.4</p>
     * 
     * @param eventId идентификатор события для формирования callback data
     * @return настроенная InlineKeyboardMarkup с кнопками завершения
     * @throws IllegalArgumentException если eventId равен null или не является положительным числом
     */
    public InlineKeyboardMarkup createEditCompletionKeyboard(Long eventId) {
        // Валидация eventId
        if (eventId == null) {
            log.error("Попытка создать клавиатуру завершения с null eventId");
            throw new IllegalArgumentException("EventId не может быть null");
        }
        
        if (eventId <= 0) {
            log.error("Попытка создать клавиатуру завершения с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом, получено: " + eventId);
        }
        
        log.debug("Создание inline-клавиатуры завершения редактирования для события ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Ряд 1: Редактировать еще или Завершить
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("✏️ Редактировать еще", "edit_more_" + eventId));
        row1.add(createButton("✅ Завершить", "edit_complete_" + eventId));
        rows.add(row1);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура завершения для события ID={} создана", eventId);
        
        return keyboard;
    }
    
    /**
     * Создает inline-кнопку с заданным текстом и callback data.
     * 
     * <p>Вспомогательный метод для упрощения создания кнопок.</p>
     * 
     * @param text текст кнопки
     * @param callbackData данные для callback query
     * @return настроенная InlineKeyboardButton
     */
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
    
    /**
     * Создает inline-клавиатуру для настройки напоминаний.
     * 
     * <p>Позволяет выбрать тип напоминания для события.</p>
     * 
     * <p><b>Требования:</b> 23.2</p>
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup с типами напоминаний
     */
    public InlineKeyboardMarkup createReminderSettingsKeyboard(Long eventId) {
        log.debug("Создание inline-клавиатуры настройки напоминаний для события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопка "Утром в день события"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton morningBtn = new InlineKeyboardButton("🌅 Утром в день события");
        morningBtn.setCallbackData("reminder_morning_" + eventId);
        row1.add(morningBtn);
        rows.add(row1);
        
        // Кнопка "Вечером накануне"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton eveningBtn = new InlineKeyboardButton("🌆 Вечером накануне");
        eveningBtn.setCallbackData("reminder_evening_" + eventId);
        row2.add(eveningBtn);
        rows.add(row2);
        
        // Кнопка "За час до события"
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton hourBtn = new InlineKeyboardButton("⏰ За час до события");
        hourBtn.setCallbackData("reminder_hour_" + eventId);
        row3.add(hourBtn);
        rows.add(row3);
        
        // Кнопка "За 10 минут"
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton tenMinBtn = new InlineKeyboardButton("⏱️ За 10 минут");
        tenMinBtn.setCallbackData("reminder_ten_min_" + eventId);
        row4.add(tenMinBtn);
        rows.add(row4);
        
        // Кнопка "Свое время"
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        InlineKeyboardButton customBtn = new InlineKeyboardButton("⚙️ Свое время");
        customBtn.setCallbackData("reminder_custom_" + eventId);
        row5.add(customBtn);
        rows.add(row5);
        
        // Кнопка "Отмена"
        List<InlineKeyboardButton> row6 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("reminder_cancel_" + eventId);
        row6.add(cancelBtn);
        rows.add(row6);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура настройки напоминаний создана");
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру меню настройки повторения события.
     * 
     * <p>Позволяет настроить параметры повторяющегося события.</p>
     * 
     * <p><b>Требования:</b> 27.2</p>
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup с опциями повторения
     */
    public InlineKeyboardMarkup createRecurrenceMenuKeyboard(Long eventId) {
        log.debug("Создание inline-клавиатуры меню повторения для события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопка "Ежедневно"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton dailyBtn = new InlineKeyboardButton("📆 Ежедневно");
        dailyBtn.setCallbackData("recurrence_daily_" + eventId);
        row1.add(dailyBtn);
        rows.add(row1);
        
        // Кнопка "Еженедельно"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton weeklyBtn = new InlineKeyboardButton("📅 Еженедельно");
        weeklyBtn.setCallbackData("recurrence_weekly_" + eventId);
        row2.add(weeklyBtn);
        rows.add(row2);
        
        // Кнопка "Ежемесячно"
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton monthlyBtn = new InlineKeyboardButton("🗓️ Ежемесячно");
        monthlyBtn.setCallbackData("recurrence_monthly_" + eventId);
        row3.add(monthlyBtn);
        rows.add(row3);
        
        // Кнопка "Отмена"
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("recurrence_cancel_" + eventId);
        row4.add(cancelBtn);
        rows.add(row4);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура меню повторения создана");
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для выбора действия с серией событий.
     * 
     * <p>Позволяет выбрать, применить ли изменения только к текущему событию
     * или ко всей серии повторяющихся событий.</p>
     * 
     * <p><b>Требования:</b> 27.7</p>
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup с опциями действия
     */
    public InlineKeyboardMarkup createSeriesActionKeyboard(Long eventId) {
        log.debug("Создание inline-клавиатуры действия с серией для события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопка "Только это событие"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton singleBtn = new InlineKeyboardButton("📌 Только это событие");
        singleBtn.setCallbackData("series_action_single_" + eventId);
        row1.add(singleBtn);
        rows.add(row1);
        
        // Кнопка "Всю серию"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton seriesBtn = new InlineKeyboardButton("📚 Всю серию");
        seriesBtn.setCallbackData("series_action_all_" + eventId);
        row2.add(seriesBtn);
        rows.add(row2);
        
        // Кнопка "Отмена"
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("series_action_cancel_" + eventId);
        row3.add(cancelBtn);
        rows.add(row3);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура действия с серией создана");
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру с действиями для выбранной даты в календаре.
     * 
     * <p>Позволяет посмотреть события на выбранную дату или создать новое.</p>
     * 
     * <p><b>Требования:</b> 17.3</p>
     * 
     * @param date выбранная дата
     * @return настроенная InlineKeyboardMarkup с действиями
     */
    public InlineKeyboardMarkup createDateActionsKeyboard(LocalDate date) {
        log.debug("Создание inline-клавиатуры действий для даты {}", date);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        String dateStr = date.toString();
        
        // Кнопка "Посмотреть события"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton viewBtn = new InlineKeyboardButton("👀 Посмотреть события");
        viewBtn.setCallbackData("date_actions_view_" + dateStr);
        row1.add(viewBtn);
        rows.add(row1);
        
        // Кнопка "Создать новое"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton createBtn = new InlineKeyboardButton("➕ Создать новое");
        createBtn.setCallbackData("date_actions_create_" + dateStr);
        row2.add(createBtn);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура действий для даты создана");
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для прикрепления файла к событию.
     * 
     * <p>Позволяет прикрепить файл или отменить действие.</p>
     * 
     * <p><b>Требования:</b> 20.1</p>
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup с опциями вложения
     */
    public InlineKeyboardMarkup createAttachmentKeyboard(Long eventId) {
        log.debug("Создание inline-клавиатуры для вложений события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопка "Прикрепить файл"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton attachBtn = new InlineKeyboardButton("📎 Прикрепить файл");
        attachBtn.setCallbackData("attach_file_" + eventId);
        row1.add(attachBtn);
        rows.add(row1);
        
        // Кнопка "Отмена"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("attach_cancel_" + eventId);
        row2.add(cancelBtn);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура для вложений создана");
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для добавления чек-листа к событию.
     * 
     * <p>Позволяет добавить чек-лист или отменить действие.</p>
     * 
     * <p><b>Требования:</b> 22.1</p>
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup с опциями чек-листа
     */
    public InlineKeyboardMarkup createChecklistKeyboard(Long eventId) {
        log.debug("Создание inline-клавиатуры для чек-листа события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопка "Добавить чек-лист"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton addBtn = new InlineKeyboardButton("✅ Добавить чек-лист");
        addBtn.setCallbackData("checklist_add_" + eventId);
        row1.add(addBtn);
        rows.add(row1);
        
        // Кнопка "Отмена"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("checklist_cancel_" + eventId);
        row2.add(cancelBtn);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура для чек-листа создана");
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для добавления комментария к событию.
     * 
     * <p>Позволяет добавить комментарий или отменить действие.</p>
     * 
     * <p><b>Требования:</b> 21.1</p>
     * 
     * @param eventId идентификатор события
     * @return настроенная InlineKeyboardMarkup с опциями комментария
     */
    public InlineKeyboardMarkup createCommentKeyboard(Long eventId) {
        log.debug("Создание inline-клавиатуры для комментария события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопка "Добавить комментарий"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton addBtn = new InlineKeyboardButton("💬 Добавить комментарий");
        addBtn.setCallbackData("comment_add_" + eventId);
        row1.add(addBtn);
        rows.add(row1);
        
        // Кнопка "Отмена"
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
        cancelBtn.setCallbackData("comment_cancel_" + eventId);
        row2.add(cancelBtn);
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура для комментария создана");
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для фильтрации событий.
     * 
     * <p>Позволяет пользователю выбрать тип событий для отображения:
     * все события, только семейные или только личные.</p>
     * 
     * <p>Клавиатура содержит следующие кнопки:</p>
     * <ul>
     *   <li>📋 Все события - показать все события (семейные и личные)</li>
     *   <li>👨‍👩‍👧‍👦 Семейные - показать только семейные события</li>
     *   <li>👤 Личные - показать только личные события</li>
     * </ul>
     * 
     * <p>Callback data формируется в формате "filter_{тип}":</p>
     * <ul>
     *   <li>"filter_all" - для всех событий</li>
     *   <li>"filter_family" - для семейных событий</li>
     *   <li>"filter_personal" - для личных событий</li>
     * </ul>
     * 
     * <p>Кнопки расположены в 2 ряда: первая кнопка "Все события" в отдельном ряду,
     * вторая и третья кнопки ("Семейные" и "Личные") в одном ряду для удобства выбора.</p>
     * 
     * <p><b>Требования:</b> 3.3</p>
     * 
     * @return настроенная InlineKeyboardMarkup с кнопками фильтрации
     */
    public InlineKeyboardMarkup createFilterKeyboard() {
        log.debug("Создание inline-клавиатуры для фильтрации событий");
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Первая строка: Все события
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton allBtn = new InlineKeyboardButton("📋 Все события");
        allBtn.setCallbackData("filter_all");
        row1.add(allBtn);
        rows.add(row1);
        
        // Вторая строка: Семейные и Личные
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        
        InlineKeyboardButton familyBtn = new InlineKeyboardButton("👨‍👩‍👧‍👦 Семейные");
        familyBtn.setCallbackData("filter_family");
        row2.add(familyBtn);
        
        InlineKeyboardButton personalBtn = new InlineKeyboardButton("👤 Личные");
        personalBtn.setCallbackData("filter_personal");
        row2.add(personalBtn);
        
        rows.add(row2);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура для фильтрации событий создана: {} рядов, {} кнопок", 
                rows.size(), rows.stream().mapToInt(List::size).sum());
        
        return keyboard;
    }
    
    /**
     * Создает inline-клавиатуру для управления событием в корзине.
     * 
     * <p>Эта клавиатура отображается под каждым событием в корзине
     * и позволяет выполнять действия восстановления или окончательного удаления.</p>
     * 
     * <p>Клавиатура содержит следующие кнопки:</p>
     * <ul>
     *   <li>♻️ Восстановить - восстановление события из корзины</li>
     *   <li>❌ Удалить навсегда - окончательное удаление события</li>
     * </ul>
     * 
     * <p>Callback data формируется в формате:</p>
     * <ul>
     *   <li>"trash_restore_{eventId}" - для восстановления</li>
     *   <li>"trash_delete_{eventId}" - для окончательного удаления</li>
     * </ul>
     * 
     * <p><b>Требования:</b> 4.1, 4.4</p>
     * 
     * @param eventId идентификатор события для формирования callback data
     * @return настроенная InlineKeyboardMarkup с кнопками управления событием в корзине
     * @throws IllegalArgumentException если eventId равен null или не является положительным числом
     */
    public InlineKeyboardMarkup createTrashActionsKeyboard(Long eventId) {
        // Валидация eventId
        if (eventId == null) {
            log.error("Попытка создать клавиатуру корзины с null eventId");
            throw new IllegalArgumentException("EventId не может быть null");
        }
        
        if (eventId <= 0) {
            log.error("Попытка создать клавиатуру корзины с некорректным eventId: {}", eventId);
            throw new IllegalArgumentException("EventId должен быть положительным числом, получено: " + eventId);
        }
        
        log.debug("Создание inline-клавиатуры для события в корзине ID={}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопки восстановления и удаления
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton restoreBtn = new InlineKeyboardButton("♻️ Восстановить");
        String restoreCallbackData = "trash_restore_" + eventId;
        restoreBtn.setCallbackData(restoreCallbackData);
        row1.add(restoreBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("❌ Удалить навсегда");
        String deleteCallbackData = "trash_delete_" + eventId;
        deleteBtn.setCallbackData(deleteCallbackData);
        row1.add(deleteBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-клавиатура для события в корзине ID={} создана: buttonCount={}, " +
                "restoreCallback='{}', deleteCallback='{}'", 
                eventId, row1.size(), restoreCallbackData, deleteCallbackData);
        
        return keyboard;
    }
}
