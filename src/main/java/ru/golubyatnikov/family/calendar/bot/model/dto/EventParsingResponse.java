package ru.golubyatnikov.family.calendar.bot.model.dto;

import lombok.Builder;

/**
 * DTO для ответа парсинга события.
 *
 * @param success успешность парсинга
 * @param parsedEvent распознанное событие (если успешно)
 * @param missingFields недостающие поля для создания события
 * @param clarificationQuestion вопрос для уточнения недостающих данных
 * @param errorMessage сообщение об ошибке (если не успешно)
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-27
 */
@Builder
public record EventParsingResponse(
        boolean success,
        ParsedEvent parsedEvent,
        String[] missingFields,
        String clarificationQuestion,
        String errorMessage
) { }
