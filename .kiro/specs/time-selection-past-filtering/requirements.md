# Документ требований: Фильтрация прошедшего времени при выборе

## Введение

Система семейного календаря должна не предлагать пользователям выбирать время, которое уже прошло, аналогично тому, как работает календарь с датами. В настоящее время при выборе времени для сегодняшнего дня пользователю показываются все часы и минуты, и только после выбора прошедшего времени система показывает ошибку. Это создает негативный пользовательский опыт.

## Глоссарь

- **System**: Telegram-бот семейного календаря
- **User**: Пользователь Telegram-бота
- **Event**: Событие в календаре с датой и временем
- **User_Time**: Текущее время в таймзоне пользователя
- **Hour_Selection**: Интерфейс выбора часа для события
- **Minute_Selection**: Интерфейс выбора минут для события
- **Past_Hour**: Час, который уже прошел в текущий день
- **Past_Minute**: Минута, которая уже прошла в текущий час текущего дня
- **Valid_Hour**: Час, который является текущим или будущим
- **Valid_Minute**: Минута, которая является текущей или будущей

## Требования

### Requirement 1: Фильтрация прошедших часов при выборе времени для сегодняшнего дня

**User Story:** Как пользователь, я хочу видеть только доступные для выбора часы, чтобы не тратить время на выбор прошедшего времени и получение ошибки.

#### Acceptance Criteria

1. WHEN a User selects today's date for an event, THE System SHALL display only hours that are current or in the future relative to User_Time
2. WHEN a User selects today's date and the current hour has not yet passed, THE System SHALL display the current hour as selectable
3. WHEN a User selects today's date and all hours have passed (23:45+), THE System SHALL display a message that it's too late to create events for today
4. WHEN a User selects a future date, THE System SHALL display all 24 hours as selectable
5. THE System SHALL use User_Time for determining which hours to display

### Requirement 2: Фильтрация прошедших минут при выборе времени для текущего часа

**User Story:** Как пользователь, я хочу видеть только доступные для выбора минуты, чтобы не выбирать прошедшее время.

#### Acceptance Criteria

1. WHEN a User selects the current hour for today's date, THE System SHALL display only minutes that are current or in the future relative to User_Time
2. WHEN a User selects the current hour and the current minute is between intervals (e.g., 17 minutes), THE System SHALL display only future intervals (30, 45)
3. WHEN a User selects the current hour and all minute intervals have passed (46+ minutes), THE System SHALL display a message to select the next hour
4. WHEN a User selects a future hour or a future date, THE System SHALL display all minute intervals (00, 15, 30, 45)
5. THE System SHALL use User_Time for determining which minutes to display

### Requirement 3: Визуальная согласованность с календарем

**User Story:** Как пользователь, я хочу, чтобы выбор времени работал так же, как выбор даты, для единообразия интерфейса.

#### Acceptance Criteria

1. WHEN displaying Hour_Selection, THE System SHALL not show past hours (similar to how past dates are not shown in calendar)
2. WHEN displaying Minute_Selection, THE System SHALL not show past minutes for current hour (similar to how past dates are not shown in calendar)
3. WHEN no valid hours are available for today, THE System SHALL display an informative message
4. WHEN no valid minutes are available for current hour, THE System SHALL display an informative message

### Requirement 4: Обработка граничных случаев

**User Story:** Как пользователь, я хочу, чтобы система корректно обрабатывала граничные случаи при выборе времени.

#### Acceptance Criteria

1. WHEN the current time is 23:46 or later and User selects today, THE System SHALL inform that it's too late to create events for today
2. WHEN the current time is XX:46 or later and User selects current hour, THE System SHALL inform to select the next hour
3. WHEN User is editing an existing event's time, THE System SHALL apply the same filtering rules
4. WHEN User returns back from minute selection to hour selection, THE System SHALL recalculate available hours based on current User_Time

### Requirement 5: Удаление устаревшей валидации

**User Story:** Как разработчик, я хочу удалить валидацию после выбора времени, так как она больше не нужна при проактивной фильтрации.

#### Acceptance Criteria

1. WHEN time filtering is implemented, THE System SHALL remove the post-selection validation for past time
2. WHEN time filtering is implemented, THE System SHALL remove the error message display for past time selection
3. THE System SHALL ensure that only valid times can be selected through the interface
