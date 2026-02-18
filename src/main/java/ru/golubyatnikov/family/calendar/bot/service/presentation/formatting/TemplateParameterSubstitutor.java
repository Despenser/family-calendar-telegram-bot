package ru.golubyatnikov.family.calendar.bot.service.presentation.formatting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Компонент для подстановки параметров в шаблоны сообщений.
 * Заменяет плейсхолдеры вида {parameter} на соответствующие значения.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Component
@Slf4j
public class TemplateParameterSubstitutor {
    
    /**
     * Подставляет параметры в шаблон.
     *
     * @param template шаблон с плейсхолдерами вида {parameter}
     * @param parameters параметры для подстановки
     *
     * @return текст с подставленными параметрами
     */
    public String substitute(String template, Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return template;
        }
        
        String result = template;
        
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (entry.getValue() != null) {
                String placeholder = "{" + entry.getKey() + "}";
                result = result.replace(placeholder, entry.getValue());
                log.trace("Подставлен параметр {} = {}", entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }
}
