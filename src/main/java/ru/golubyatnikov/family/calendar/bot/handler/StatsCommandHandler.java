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
 * <p>Этот обработчик показывает статистику событий семьи за текущий месяц:</p>
 * <ul>
 *   <li>Общее количество событий</li>
 *   <li>Количество завершенных событий</li>
 *   <li>Количество активных событий</li>
 *   <li>Количество семейных событий</li>
 *   <li>Количество персональных событий пользователя</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 31.3</p>
 * 
 * @see CommandHandler
 * @see StatisticsService
 * @author Family Calendar Bot Team
 * @version 1.0.0
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
     * отформатированный отчет пользователю.</p>
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
            messageBuilder.append(String.format("📊 %s\n", bold("Статистика событий")));
            messageBuilder.append(italic(currentMonth.atDay(1).format(MONTH_FORMATTER))).append("\n\n");
            
            // Общая статистика
            messageBuilder.append(String.format("📋 %s\n", bold("Общая статистика:")));
            messageBuilder.append(String.format("   • Всего событий: %s\n", bold(String.valueOf(stats.getTotalEvents()))));
            messageBuilder.append(String.format("   • Завершено: %s\n", bold(String.valueOf(stats.getCompletedEvents()))));
            messageBuilder.append(String.format("   • Активных: %s\n\n", bold(String.valueOf(stats.getActiveEvents()))));
            
            // Статистика по типам
            messageBuilder.append(String.format("👨‍👩‍👧‍👦 %s\n", bold("По типам событий:")));
            messageBuilder.append(String.format("   • Семейные: %s\n", bold(String.valueOf(stats.getFamilyEvents()))));
            messageBuilder.append(String.format("   • Персональные: %s\n\n", bold(String.valueOf(stats.getPersonalEvents()))));
            
            // Процент завершенных событий
            if (stats.getTotalEvents() > 0) {
                double completionRate = (stats.getCompletedEvents() * 100.0) / stats.getTotalEvents();
                messageBuilder.append(String.format("✅ %s %s\n\n",
                             bold("Процент завершения:"),
                             bold(String.format("%.1f%%", completionRate))));
            }
            
            // Дополнительная информация
            if (stats.getTotalEvents() == 0) {
                messageBuilder.append(italic("В этом месяце пока нет событий. " +
                                    "Создайте первое событие с помощью ")).append(escape("/add_event"));
            } else if (stats.getActiveEvents() > 0) {
                messageBuilder.append(italic(String.format("У вас %d активных событий в этом месяце",
                             stats.getActiveEvents())));
            } else {
                messageBuilder.append(italic("Все события этого месяца завершены! 🎉"));
            }
            
            String responseMessage = messageBuilder.toString();
            log.info("Пользователю ID={} будет отправлена статистика: всего={}, завершено={}, активных={}", 
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
