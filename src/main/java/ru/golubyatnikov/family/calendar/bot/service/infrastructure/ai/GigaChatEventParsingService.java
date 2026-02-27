package ru.golubyatnikov.family.calendar.bot.service.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import ru.golubyatnikov.family.calendar.bot.config.EventParsingConfig;
import ru.golubyatnikov.family.calendar.bot.model.dto.EventParsingResponse;
import ru.golubyatnikov.family.calendar.bot.model.dto.ParsedEvent;
import ru.golubyatnikov.family.calendar.bot.model.dto.ValidationResult;
import ru.golubyatnikov.family.calendar.bot.service.presentation.formatting.DateTimeFormattingService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * Сервис для парсинга событий из текста с использованием GigaChat AI.
 * Реализует паттерн Agent-as-a-Judge.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class GigaChatEventParsingService {

    private final ChatClient parserAgent;
    private final ChatClient judgeAgent;
    private final DateTimeFormattingService dateTimeFormattingService;
    private final EventParsingConfig config;
    private final ObjectMapper objectMapper;

    public EventParsingResponse parseEventFromText(@NonNull String userText, @NonNull String conversationId) {
        try {
            String parserResponse = parserAgent
                    .prompt()
                    .system(buildParserSystemPrompt())
                    .user(userText)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            String judgeResponse = judgeAgent
                    .prompt()
                    .system(buildJudgeSystemPrompt())
                    .user(buildJudgeUserPrompt(userText, parserResponse))
                    .call()
                    .content();

            return processValidationResult(Objects.requireNonNull(judgeResponse));

        } catch (Exception e) {
            log.error("Ошибка при парсинге события через GigaChat: {}", e.getMessage(), e);

            return EventParsingResponse.builder()
                    .success(false)
                    .errorMessage("Не удалось распознать событие. Попробуйте переформулировать запрос.")
                    .build();
        }
    }

    private @NonNull String buildParserSystemPrompt() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate dayAfterTomorrow = today.plusDays(2);
        
        // Вычисляем ближайшие дни недели
        LocalDate nextMonday = getNextDayOfWeek(today, DayOfWeek.MONDAY);
        LocalDate nextTuesday = getNextDayOfWeek(today,DayOfWeek.TUESDAY);
        LocalDate nextWednesday = getNextDayOfWeek(today, DayOfWeek.WEDNESDAY);
        LocalDate nextThursday = getNextDayOfWeek(today, DayOfWeek.THURSDAY);
        LocalDate nextFriday = getNextDayOfWeek(today, DayOfWeek.FRIDAY);
        LocalDate nextSaturday = getNextDayOfWeek(today, DayOfWeek.SATURDAY);
        LocalDate nextSunday = getNextDayOfWeek(today, DayOfWeek.SUNDAY);
        
        return String.format(config.getParserSystemPrompt(),
                dateTimeFormattingService.formatDate(today),
                dateTimeFormattingService.formatDate(tomorrow),
                dateTimeFormattingService.formatDate(dayAfterTomorrow),
                dateTimeFormattingService.formatDate(nextMonday),
                dateTimeFormattingService.formatDate(nextTuesday),
                dateTimeFormattingService.formatDate(nextWednesday),
                dateTimeFormattingService.formatDate(nextThursday),
                dateTimeFormattingService.formatDate(nextFriday),
                dateTimeFormattingService.formatDate(nextSaturday),
                dateTimeFormattingService.formatDate(nextSunday));
    }
    
    private LocalDate getNextDayOfWeek(@NonNull LocalDate from, DayOfWeek targetDay) {
        LocalDate date = from.plusDays(1); // Начинаем с завтрашнего дня
        while (date.getDayOfWeek() != targetDay) {
            date = date.plusDays(1);
        }
        return date;
    }

    private @NonNull String buildJudgeSystemPrompt() {
        return String.format(config.getJudgeSystemPrompt(), dateTimeFormattingService.formatDate(LocalDate.now()));
    }

    private @NonNull String buildJudgeUserPrompt(String userText, String parserResponse) {
        return String.format(config.getJudgeUserPrompt(), userText, parserResponse);
    }

    private EventParsingResponse processValidationResult(@NonNull String judgeResponse) {
        try {
            ValidationResult validation = objectMapper.readValue(extractJson(judgeResponse), ValidationResult.class);
            
            if (validation.valid() && 
                validation.title() != null && !validation.title().isBlank() &&
                validation.date() != null && !validation.date().isBlank() &&
                validation.time() != null && !validation.time().isBlank()) {
                return buildSuccessResponse(validation);
            }
            
            return buildClarificationResponse(validation);
            
        } catch (JsonProcessingException e) {
            log.error("Ошибка парсинга JSON от агента-валидатора: {}", e.getMessage());

            return EventParsingResponse.builder()
                    .success(false)
                    .errorMessage("Не удалось обработать ответ. Попробуйте переформулировать запрос.")
                    .build();
        }
    }

    private EventParsingResponse buildSuccessResponse(@NonNull ValidationResult validation) {
        try {
            LocalDate date = parseDate(validation.date());
            LocalTime time = parseTime(validation.time());
            ParsedEvent parsedEvent = new ParsedEvent(validation.title(), date, time);
            
            if (!parsedEvent.isValid()) {
                return EventParsingResponse.builder()
                        .success(false)
                        .errorMessage("Дата события не может быть в прошлом")
                        .build();
            }
            
            return EventParsingResponse.builder()
                    .success(true)
                    .parsedEvent(parsedEvent)
                    .build();
                    
        } catch (DateTimeParseException e) {
            log.error("Ошибка парсинга даты/времени: {}", e.getMessage());

            return EventParsingResponse.builder()
                    .success(false)
                    .errorMessage("Неверный формат даты или времени")
                    .build();
        }
    }

    private EventParsingResponse buildClarificationResponse(@NonNull ValidationResult validation) {
        String clarification = validation.clarificationQuestion();
        if (clarification == null || clarification.isBlank()) {
            clarification = buildDefaultClarificationQuestion(validation.missingFields());
        }
        
        return EventParsingResponse.builder()
                .success(false)
                .missingFields(validation.missingFields() != null ? 
                        validation.missingFields().toArray(new String[0]) : new String[0])
                .clarificationQuestion(clarification)
                .build();
    }

    private @NonNull String extractJson(@NonNull String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        }
        else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        return trimmed.trim();
    }

    private @NonNull String buildDefaultClarificationQuestion(List<String> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) {
            return "Пожалуйста, уточните данные о событии.";
        }
        
        if (missingFields.size() == 1) {
            return "Пожалуйста, укажите " + missingFields.getFirst() + " события.";
        }
        
        String fields = String.join(", ", missingFields);
        return "Пожалуйста, укажите следующие данные: " + fields + ".";
    }

    private @NonNull LocalDate parseDate(@NonNull String dateStr) {
        for (DateTimeFormatter formatter : dateTimeFormattingService.getDateParseFormatters()) {
            try {
                return LocalDate.parse(dateStr, formatter);

            } catch (DateTimeParseException e) {
                // Пробуем следующий формат
            }
        }
        throw new DateTimeParseException("Не удалось распознать дату", dateStr, 0);
    }

    private @NonNull LocalTime parseTime(@NonNull String timeStr) {
        for (DateTimeFormatter formatter : dateTimeFormattingService.getTimeParseFormatters()) {
            try {
                return LocalTime.parse(timeStr, formatter);

            } catch (DateTimeParseException e) {
                // Пробуем следующий формат
            }
        }
        throw new DateTimeParseException("Не удалось распознать время", timeStr, 0);
    }
}
