package ru.golubyatnikov.family.calendar.bot.util;

import java.util.IllegalFormatException;

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
     */
    private static final char[] SPECIAL_CHARS = {
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
     * escape("Hello (world)!") -> "Hello \\(world\\)\\!"
     * escape("Price: $100") -> "Price: \\$100"
     * escape("") -> ""
     * escape(null) -> ""
     * escape("Привет 👋") -> "Привет 👋" // эмодзи не экранируются
     * }</pre>
     */
    public static String escape(String text) {
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
        
        return "*" + escape(text) + "*";
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
        
        return "_" + escape(text) + "_";
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
        
        return "*_" + escape(text) + "_*";
    }
    
    /**
     * Форматирует сообщение с автоматическим экранированием всех частей.
     * 
     * <p>Метод работает аналогично String.format(), но автоматически экранирует
     * все специальные символы MarkdownV2 как в шаблоне, так и в аргументах.
     * Это предотвращает ошибки парсинга MarkdownV2 при отправке сообщений в Telegram.</p>
     * 
     * <p>Важно: метод экранирует все части сообщения, включая статический текст
     * в шаблоне. Если какой-то аргумент уже экранирован, это может привести к
     * двойному экранированию. В таких случаях используйте escape() напрямую.</p>
     * 
     * <p>Поддерживаемые плейсхолдеры: только %s (строка). Все аргументы будут
     * преобразованы в строки через toString() и экранированы.</p>
     * 
     * @param template шаблон сообщения с плейсхолдерами %s (например, "Дата: %s")
     * @param args аргументы для подстановки (будут экранированы автоматически)
     * @return полностью экранированное сообщение, готовое для отправки в Telegram
     * @throws IllegalArgumentException если template равен null, или если количество
     *         плейсхолдеров не совпадает с количеством аргументов
     * 
     * @example
     * <pre>{@code
     * // Простое использование
     * String msg = formatMessage("Дата: %s", "12.01.2026");
     * // Результат: "Дата: 12\\.01\\.2026"
     * 
     * // С несколькими аргументами
     * String msg = formatMessage("Событие: %s в %s", "Встреча!", "14:30");
     * // Результат: "Событие: Встреча\\! в 14:30"
     * 
     * // С числовыми аргументами
     * String msg = formatMessage("Найдено событий: %s", 5);
     * // Результат: "Найдено событий: 5"
     * 
     * // Со специальными символами в шаблоне
     * String msg = formatMessage("✅ Дата выбрана: %s\n\nТеперь выберите час:", "12.01.2026");
     * // Результат: "✅ Дата выбрана: 12\\.01\\.2026\n\nТеперь выберите час:"
     * 
     * // С эмодзи (эмодзи не экранируются)
     * String msg = formatMessage("Привет 👋 %s!", "Мир");
     * // Результат: "Привет 👋 Мир\\!"
     * }</pre>
     */
    public static String formatMessage(String template, Object... args) {
        if (template == null) {
            throw new IllegalArgumentException("Шаблон сообщения не может быть null");
        }
        
        // Если нет аргументов, просто экранируем шаблон
        if (args == null || args.length == 0) {
            return escape(template);
        }
        
        // Проверяем количество плейсхолдеров
        long placeholderCount = countPlaceholders(template);
        if (placeholderCount != args.length) {
            throw new IllegalArgumentException(
                String.format(
                    "Ошибка форматирования: количество плейсхолдеров (%d) не совпадает с количеством аргументов (%d). " +
                    "Шаблон: '%s'",
                    placeholderCount, args.length, template
                )
            );
        }
        
        // Экранируем все аргументы
        Object[] escapedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                throw new IllegalArgumentException(
                    String.format("Аргумент с индексом %d не может быть null", i)
                );
            }
            // Преобразуем аргумент в строку и экранируем
            escapedArgs[i] = escape(args[i].toString());
        }
        
        // Сначала форматируем сообщение с неэкранированным шаблоном
        String formatted = String.format(template, escapedArgs);
        
        // Затем экранируем весь результат, но это приведет к двойному экранированию аргументов
        // Поэтому нужен другой подход: экранировать шаблон по частям
        
        // Разбиваем шаблон на части между плейсхолдерами
        String[] parts = template.split("%s", -1);
        
        // Собираем результат: экранированная часть + экранированный аргумент
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            result.append(escape(parts[i]));
            if (i < escapedArgs.length) {
                result.append(escapedArgs[i]);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Подсчитывает количество плейсхолдеров %s в шаблоне.
     * 
     * @param template шаблон для анализа
     * @return количество плейсхолдеров %s
     */
    private static long countPlaceholders(String template) {
        int count = 0;
        int index = 0;
        while ((index = template.indexOf("%s", index)) != -1) {
            count++;
            index += 2;
        }
        return count;
    }
    
    /**
     * Проверяет, является ли символ специальным символом MarkdownV2.
     * 
     * @param c символ для проверки
     * @return true если символ требует экранирования, false в противном случае
     */
    private static boolean isSpecialChar(char c) {
        for (char specialChar : SPECIAL_CHARS) {
            if (c == specialChar) {
                return true;
            }
        }
        return false;
    }
}
