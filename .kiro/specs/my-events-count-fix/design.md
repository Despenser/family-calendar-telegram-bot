# Проектирование: Исправление подсчета событий в команде /my_events

## Overview

Данный документ описывает проектное решение для исправления ошибки подсчета событий в команде `/my_events`. Проблема заключается в том, что метод `getActiveEventsCount` в `EventService` считает все события, кроме удаленных, включая черновики и завершенные события. Это приводит к несоответствию между количеством в шапке и фактически отображаемыми событиями.

Решение заключается в изменении логики подсчета событий, чтобы учитывались только события со статусом `ACTIVE`.

## Architecture

Изменения затрагивают следующие компоненты:

1. **EventRepository** - добавление нового метода для подсчета только активных событий
2. **EventService** - изменение метода `getActiveEventsCount` для использования нового метода репозитория

Архитектура остается прежней - многослойная структура с четким разделением ответственности.

## Components and Interfaces

### EventRepository

Добавляется новый метод:

```java
/**
 * Подсчитывает количество событий пользователя со статусом ACTIVE.
 * 
 * @param userId идентификатор пользователя
 * @param status статус события (ACTIVE)
 * @return количество активных событий
 */
int countByUserIdAndStatus(Long userId, Event.EventStatus status);
```

### EventService

Изменяется существующий метод `getActiveEventsCount`:

```java
/**
 * Получает количество активных событий пользователя.
 * 
 * <p>Подсчитывает только события со статусом ACTIVE, исключая:</p>
 * <ul>
 *   <li>Удаленные события (DELETED)</li>
 *   <li>Черновики (DRAFT)</li>
 *   <li>Завершенные события (COMPLETED)</li>
 * </ul>
 * 
 * @param userId идентификатор пользователя
 * @return количество активных событий пользователя
 */
public int getActiveEventsCount(Long userId) {
    int count = eventRepository.countByUserIdAndStatus(userId, Event.EventStatus.ACTIVE);
    log.debug("Подсчитано активных событий для пользователя ID={}: {}", userId, count);
    return count;
}
```

## Data Models

Изменения в моделях данных не требуются. Используются существующие:

- **Event** - сущность события с полем `status` типа `EventStatus`
- **EventStatus** - enum со значениями: ACTIVE, DRAFT, COMPLETED, DELETED

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Подсчет соответствует отображению

*For any* пользователя, количество событий, возвращаемое методом `getActiveEventsCount`, должно быть равно количеству событий, возвращаемых методом `getUserEvents` (который возвращает только события со статусом ACTIVE).

**Validates: Requirements 1.1, 1.2**

### Property 2: Исключение неактивных событий

*For any* пользователя, метод `getActiveEventsCount` должен возвращать количество, которое не включает события со статусами DRAFT, COMPLETED или DELETED.

**Validates: Requirements 1.1**

### Property 3: Консистентность после редактирования

*For any* события, после его редактирования количество активных событий пользователя должно оставаться неизменным (если событие не меняло статус).

**Validates: Requirements 1.3, 1.4**

### Property 4: Консистентность после удаления

*For any* события со статусом ACTIVE, после его удаления количество активных событий пользователя должно уменьшиться на 1.

**Validates: Requirements 1.5**

## Error Handling

Обработка ошибок не изменяется. Метод `getActiveEventsCount` не выбрасывает исключений, так как:

1. Если пользователь не существует, метод вернет 0
2. Если у пользователя нет событий, метод вернет 0

Это соответствует текущему поведению системы.

## Testing Strategy

### Unit Tests

1. **Тест метода countByUserIdAndStatus в EventRepository**
   - Создать пользователя с событиями разных статусов
   - Вызвать метод с параметром ACTIVE
   - Проверить, что возвращается только количество событий со статусом ACTIVE

2. **Тест метода getActiveEventsCount в EventService**
   - Создать пользователя с событиями разных статусов (ACTIVE, DRAFT, COMPLETED, DELETED)
   - Вызвать метод getActiveEventsCount
   - Проверить, что возвращается только количество событий со статусом ACTIVE

3. **Интеграционный тест команды /my_events**
   - Создать пользователя с 2 активными событиями, 1 черновиком и 1 завершенным
   - Вызвать команду /my_events
   - Проверить, что в шапке отображается "Всего событий: 2"
   - Проверить, что отображается ровно 2 события

### Property-Based Tests

Для данного исправления property-based тесты не требуются, так как логика простая и полностью покрывается unit-тестами. Основная проверка - это соответствие количества в шапке количеству отображаемых событий, что проверяется интеграционным тестом.

## Implementation Notes

1. **Обратная совместимость**: Изменение не нарушает существующую функциональность, так как метод `getActiveEventsCount` используется только для отображения количества в шапке "Мои события"

2. **Производительность**: Новый метод `countByUserIdAndStatus` будет использовать тот же индекс, что и старый метод `countByUserIdAndStatusNot`, поэтому производительность не изменится

3. **Миграция данных**: Не требуется, так как изменения касаются только логики подсчета

4. **Места использования**: Метод `getActiveEventsCount` используется в следующих местах:
   - `EventService.sendOrUpdateEventMessage` - для формирования шапки при обновлении сообщения первого события
   - `EventCallbackHandler.handleEditCallback` - для формирования шапки после редактирования
   - `DateTimeCallbackHandler` - для формирования шапки после изменения даты/времени
   - `UpdateProcessor` - для формирования шапки после обновления события

Все эти места будут автоматически использовать исправленную логику подсчета.
