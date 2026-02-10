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
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-01-27
 */
@Getter
@Setter
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "reminder.defaults")
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
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.warn("Автоматические напоминания отключены глобально (reminder.defaults.enabled=false). " +
                    "Напоминания не будут создаваться автоматически при создании событий.");
        } else {
            log.info("Автоматические напоминания включены. Типы по умолчанию: {}", types);
        }
    }
}
