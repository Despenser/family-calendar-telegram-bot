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
 * Обработчик команды /stats для отображения статистики активных событий.
 * 
 * <p>Этот обработчик показывает статистику только активных событий семьи за текущий месяц.
 * Завершенные, удаленные и черновики исключаются из подсчета "Всего событий".</p>
 * 
 * <p>Отображаемая статистика:</p>
 * <ul>
 *   <li>Общее количество активных событий (статус ACTIVE)</li>
 *   <li>Количество завершенных событий (статус COMPLETED)</li>
 *   <li>Количество активных событий (статус ACTIVE)</li>
 *   <li>Количество семейных активных событий</li>
 *   <li>Количество персональных активных событий пользователя</li>
 *   <li>Процент завершения (завершенные / (активные + завершенные))</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 1.1 - статистика показывает только активные события</p>
 * 
 * @see CommandHandler
 * @see StatisticsService
 * @author Family Calendar Bot Team
 * @version 2.0.0
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
     * <p>Получает статистику только активных событий за текущий месяц и отправляет
     * отформатированный отчет пользователю. События со статусами COMPLETED, DELETED
     * и DRAFT исключаются из подсчета "Всего событий".</p>
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
                          .append(bold("Статистика активных событий"))
                          .append(escape("\n"))
                          .append(italic(currentMonth.atDay(1).format(MONTH_FORMATTER)))
                          .append(escape("\n\n"));
            
            // Общая статистика
            messageBuilder.append(escape("📋 "))
                          .append(bold("Общая статистика:"))
                          .append(escape("\n"));
            messageBuilder.append(escape("• Всего событий: "))
                          .append(bold(String.valueOf(stats.getTotalEvents())))
                          .append(escape(" (только активные)"))
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
                messageBuilder.append(italic("В этом месяце нет активных событий. Создайте первое событие с помощью /add_event"));
            } else if (stats.getActiveEvents() > 0) {
                messageBuilder.append(italic(String.format("У вас %d активных событий в этом месяце", stats.getActiveEvents())));
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
