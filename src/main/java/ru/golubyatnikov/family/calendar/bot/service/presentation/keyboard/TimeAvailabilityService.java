package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Сервис для определения доступных временных интервалов.
 * Фильтрует прошедшие часы и минуты на основе текущего времени пользователя.
 */
@Service
@Slf4j
public class TimeAvailabilityService {

    private static final int MAX_HOUR = 23;
    private static final int CUTOFF_MINUTE = 46;
    private static final List<Integer> MINUTE_INTERVALS = List.of(0, 15, 30, 45);

    /**
     * Определяет доступные часы для выбора на основе текущего времени пользователя.
     * 
     * @param selectedDate выбранная дата
     * @param user пользователь
     *
     * @return список доступных часов (0-23)
     */
    public List<Integer> getAvailableHours(LocalDate selectedDate, User user) {
        if (selectedDate == null || user == null) {
            throw new IllegalArgumentException("Selected date and user cannot be null");
        }
        
        LocalDate today = user.getCurrentDate();
        
        // Для будущих дат все часы доступны
        if (selectedDate.isAfter(today)) {
            return IntStream.range(0, 24).boxed().collect(Collectors.toList());
        }
        
        // Для сегодняшнего дня фильтруем прошедшие часы
        if (selectedDate.equals(today)) {
            return getAvailableHoursForToday(user);
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
     *
     * @return список доступных минут (0, 15, 30, 45)
     */
    public List<Integer> getAvailableMinutes(int selectedHour, LocalDate selectedDate, User user) {
        if (selectedHour < 0 || selectedHour > MAX_HOUR || selectedDate == null || user == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        LocalDate today = user.getCurrentDate();
        
        // Для будущих дат все интервалы доступны
        if (selectedDate.isAfter(today)) {
            return MINUTE_INTERVALS;
        }
        
        // Для сегодняшнего дня проверяем текущий час
        if (selectedDate.equals(today)) {
            return getAvailableMinutesForToday(selectedHour, user);
        }
        
        log.warn("Попытка получить доступные минуты для прошлой даты {}", selectedDate);
        return Collections.emptyList();
    }

    private @NonNull List<Integer> getAvailableHoursForToday(@NonNull User user) {
        var currentDateTime = user.getCurrentDateTime();
        int currentHour = currentDateTime.getHour();
        int currentMinute = currentDateTime.getMinute();
        
        // Если уже поздно (23:46+), нет доступных часов
        if (currentHour == MAX_HOUR && currentMinute >= CUTOFF_MINUTE) {
            return Collections.emptyList();
        }
        
        List<Integer> availableHours = IntStream.rangeClosed(currentHour, MAX_HOUR)
                .boxed()
                .collect(Collectors.toList());
        
        return availableHours;
    }

    private List<Integer> getAvailableMinutesForToday(int selectedHour, @NonNull User user) {
        var currentDateTime = user.getCurrentDateTime();
        int currentHour = currentDateTime.getHour();
        int currentMinute = currentDateTime.getMinute();
        
        // Если выбран будущий час, все интервалы доступны
        if (selectedHour > currentHour) {
            return MINUTE_INTERVALS;
        }
        
        // Если выбран текущий час, фильтруем прошедшие интервалы
        if (selectedHour == currentHour) {
            return getAvailableMinutesForCurrentHour(currentHour, currentMinute);
        }
        
        log.warn("Попытка получить доступные минуты для прошлого часа {} (текущий {})", 
                selectedHour, currentHour);
        return Collections.emptyList();
    }

    private @NonNull List<Integer> getAvailableMinutesForCurrentHour(int currentHour, int currentMinute) {
        // Если уже поздно (XX:46+), нет доступных интервалов
        if (currentMinute >= CUTOFF_MINUTE) {
            return Collections.emptyList();
        }
        
        List<Integer> availableMinutes = MINUTE_INTERVALS.stream()
                .filter(minute -> minute > currentMinute)
                .collect(Collectors.toList());
        
        return availableMinutes;
    }
}
