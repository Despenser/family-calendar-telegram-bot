package ru.golubyatnikov.family.calendar.bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.golubyatnikov.family.calendar.bot.model.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.Event;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис для компоновки сложных клавиатур (календари, выбор времени).
 * 
 * <p>KeyboardLayoutService предоставляет методы для создания сложных
 * многострочных клавиатур с динамическим содержимым.</p>
 * 
 * <p><b>Требования:</b> 1.1</p>
 * 
 * @author Family Calendar Bot Team
 * @since 2026-02-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeyboardLayoutService {

    private final EventRepository eventRepository;

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
    
    // Маппинг цифр на надстрочные Unicode символы
    private static final Map<Character, Character> SUPERSCRIPT_DIGITS = Map.of(
        '0', '⁰',
        '1', '¹',
        '2', '²',
        '3', '³',
        '4', '⁴',
        '5', '⁵',
        '6', '⁶',
        '7', '⁷',
        '8', '⁸',
        '9', '⁹'
    );

    /**
     * Создает inline-календарь для выбора даты события с учетом таймзоны пользователя.
     * 
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если month не в диапазоне 1-12
     */
    public InlineKeyboardMarkup createCalendarKeyboard(int year, int month, User user) {
        return createCalendarKeyboard(year, month, user, false, null);
    }

    /**
     * Создает inline-календарь для выбора даты события с учетом таймзоны пользователя.
     * 
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     * @param editingEventId ID редактируемого события (null для создания нового)
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если month не в диапазоне 1-12
     */
    public InlineKeyboardMarkup createCalendarKeyboard(int year, int month, User user, Long editingEventId) {
        return createCalendarKeyboard(year, month, user, false, editingEventId);
    }

    /**
     * Создает inline-календарь для просмотра событий с возможностью навигации в прошлое.
     * 
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если month не в диапазоне 1-12
     */
    public InlineKeyboardMarkup createViewCalendarKeyboard(int year, int month, User user) {
        return createCalendarKeyboard(year, month, user, true, null);
    }

    /**
     * Создает inline-календарь для выбора даты события с учетом таймзоны пользователя.
     * 
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь для определения timezone и семьи
     * @param allowPastDates разрешить выбор прошлых дат и навигацию в прошлое
     * @param editingEventId ID редактируемого события (null для создания нового)
     * @return настроенная InlineKeyboardMarkup с календарем
     * @throws IllegalArgumentException если month не в диапазоне 1-12
     */
    private InlineKeyboardMarkup createCalendarKeyboard(int year, int month, User user, boolean allowPastDates, Long editingEventId) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        
        Long familyId = user.getFamily() != null ? user.getFamily().getId() : null;
        ZoneId userZone = user.getZoneId();
        
        log.debug("Создание inline-календаря для {}-{:02d}, userId={}, timezone={}, familyId={}, allowPastDates={}, editingEventId={}", 
                year, month, user.getId(), user.getTimezone(), familyId, allowPastDates, editingEventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        YearMonth yearMonth = YearMonth.of(year, month);
        YearMonth currentYearMonth = YearMonth.now(userZone);
        LocalDate firstDay = yearMonth.atDay(1);
        int daysInMonth = yearMonth.lengthOfMonth();
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue();
        
        // Получаем события семьи за этот месяц
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        
        // Для календаря просмотра получаем активные и завершенные события, для создания - только активные
        List<Event> monthEvents;
        if (allowPastDates) {
            // Календарь просмотра - включаем завершенные события
            monthEvents = eventRepository
                .findByFamilyIdAndEventDateBetween(familyId, monthStart, monthEnd).stream()
                .filter(e -> e.getStatus() == Event.EventStatus.ACTIVE || e.getStatus() == Event.EventStatus.COMPLETED)
                .collect(Collectors.toList());
        } else {
            // Календарь создания - только активные события
            monthEvents = eventRepository
                .findByFamilyIdAndEventDateBetweenAndStatus(
                    familyId, monthStart, monthEnd, Event.EventStatus.ACTIVE);
        }
        
        // Группируем события по датам
        Map<LocalDate, Event> firstEventByDate = monthEvents.stream()
            .sorted(Comparator.comparing(Event::getEventTime))
            .collect(Collectors.toMap(
                Event::getEventDate,
                Function.identity(),
                (existing, replacement) -> existing,
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
        
        LocalDate today = user.getCurrentDate();
        
        log.debug("Текущая дата в timezone пользователя {}: {}", user.getTimezone(), today);
        
        // Подсчитываем количество событий для каждой даты
        Map<LocalDate, Long> eventCountByDate = monthEvents.stream()
            .collect(Collectors.groupingBy(Event::getEventDate, Collectors.counting()));
        
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = LocalDate.of(year, month, day);
            InlineKeyboardButton dayBtn;
            
            // Даты в прошлом отображаются как пустые ячейки (только если не разрешены прошлые даты)
            if (!allowPastDates && date.isBefore(today)) {
                dayBtn = new InlineKeyboardButton(" ");
                dayBtn.setCallbackData("calendar_ignore");
            } else {
                String dayText = String.valueOf(day);
                boolean isCurrentDay = date.equals(today);
                
                // Если на этот день есть событие, добавляем инициал создателя
                if (firstEventByDate.containsKey(date)) {
                    Event event = firstEventByDate.get(date);
                    String creatorInitial = event.getUser().getFirstName()
                        .substring(0, 1).toUpperCase();
                    String superscriptInitial = toSuperscript(creatorInitial);
                    
                    long eventCount = eventCountByDate.getOrDefault(date, 0L);
                    
                    // Для текущего дня добавляем квадратные скобки вокруг даты
                    if (isCurrentDay) {
                        dayText = "[" + day + "]";
                    }
                    
                    // Добавляем инициал после даты (или после скобки для текущего дня)
                    if (eventCount > 1) {
                        // Преобразуем число в надстрочный формат
                        String superscriptCount = toSuperscriptNumber(String.valueOf(eventCount));
                        dayText = dayText + superscriptInitial + superscriptCount;
                    } else {
                        dayText = dayText + superscriptInitial;
                    }
                } else {
                    // Для текущего дня без событий используем квадратные скобки
                    if (isCurrentDay) {
                        dayText = "[" + day + "]";
                    }
                    // Убираем точку - оставляем даты без дополнительных символов
                }
                
                dayBtn = new InlineKeyboardButton(dayText);
                // Используем единый префикс calendar_ для всех типов календарей
                dayBtn.setCallbackData(String.format("calendar_%d-%02d-%02d", year, month, day));
            }
            
            currentRow.add(dayBtn);
            
            // Переход на новую строку после воскресенья
            if ((firstDayOfWeek + day - 1) % 7 == 0) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
            }
        }
        
        // Добавляем последнюю строку
        if (!currentRow.isEmpty()) {
            while (currentRow.size() < 7) {
                InlineKeyboardButton emptyBtn = new InlineKeyboardButton(" ");
                emptyBtn.setCallbackData("calendar_ignore");
                currentRow.add(emptyBtn);
            }
            rows.add(currentRow);
        }
        
        // Кнопки навигации
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        
        YearMonth prevMonth = yearMonth.minusMonths(1);
        // Для календаря просмотра разрешаем навигацию в прошлое
        if (!allowPastDates && prevMonth.isBefore(currentYearMonth)) {
            InlineKeyboardButton disabledBtn = new InlineKeyboardButton("   ");
            disabledBtn.setCallbackData("calendar_ignore");
            navigationRow.add(disabledBtn);
        } else {
            InlineKeyboardButton prevBtn = new InlineKeyboardButton("⬅️");
            // Используем единый префикс calendar_ для всех типов календарей
            prevBtn.setCallbackData(String.format("calendar_%d-%02d", 
                prevMonth.getYear(), prevMonth.getMonthValue()));
            navigationRow.add(prevBtn);
        }
        
        InlineKeyboardButton todayBtn = new InlineKeyboardButton();
        
        if (editingEventId != null) {
            // При редактировании - кнопка "Назад"
            todayBtn.setText("🔙 Назад");
            todayBtn.setCallbackData(CallbackPrefix.EDIT_BACK.withPayload(editingEventId.toString()));
        } else {
            // При создании или просмотре - кнопка "Сегодня"
            todayBtn.setText("Сегодня");
            todayBtn.setCallbackData(String.format("calendar_%d-%02d", 
                currentYearMonth.getYear(), currentYearMonth.getMonthValue()));
        }
        
        navigationRow.add(todayBtn);
        
        InlineKeyboardButton nextBtn = new InlineKeyboardButton("➡️");
        YearMonth nextMonth = yearMonth.plusMonths(1);
        // Используем единый префикс calendar_ для всех типов календарей
        nextBtn.setCallbackData(String.format("calendar_%d-%02d", 
            nextMonth.getYear(), nextMonth.getMonthValue()));
        navigationRow.add(nextBtn);
        
        rows.add(navigationRow);
        
        // Добавляем кнопку "Отмена" в отдельный ряд только при создании нового события
        if (editingEventId == null && !allowPastDates) {
            List<InlineKeyboardButton> cancelRow = new ArrayList<>();
            InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
            cancelBtn.setCallbackData("calendar_cancel");
            cancelRow.add(cancelBtn);
            rows.add(cancelRow);
        }
        
        keyboard.setKeyboard(rows);
        
        log.debug("Inline-календарь для {}-{:02d} создан с {} рядами", year, month, rows.size());
        
        return keyboard;
    }

    /**
     * Создает inline-клавиатуру для выбора часа события.
     * 
     * @return настроенная InlineKeyboardMarkup
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
     * @param selectedHour выбранный час (0-23)
     * @return настроенная InlineKeyboardMarkup
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
        
        InlineKeyboardButton backBtn = new InlineKeyboardButton("🔙 Назад");
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
     * Создает inline-клавиатуру для выбора часа с фильтрацией прошедших часов.
     * 
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createFilteredHourSelectionKeyboard(LocalDate selectedDate, User user) {
        return createFilteredHourSelectionKeyboard(selectedDate, user, null);
    }

    /**
     * Создает inline-клавиатуру для выбора часа с фильтрацией прошедших часов.
     * 
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @param editingEventId ID редактируемого события (null для создания нового)
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createFilteredHourSelectionKeyboard(LocalDate selectedDate, User user, Long editingEventId) {
        if (selectedDate == null) {
            log.error("Попытка создать фильтрованную клавиатуру часов с null selectedDate");
            throw new IllegalArgumentException("Selected date cannot be null");
        }
        if (user == null) {
            log.error("Попытка создать фильтрованную клавиатуру часов с null user");
            throw new IllegalArgumentException("User cannot be null");
        }
        
        log.debug("Создание фильтрованной inline-клавиатуры для выбора часа: date={}, userId={}, timezone={}, editingEventId={}", 
                selectedDate, user.getId(), user.getTimezone(), editingEventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Заголовок
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        InlineKeyboardButton headerBtn = new InlineKeyboardButton("Выберите час:");
        headerBtn.setCallbackData("time_ignore");
        headerRow.add(headerBtn);
        rows.add(headerRow);
        
        // Получаем доступные часы
        List<Integer> availableHours = getAvailableHours(selectedDate, user);
        
        log.debug("Доступно {} часов для даты {}", availableHours.size(), selectedDate);
        
        // Создаем кнопки (по 4 в ряд)
        if (!availableHours.isEmpty()) {
            List<InlineKeyboardButton> currentRow = new ArrayList<>();
            for (int i = 0; i < availableHours.size(); i++) {
                int hour = availableHours.get(i);
                InlineKeyboardButton hourBtn = new InlineKeyboardButton(
                    String.format("%02d:00", hour));
                hourBtn.setCallbackData(String.format("hour_%02d", hour));
                currentRow.add(hourBtn);
                
                if ((i + 1) % 4 == 0 || i == availableHours.size() - 1) {
                    rows.add(new ArrayList<>(currentRow));
                    currentRow.clear();
                }
            }
        }
        
        // Кнопка отмены или возврата
        List<InlineKeyboardButton> cancelRow = new ArrayList<>();
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        
        if (editingEventId != null) {
            // При редактировании - кнопка "Назад"
            cancelBtn.setText("🔙 Назад");
            cancelBtn.setCallbackData(CallbackPrefix.EDIT_BACK.withPayload(editingEventId.toString()));
        } else {
            // При создании - кнопка "Отмена"
            cancelBtn.setText("❌ Отмена");
            cancelBtn.setCallbackData("time_cancel");
        }
        
        cancelRow.add(cancelBtn);
        rows.add(cancelRow);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Фильтрованная inline-клавиатура для выбора часа создана с {} рядами", rows.size());
        
        return keyboard;
    }

    /**
     * Создает inline-клавиатуру для выбора минут с фильтрацией прошедших минут.
     * 
     * @param selectedHour выбранный час (0-23)
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createFilteredMinuteSelectionKeyboard(int selectedHour, LocalDate selectedDate, User user) {
        return createFilteredMinuteSelectionKeyboard(selectedHour, selectedDate, user, null);
    }

    /**
     * Создает inline-клавиатуру для выбора минут с фильтрацией прошедших минут.
     * 
     * @param selectedHour выбранный час (0-23)
     * @param selectedDate выбранная дата события
     * @param user пользователь (для определения timezone)
     * @param editingEventId ID редактируемого события (null для создания нового)
     * @return настроенная InlineKeyboardMarkup
     * @throws IllegalArgumentException если параметры некорректны
     */
    public InlineKeyboardMarkup createFilteredMinuteSelectionKeyboard(int selectedHour, LocalDate selectedDate, User user, Long editingEventId) {
        if (selectedHour < 0 || selectedHour > 23) {
            log.error("Попытка создать фильтрованную клавиатуру минут с некорректным selectedHour: {}", selectedHour);
            throw new IllegalArgumentException("Selected hour must be between 0 and 23");
        }
        if (selectedDate == null) {
            log.error("Попытка создать фильтрованную клавиатуру минут с null selectedDate");
            throw new IllegalArgumentException("Selected date cannot be null");
        }
        if (user == null) {
            log.error("Попытка создать фильтрованную клавиатуру минут с null user");
            throw new IllegalArgumentException("User cannot be null");
        }
        
        log.debug("Создание фильтрованной inline-клавиатуры для выбора минут: hour={}, date={}, userId={}, timezone={}, editingEventId={}", 
                selectedHour, selectedDate, user.getId(), user.getTimezone(), editingEventId);
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        // Заголовок
        List<InlineKeyboardButton> headerRow = new ArrayList<>();
        InlineKeyboardButton headerBtn = new InlineKeyboardButton(
            String.format("Выберите минуты (час: %02d):", selectedHour));
        headerBtn.setCallbackData("time_ignore");
        headerRow.add(headerBtn);
        rows.add(headerRow);
        
        // Получаем доступные минуты
        List<Integer> availableMinutes = getAvailableMinutes(selectedHour, selectedDate, user);
        
        log.debug("Доступно {} интервалов минут для часа {} даты {}", 
                availableMinutes.size(), selectedHour, selectedDate);
        
        // Создаем кнопки
        if (!availableMinutes.isEmpty()) {
            List<InlineKeyboardButton> minutesRow = new ArrayList<>();
            for (int minute : availableMinutes) {
                InlineKeyboardButton minuteBtn = new InlineKeyboardButton(
                    String.format("%02d:%02d", selectedHour, minute));
                minuteBtn.setCallbackData(String.format("time_%02d:%02d", selectedHour, minute));
                minutesRow.add(minuteBtn);
            }
            rows.add(minutesRow);
        }
        
        // Кнопки навигации
        List<InlineKeyboardButton> navigationRow = new ArrayList<>();
        
        InlineKeyboardButton backBtn = new InlineKeyboardButton("🔙 Назад");
        backBtn.setCallbackData("time_back");
        navigationRow.add(backBtn);
        
        if (editingEventId == null) {
            // При создании - кнопка "Отмена"
            InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Отмена");
            cancelBtn.setCallbackData("time_cancel");
            navigationRow.add(cancelBtn);
        }
        
        rows.add(navigationRow);
        
        keyboard.setKeyboard(rows);
        
        log.debug("Фильтрованная inline-клавиатура для выбора минут создана с {} рядами", rows.size());
        
        return keyboard;
    }

    /**
     * Определяет доступные часы для выбора на основе текущего времени пользователя.
     * 
     * @param selectedDate выбранная дата
     * @param user пользователь
     * @return список доступных часов (0-23)
     */
    private List<Integer> getAvailableHours(LocalDate selectedDate, User user) {
        if (selectedDate == null || user == null) {
            throw new IllegalArgumentException("Selected date and user cannot be null");
        }
        
        LocalDate today = user.getCurrentDate();
        
        // Для будущих дат все часы доступны
        if (selectedDate.isAfter(today)) {
            log.debug("Выбрана будущая дата {}, все 24 часа доступны", selectedDate);
            return java.util.stream.IntStream.range(0, 24)
                    .boxed()
                    .collect(Collectors.toList());
        }
        
        // Для сегодняшнего дня фильтруем прошедшие часы
        if (selectedDate.equals(today)) {
            var currentDateTime = user.getCurrentDateTime();
            int currentHour = currentDateTime.getHour();
            int currentMinute = currentDateTime.getMinute();
            
            if (currentHour == 23 && currentMinute >= 46) {
                log.debug("Текущее время {}:{} >= 23:46, нет доступных часов для сегодня", 
                        currentHour, currentMinute);
                return Collections.emptyList();
            }
            
            List<Integer> availableHours = java.util.stream.IntStream.rangeClosed(currentHour, 23)
                    .boxed()
                    .collect(Collectors.toList());
            
            log.debug("Выбрана сегодняшняя дата {}, текущее время {}:{}, доступно {} часов", 
                    selectedDate, currentHour, currentMinute, availableHours.size());
            
            return availableHours;
        }
        
        log.warn("Попытка получить доступные часы для прошлой даты {}", selectedDate);
        return Collections.emptyList();
    }
    
    /**
     * Определяет доступные минутные интервалы для выбора.
     * 
     * @param selectedHour выбранный час (0-23)
     * @param selectedDate выбранная дата
     * @param user пользователь
     * @return список доступных минут (0, 15, 30, 45)
     */
    private List<Integer> getAvailableMinutes(int selectedHour, LocalDate selectedDate, User user) {
        if (selectedHour < 0 || selectedHour > 23 || selectedDate == null || user == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        List<Integer> allMinutes = List.of(0, 15, 30, 45);
        
        LocalDate today = user.getCurrentDate();
        
        // Для будущих дат все интервалы доступны
        if (selectedDate.isAfter(today)) {
            log.debug("Выбрана будущая дата {}, все 4 интервала минут доступны", selectedDate);
            return allMinutes;
        }
        
        // Для сегодняшнего дня проверяем текущий час
        if (selectedDate.equals(today)) {
            var currentDateTime = user.getCurrentDateTime();
            int currentHour = currentDateTime.getHour();
            int currentMinute = currentDateTime.getMinute();
            
            // Если выбран будущий час, все интервалы доступны
            if (selectedHour > currentHour) {
                log.debug("Выбран будущий час {} (текущий {}), все 4 интервала минут доступны", 
                        selectedHour, currentHour);
                return allMinutes;
            }
            
            // Если выбран текущий час, фильтруем прошедшие интервалы
            if (selectedHour == currentHour) {
                if (currentMinute >= 46) {
                    log.debug("Текущее время {}:{} >= XX:46, нет доступных интервалов минут", 
                            currentHour, currentMinute);
                    return Collections.emptyList();
                }
                
                List<Integer> availableMinutes = allMinutes.stream()
                        .filter(minute -> minute > currentMinute)
                        .collect(Collectors.toList());
                
                log.debug("Выбран текущий час {}, текущие минуты {}, доступно {} интервалов", 
                        selectedHour, currentMinute, availableMinutes.size());
                
                return availableMinutes;
            }
            
            log.warn("Попытка получить доступные минуты для прошлого часа {} (текущий {})", 
                    selectedHour, currentHour);
            return Collections.emptyList();
        }
        
        log.warn("Попытка получить доступные минуты для прошлой даты {}", selectedDate);
        return Collections.emptyList();
    }
    
    /**
     * Преобразует строку в надстрочный формат используя Unicode символы.
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
     * Преобразует число в надстрочный формат используя Unicode символы.
     * 
     * @param number число в виде строки для преобразования
     * @return число с надстрочными цифрами
     */
    private String toSuperscriptNumber(String number) {
        if (number == null || number.isEmpty()) {
            return number;
        }
        
        StringBuilder result = new StringBuilder();
        for (char c : number.toCharArray()) {
            result.append(SUPERSCRIPT_DIGITS.getOrDefault(c, c));
        }
        
        return result.toString();
    }
}
