package ru.golubyatnikov.family.calendar.bot.util;

import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * Утилитный класс для форматирования текста в MarkdownV2 формате Telegram.
 * 
 * <p>MarkdownV2 требует экранирования следующих специальных символов:
 * _ * [ ] ( ) ~ ` > # + - = | { } . !</p>
 * 
 * <p>Примеры использования:</p>
 * <pre>{@code
 * // Экранирование специальных символов
 * String escaped = MarkdownFormatter.escape("Цена: 100$");
 * // Результат: "Цена: 100\\$"
 * 
 * // Форматирование жирным шрифтом
 * String bold = MarkdownFormatter.bold("Важное сообщение!");
 * // Результат: "*Важное сообщение\\!*"
 * 
 * // Форматирование курсивом
 * String italic = MarkdownFormatter.italic("Примечание");
 * // Результат: "_Примечание_"
 * 
 * // Форматирование моноширинным шрифтом
 * String code = MarkdownFormatter.code("System.out.println()");
 * // Результат: "`System\\.out\\.println\\(\\)`"
 * 
 * // Форматирование жирным курсивом
 * String boldItalic = MarkdownFormatter.boldItalic("Очень важно");
 * // Результат: "*_Очень важно_*"
 * }</pre>
 * 
 * @author Family Calendar Bot
 * @version 1.0
 * @since 2026-01-12
 */
public final class MarkdownFormatter {
    
    /**
     * Массив специальных символов MarkdownV2, которые требуют экранирования.
     * Полный список зарезервированных символов согласно спецификации Telegram MarkdownV2:
     * '_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!'
     */
    private static final char[] MARKDOWN_SPECIAL_CHARS = {
        '_', '*', '[', ']', '(', ')', '~', '`', 
        '>', '#', '+', '-', '=', '|', '{', '}', 
        '.', '!'
    };
    
    /**
     * Приватный конструктор для предотвращения создания экземпляров утилитного класса.
     * 
     * @throws UnsupportedOperationException всегда, так как это утилитный класс
     */
    private MarkdownFormatter() {
        throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
    }
    
    /**
     * Экранирует специальные символы MarkdownV2 в тексте.
     * 
     * <p>Метод добавляет обратный слеш (\) перед каждым специальным символом,
     * который имеет особое значение в MarkdownV2. Эмодзи и другие Unicode символы
     * не экранируются.</p>
     * 
     * <p>Список экранируемых символов:
     * _ * [ ] ( ) ~ ` > # + - = | { } . !</p>
     * 
     * @param text текст для экранирования, может быть null или пустым
     * @return экранированный текст, или пустая строка если входной текст null или пустой
     * 
     * @example
     * <pre>{@code
     * escapeMarkdownV2("Hello (world)!") -> "Hello \\(world\\)\\!"
     * escapeMarkdownV2("Price: $100") -> "Price: \\$100"
     * escapeMarkdownV2("") -> ""
     * escapeMarkdownV2(null) -> ""
     * escapeMarkdownV2("Привет 👋") -> "Привет 👋" // эмодзи не экранируются
     * }</pre>
     */
    public static String escapeMarkdownV2(String text) {
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
     * <p>Метод добавляет обратный слеш (\) перед каждым специальным символом,
     * который имеет особое значение в MarkdownV2. Эмодзи и другие Unicode символы
     * не экранируются.</p>
     * 
     * <p>Список экранируемых символов:
     * _ * [ ] ( ) ~ ` > # + - = | { } . !</p>
     * 
     * <p>Этот метод является алиасом для {@link #escapeMarkdownV2(String)} для обратной совместимости.</p>
     * 
     * @param text текст для экранирования, может быть null или пустым
     * @return экранированный текст, или пустая строка если входной текст null или пустой
     * 
     * @example
     * <pre>{@code
     * escape("Hello (world)!") -> "Hello \\(world\\)\\!"
     * escape("Price: $100") -> "Price: \\$100"
     * escape("") -> ""
     * escape(null) -> ""
     * escape("Привет 👋") -> "Привет 👋" // эмодзи не экранируются
     * }</pre>
     */
    public static String escape(String text) {
        return escapeMarkdownV2(text);
    }
    
    /**
     * Форматирует текст жирным шрифтом в MarkdownV2.
     * 
     * <p>Метод оборачивает текст в символы * и автоматически экранирует
     * все специальные символы внутри текста.</p>
     * 
     * @param text текст для форматирования, может быть null или пустым
     * @return текст в формате *текст* с экранированными специальными символами,
     *         или пустая строка если входной текст null или пустой
     * 
     * @example
     * <pre>{@code
     * bold("Важно") -> "*Важно*"
     * bold("Важно!") -> "*Важно\\!*"
     * bold("") -> ""
     * bold(null) -> ""
     * bold("Цена: 100$") -> "*Цена: 100\\$*"
     * }</pre>
     */
    public static String bold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        return "*" + escapeMarkdownV2(text) + "*";
    }
    
    /**
     * Форматирует текст курсивом в MarkdownV2.
     * 
     * <p>Метод оборачивает текст в символы _ и автоматически экранирует
     * все специальные символы внутри текста.</p>
     * 
     * @param text текст для форматирования, может быть null или пустым
     * @return текст в формате _текст_ с экранированными специальными символами,
     *         или пустая строка если входной текст null или пустой
     * 
     * @example
     * <pre>{@code
     * italic("Примечание") -> "_Примечание_"
     * italic("Примечание!") -> "_Примечание\\!_"
     * italic("") -> ""
     * italic(null) -> ""
     * }</pre>
     */
    public static String italic(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        return "_" + escapeMarkdownV2(text) + "_";
    }
    
    /**
     * Форматирует текст моноширинным шрифтом в MarkdownV2.
     * 
     * <p>Метод оборачивает текст в обратные кавычки (`) БЕЗ экранирования
     * специальных символов внутри текста. Согласно спецификации MarkdownV2,
     * внутри моноширинного текста экранирование не требуется, кроме самих
     * обратных кавычек и обратного слеша.</p>
     * 
     * @param text текст для форматирования, может быть null или пустым
     * @return текст в формате `текст` без экранирования специальных символов,
     *         или пустая строка если входной текст null или пустой
     * 
     * @example
     * <pre>{@code
     * code("System.out.println()") -> "`System.out.println()`"
     * code("var x = 10;") -> "`var x = 10;`"
     * code("/my_events") -> "`/my_events`"
     * code("") -> ""
     * code(null) -> ""
     * }</pre>
     */
    public static String code(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // Внутри моноширинного текста нужно экранировать только ` и \
        String escaped = text.replace("\\", "\\\\").replace("`", "\\`");
        return "`" + escaped + "`";
    }
    
    /**
     * Форматирует текст жирным курсивом в MarkdownV2.
     * 
     * <p>Метод комбинирует жирное форматирование и курсив, оборачивая текст
     * в символы *_ и _*. Автоматически экранирует все специальные символы
     * внутри текста.</p>
     * 
     * @param text текст для форматирования, может быть null или пустым
     * @return текст в формате *_текст_* с экранированными специальными символами,
     *         или пустая строка если входной текст null или пустой
     * 
     * @example
     * <pre>{@code
     * boldItalic("Очень важно") -> "*_Очень важно_*"
     * boldItalic("Внимание!") -> "*_Внимание\\!_*"
     * boldItalic("") -> ""
     * boldItalic(null) -> ""
     * }</pre>
     */
    public static String boldItalic(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        return "*_" + escapeMarkdownV2(text) + "_*";
    }
    
    /**
     * Форматирует сообщение с автоматическим экранированием всех частей.
     * 
     * <p>Метод работает аналогично String.format(), но автоматически экранирует
     * все специальные символы MarkdownV2 в результате форматирования.
     * Это предотвращает ошибки парсинга MarkdownV2 при отправке сообщений в Telegram.</p>
     * 
     * <p>Поддерживает все стандартные плейсхолдеры Java: %s, %d, %02d, %f, %.2f и т.д.
     * Метод сначала форматирует сообщение через String.format(), а затем экранирует
     * все специальные символы MarkdownV2 в результате.</p>
     * 
     * <p>Важно: метод экранирует весь результат форматирования, включая статический текст
     * в шаблоне и отформатированные значения. Эмодзи не экранируются.</p>
     * 
     * @param template шаблон сообщения с плейсхолдерами (например, "Час: %02d:00")
     * @param args аргументы для подстановки
     * @return полностью экранированное сообщение, готовое для отправки в Telegram
     * @throws IllegalArgumentException если template равен null, или если форматирование
     *         не удалось (несоответствие типов, количества аргументов, некорректный формат плейсхолдера)
     * 
     * @example
     * <pre>{@code
     * // Строковые плейсхолдеры
     * String msg = formatMessage("Дата: %s", "12.01.2026");
     * // Результат: "Дата: 12\\.01\\.2026"
     * 
     * // Целочисленные плейсхолдеры
     * String msg = formatMessage("Час: %02d:00", 9);
     * // Результат: "Час: 09:00"
     * 
     * // Плейсхолдеры с плавающей точкой
     * String msg = formatMessage("Цена: %.2f руб.", 123.456);
     * // Результат: "Цена: 123\\.46 руб\\."
     * 
     * // Смешанные плейсхолдеры
     * String msg = formatMessage("Событие %s в %02d:%02d", "Встреча", 14, 30);
     * // Результат: "Событие Встреча в 14:30"
     * 
     * // С эмодзи (эмодзи не экранируются)
     * String msg = formatMessage("✅ Час выбран: %02d:00", 9);
     * // Результат: "✅ Час выбран: 09:00"
     * 
     * // Без аргументов (просто экранирование)
     * String msg = formatMessage("Привет!");
     * // Результат: "Привет\\!"
     * }</pre>
     */
    public static String formatMessage(String template, Object... args) {
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
