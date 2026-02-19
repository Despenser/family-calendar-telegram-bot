package ru.golubyatnikov.family.calendar.bot.service.presentation.help;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.handler.command.CommandHandler;
import ru.golubyatnikov.family.calendar.bot.model.enums.CommandCategory;
import ru.golubyatnikov.family.calendar.bot.service.infrastructure.help.CommandMetadata;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Группировка и сортировка команд по категориям.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandGrouper {

    private static final List<String> VIEW_EVENTS_ORDER = List.of("/today", "/week", "/month");
    private static final String START_COMMAND = "/start";

    private final CommandFormatter commandFormatter;
    private final CommandMetadata commandMetadata;

    /**
     * Группирует команды по категориям и форматирует их.
     *
     * @param handlers список обработчиков команд
     * @param isAuthorized статус авторизации пользователя
     *
     * @return Map категория -> список отформатированных команд
     */
    public Map<CommandCategory, List<String>> groupAndFormat(
            List<CommandHandler> handlers, 
            boolean isAuthorized) {
        
        return filterValidHandlers(handlers).stream()
                .collect(Collectors.groupingBy(
                        this::determineCategory,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                categoryHandlers -> formatHandlers(categoryHandlers, isAuthorized)
                        )
                ));
    }

    /**
     * Фильтрует валидные обработчики команд.
     */
    private @NonNull List<CommandHandler> filterValidHandlers(@NonNull List<CommandHandler> handlers) {
        return handlers.stream()
                .filter(handler -> handler != null && handler.getCommand() != null)
                .filter(handler -> !START_COMMAND.equals(handler.getCommand()))
                .toList();
    }

    /**
     * Определяет категорию для обработчика команды.
     */
    private CommandCategory determineCategory(@NonNull CommandHandler handler) {
        return commandMetadata.getCategoryFor(handler.getCommand());
    }

    /**
     * Форматирует список обработчиков с учетом категории.
     */
    private @NonNull List<String> formatHandlers(@NonNull List<CommandHandler> handlers, boolean isAuthorized) {
        CommandCategory category = determineCategory(handlers.getFirst());
        Comparator<CommandHandler> comparator = getComparatorForCategory(category);

        return handlers.stream()
                .sorted(comparator)
                .map(handler -> commandFormatter.format(handler, isAuthorized))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * Возвращает компаратор для сортировки команд в зависимости от категории.
     */
    private Comparator<CommandHandler> getComparatorForCategory(CommandCategory category) {
        if (category == CommandCategory.VIEW_EVENTS) {
            return Comparator.comparingInt(handler -> {
                int index = VIEW_EVENTS_ORDER.indexOf(handler.getCommand());
                return index == -1 ? Integer.MAX_VALUE : index;
            });
        }
        return Comparator.comparing(CommandHandler::getCommand);
    }
}
