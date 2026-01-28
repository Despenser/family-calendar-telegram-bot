# Requirements Document

## Introduction

Исправление бага с фильтрацией завершенных событий в команде /my_events. После завершения нескольких событий и оставления одного активного, команда /my_events некорректно показывает "нет событий", хотя активное событие существует в базе данных.

## Glossary

- **System**: Telegram бот семейного календаря
- **Active_Event**: Событие со статусом ACTIVE
- **Completed_Event**: Событие со статусом COMPLETED
- **My_Events_Command**: Команда /my_events для отображения активных событий пользователя

## Requirements

### Requirement 1

**User Story:** Как пользователь, я хочу видеть все свои активные события при вызове /my_events, чтобы управлять ими независимо от того, сколько событий я завершил ранее.

#### Acceptance Criteria

1. WHEN пользователь вызывает /my_events THEN THE System SHALL отобразить все события со статусом ACTIVE
2. WHEN пользователь завершает несколько событий и оставляет одно активное THEN THE System SHALL отобразить это активное событие при вызове /my_events
3. WHEN пользователь вызывает /my_events повторно THEN THE System SHALL отобразить тот же список активных событий
4. WHEN в базе данных есть активные события THEN THE System SHALL корректно их извлекать независимо от количества завершенных событий

### Requirement 2

**User Story:** Как разработчик, я хочу иметь подробное логирование работы метода getUserEvents, чтобы быстро диагностировать проблемы с фильтрацией событий.

#### Acceptance Criteria

1. WHEN метод getUserEvents вызывается THEN THE System SHALL логировать запрос с userId на уровне INFO
2. WHEN метод getUserEvents возвращает результат THEN THE System SHALL логировать количество найденных событий на уровне INFO
3. WHEN происходит ошибка при получении событий THEN THE System SHALL логировать детали ошибки на уровне ERROR
