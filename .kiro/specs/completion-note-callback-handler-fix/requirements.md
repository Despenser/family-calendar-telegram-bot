# Требования: Исправление обработки callback для добавления заметки к завершенному событию

## Введение

При попытке добавить заметку к завершенному событию возникает ошибка "Неизвестный callback data: 'add_completion_note_13'". Проблема заключается в том, что в `EventCallbackHandler` реализован метод `handleAddCompletionNote`, но он не вызывается в главном методе `handle`, так как отсутствует проверка на префикс `ADD_COMPLETION_NOTE`.

## Глоссарий

- **EventCallbackHandler**: Обработчик callback queries для операций с событиями
- **CallbackPrefix**: Enum с префиксами для различных типов callback данных
- **ADD_COMPLETION_NOTE**: Префикс callback для добавления заметки к завершенному событию (формат: `add_completion_note_{eventId}`)
- **SKIP_COMPLETION_NOTE**: Префикс callback для пропуска добавления заметки к завершенному событию
- **CallbackQuery**: Объект Telegram API, представляющий нажатие на inline кнопку
- **CallbackQueryDispatcher**: Диспетчер для маршрутизации callback queries к соответствующим обработчикам

## Требования

### Требование 1

**User Story:** Как пользователь, я хочу иметь возможность добавить заметку к завершенному событию, чтобы зафиксировать результаты или впечатления о событии.

#### Критерии приемки

1. WHEN пользователь нажимает кнопку "📝 Добавить заметку" после завершения события THEN система SHALL вызвать метод `handleAddCompletionNote` для обработки callback
2. WHEN метод `handle` в `EventCallbackHandler` получает callback data с префиксом `ADD_COMPLETION_NOTE` THEN система SHALL корректно маршрутизировать запрос к методу `handleAddCompletionNote`
3. WHEN метод `canHandle` в `EventCallbackHandler` проверяет callback data с префиксом `ADD_COMPLETION_NOTE` THEN система SHALL возвращать true
4. WHEN пользователь нажимает кнопку "⏭️ Пропустить" при добавлении заметки THEN система SHALL вызвать метод `handleSkipCompletionNote` для обработки callback
5. WHEN метод `handle` в `EventCallbackHandler` получает callback data с префиксом `SKIP_COMPLETION_NOTE` THEN система SHALL корректно маршрутизировать запрос к методу `handleSkipCompletionNote`

### Требование 2

**User Story:** Как разработчик, я хочу чтобы все callback с префиксами `ADD_COMPLETION_NOTE` и `SKIP_COMPLETION_NOTE` корректно обрабатывались, чтобы избежать ошибок "Неизвестный callback data".

#### Критерии приемки

1. WHEN `CallbackQueryDispatcher` получает callback data с префиксом `ADD_COMPLETION_NOTE` THEN система SHALL найти `EventCallbackHandler` как подходящий обработчик
2. WHEN `CallbackQueryDispatcher` получает callback data с префиксом `SKIP_COMPLETION_NOTE` THEN система SHALL найти `EventCallbackHandler` как подходящий обработчик
3. WHEN система обрабатывает callback с префиксом `ADD_COMPLETION_NOTE` или `SKIP_COMPLETION_NOTE` THEN система SHALL NOT логировать предупреждение "Неизвестный callback data"
4. WHEN система обрабатывает callback с префиксом `ADD_COMPLETION_NOTE` или `SKIP_COMPLETION_NOTE` THEN система SHALL NOT отправлять пользователю сообщение "❌ Неизвестная команда"
