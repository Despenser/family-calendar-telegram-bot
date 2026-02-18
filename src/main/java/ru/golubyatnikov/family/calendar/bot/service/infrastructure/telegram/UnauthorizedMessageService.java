package ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.enums.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.TemplateParameterSubstitutor;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.UnauthorizedMessageFormatter;

import java.util.Map;

/**
 * Сервис для формирования сообщений об ограничении доступа для неавторизованных пользователей.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-12
 */
@Service
@RequiredArgsConstructor
public class UnauthorizedMessageService {
    
    private final MessageTemplateProvider templateProvider;
    private final TemplateParameterSubstitutor parameterSubstitutor;
    private final UnauthorizedMessageFormatter messageFormatter;
    
    /**
     * Получает сообщение для указанной категории без параметров.
     *
     * @param category категория сообщения
     *
     * @return отформатированное сообщение с экранированными специальными символами MarkdownV2
     * @throws IllegalArgumentException если category равен null
     */
    public String getMessage(MessageCategory category) {
        return getMessage(category, Map.of());
    }
    
    /**
     * Получает сообщение для указанной категории с подстановкой параметров.
     *
     * @param category категория сообщения
     * @param parameters параметры для подстановки в шаблон
     *
     * @return отформатированное сообщение с подставленными параметрами
     * @throws IllegalArgumentException если category равен null
     */
    public String getMessage(MessageCategory category, Map<String, String> parameters) {
        if (category == null) {
            throw new IllegalArgumentException("Категория сообщения не может быть null");
        }
        
        String template = templateProvider.getTemplate(category);
        String messageText = parameterSubstitutor.substitute(template, parameters);
        
        return messageFormatter.format(messageText);
    }
}
