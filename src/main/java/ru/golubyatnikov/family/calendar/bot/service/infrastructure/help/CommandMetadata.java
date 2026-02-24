package ru.golubyatnikov.family.calendar.bot.service.infrastructure.help;

import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.enums.CommandCategory;
import java.util.Map;

import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Commands.*;
import static ru.golubyatnikov.family.calendar.bot.util.EmojiConstants.Misc.LOCK;

/**
 * Метаданные команд: категории и эмодзи.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-12
 */
@Component
public class CommandMetadata {

    private static final Map<String, CommandCategory> COMMAND_CATEGORIES = Map.ofEntries(
            Map.entry("/today", CommandCategory.VIEW_EVENTS),
            Map.entry("/week", CommandCategory.VIEW_EVENTS),
            Map.entry("/month", CommandCategory.VIEW_EVENTS),
            Map.entry("/add_event", CommandCategory.MANAGE_EVENTS),
            Map.entry("/my_events", CommandCategory.MANAGE_EVENTS),
            Map.entry("/search", CommandCategory.SEARCH_FILTER),
            Map.entry("/filter", CommandCategory.SEARCH_FILTER),
            Map.entry("/stats", CommandCategory.STATS_TRASH),
            Map.entry("/trash", CommandCategory.STATS_TRASH),
            Map.entry("/calendar", CommandCategory.CALENDAR),
            Map.entry("/help", CommandCategory.HELP),
            Map.entry("/start", CommandCategory.HELP)
    );

    private static final Map<String, String> COMMAND_EMOJIS = Map.ofEntries(
            Map.entry("/start", START),
            Map.entry("/help", HELP),
            Map.entry("/calendar", CALENDAR),
            Map.entry("/add_event", ADD_EVENT),
            Map.entry("/my_events", MY_EVENTS),
            Map.entry("/month", MONTH),
            Map.entry("/today", TODAY),
            Map.entry("/week", WEEK),
            Map.entry("/search", SEARCH),
            Map.entry("/filter", FILTER),
            Map.entry("/stats", STATS),
            Map.entry("/trash", TRASH)
    );

    /**
     * Возвращает категорию для команды.
     */
    public CommandCategory getCategoryFor(String command) {
        return COMMAND_CATEGORIES.getOrDefault(command, CommandCategory.HELP);
    }

    /**
     * Возвращает эмодзи для команды.
     */
    public String getEmojiFor(String command) {
        return COMMAND_EMOJIS.getOrDefault(command, "");
    }

    /**
     * Возвращает эмодзи замка для заблокированных команд.
     */
    public String getLockEmoji() {
        return LOCK;
    }
}
