package ru.golubyatnikov.family.calendar.bot.service.infrastructure.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.model.enums.MessageCategory;
import ru.golubyatnikov.family.calendar.bot.util.MarkdownFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * //TODO разобраться отдельно но я бы провел рефакторинг
 * Сервис для формирования сообщений об ограничении доступа для неавторизованных пользователей.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-12
 */
@Service
@Slf4j
public class UnauthorizedMessageService {
    
    private final Map<MessageCategory, String> messageTemplates;
    private final String prefix;
    private final String contactAdmin;
    
    // Дефолтные сообщения на случай отсутствия конфигурации
    private static final Map<MessageCategory, String> DEFAULT_TEMPLATES = Map.of(
        MessageCategory.EVENT_CREATION, "Создание событий доступно только зарегистрированным пользователям семейного календаря.",
        MessageCategory.EVENT_VIEWING, "Просмотр событий доступен только членам семейного календаря.",
        MessageCategory.EVENT_MANAGEMENT, "Управление событиями доступно только зарегистрированным пользователям.",
        MessageCategory.SEARCH_FILTER, "Поиск и фильтрация событий доступны только членам семейного календаря.",
        MessageCategory.TRASH_MANAGEMENT, "Управление корзиной доступно только зарегистрированным пользователям.",
        MessageCategory.STATISTICS, "Просмотр статистики доступен только членам семейного календаря.",
        MessageCategory.GENERAL, "Эта функция доступна только зарегистрированным пользователям семейного календаря."
    );
    
    /**
     * Конструктор для инициализации сервиса с конфигурацией из application.yml.
     *
     * @param prefix префикс для всех сообщений
     * @param contactAdmin текст инструкции по получению доступа
     * @param eventCreation шаблон сообщения для создания событий
     * @param eventViewing шаблон сообщения для просмотра событий
     * @param eventManagement шаблон сообщения для управления событиями
     * @param searchFilter шаблон сообщения для поиска и фильтрации
     * @param trashManagement шаблон сообщения для управления корзиной
     * @param statistics шаблон сообщения для статистики
     * @param general общий шаблон сообщения
     */
    public UnauthorizedMessageService(
            @Value("${bot.messages.unauthorized.prefix:🔒}") String prefix,
            @Value("${bot.messages.unauthorized.contact-admin:Для получения доступа обратитесь к администратору вашей семьи.}") String contactAdmin,
            @Value("${bot.messages.unauthorized.templates.event-creation:#{null}}") String eventCreation,
            @Value("${bot.messages.unauthorized.templates.event-viewing:#{null}}") String eventViewing,
            @Value("${bot.messages.unauthorized.templates.event-management:#{null}}") String eventManagement,
            @Value("${bot.messages.unauthorized.templates.search-filter:#{null}}") String searchFilter,
            @Value("${bot.messages.unauthorized.templates.trash-management:#{null}}") String trashManagement,
            @Value("${bot.messages.unauthorized.templates.statistics:#{null}}") String statistics,
            @Value("${bot.messages.unauthorized.templates.general:#{null}}") String general) {

        this.prefix = prefix;
        this.contactAdmin = contactAdmin;
        this.messageTemplates = initializeMessages(
            eventCreation, eventViewing, eventManagement, 
            searchFilter, trashManagement, statistics, general
        );
    }
    
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
        
        String template = messageTemplates.get(category);
        
        if (template == null) {
            template = messageTemplates.get(MessageCategory.GENERAL);
            
            if (template == null) {
                template = DEFAULT_TEMPLATES.get(MessageCategory.GENERAL);
                
                if (template == null) {
                    return """
                            🔒 Эта функция доступна только зарегистрированным пользователям\\.\
                            
                            
                            Для получения доступа обратитесь к администратору\\.""";
                }
            }
        }
        
