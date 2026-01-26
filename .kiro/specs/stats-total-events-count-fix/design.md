# Design Document

## Overview

Исправление логики подсчета общего количества событий в статистике. Текущая реализация считает только активные события, что приводит к некорректному отображению статистики. Необходимо изменить подсчет так, чтобы "Всего событий" показывало сумму активных и завершенных событий.

## Architecture

Изменения затрагивают два компонента:
1. **StatisticsService** - изменение логики подсчета totalEvents
2. **StatsCommandHandler** - удаление текста "(только активные)" из вывода

## Components and Interfaces

### StatisticsService

**Метод:** `getMonthlyStatistics(Long familyId, Long userId, int year, int month)`

**Текущая логика:**
```java
long totalEvents = eventRepository.countByFamilyIdAndEventDateBetweenAndStatus(
    familyId, startDate, endDate, Event.EventStatus.ACTIVE
);
```

**Новая логика:**
```java
long activeEvents = eventRepository.countByFamilyIdAndEventDateBetweenAndStatus(
    familyId, startDate, endDate, Event.EventStatus.ACTIVE
);

long completedEvents = eventRepository.countByFamilyIdAndEventDateBetweenAndStatus(
    familyId, startDate, endDate, Event.EventStatus.COMPLETED
);

long totalEvents = activeEvents + completedEvents;
```

### StatsCommandHandler

**Изменение вывода:**

Текущий код (строка 92-95):
```java
messageBuilder.append(escape("• Всего событий: "))
              .append(bold(String.valueOf(stats.getTotalEvents())))
              .append(escape(" (только активные)"))
              .append(escape("\n"));
```

Новый код:
```java
messageBuilder.append(escape("• Всего событий: "))
              .append(bold(String.valueOf(stats.getTotalEvents())))
              .append(escape("\n"));
```

## Data Models

Изменений в моделях данных не требуется. Используются существующие поля класса `EventStatistics`:
- `totalEvents` - теперь будет содержать сумму активных и завершенных событий
- `activeEvents` - количество активных событий
- `completedEvents` - количество завершенных событий

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Total events equals sum of active and completed

*For any* family and time period, the total events count should equal the sum of active events and completed events.

**Validates: Requirements 1.1**

### Property 2: Total events is never less than active or completed

*For any* statistics result, total events should be greater than or equal to both active events count and completed events count.

**Validates: Requirements 1.1**

## Error Handling

Изменения не влияют на обработку ошибок. Существующая обработка исключений в `StatsCommandHandler` остается без изменений.

## Testing Strategy

### Unit Tests

1. **StatisticsService.getMonthlyStatistics():**
   - Тест с только активными событиями
   - Тест с только завершенными событиями
   - Тест со смешанными активными и завершенными событиями
   - Тест с нулевым количеством событий

2. **StatsCommandHandler.handle():**
   - Проверка отсутствия текста "(только активные)" в выводе
   - Проверка корректного отображения totalEvents

### Property-Based Tests

Минимум 100 итераций для каждого теста.

**Property Test 1: Total equals sum**
- Генерировать случайное количество активных и завершенных событий
- Проверять, что totalEvents = activeEvents + completedEvents
- **Feature: stats-total-events-count-fix, Property 1: Total events equals sum of active and completed**

**Property Test 2: Total is maximum**
- Генерировать случайное количество активных и завершенных событий
- Проверять, что totalEvents >= activeEvents и totalEvents >= completedEvents
- **Feature: stats-total-events-count-fix, Property 2: Total events is never less than active or completed**
