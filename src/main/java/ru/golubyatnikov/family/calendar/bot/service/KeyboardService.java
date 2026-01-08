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
     * @see #createDeleteConfirmationKeyboard(Long)
     */
    public InlineKeyboardMarkup createEventActionsKeyboard(Long eventId) {
        log.debug("Создание inline клавиатуры для события {}", eventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Кнопки редактирования и удаления
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        
        InlineKeyboardButton editBtn = new InlineKeyboardButton("✏️ Редактировать");
        editBtn.setCallbackData("edit_" + eventId);
        row1.add(editBtn);
        
        InlineKeyboardButton deleteBtn = new InlineKeyboardButton("🗑️ Удалить");
        deleteBtn.setCallbackData("delete_" + eventId);
        row1.add(deleteBtn);
        
        rows.add(row1);
        keyboard.setKeyboard(rows);
        
        log.debug("Inline клавиатура для события {} создана с {} кнопками", eventId, row1.size());
        
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
                
                // Если на этот день есть событие, добавляем инициал создателя в надстрочном формате
                if (firstEventByDate.containsKey(date)) {
                    Event event = firstEventByDate.get(date);
                    String creatorInitial = event.getUser().getFirstName()
                        .substring(0, 1).toUpperCase();
                    String superscriptInitial = toSuperscript(creatorInitial);
                    dayText = day + superscriptInitial;
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
}
