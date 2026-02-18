package ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import ru.golubyatnikov.family.calendar.bot.config.UnauthorizedMessagesConfig;
import ru.golubyatnikov.family.calendar.bot.model.enums.MessageCategory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Провайдер шаблонов сообщений с поддержкой fallback на дефолтные значения.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Component
@Slf4j
public class MessageTemplateProvider {
    
    private static final Map<MessageCategory, String> DEFAULT_TEMPLATES = Map.of(
        MessageCategory.EVENT_CREATION, "Создание событий доступно только зарегистрированным пользователям семейного календаря.",
        MessageCategory.EVENT_VIEWING, "Просмотр событий доступен только членам семейного календаря.",
        MessageCategory.EVENT_MANAGEMENT, "Управление событиями доступно только зарегистрированным пользователям.",
        MessageCategory.SEARCH_FILTER, "Поиск и фильтрация событий доступны только членам семейного календаря.",
        MessageCategory.TRASH_MANAGEMENT, "Управление корзиной доступно только зарегистрированным пользователям.",
        MessageCategory.STATISTICS, "Просмотр статистики доступен только членам семейного календаря.",
        MessageCategory.GENERAL, "Эта функция доступна только зарегистрированным пользователям семейного календаря."
    );
    
    private final Map<MessageCategory, String> templates;
    
    public MessageTemplateProvider(UnauthorizedMessagesConfig config) {
        this.templates = initializeTemplates(config);
    }
    
    /**
     * Получает шаблон для указанной категории.
     * Если шаблон не найден, возвращает общий шаблон или дефолтный.
     *
     * @param category категория сообщения
     * @return шаблон сообщения
     */
    public String getTemplate(MessageCategory category) {
        String template = templates.get(category);
        
        if (template != null) {
            return template;
        }
        
        log.debug("Шаблон для категории {} не найден, используется общий шаблон", category);
        template = templates.get(MessageCategory.GENERAL);
        
        return template != null ? template : DEFAULT_TEMPLATES.get(MessageCategory.GENERAL);
    }
    
    /**
     * Инициализирует шаблоны из конфигурации с fallback на дефолтные значения.
     */
    private @NonNull Map<MessageCategory, String> initializeTemplates(@NonNull UnauthorizedMessagesConfig config) {
        Map<MessageCategory, String> configTemplates = config.toTemplateMap();
        Map<MessageCategory, String> result = new EnumMap<>(MessageCategory.class);
        
        for (MessageCategory category : MessageCategory.values()) {
            String template = configTemplates.get(category);
            
            if (isBlank(template)) {
                template = DEFAULT_TEMPLATES.get(category);
                log.debug("Используется дефолтный шаблон для категории {}", category);
            }
            
            result.put(category, template);
        }
        return result;
    }
    
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
