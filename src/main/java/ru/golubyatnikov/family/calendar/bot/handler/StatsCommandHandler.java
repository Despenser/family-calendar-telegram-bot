package ru.golubyatnikov.family.calendar.bot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.golubyatnikov.family.calendar.bot.model.User;
import ru.golubyatnikov.family.calendar.bot.service.StatisticsService;
import ru.golubyatnikov.family.calendar.bot.service.TelegramMessageService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.*;

/**
 * Обработчик команды /stats для отображения статистики событий.
 * 
 * <p>Этот обработчик показывает статистику событий семьи за текущий месяц.
 * "Всего событий" включает сумму активных и завершенных событий.
 * Удаленные события и черновики исключаются из подсчета.</p>
 * 
 * <p>Отображаемая статистика:</p>
 * <ul>
 *   <li>Общее количество событий (активные + завершенные)</li>
 *   <li>Количество завершенных событий (статус COMPLETED)</li>
 *   <li>Количество активных событий (статус ACTIVE)</li>
 *   <li>Количество семейных событий</li>
 *   <li>Количество персональных событий пользователя</li>
 *   <li>Процент завершения (завершенные / (активные + завершенные))</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1, 1.3 - статистика показывает сумму активных и завершенных событий</p>
 * 
 * @see CommandHandler
 * @see StatisticsService
 * @author Family Calendar Bot Team
 * @version 2.1.0
 * @since 2026-01-08
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatsCommandHandler implements CommandHandler {
    
    private final StatisticsService statisticsService;
    private final TelegramMessageService messageService;
    
    private static final DateTimeFormatter MONTH_FORMATTER = 
        DateTimeFormatter.ofPattern("LLLL yyyy", new Locale("ru"));
    
    /**
     * Обрабатывает команду /stats.
     * 
     * <p>Получает статистику событий за текущий месяц и отправляет
     * отформатированный отчет пользователю. "Всего событий" включает сумму
     * активных и завершенных событий. События со статусами DELETED
     * и DRAFT исключаются из подсчета.</p>
     * 
     * @param message сообщение от пользователя с командой
     * @param user пользователь, отправивший команду
     * @return сообщение для отправки пользователю
     */
    @Override
    public String handle(Message message, User user) {
        Long chatId = message.getChatId();
        log.debug("Обработка команды /stats для пользователя ID={}, семья ID={}", 
                  user.getId(), user.getFamily().getId());
        
        try {
            // Получение статистики за текущий месяц
            YearMonth currentMonth = YearMonth.now();
            StatisticsService.EventStatistics stats = statisticsService.getMonthlyStatistics(
                user.getFamily().getId(),
                user.getId(),
                currentMonth.getYear(),
                currentMonth.getMonthValue()
            );
            
            // Формирование сообщения со статистикой
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append(escape("📊 "))
                          .append(bold("Статистика событий"))
                          .append(escape("\n"))
                          .append(italic(currentMonth.atDay(1).format(MONTH_FORMATTER)))
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
                          .append(bold(String.valueOf(stats.getFamilyEvents())))
                          .append(escape("\n"));
            messageBuilder.append(escape("• Персональные: "))
                          .append(bold(String.valueOf(stats.getPersonalEvents())))
                          .append(escape("\n\n"));
            
            // Процент завершенных событий
            if (stats.getTotalEvents() > 0) {
                double completionRate = (stats.getCompletedEvents() * 100.0) / stats.getTotalEvents();
                messageBuilder.append(escape("✅ "))
                              .append(bold("Процент завершения:"))
                              .append(escape(" "))
                              .append(bold(String.format("%.1f%%", completionRate)))
                              .append(escape("\n\n"));
            }
            
            // Дополнительная информация
            if (stats.getTotalEvents() == 0) {
                messageBuilder.append(italic("В этом месяце нет событий. Создайте первое событие с помощью /add_event"));
            } else if (stats.getActiveEvents() > 0) {
                messageBuilder.append(italic(String.format("Активных событий в этом месяце - %d", stats.getActiveEvents())));
            } else {
                messageBuilder.append(italic("Все события этого месяца завершены! 🎉"));
            }
            
            String responseMessage = messageBuilder.toString();
            log.debug("Пользователю ID={} будет отправлена статистика: всего={}, завершено={}, активных={}", 
                     user.getId(), stats.getTotalEvents(), stats.getCompletedEvents(), stats.getActiveEvents());
            return responseMessage;
            
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
    
    @Override
    public boolean requiresAuth() {
        return true;
    }
}
