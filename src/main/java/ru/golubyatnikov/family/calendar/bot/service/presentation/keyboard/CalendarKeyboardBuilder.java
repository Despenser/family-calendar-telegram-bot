package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import ru.golubyatnikov.family.calendar.bot.model.context.CalendarContext;
import ru.golubyatnikov.family.calendar.bot.model.entity.Event;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.model.enums.CallbackPrefix;
import ru.golubyatnikov.family.calendar.bot.model.enums.EventStatus;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Actions.CANCEL;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.BACK;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.ARROW_LEFT;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.ARROW_RIGHT;

/**
 * Компонент для построения календарных клавиатур.
 * Отвечает за создание inline-клавиатур с календарем для выбора даты.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CalendarKeyboardBuilder {

    private static final int DAYS_IN_WEEK = 7;
    private static final String[] WEEK_DAYS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    private final EventRepository eventRepository;
    private final DateTimeFormattingService dateTimeFormattingService;
    private final SuperscriptConverter superscriptConverter;
    private final KeyboardFactory keyboardFactory;

    /**
     * Создает календарь для создания нового события.
     * Прошлые даты недоступны для выбора.
     *
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь, для которого создается календарь
     *
     * @return разметка inline-клавиатуры с календарем
     */
    public InlineKeyboardMarkup createForNewEvent(int year, int month, User user) {
        return createCalendar(year, month, user, false, null, true);
    }

    /**
     * Создает календарь для редактирования существующего события.
     * Прошлые даты недоступны для выбора.
     *
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь, для которого создается календарь
     * @param eventId ID редактируемого события
     *
     * @return разметка inline-клавиатуры с календарем
     */
    public InlineKeyboardMarkup createForEventEdit(int year, int month, User user, Long eventId) {
        return createCalendar(year, month, user, false, eventId, false);
    }

    /**
     * Создает календарь для просмотра событий.
     * Доступна навигация по прошлым датам, отображаются завершенные события.
     *
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь, для которого создается календарь
     *
     * @return разметка inline-клавиатуры с календарем
     */
    public InlineKeyboardMarkup createForEventView(int year, int month, User user) {
        return createCalendar(year, month, user, true, null, false);
    }
    /**
     * Создает календарь для создания или редактирования события с учетом флага isFromAddEventCommand.
     *
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь
     * @param editingEventId ID редактируемого события (null для создания нового)
     * @param isFromAddEventCommand true если создание началось из команды /add_event
     *
     * @return разметка inline-клавиатуры с календарем
     */
    public InlineKeyboardMarkup createForEventCreationOrEdit(int year, int month, User user,
                                                             Long editingEventId, boolean isFromAddEventCommand) {
        return createCalendar(year, month, user, false, editingEventId, isFromAddEventCommand);
    }

    /**
     * Основной метод создания календаря.
     * Создает полную структуру календаря с заголовком, днями недели, датами и навигацией.
     *
     * @param year год для отображения
     * @param month месяц для отображения (1-12)
     * @param user пользователь, для которого создается календарь
     * @param allowPastDates разрешить ли выбор прошлых дат
     * @param editingEventId ID редактируемого события (null для создания нового)
     * @param isFromAddEventCommand true если календарь вызван из команды /add_event
     *
     * @return разметка inline-клавиатуры с календарем
     */
    private InlineKeyboardMarkup createCalendar(int year, int month, @NonNull User user,
                                                boolean allowPastDates, Long editingEventId,
                                                boolean isFromAddEventCommand) {

        validateMonth(month);

        YearMonth yearMonth = YearMonth.of(year, month);
        CalendarContext context = new CalendarContext(yearMonth, user, allowPastDates, 
                                                     editingEventId, isFromAddEventCommand);
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(createHeaderRow(context));
        rows.add(createWeekDaysRow());
        rows.addAll(createDaysRows(context));
        rows.add(createNavigationRow(context));
        
        if (shouldShowCancelButton(context)) {
            rows.add(createCancelRow());
        }

        return keyboardFactory.createMarkup(rows);
    }

    /**
     * Валидирует номер месяца.
     *
     * @param month номер месяца (должен быть от 1 до 12)
     * @throws IllegalArgumentException если месяц вне допустимого диапазона
     */
    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
    }

    /**
     * Создает строку заголовка календаря с названием месяца и года.
     *
     * @param context контекст календаря
     * @return строка клавиатуры с заголовком
     */
    private InlineKeyboardRow createHeaderRow(@NonNull CalendarContext context) {
        return keyboardFactory.createRow(
            keyboardFactory.createButton(
                dateTimeFormattingService.formatMonth(context.yearMonth().atDay(1)),
                CallbackPrefix.CALENDAR_IGNORE.withPayload("")
            )
        );
    }

    /**
     * Создает строку с названиями дней недели (Пн, Вт, Ср, Чт, Пт, Сб, Вс).
     *
     * @return строка клавиатуры с днями недели
     */
    private InlineKeyboardRow createWeekDaysRow() {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        for (String day : WEEK_DAYS) {
            buttons.add(keyboardFactory.createButton(day, CallbackPrefix.CALENDAR_IGNORE.withPayload("")));
        }
        return keyboardFactory.createRow(buttons);
    }

    /**
     * Создает строки с датами месяца.
     * Каждая дата отображается с индикаторами событий (инициал создателя и счетчик).
     * Прошлые даты могут быть скрыты в зависимости от контекста.
     *
     * @param context контекст календаря
     * @return список строк клавиатуры с датами
     */
    private @NonNull List<InlineKeyboardRow> createDaysRows(@NonNull CalendarContext context) {
        Map<LocalDate, Event> eventsByDate = loadEventsForMonth(context);
        Map<LocalDate, Long> eventCountByDate = countEventsByDate(eventsByDate);
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        
        // Пустые ячейки до первого дня месяца
        int firstDayOfWeek = context.firstDay().getDayOfWeek().getValue();
        for (int i = 1; i < firstDayOfWeek; i++) {
            currentRow.add(createEmptyButton());
        }
        
        // Дни месяца
        for (int day = 1; day <= context.daysInMonth(); day++) {
            LocalDate date = LocalDate.of(context.year(), context.month(), day);
            currentRow.add(createDayButton(date, context, eventsByDate, eventCountByDate));
            
            // Переход на новую строку после воскресенья
            if ((firstDayOfWeek + day - 1) % DAYS_IN_WEEK == 0) {
                rows.add(keyboardFactory.createRow(new ArrayList<>(currentRow)));
                currentRow.clear();
            }
        }
        
        // Заполняем последнюю строку пустыми ячейками
        if (!currentRow.isEmpty()) {
            while (currentRow.size() < DAYS_IN_WEEK) {
                currentRow.add(createEmptyButton());
            }
            rows.add(keyboardFactory.createRow(currentRow));
        }
        
        return rows;
    }

    /**
     * Загружает события для отображаемого месяца.
     * В режиме просмотра включает завершенные события, в режиме создания - только активные.
     *
     * @param context контекст календаря
     * @return карта событий по датам (первое событие дня по времени)
     */
    private Map<LocalDate, Event> loadEventsForMonth(@NonNull CalendarContext context) {
        Long familyId = context.user().getFamily() != null ? context.user().getFamily().getId() : null;
        LocalDate monthStart = context.yearMonth().atDay(1);
        LocalDate monthEnd = context.yearMonth().atEndOfMonth();
        
        List<Event> monthEvents;
        if (context.isViewingCalendar()) {
            // Календарь просмотра - включаем завершенные события
            monthEvents = eventRepository
                .findByFamilyIdAndEventDateBetween(familyId, monthStart, monthEnd).stream()
                .filter(e -> e.getStatus() == EventStatus.ACTIVE || e.getStatus() == EventStatus.COMPLETED)
                .collect(Collectors.toList());
        } else {
            // Календарь создания - только активные события
            monthEvents = eventRepository
                .findByFamilyIdAndEventDateBetweenAndStatus(
                    familyId, monthStart, monthEnd, EventStatus.ACTIVE);
        }
        
        // Группируем события по датам (берем первое по времени)
        return monthEvents.stream()
            .sorted(Comparator.comparing(Event::getEventTime))
            .collect(Collectors.toMap(
                Event::getEventDate,
                Function.identity(),
                (existing, replacement) -> existing,
                LinkedHashMap::new
            ));
    }

    /**
     * Подсчитывает количество событий для каждой даты.
     *
     * @param eventsByDate карта событий по датам
     * @return карта с количеством событий для каждой даты
     */
    private Map<LocalDate, Long> countEventsByDate(@NonNull Map<LocalDate, Event> eventsByDate) {
        return eventsByDate.values().stream()
            .collect(Collectors.groupingBy(Event::getEventDate, Collectors.counting()));
    }

    /**
     * Создает пустую кнопку для заполнения календарной сетки.
     *
     * @return пустая кнопка
     */
    private InlineKeyboardButton createEmptyButton() {
        return keyboardFactory.createButton(" ", CallbackPrefix.CALENDAR_IGNORE.withPayload(""));
    }

    /**
     * Создает кнопку для отображения дня в календаре.
     * Прошлые даты могут быть скрыты в зависимости от контекста.
     *
     * @param date дата для кнопки
     * @param context контекст календаря
     * @param eventsByDate карта событий по датам
     * @param eventCountByDate карта с количеством событий по датам
     *
     * @return кнопка с датой или пустая кнопка
     */
    private InlineKeyboardButton createDayButton(LocalDate date, @NonNull CalendarContext context,
                                                  Map<LocalDate, Event> eventsByDate,
                                                  Map<LocalDate, Long> eventCountByDate) {

        // Прошлые даты отображаются как пустые (если не разрешены)
        if (!context.allowPastDates() && date.isBefore(context.today())) {
            return createEmptyButton();
        }
        
        String dayText = buildDayText(date, context, eventsByDate, eventCountByDate);
        
        String prefix = context.isViewingCalendar() ? CallbackPrefix.CALENDAR.getPrefix() : CallbackPrefix.DATE.getPrefix();
        String callbackData = String.format("%s%d-%02d-%02d", 
            prefix, date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        
        return keyboardFactory.createButton(dayText, callbackData);
    }

    /**
     * Формирует текст для кнопки дня.
     * Добавляет квадратные скобки для текущего дня и индикаторы событий
     * (инициал создателя в верхнем индексе и счетчик событий).
     *
     * @param date дата
     * @param context контекст календаря
     * @param eventsByDate карта событий по датам
     * @param eventCountByDate карта с количеством событий по датам
     *
     * @return отформатированный текст для кнопки
     */
    private String buildDayText(@NonNull LocalDate date,
                                @NonNull CalendarContext context,
                                Map<LocalDate, Event> eventsByDate,
                                Map<LocalDate, Long> eventCountByDate) {

        int day = date.getDayOfMonth();
        boolean isToday = date.equals(context.today());
        
        String dayText = String.valueOf(day);
        
        // Добавляем квадратные скобки для текущего дня
        if (isToday) {
            dayText = "[" + day + "]";
        }
        
        // Добавляем инициал создателя события и счетчик
        if (eventsByDate.containsKey(date)) {
            Event event = eventsByDate.get(date);
            String creatorInitial = event.getUser().getFirstName().substring(0, 1).toUpperCase();
            String superscriptInitial = superscriptConverter.toSuperscript(creatorInitial);
            
            long eventCount = eventCountByDate.getOrDefault(date, 0L);
            
            if (eventCount > 1) {
                String superscriptCount = superscriptConverter.toSuperscriptNumber(String.valueOf(eventCount));
                dayText = dayText + superscriptInitial + superscriptCount;
            } else {
                dayText = dayText + superscriptInitial;
            }
        }
        
        return dayText;
    }

    /**
     * Создает строку навигации с кнопками перехода между месяцами.
     * Включает кнопки "Предыдущий месяц", "Сегодня"/"Назад" и "Следующий месяц".
     *
     * @param context контекст календаря
     * @return строка клавиатуры с навигацией
     */
    private InlineKeyboardRow createNavigationRow(@NonNull CalendarContext context) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        
        // Кнопка "Назад" (предыдущий месяц)
        YearMonth prevMonth = context.yearMonth().minusMonths(1);
        if (!context.allowPastDates() && prevMonth.isBefore(context.currentYearMonth())) {
            buttons.add(createDisabledNavigationButton());
        } else {
            buttons.add(createPrevMonthButton(prevMonth));
        }
        
        // Кнопка "Сегодня" или "Назад к событию"
        buttons.add(createCenterButton(context));
        
        // Кнопка "Вперед" (следующий месяц)
        buttons.add(createNextMonthButton(context.yearMonth().plusMonths(1)));
        
        return keyboardFactory.createRow(buttons);
    }

    /**
     * Создает неактивную кнопку навигации (заглушку).
     *
     * @return неактивная кнопка
     */
    private InlineKeyboardButton createDisabledNavigationButton() {
        return keyboardFactory.createButton("   ", CallbackPrefix.CALENDAR_IGNORE.withPayload(""));
    }

    /**
     * Создает кнопку перехода к предыдущему месяцу.
     *
     * @param month месяц для перехода
     * @return кнопка "Назад"
     */
    private InlineKeyboardButton createPrevMonthButton(@NonNull YearMonth month) {
        return keyboardFactory.createButton(ARROW_LEFT, 
            CallbackPrefix.CALENDAR.withPayload(String.format("%d-%02d", month.getYear(), month.getMonthValue())));
    }

    /**
     * Создает кнопку перехода к следующему месяцу.
     *
     * @param month месяц для перехода
     * @return кнопка "Вперед"
     */
    private InlineKeyboardButton createNextMonthButton(@NonNull YearMonth month) {
        return keyboardFactory.createButton(ARROW_RIGHT, 
            CallbackPrefix.CALENDAR.withPayload(String.format("%d-%02d", month.getYear(), month.getMonthValue())));
    }

    /**
     * Создает центральную кнопку навигации.
     * В режиме редактирования - кнопка "Назад к событию",
     * в остальных режимах - кнопка "Сегодня".
     *
     * @param context контекст календаря
     * @return центральная кнопка навигации
     */
    private InlineKeyboardButton createCenterButton(@NonNull CalendarContext context) {
        if (context.isEditingEvent()) {
            // При редактировании - кнопка "Назад"
            return keyboardFactory.createButton(BACK + " Назад", 
                CallbackPrefix.EDIT_BACK.withPayload(context.editingEventId().toString()));

        } else {
            // При создании или просмотре - кнопка "Сегодня"
            return keyboardFactory.createButton("Сегодня", 
                CallbackPrefix.CALENDAR.withPayload(String.format("%d-%02d", 
                    context.currentYearMonth().getYear(), context.currentYearMonth().getMonthValue())));
        }
    }

    /**
     * Определяет, нужно ли отображать кнопку "Отмена".
     * Кнопка отображается только при создании нового события.
     *
     * @param context контекст календаря
     * @return true, если нужно показать кнопку отмены
     */
    private boolean shouldShowCancelButton(@NonNull CalendarContext context) {
        return context.isCreatingEvent();
    }

    /**
     * Создает строку с кнопкой "Отмена".
     *
     * @return строка клавиатуры с кнопкой отмены
     */
    private InlineKeyboardRow createCancelRow() {
        return keyboardFactory.createRow(
            keyboardFactory.createButton(CANCEL + " Отменить создание", CallbackPrefix.CALENDAR_CANCEL.withPayload(""))
        );
    }
}
