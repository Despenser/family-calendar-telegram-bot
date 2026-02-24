package ru.golubyatnikov.family.calendar.bot.service.presentation.help;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.enums.CommandCategory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.bold;
import static ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter.escape;

/**
 * Построение итогового сообщения справки.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Component
@RequiredArgsConstructor
public class HelpMessageBuilder {

    private static final CommandCategory[] CATEGORY_ORDER = {
            CommandCategory.CALENDAR,
            CommandCategory.MANAGE_EVENTS,
            CommandCategory.VIEW_EVENTS,
            CommandCategory.SEARCH_FILTER,
            CommandCategory.STATS_TRASH,
            CommandCategory.HELP
    };

    /**
     * Строит полное сообщение справки.
     *
     * @param groupedCommands команды, сгруппированные по категориям
     * @param isAuthorized статус авторизации пользователя
     *
     * @return отформатированное сообщение справки
     */
    public String build(Map<CommandCategory, List<String>> groupedCommands, boolean isAuthorized) {
        StringBuilder message = new StringBuilder();

        appendHeader(message);
        appendDescription(message);
        
        if (!isAuthorized) {
            appendAuthorizationWarning(message);
        }

        appendCommandsList(message, groupedCommands);
        appendFooter(message, isAuthorized);

        return message.toString();
    }

    /**
     * Добавляет заголовок справки.
     */
    private void appendHeader(@NonNull StringBuilder message) {
        message.append("📚 ")
                .append(bold("Справка по командам Семейного Календаря"))
                .append("\n\n");
    }

    /**
     * Добавляет описание возможностей бота.
     */
    private void appendDescription(@NonNull StringBuilder message) {
        message.append(escape("Семейный календарь помогает организовать события и задачи для всей семьи. "))
                .append(escape("Вы можете создавать события, просматривать расписание, получать напоминания и многое другое."))
                .append("\n\n");
    }

    /**
     * Добавляет предупреждение о необходимости авторизации.
     */
    private void appendAuthorizationWarning(@NonNull StringBuilder message) {
        message.append(escape("⚠️ Вы не зарегистрированы в семейном календаре.")).append("\n")
                .append(escape("Некоторые команды требуют регистрации (отмечены 🔒).")).append("\n")
                .append(escape("Для получения доступа к полному функционалу обратитесь к администратору вашей семьи."))
                .append("\n\n");
    }

    /**
     * Добавляет список команд по категориям.
     */
    private void appendCommandsList(@NonNull StringBuilder message,
                                    Map<CommandCategory, List<String>> groupedCommands) {

        message.append(bold("Доступные команды:")).append("\n\n");

        Arrays.stream(CATEGORY_ORDER).forEach(category -> {
            List<String> commands = groupedCommands.get(category);
            if (commands == null || commands.isEmpty()) {
                return;
            }

            message.append(bold(category.getDisplayName())).append("\n");
            commands.forEach(cmd -> message.append(cmd).append("\n"));
            message.append("\n");
        });
    }

    /**
     * Добавляет подвал сообщения с инструкциями.
     */
    private void appendFooter(@NonNull StringBuilder message, boolean isAuthorized) {
        message.append(escape("Для использования команды просто отправьте её в чат.")).append("\n");

        if (!isAuthorized) {
            message.append(escape("После регистрации вам станут доступны все функции бота."));

        } else {
            message.append(escape("Если у вас возникли вопросы, обратитесь к администратору семьи."));
        }
    }
}
