package ru.golubyatnikov.family.calendar.bot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация AI агентов для парсинга событий.
 * Создает бины для агента-парсера и агента-валидатора (Judge).
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-27
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class GigaChatAgentsConfig {

    private final ChatClient.Builder chatClientBuilder;
    private final EventParsingConfig eventParsingConfig;

    /**
     * Создает бин агента-парсера с ChatMemory для сохранения контекста диалога.
     * Агент извлекает данные о событии из текста пользователя.
     *
     * @return настроенный ChatClient для парсинга
     */
    @Bean
    public ChatClient parserAgent() {
        log.info("Инициализация агента-парсера событий с ChatMemory (maxMessages: {})", 
                eventParsingConfig.getMaxConversationHistorySize());
        
        ChatMemory parserMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(eventParsingConfig.getMaxConversationHistorySize())
                .build();

        return chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(parserMemory).build())
                .build();
    }

    /**
     * Создает бин агента-валидатора (Judge) без ChatMemory.
     * Агент проверяет корректность и полноту данных, извлеченных парсером.
     *
     * @return настроенный ChatClient для валидации
     */
    @Bean
    public ChatClient judgeAgent() {
        log.info("Инициализация агента-валидатора (Judge) событий");
        
        return chatClientBuilder.build();
    }
}
