package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
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

/**
 * TODO пробежаться свежим взглядом но как будто бы нужен рефакторинг
 * Компонент для построения календарных клавиатур.
 * Отвечает за создание inline-клавиатур с календарем для выбора даты.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CalendarKeyboardBuilder {

    private static final String CALENDAR_PREFIX = "calendar_";
    private static final String IGNORE_CALLBACK = "calendar_ignore";
    private static final String CANCEL_CALLBACK = "calendar_cancel";
    private static final int DAYS_IN_WEEK = 7;
    private static final String[] WEEK_DAYS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    private final EventRepository eventRepository;
    private final DateTimeFormattingService dateTimeFormattingService;
    private final SuperscriptConverter superscriptConverter;
    private final KeyboardFactory keyboardFactory;

    /**
     * Создает календарь для создания нового события.
     */
    public InlineKeyboardMarkup createForNewEvent(int year, int month, User user) {
        return createCalendar(year, month, user, false, null);
    }

    /**
     * Создает календарь для редактирования существующего события.
     */
    public InlineKeyboardMarkup createForEventEdit(int year, int month, User user, Long eventId) {
        return createCalendar(year, month, user, false, eventId);
    }

    /**
     * Создает календарь для просмотра событий (с возможностью навигации в прошлое).
     */
    public InlineKeyboardMarkup createForEventView(int year, int month, User user) {
        return createCalendar(year, month, user, true, null);
    }

    /**
     * Основной метод создания календаря.
     */
    private InlineKeyboardMarkup createCalendar(int year, int month, @NonNull User user,
                                                boolean allowPastDates, Long editingEventId) {

        validateMonth(month);

        YearMonth yearMonth = YearMonth.of(year, month);
        CalendarContext context = new CalendarContext(yearMonth, user, allowPastDates, editingEventId);
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(createHeaderRow(context));
        rows.add(createWeekDaysRow());
        rows.addAll(createDaysRows(context));
        rows.add(createNavigationRow(context));
        
        if (shouldShowCancelButton(context)) {
            rows.add(createCancelRow());
        }

        log.debug("Календарь создан с {} рядами", rows.size());
        return keyboardFactory.createMarkup(rows);
    }

    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
    }

    private InlineKeyboardRow createHeaderRow(@NonNull CalendarContext context) {
        return keyboardFactory.createRow(
            keyboardFactory.createButton(
                dateTimeFormattingService.formatMonth(context.yearMonth.atDay(1)),
                IGNORE_CALLBACK
            )
        );
    }

    private InlineKeyboardRow createWeekDaysRow() {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        for (String day : WEEK_DAYS) {
            buttons.add(keyboardFactory.createButton(day, IGNORE_CALLBACK));
        }
        return keyboardFactory.createRow(buttons);
    }

    private @NonNull List<InlineKeyboardRow> createDaysRows(@NonNull CalendarContext context) {
        Map<LocalDate, Event> eventsByDate = loadEventsForMonth(context);
        Map<LocalDate, Long> eventCountByDate = countEventsByDate(eventsByDate);
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();
        
        // Пустые ячейки до первого дня месяца
        int firstDayOfWeek = context.firstDay.getDayOfWeek().getValue();
        for (int i = 1; i < firstDayOfWeek; i++) {
            currentRow.add(createEmptyButton());
        }
        
        // Дни месяца
        for (int day = 1; day <= context.daysInMonth; day++) {
            LocalDate date = LocalDate.of(context.year, context.month, day);
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

    private Map<LocalDate, Event> loadEventsForMonth(@NonNull CalendarContext context) {
        Long familyId = context.user.getFamily() != null ? context.user.getFamily().getId() : null;
        LocalDate monthStart = context.yearMonth.atDay(1);
        LocalDate monthEnd = context.yearMonth.atEndOfMonth();
        
        List<Event> monthEvents;
        if (context.allowPastDates) {
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

    private Map<LocalDate, Long> countEventsByDate(@NonNull Map<LocalDate, Event> eventsByDate) {
        return eventsByDate.values().stream()
            .collect(Collectors.groupingBy(Event::getEventDate, Collectors.counting()));
    }

    private InlineKeyboardButton createEmptyButton() {
        return keyboardFactory.createButton(" ", IGNORE_CALLBACK);
    }

    private InlineKeyboardButton createDayButton(LocalDate date, @NonNull CalendarContext context,
                                                  Map<LocalDate, Event> eventsByDate,
                                                  Map<LocalDate, Long> eventCountByDate) {
        // Прошлые даты отображаются как пустые (если не разрешены)
        if (!context.allowPastDates && date.isBefore(context.today)) {
            return createEmptyButton();
        }
        
        String dayText = buildDayText(date, context, eventsByDate, eventCountByDate);
        String callbackData = String.format("%s%d-%02d-%02d", 
            CALENDAR_PREFIX, date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        
        return keyboardFactory.createButton(dayText, callbackData);
    }

    private String buildDayText(@NonNull LocalDate date,
                                @NonNull CalendarContext context,
                                Map<LocalDate, Event> eventsByDate,
                                Map<LocalDate, Long> eventCountByDate) {

        int day = date.getDayOfMonth();
        boolean isToday = date.equals(context.today);
        
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

    private InlineKeyboardRow createNavigationRow(@NonNull CalendarContext context) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        
        // Кнопка "Назад" (предыдущий месяц)
        YearMonth prevMonth = context.yearMonth.minusMonths(1);
        if (!context.allowPastDates && prevMonth.isBefore(context.currentYearMonth)) {
            buttons.add(createDisabledNavigationButton());
        } else {
            buttons.add(createPrevMonthButton(prevMonth));
        }
        
        // Кнопка "Сегодня" или "Назад к событию"
        buttons.add(createCenterButton(context));
        
        // Кнопка "Вперед" (следующий месяц)
        buttons.add(createNextMonthButton(context.yearMonth.plusMonths(1)));
        
        return keyboardFactory.createRow(buttons);
    }

    private InlineKeyboardButton createDisabledNavigationButton() {
        return keyboardFactory.createButton("   ", IGNORE_CALLBACK);
    }

    private InlineKeyboardButton createPrevMonthButton(@NonNull YearMonth month) {
        return keyboardFactory.createButton("⬅️", 
            String.format("%s%d-%02d", CALENDAR_PREFIX, month.getYear(), month.getMonthValue()));
    }

    private InlineKeyboardButton createNextMonthButton(@NonNull YearMonth month) {
        return keyboardFactory.createButton("➡️", 
            String.format("%s%d-%02d", CALENDAR_PREFIX, month.getYear(), month.getMonthValue()));
    }

    private InlineKeyboardButton createCenterButton(@NonNull CalendarContext context) {
        if (context.editingEventId != null) {
            // При редактировании - кнопка "Назад"
            return keyboardFactory.createButton("🔙 Назад", 
                CallbackPrefix.EDIT_BACK.withPayload(context.editingEventId.toString()));
        } else {
            // При создании или просмотре - кнопка "Сегодня"
            return keyboardFactory.createButton("Сегодня", 
                String.format("%s%d-%02d", CALENDAR_PREFIX, 
                    context.currentYearMonth.getYear(), context.currentYearMonth.getMonthValue()));
        }
    }

    private boolean shouldShowCancelButton(@NonNull CalendarContext context) {
        return context.editingEventId == null && !context.allowPastDates;
    }

    private InlineKeyboardRow createCancelRow() {
        return keyboardFactory.createRow(
            keyboardFactory.createButton("🔙 Назад", CANCEL_CALLBACK)
        );
    }

    /**
     * TODO вынести в model.context
     * Контекст для построения календаря.
     */
    private static class CalendarContext {
        final YearMonth yearMonth;
        final YearMonth currentYearMonth;
        final User user;
        final boolean allowPastDates;
        final Long editingEventId;
        final LocalDate today;
        final LocalDate firstDay;
        final int year;
        final int month;
        final int daysInMonth;

        CalendarContext(YearMonth yearMonth, User user, boolean allowPastDates, Long editingEventId) {
            this.yearMonth = yearMonth;
            this.user = user;
            this.allowPastDates = allowPastDates;
            this.editingEventId = editingEventId;
            this.today = user.getCurrentDate();
            this.currentYearMonth = YearMonth.now(user.getZoneId());
            this.firstDay = yearMonth.atDay(1);
            this.year = yearMonth.getYear();
            this.month = yearMonth.getMonthValue();
            this.daysInMonth = yearMonth.lengthOfMonth();
        }
    }
}
