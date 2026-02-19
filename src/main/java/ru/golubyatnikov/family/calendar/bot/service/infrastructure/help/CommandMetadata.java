package ru.golubyatnikov.family.calendar.bot.service.infrastructure.help;

import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.model.enums.CommandCategory;
import java.util.Map;

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
            Map.entry("/help", CommandCategory.HELP),
            Map.entry("/start", CommandCategory.HELP)
    );

    private static final Map<String, String> COMMAND_EMOJIS = Map.ofEntries(
            Map.entry("/start", "🚀"),
            Map.entry("/help", "📚"),
            Map.entry("/add_event", "➕"),
            Map.entry("/my_events", "📝"),
            Map.entry("/month", "🗓️"),
            Map.entry("/today", "📅"),
            Map.entry("/week", "📆"),
            Map.entry("/search", "🔍"),
            Map.entry("/filter", "🫧"),
            Map.entry("/stats", "📊"),
            Map.entry("/trash", "🗑️")
    );

    private static final String LOCK_EMOJI = "🔒";

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
        return LOCK_EMOJI;
    }
}
