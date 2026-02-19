package ru.golubyatnikov.family.calendar.bot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import ru.golubyatnikov.family.calendar.bot.model.dto.EventStatistics;
import ru.golubyatnikov.family.calendar.bot.model.entity.User;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;
import ru.golubyatnikov.family.calendar.bot.service.domain.statistics.StatisticsService;
import java.time.YearMonth;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /stats для отображения статистики событий.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatsCommandHandler implements CommandHandler {
    
    private final StatisticsService statisticsService;
    private final DateTimeFormattingService dateTimeFormattingService;
    
    /**
     * Обрабатывает команду /stats.
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     *
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, @NonNull User user) {
        try {
            // Получение статистики за текущий месяц в таймзоне пользователя
            YearMonth currentMonth = YearMonth.now(user.getZoneId());
            EventStatistics stats = statisticsService.getMonthlyStatistics(
                user.getFamily().getId(),
                user.getId(),
                currentMonth.getYear(),
                currentMonth.getMonthValue()
            );
            
            // Формирование сообщения со статистикой
            String monthName = dateTimeFormattingService.formatMonth(currentMonth.atDay(1));
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(escape("📊 "))
                          .append(bold("Статистика событий"))
                          .append(escape(" ("))
                          .append(escape(monthName))
                          .append(escape(")"))
                          .append(escape("\n\n"));
            
            // Общая статистика
            messageBuilder.append(escape("📋 "))
                          .append(bold("Общая статистика:"))
                          .append(escape("\n"));
            messageBuilder.append(escape("• Всего событий: "))
                          .append(bold(String.valueOf(stats.getTotalEvents())))
                          .append(escape("\n"));
            messageBuilder.append(escape("• Завершено: "))
                          .append(bold(String.valueOf(stats.getCompletedEvents())))
                          .append(escape("\n"));
            messageBuilder.append(escape("• Активных: "))
                          .append(bold(String.valueOf(stats.getActiveEvents())))
                          .append(escape("\n\n"));

            // Статистика по типам
            messageBuilder.append(escape("👨‍👩‍👧‍👦 "))
                          .append(bold("По типам событий:"))
                          .append(escape("\n"));
            messageBuilder.append(escape("• Семейные: "))
                          .append(bold(String.valueOf(stats.getFamilyEvents())));
            
            if (stats.getTotalEvents() > 0) {
                messageBuilder.append(escape(" ("))
                              .append(escape(String.format("%.1f%%", stats.getFamilyEventsRate())))
                              .append(escape(")"));
            }
            messageBuilder.append(escape("\n"));
            
            messageBuilder.append(escape("• Персональные: "))
                          .append(bold(String.valueOf(stats.getPersonalEvents())));
            
            if (stats.getTotalEvents() > 0) {
                messageBuilder.append(escape(" ("))
                              .append(escape(String.format("%.1f%%", stats.getPersonalEventsRate())))
                              .append(escape(")"));
            }
            messageBuilder.append(escape("\n\n"));
            
            // Процент завершенных событий
            if (stats.getTotalEvents() > 0) {
                messageBuilder.append(escape("✅ "))
                              .append("Процент завершения:")
                              .append(escape(" "))
                              .append(bold(String.format("%.1f%%", stats.getCompletionRate())))
                              .append(escape("\n"));
            }
            
            // Дополнительная информация
            if (stats.getTotalEvents() == 0) {
                messageBuilder.append(italic("В этом месяце нет событий. Создайте первое событие с помощью /add_event"));
            } else if (stats.getActiveEvents() > 0) {
                messageBuilder.append(escape("📌 "))
                              .append("Активных событий в этом месяце:")
                              .append(escape(" "))
                              .append(bold(String.valueOf(stats.getActiveEvents())));
            } else {
                messageBuilder.append(italic("Все события этого месяца завершены! 🎉"));
            }

            return messageBuilder.toString();
            
        } catch (Exception e) {
            log.error("Ошибка при обработке команды /stats для пользователя ID={}", user.getId(), e);
            return "❌ Произошла ошибка при получении статистики. Попробуйте позже.";
        }
    }

    @Override
    public String getCommand() {
        return "/stats";
    }
    
    @Override
    public String getDescription() {
        return "Статистика событий за месяц";
    }
}
