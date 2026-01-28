# Design Document

## Overview

Исправление бага с фильтрацией завершенных событий в команде /my_events. Проблема возникает из-за того, что метод `getUserEvents` использует уровень логирования DEBUG, что затрудняет диагностику, и возможно есть проблема с кэшированием или транзакциями при получении событий.

## Architecture

Проблема локализована в следующих компонентах:
- `EventService.getUserEvents()` - метод получения активных событий пользователя
- `MyEventsCommandHandler.handle()` - обработчик команды /my_events
- `EventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc()` - репозиторий для получения событий

## Root Cause Analysis

### Потенциальные причины проблемы:

1. **Кэширование на уровне Hibernate/JPA**: Возможно, Hibernate кэширует результаты запроса и не обновляет их после завершения событий
2. **Транзакционная изоляция**: Метод `getUserEvents` помечен как `@Transactional(readOnly = true)`, что может привести к чтению устаревших данных
3. **Недостаточное логирование**: Логи на уровне DEBUG не позволяют диагностировать проблему в production

## Components and Interfaces

### EventService

```java
/**
 * Получает активные события пользователя с улучшенным логированием.
 */
@Transactional(readOnly = true)
public List<Event> getUserEvents(Long userId) {
    log.info("Получение активных событий пользователя ID={}", userId);
    
    List<Event> events = eventRepository.findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(
        userId, 
        Event.EventStatus.ACTIVE
    );
    
    log.info("Найдено {} активных событий для пользователя ID={}", events.size(), userId);
    
    // Дополнительное логирование для диагностики
    if (log.isDebugEnabled()) {
        events.forEach(event -> 
            log.debug("  - Событие ID={}, title='{}', status={}, date={}", 
                event.getId(), event.getTitle(), event.getStatus(), event.getEventDate())
        );
    }
    
    return events;
}
```

### EventRepository

Добавим явное указание на то, что нужно игнорировать кэш:

```java
@EntityGraph(attributePaths = {"user", "family"})
@QueryHints(@QueryHint(name = org.hibernate.annotations.QueryHints.CACHEABLE, value = "false"))
List<Event> findByUserIdAndStatusOrderByEventDateAscEventTimeAsc(Long userId, Event.EventStatus status);
```

## Data Models

Изменений в моделях данных не требуется.

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Active events retrieval consistency

*For any* user with active events in the database, calling `getUserEvents` multiple times in succession should return the same list of active events (assuming no events are created, completed, or deleted between calls).

**Validates: Requirements 1.1, 1.3**

### Property 2: Status filter correctness

*For any* user, the `getUserEvents` method should return only events with status ACTIVE, excluding all events with status COMPLETED or DELETED.

**Validates: Requirements 1.1, 1.4**

### Property 3: Logging completeness

*For any* call to `getUserEvents`, the system should log both the request (with userId) and the result (with event count) at INFO level.

**Validates: Requirements 2.1, 2.2**

## Error Handling

- Если `getUserEvents` возвращает пустой список, логировать это как INFO, а не как ошибку
- Если происходит исключение при получении событий, логировать полный stack trace на уровне ERROR
- Добавить try-catch блок в `MyEventsCommandHandler` для обработки неожиданных исключений

## Testing Strategy

### Unit Tests

1. Тест на корректность фильтрации по статусу:
   - Создать события с разными статусами (ACTIVE, COMPLETED, DELETED)
   - Вызвать `getUserEvents`
   - Проверить, что возвращаются только ACTIVE события

2. Тест на повторяемость результатов:
   - Вызвать `getUserEvents` дважды подряд
   - Проверить, что результаты идентичны

### Integration Tests

1. Тест на реальной базе данных:
   - Создать несколько событий
   - Завершить часть из них
   - Вызвать `/my_events`
   - Проверить, что отображаются только активные события

### Property-Based Tests

Минимум 100 итераций для каждого теста.

**Property Test 1: Active events retrieval consistency**
- Генерировать случайного пользователя с случайным набором событий
- Вызывать `getUserEvents` дважды
- Проверять, что результаты идентичны
- **Feature: my-events-completed-filter-bug, Property 1: Active events retrieval consistency**

**Property Test 2: Status filter correctness**
- Генерировать случайного пользователя с событиями разных статусов
- Вызывать `getUserEvents`
- Проверять, что все возвращенные события имеют статус ACTIVE
- **Feature: my-events-completed-filter-bug, Property 2: Status filter correctness**

## Implementation Notes

1. Изменить уровень логирования в `getUserEvents` с DEBUG на INFO
2. Добавить `@QueryHints` для отключения кэширования в репозитории
3. Добавить дополнительное логирование в `MyEventsCommandHandler` для диагностики
4. Рассмотреть возможность добавления `entityManager.clear()` перед вызовом `getUserEvents` для очистки кэша первого уровня