        try {
            // Подстановка параметров в шаблон ДО экранирования
            String messageText = substituteParameters(template, parameters);
            
            // Форматирование финального сообщения (с экранированием)
            return formatMessage(messageText);
            
        } catch (Exception e) {
            
            // Возвращаем простое сообщение без параметров в случае ошибки
            try {
                return formatMessage(template);
            } catch (Exception e2) {
                return """
                        🔒 Эта функция доступна только зарегистрированным пользователям\\.\
                        
                        
                        Для получения доступа обратитесь к администратору\\.""";
            }
        }
    }
    
    /**
     * Инициализирует сообщения для всех категорий из конфигурации.
     *
     * @param eventCreation шаблон для создания событий
     * @param eventViewing шаблон для просмотра событий
     * @param eventManagement шаблон для управления событиями
     * @param searchFilter шаблон для поиска и фильтрации
     * @param trashManagement шаблон для управления корзиной
     * @param statistics шаблон для статистики
     * @param general общий шаблон
     *
     * @return Map с шаблонами для каждой категории
     */
    private Map<MessageCategory, String> initializeMessages(
            String eventCreation, String eventViewing, String eventManagement,
            String searchFilter, String trashManagement, String statistics, String general) {
        
        Map<MessageCategory, String> templates = new EnumMap<>(MessageCategory.class);
        
        templates.put(MessageCategory.EVENT_CREATION, 
            loadTemplateWithFallback(MessageCategory.EVENT_CREATION, eventCreation));
        
        templates.put(MessageCategory.EVENT_VIEWING,
            loadTemplateWithFallback(MessageCategory.EVENT_VIEWING, eventViewing));
        
        templates.put(MessageCategory.EVENT_MANAGEMENT,
            loadTemplateWithFallback(MessageCategory.EVENT_MANAGEMENT, eventManagement));
        
        templates.put(MessageCategory.SEARCH_FILTER,
            loadTemplateWithFallback(MessageCategory.SEARCH_FILTER, searchFilter));
        
        templates.put(MessageCategory.TRASH_MANAGEMENT,
            loadTemplateWithFallback(MessageCategory.TRASH_MANAGEMENT, trashManagement));
        
        templates.put(MessageCategory.STATISTICS,
            loadTemplateWithFallback(MessageCategory.STATISTICS, statistics));
        
        templates.put(MessageCategory.GENERAL,
            loadTemplateWithFallback(MessageCategory.GENERAL, general));
        
        return templates;
    }
    
    /**
     * Загружает шаблон из конфигурации с fallback на дефолтное значение.
     * 
     * <p>Если шаблон из конфигурации равен null или пустой строке, используется
     * дефолтное значение с логированием предупреждения.</p>
     * 
     * @param category категория сообщения
     * @param configuredTemplate шаблон из конфигурации (может быть null)
     * @return шаблон из конфигурации или дефолтное значение
     */
    private String loadTemplateWithFallback(MessageCategory category, String configuredTemplate) {
        if (configuredTemplate == null || configuredTemplate.trim().isEmpty()) {
            log.warn("Шаблон для категории {} отсутствует в конфигурации, используется дефолтное значение", 
                    category);
            return DEFAULT_TEMPLATES.get(category);
        }
        
        return configuredTemplate;
    }
    
    /**
     * Подставляет параметры в шаблон сообщения.
     * 
     * <p>Заменяет плейсхолдеры вида {parameter} на соответствующие значения
     * из Map параметров. Если параметр не найден, плейсхолдер остается без изменений.</p>
     * 
     * <p>Поддерживаемые параметры:</p>
     * <ul>
     *   <li>{command} - имя команды</li>
     *   <li>{feature} - название функции</li>
     * </ul>
     * 
     * <p><b>Обработка ошибок:</b> Ошибки подстановки логируются, но не прерывают выполнение.
     * В случае ошибки возвращается исходный шаблон.</p>
     * 
     * @param template шаблон с плейсхолдерами
     * @param parameters Map с параметрами для подстановки
     * @return текст с подставленными параметрами
     */
    private String substituteParameters(String template, Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return template;
        }
        
        try {
            String result = template;
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue();
                
                if (value != null) {
                    result = result.replace(placeholder, value);
                    log.trace("Подставлен параметр {} = {} в шаблон", entry.getKey(), value);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Ошибка при подстановке параметров в шаблон: {}", e.getMessage());
            return template;
        }
    }
    
    /**
     * Форматирует сообщение с префиксом и инструкциями.
     * 
     * <p>Создает полное сообщение, состоящее из:</p>
     * <ol>
     *   <li>Префикса (эмодзи "🔒")</li>
     *   <li>Основного текста с объяснением ограничения</li>
     *   <li>Инструкций по получению доступа</li>
     * </ol>
     * 
     * <p>Все специальные символы MarkdownV2 экранируются с помощью
     * {@link MarkdownFormatter#formatMessage(String, Object...)} для корректного отображения в Telegram.</p>
     * 
     * <p><b>Обработка ошибок:</b> Ошибки форматирования логируются. В случае ошибки
     * возвращается простое сообщение без форматирования.</p>
     * 
     * @param mainText основной текст сообщения
     * @return отформатированное сообщение с экранированными специальными символами
     */
    private String formatMessage(String mainText) {
        try {
            return MarkdownFormatter.formatMessage("%s %s\n\n%s",
                prefix,
                mainText,
                contactAdmin);
        } catch (Exception e) {
            log.error("Ошибка при форматировании сообщения: {}", e.getMessage());
            
            // Возвращаем простое сообщение без форматирования в случае ошибки
            try {
                return String.format("%s %s\n\n%s", prefix, mainText, contactAdmin);
            } catch (Exception e2) {
                log.error("Критическая ошибка при форматировании fallback сообщения: {}", e2.getMessage());
                return prefix + " " + mainText + "\n\n" + contactAdmin;
            }
        }
    }
}
