package ru.golubyatnikov.family.calendar.bot.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.golubyatnikov.family.calendar.bot.model.Event;

import ru.golubyatnikov.family.calendar.bot.model.Family;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.repository.EventRepository;
import ru.golubyatnikov.family.calendar.bot.repository.FamilyRepository;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Планировщик для отправки еженедельных сводок событий.
 * 
 * <p>Этот компонент выполняет следующие задачи:</p>
 * <ul>
 *   <li>Сбор событий на следующую неделю для каждой семьи</li>
 *   <li>Форматирование сводки с группировкой по дням</li>
 *   <li>Отправка сводки всем членам семьи</li>
 * </ul>
 * 
 * <p>Планировщик запускается каждое воскресенье в 20:00 для подготовки
 * к предстоящей неделе.</p>
 * 
 * <p><b>Требования:</b> 28.6</p>
 * 
 * @see Event
 * @see Family
 * @see EventRepository
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklySummaryScheduler {
    
    private final EventRepository eventRepository;
    private final FamilyRepository familyRepository;
    private final TelegramMessageService messageService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale RUSSIAN_LOCALE = new Locale("ru", "RU");
    
    /**
     * Отправляет еженедельные сводки всем семьям.
     * 
     * <p>Выполняется каждое воскресенье в 20:00 по расписанию.
     * Использует cron выражение: "0 0 20 * * SUN" (каждое воскресенье в 20:00)</p>
     * 
     * <p>Для каждой семьи:</p>
     * <ol>
     *   <li>Получает события на следующую неделю (понедельник-воскресенье)</li>
     *   <li>Группирует события по дням недели</li>
     *   <li>Форматирует сводку с эмодзи и Markdown</li>
     *   <li>Отправляет сводку всем членам семьи</li>
     * </ol>
     */
    @Scheduled(cron = "0 0 20 * * SUN")
    @Transactional(readOnly = true)
    public void sendWeeklySummary() {
        log.info("Запуск отправки еженедельных сводок");
        
        try {
            // Определяем период следующей недели (понедельник-воскресенье)
            LocalDate today = LocalDate.now();
            LocalDate nextMonday = today.with(DayOfWeek.MONDAY).plusWeeks(1);
            LocalDate nextSunday = nextMonday.plusDays(6);
            
            log.debug("Период сводки: {} - {}", nextMonday, nextSunday);
            
            // Получаем все семьи
            List<Family> families = familyRepository.findAll();
            
            if (families.isEmpty()) {
                log.info("Семей не найдено, сводки не отправляются");
                return;
            }
            
            log.info("Найдено {} семей для отправки сводок", families.size());
            
            int sentCount = 0;
            for (Family family : families) {
                try {
                    sendFamilySummary(family, nextMonday, nextSunday);
                    sentCount++;
                } catch (Exception e) {
                    log.error("Ошибка при отправке сводки семье ID={}: {}", 
                             family.getId(), e.getMessage(), e);
                }
            }
            
            log.info("Еженедельные сводки отправлены: {} из {} семей", sentCount, families.size());
            
        } catch (Exception e) {
            log.error("Ошибка при выполнении отправки еженедельных сводок: {}", 
                     e.getMessage(), e);
        }
    }
    
    /**
     * Отправляет еженедельную сводку одной семье.
     * 
     * @param family семья для отправки сводки
     * @param startDate начало периода (понедельник)
     * @param endDate конец периода (воскресенье)
     */
    private void sendFamilySummary(Family family, LocalDate startDate, LocalDate endDate) {
        log.debug("Формирование сводки для семьи ID={}", family.getId());
        
        // Получаем активные события семьи на неделю
        List<Event> events = eventRepository.findByFamilyIdAndEventDateBetweenAndStatus(
            family.getId(),
            startDate,
            endDate,
            Event.EventStatus.ACTIVE
        );
        
        // Формируем сообщение
        String summary = formatWeeklySummary(events, startDate, endDate);
        
        // Отправляем всем членам семьи
        int sentToMembers = 0;
        for (User member : family.getMembers()) {
            try {
                messageService.sendMessage(member.getTelegramId(), summary);
                sentToMembers++;
                log.debug("Сводка отправлена пользователю ID={}", member.getId());
            } catch (Exception e) {
                log.error("Ошибка при отправке сводки пользователю ID={}: {}", 
                         member.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Сводка для семьи ID={} отправлена {} из {} членов семьи", 
                 family.getId(), sentToMembers, family.getMembers().size());
    }
    
    /**
     * Форматирует еженедельную сводку событий.
     * 
     * @param events список событий на неделю
     * @param startDate начало периода
     * @param endDate конец периода
     * @return отформатированная сводка в Markdown
     */
    private String formatWeeklySummary(List<Event> events, LocalDate startDate, LocalDate endDate) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("📅 ").append(bold("Еженедельная сводка")).append("\n");
        summary.append(italic(startDate.format(DATE_FORMATTER) + " \\- " + endDate.format(DATE_FORMATTER))).append("\n\n");
        
        if (events.isEmpty()) {
            summary.append("На следующей неделе нет запланированных событий.\n\n");
            summary.append(italic("Хорошее время для отдыха или планирования новых дел!"));
            return summary.toString();
        }
        
        // Группируем события по дням
        Map<LocalDate, List<Event>> eventsByDate = events.stream()
            .collect(Collectors.groupingBy(Event::getEventDate));
        
        // Проходим по всем дням недели
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            List<Event> dayEvents = eventsByDate.get(currentDate);
            
            if (dayEvents != null && !dayEvents.isEmpty()) {
                // Заголовок дня
                String dayName = currentDate.getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, RUSSIAN_LOCALE);
                summary.append("🔹 ").append(bold(capitalize(dayName) + ", " + currentDate.format(DATE_FORMATTER)))
                       .append("\n");
                
                // События дня
                for (Event event : dayEvents) {
                    summary.append(formatEvent(event));
                }
                
                summary.append("\n");
            }
            
            currentDate = currentDate.plusDays(1);
        }
        
        summary.append(italic("Всего событий: " + events.size()));
        
        return summary.toString();
    }
    
    /**
     * Форматирует одно событие для сводки.
     * 
     * @param event событие для форматирования
     * @return отформатированная строка
     */
    private String formatEvent(Event event) {
        StringBuilder sb = new StringBuilder();
        
        // Иконка типа события
        if (event.getIsPersonal()) {
            sb.append("   🔒 ");
        } else {
            sb.append("   👨‍👩‍👧‍👦 ");
        }
        
        // Время события
        if (event.getEventTime() != null) {
            sb.append(bold(event.getEventTime().format(TIME_FORMATTER)));
            
            if (event.getEndTime() != null) {
                sb.append(" - ").append(bold(event.getEndTime().format(TIME_FORMATTER)));
            }
            
            sb.append(" - ");
        }
        
        // Название события
        sb.append(bold(event.getTitle()));
        
        // Создатель события
        sb.append(" (").append(escape(event.getUser().getFirstName())).append(")");
        
        sb.append("\n");
        
        return sb.toString();
    }
    
    /**
     * Делает первую букву строки заглавной.
     * 
     * @param str строка для обработки
     * @return строка с заглавной первой буквой
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}

