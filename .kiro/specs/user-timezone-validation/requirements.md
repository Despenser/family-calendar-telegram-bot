# Документ требований: Валидация дат с учетом таймзоны пользователя

## Введение

Система семейного календаря должна корректно валидировать даты событий с учетом таймзоны пользователя. В настоящее время календарь использует серверное время (UTC) для определения текущей даты, что приводит к некорректной валидации: пользователи в таймзоне UTC+3 (Москва) могут создавать события в прошлом, так как серверное время отстает на 3 часа.

## Глоссарь

- **System**: Telegram-бот семейного календаря
- **User**: Пользователь Telegram-бота
- **Event**: Событие в календаре с датой и временем
- **Timezone**: Часовой пояс пользователя (например, Europe/Moscow для UTC+3)
- **Server_Time**: Время на сервере (обычно UTC)
- **User_Time**: Текущее время в таймзоне пользователя
- **Calendar**: Интерактивный календарь для выбора даты события
- **Past_Date**: Дата, которая уже прошла в таймзоне пользователя
- **Valid_Date**: Дата, которая является текущей или будущей в таймзоне пользователя

## Требования

### Requirement 1: Хранение таймзоны пользователя

**User Story:** Как пользователь, я хочу, чтобы система знала мой часовой пояс, чтобы корректно валидировать даты событий.

#### Acceptance Criteria

1. THE System SHALL store the timezone for each User in the database
2. WHEN a User registers, THE System SHALL detect and save their timezone based on Telegram client data
3. WHEN timezone data is unavailable, THE System SHALL use UTC+3 (Europe/Moscow) as the default timezone
4. THE System SHALL allow Users to update their timezone through settings

### Requirement 2: Валидация дат при создании события

**User Story:** Как пользователь, я хочу, чтобы календарь не позволял мне выбирать прошедшие даты, чтобы избежать создания событий в прошлом.

#### Acceptance Criteria

1. WHEN displaying the Calendar, THE System SHALL calculate the current date using User_Time
2. WHEN a date is in the past relative to User_Time, THE System SHALL display it as an empty cell without text
3. WHEN a date is today or in the future relative to User_Time, THE System SHALL display it as a selectable button
4. WHEN a User attempts to select a Past_Date, THE System SHALL prevent the selection
5. THE System SHALL mark the current date in User_Time with a visual indicator (📍)

### Requirement 3: Валидация времени при создании события

**User Story:** Как пользователь, я хочу, чтобы система не позволяла мне создавать события в прошлом времени сегодняшнего дня, чтобы все мои события были актуальными.

#### Acceptance Criteria

1. WHEN a User selects today's date, THE System SHALL validate that the selected time is not in the past relative to User_Time
2. WHEN a User selects a future date, THE System SHALL allow any time selection
3. WHEN a User attempts to select a past time for today, THE System SHALL show an error message
4. THE System SHALL use User_Time for all time validations

### Requirement 4: Отображение времени в интерфейсе

**User Story:** Как пользователь, я хочу видеть все даты и время в моем часовом поясе, чтобы не путаться с временными зонами.

#### Acceptance Criteria

1. WHEN displaying Event dates and times, THE System SHALL format them using User_Time
2. WHEN showing "Сегодня" or "Завтра" labels, THE System SHALL calculate them relative to User_Time
3. WHEN displaying event lists, THE System SHALL group events by date in User_Time
4. THE System SHALL consistently use User_Time throughout the entire interface

### Requirement 5: Миграция существующих данных

**User Story:** Как администратор системы, я хочу, чтобы существующие пользователи получили корректную таймзону, чтобы система работала правильно для всех.

#### Acceptance Criteria

1. THE System SHALL add a timezone column to the users table
2. WHEN the migration runs, THE System SHALL set Europe/Moscow as the default timezone for all existing Users
3. THE System SHALL ensure the migration is idempotent and can be safely re-run
4. THE System SHALL log the migration process for audit purposes
