package ru.golubyatnikov.family.calendar.bot.util;

import org.springframework.lang.NonNull;

import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * Утилитный класс для форматирования текста в MarkdownV2 формате Telegram.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-16
 */
public final class MarkdownFormatter {
    
    /**
     * Массив специальных символов MarkdownV2, которые требуют экранирования.
     * Полный список зарезервированных символов согласно спецификации Telegram MarkdownV2:
     */
    private static final char[] MARKDOWN_SPECIAL_CHARS = {
        '_', '*', '[', ']', '(', ')', '~', '`', 
        '>', '#', '+', '-', '=', '|', '{', '}', 
        '.', '!'
    };
    
    /**
     * Приватный конструктор для предотвращения создания экземпляров.
     * 
     * @throws UnsupportedOperationException всегда, так как это утилитный класс
     */
    private MarkdownFormatter() {
        throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
    }
    
    /**
     * Экранирует специальные символы MarkdownV2 в тексте.
     *
     * @param text текст для экранирования, может быть null или пустым
     * @return экранированный текст, или пустая строка если входной текст null или пустой
     */
    public static @NonNull String escapeMarkdownV2(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder(text.length() * 2);
        
        for (char c : text.toCharArray()) {
            if (isSpecialChar(c)) {
                result.append('\\');
            }
            result.append(c);
        }
        
        return result.toString();
    }
    
    /**
     * Экранирует специальные символы MarkdownV2 в тексте.
     *
     * @param text текст для экранирования, может быть null или пустым
     * @return экранированный текст, или пустая строка если входной текст null или пустой
     */
    public static @NonNull String escape(String text) {
        return escapeMarkdownV2(text);
    }
    
    /**
     * Форматирует текст жирным шрифтом в MarkdownV2.
     *
     * @param text текст для форматирования, может быть null или пустым
     * @return текст в формате *текст* с экранированными специальными символами,
     *         или пустая строка если входной текст null или пустой
     */
    public static @NonNull String bold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        return "*" + escapeMarkdownV2(text) + "*";
    }
    
    /**
     * Форматирует текст курсивом в MarkdownV2.
     *
     * @param text текст для форматирования, может быть null или пустым
     * @return текст в формате _текст_ с экранированными специальными символами,
     *         или пустая строка если входной текст null или пустой
     */
    public static @NonNull String italic(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        return "_" + escapeMarkdownV2(text) + "_";
    }
    
    /**
     * Форматирует текст моноширинным шрифтом в MarkdownV2.
     *
     * @param text текст для форматирования, может быть null или пустым
     * @return текст в формате `текст` без экранирования специальных символов,
     *         или пустая строка если входной текст null или пустой
     */
    public static @NonNull String code(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // Внутри моноширинного текста нужно экранировать только ` и \
        String escaped = text.replace("\\", "\\\\").replace("`", "\\`");
        return "`" + escaped + "`";
    }

    
    /**
     * Форматирует сообщение с автоматическим экранированием всех частей.
     *
     * @param template шаблон сообщения с плейсхолдерами (например, "Час: %02d:00")
     * @param args аргументы для подстановки
     *
     * @return полностью экранированное сообщение, готовое для отправки в Telegram
     * @throws IllegalArgumentException если template равен null, или если форматирование
     *         не удалось (несоответствие типов, количества аргументов, некорректный формат плейсхолдера)
     */
    public static @NonNull String formatMessage(String template, Object... args) {
        if (template == null) {
            throw new IllegalArgumentException("Шаблон сообщения не может быть null");
        }
        
        // Если нет аргументов, просто экранируем шаблон
        if (args == null || args.length == 0) {
            return escapeMarkdownV2(template);
        }
        
        try {
            // Форматируем сообщение через String.format() с Locale.US для точки в числах
            String formatted = String.format(Locale.US, template, args);
            
            // Экранируем результат
            return escapeMarkdownV2(formatted);

        } catch (IllegalFormatException e) {
            // Преобразуем в понятное сообщение об ошибке
            throw new IllegalArgumentException(
                String.format(
                    "Ошибка форматирования сообщения. Шаблон: '%s', количество аргументов: %d. Причина: %s",
                    template, args.length, e.getMessage()
                ),
                e
            );
        }
    }
    
    /**
     * Проверяет, является ли символ специальным символом MarkdownV2.
     * 
     * @param c символ для проверки
     * @return true если символ требует экранирования, false в противном случае
     */
    private static boolean isSpecialChar(char c) {
        for (char specialChar : MARKDOWN_SPECIAL_CHARS) {
            if (c == specialChar) {
                return true;
            }
        }
        return false;
    }
}
