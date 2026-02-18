package ru.golubyatnikov.family.calendar.bot.util;

/**
 * Утилитный класс для работы с командами Telegram бота.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
public final class CommandUtil {

    private CommandUtil() {
        throw new UnsupportedOperationException("Утилитный класс не может быть инстанцирован");
    }

    /**
     * Извлекает команду из текста сообщения.
     *
     * @param text текст сообщения
     * @return команда (включая символ '/') в нижнем регистре или null, если команда не найдена
     */
    public static String extractCommand(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String trimmed = text.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }

        // Находим первый пробел или берем всю строку
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex).toLowerCase();
        }

        return trimmed.toLowerCase();
    }
}
