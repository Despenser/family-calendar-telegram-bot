package ru.golubyatnikov.family.calendar.bot.service.presentation.keyboard;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Компонент для преобразования текста в надстрочный формат (superscript).
 * Используется для отображения инициалов и счетчиков событий в календаре.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-03
 */
@Component
public class SuperscriptConverter {

    /**
     * Маппинг русских букв на надстрочные Unicode символы
     */
    private static final Map<Character, Character> SUPERSCRIPT_MAP = Map.<Character, Character>ofEntries(
        Map.entry('А', 'ᴬ'), Map.entry('а', 'ᴬ'),
        Map.entry('Б', 'ᴮ'), Map.entry('б', 'ᴮ'),
        Map.entry('В', 'ⱽ'), Map.entry('в', 'ⱽ'),
        Map.entry('Г', 'ᴳ'), Map.entry('г', 'ᴳ'),
        Map.entry('Д', 'ᴰ'), Map.entry('д', 'ᴰ'),
        Map.entry('Е', 'ᴱ'), Map.entry('е', 'ᴱ'),
        Map.entry('Ж', 'ᴶ'), Map.entry('ж', 'ᴶ'),
        Map.entry('З', 'ᶻ'), Map.entry('з', 'ᶻ'),
        Map.entry('И', 'ᴵ'), Map.entry('и', 'ᴵ'),
        Map.entry('К', 'ᴷ'), Map.entry('к', 'ᴷ'),
        Map.entry('Л', 'ᴸ'), Map.entry('л', 'ᴸ'),
        Map.entry('М', 'ᴹ'), Map.entry('м', 'ᴹ'),
        Map.entry('Н', 'ᴺ'), Map.entry('н', 'ᴺ'),
        Map.entry('О', 'ᴼ'), Map.entry('о', 'ᴼ'),
        Map.entry('П', 'ᴾ'), Map.entry('п', 'ᴾ'),
        Map.entry('Р', 'ᴿ'), Map.entry('р', 'ᴿ'),
        Map.entry('С', 'ˢ'), Map.entry('с', 'ˢ'),
        Map.entry('Т', 'ᵀ'), Map.entry('т', 'ᵀ'),
        Map.entry('У', 'ᵁ'), Map.entry('у', 'ᵁ'),
        Map.entry('Ф', 'ᶠ'), Map.entry('ф', 'ᶠ'),
        Map.entry('Х', 'ˣ'), Map.entry('х', 'ˣ'),
        Map.entry('Ч', 'ᶜ'), Map.entry('ч', 'ᶜ'),
        Map.entry('Ш', 'ᵂ'), Map.entry('ш', 'ᵂ'),
        Map.entry('Ы', 'ʸ'), Map.entry('ы', 'ʸ'),
        Map.entry('Э', 'ᴱ'), Map.entry('э', 'ᴱ'),
        Map.entry('Ю', 'ᵁ'), Map.entry('ю', 'ᵁ'),
        Map.entry('Я', 'ᴬ'), Map.entry('я', 'ᴬ')
    );

    /**
     * Маппинг цифр на надстрочные Unicode символы
     */
    private static final Map<Character, Character> SUPERSCRIPT_DIGITS = Map.of(
        '0', '⁰',
        '1', '¹',
        '2', '²',
        '3', '³',
        '4', '⁴',
        '5', '⁵',
        '6', '⁶',
        '7', '⁷',
        '8', '⁸',
        '9', '⁹'
    );

    /**
     * Преобразует текст в надстрочный формат используя Unicode символы.
     * 
     * @param text текст для преобразования
     * @return текст с надстрочными символами
     */
    public String toSuperscript(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(SUPERSCRIPT_MAP.getOrDefault(c, c));
        }
        
        return result.toString();
    }

    /**
     * Преобразует число в надстрочный формат используя Unicode символы.
     * 
     * @param number число в виде строки для преобразования
     * @return число с надстрочными цифрами
     */
    public String toSuperscriptNumber(String number) {
        if (number == null || number.isEmpty()) {
            return number;
        }
        
        StringBuilder result = new StringBuilder();
        for (char c : number.toCharArray()) {
            result.append(SUPERSCRIPT_DIGITS.getOrDefault(c, c));
        }
        
        return result.toString();
    }
}
