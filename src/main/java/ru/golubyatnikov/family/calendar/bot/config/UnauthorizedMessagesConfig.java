package ru.golubyatnikov.family.calendar.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.golubyatnikov.family.calendar.bot.model.enums.MessageCategory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Конфигурация сообщений для неавторизованных пользователей.
 * Загружается из application.yml с префиксом app.bot-messages.unauthorized
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-18
 */
@Configuration
@ConfigurationProperties(prefix = "app.bot-messages.unauthorized")
@Getter
@Setter
public class UnauthorizedMessagesConfig {
    
    /**
     * Префикс для всех сообщений
     */
    private String prefix = "🔒";
    
    /**
     * Текст инструкции по получению доступа
     */
    private String contactAdmin = "Для получения доступа обратитесь к администратору вашей семьи.";
    
    /**
     * Шаблоны сообщений для разных категорий
     */
    private Templates templates = new Templates();
    
    @Getter
    @Setter
    public static class Templates {
        private String eventCreation;
        private String eventViewing;
        private String eventManagement;
        private String searchFilter;
        private String trashManagement;
        private String statistics;
        private String general;
    }
    
    /**
     * Преобразует конфигурацию в Map для удобного доступа по категориям.
     *
     * @return Map с шаблонами для каждой категории
     */
    public Map<MessageCategory, String> toTemplateMap() {
        Map<MessageCategory, String> map = new EnumMap<>(MessageCategory.class);
        map.put(MessageCategory.EVENT_CREATION, templates.getEventCreation());
        map.put(MessageCategory.EVENT_VIEWING, templates.getEventViewing());
        map.put(MessageCategory.EVENT_MANAGEMENT, templates.getEventManagement());
        map.put(MessageCategory.SEARCH_FILTER, templates.getSearchFilter());
        map.put(MessageCategory.TRASH_MANAGEMENT, templates.getTrashManagement());
        map.put(MessageCategory.STATISTICS, templates.getStatistics());
        map.put(MessageCategory.GENERAL, templates.getGeneral());
        return map;
    }
}
