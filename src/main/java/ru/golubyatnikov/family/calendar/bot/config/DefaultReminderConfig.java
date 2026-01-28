package ru.golubyatnikov.family.calendar.bot.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ru.golubyatnikov.family.calendar.bot.model.Reminder.ReminderType;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурация автоматических напоминаний по умолчанию.
 * Загружает настройки из application.yml (секция reminder.defaults).
 * 
 * <p>Позволяет настроить:
 * <ul>
 *   <li>Глобальное включение/отключение автоматических напоминаний</li>
 *   <li>Список типов напоминаний, создаваемых автоматически</li>
 * </ul>
 * 
 * @author Golubyatnikov Aleksey
 * @version 1.0.0
 * @since 2026-01-27
 * @see ReminderType
 */
@Configuration
@ConfigurationProperties(prefix = "reminder.defaults")
@Getter
@Setter
@Slf4j
public class DefaultReminderConfig {
    
    /**
     * Флаг глобального включения/отключения автоматических напоминаний.
     * По умолчанию: true (включено)
     */
    private boolean enabled = true;
    
    /**
     * Список типов напоминаний, создаваемых автоматически при создании события.
     * По умолчанию: [EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE]
     */
    private List<ReminderType> types = new ArrayList<>(List.of(
        ReminderType.EVENING_BEFORE,
        ReminderType.ONE_HOUR_BEFORE,
        ReminderType.FIFTEEN_MINUTES_BEFORE
    ));
    
    /**
     * Инициализация конфигурации после загрузки свойств.
     * Логирует состояние автоматических напоминаний при старте приложения.
     * 
     * <p>Если автоматические напоминания отключены глобально (enabled = false),
     * логирует предупреждение с уровнем WARN.</p>
     * 
     * <p><b>Требование:</b> 10.5</p>
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.warn("⚠️ Автоматические напоминания отключены глобально (reminder.defaults.enabled=false). " +
                    "Напоминания не будут создаваться автоматически при создании событий.");
        } else {
            log.info("✅ Автоматические напоминания включены. Типы по умолчанию: {}", types);
        }
    }
}
