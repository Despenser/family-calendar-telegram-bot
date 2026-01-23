# Документ требований: Переиспользование сообщения при просмотре вложения

## Введение

При текущей реализации просмотра вложений в Telegram боте семейного календаря, когда пользователь выбирает вложение из списка для просмотра, бот отправляет файл новым сообщением. Это создает избыточные сообщения в чате и ухудшает пользовательский опыт. Данная функция исправит это поведение, заставляя бот редактировать текущее сообщение (где был список вложений) вместо создания нового.

## Глоссарий

- **Attachment_Callback_Handler**: Обработчик callback queries для работы с вложениями к событиям
- **Telegram_Message_Service**: Сервис для отправки и редактирования сообщений через Telegram Bot API
- **Message_Id**: Уникальный идентификатор сообщения в Telegram
- **Callback_Query**: Запрос от Telegram, содержащий информацию о нажатой inline кнопке
- **Edit_Message**: Операция редактирования существующего сообщения в Telegram
- **Attachment_View**: Просмотр выбранного вложения пользователем

## Требования

### Требование 1: Редактирование сообщения при просмотре вложения

**User Story:** Как пользователь, я хочу, чтобы при выборе вложения для просмотра текущее сообщение редактировалось, а не создавалось новое, чтобы чат оставался чистым и организованным.

#### Acceptance Criteria

1. WHEN пользователь нажимает кнопку просмотра вложения из списка вложений, THEN THE Attachment_Callback_Handler SHALL удалить текущее сообщение со списком вложений
2. WHEN текущее сообщение удалено, THEN THE Attachment_Callback_Handler SHALL отправить файл вложения новым сообщением с клавиатурой навигации
3. WHEN файл вложения отправлен, THEN THE Attachment_Callback_Handler SHALL сохранить новый Message_Id в состоянии разговора
4. WHEN пользователь возвращается к списку вложений, THEN THE Attachment_Callback_Handler SHALL использовать сохраненный Message_Id для редактирования сообщения

### Требование 2: Обработка ошибок удаления сообщения

**User Story:** Как разработчик, я хочу, чтобы система корректно обрабатывала ситуации, когда сообщение не может быть удалено, чтобы пользователь всегда получал запрошенный файл.

#### Acceptance Criteria

1. IF удаление сообщения не удалось (сообщение уже удалено пользователем), THEN THE Attachment_Callback_Handler SHALL продолжить отправку файла новым сообщением
2. WHEN ошибка удаления обнаружена, THEN THE Attachment_Callback_Handler SHALL логировать предупреждение с деталями ошибки
3. WHEN файл отправлен после неудачного удаления, THEN THE Attachment_Callback_Handler SHALL сохранить новый Message_Id в состоянии разговора

### Требование 3: Сохранение контекста сообщения вложения

**User Story:** Как система, я должна отслеживать Message_Id текущего сообщения вложения, чтобы корректно редактировать его при навигации.

#### Acceptance Criteria

1. WHEN файл вложения отправлен, THEN THE Attachment_Callback_Handler SHALL сохранить Message_Id через Conversation_State_Service
2. WHEN пользователь возвращается к списку вложений, THEN THE Attachment_Callback_Handler SHALL получить сохраненный Message_Id из Conversation_State_Service
3. WHEN Message_Id получен, THEN THE Attachment_Callback_Handler SHALL использовать его для редактирования сообщения
4. WHEN редактирование не удалось, THEN THE Attachment_Callback_Handler SHALL отправить новое сообщение и обновить сохраненный Message_Id

### Требование 4: Удаление сообщений через Telegram API

**User Story:** Как система, я должна иметь возможность удалять сообщения через Telegram Bot API, чтобы реализовать механизм замены сообщений.

#### Acceptance Criteria

1. THE Telegram_Message_Service SHALL предоставить метод deleteMessage для удаления сообщений
2. WHEN метод deleteMessage вызван с валидными chatId и messageId, THEN THE Telegram_Message_Service SHALL выполнить запрос DeleteMessage к Telegram API
3. IF удаление успешно, THEN THE Telegram_Message_Service SHALL вернуть true
4. IF удаление не удалось (сообщение не найдено), THEN THE Telegram_Message_Service SHALL вернуть false
5. IF произошла другая ошибка, THEN THE Telegram_Message_Service SHALL выбросить TelegramApiException

### Требование 5: Логирование операций с сообщениями

**User Story:** Как разработчик, я хочу видеть подробные логи операций с сообщениями, чтобы диагностировать проблемы в продакшене.

#### Acceptance Criteria

1. WHEN сообщение удаляется, THEN THE Attachment_Callback_Handler SHALL логировать DEBUG сообщение с chatId, messageId и userId
2. WHEN удаление успешно, THEN THE Attachment_Callback_Handler SHALL логировать INFO сообщение с подтверждением
3. WHEN удаление не удалось, THEN THE Attachment_Callback_Handler SHALL логировать WARN сообщение с причиной
4. WHEN файл отправлен, THEN THE Attachment_Callback_Handler SHALL логировать INFO сообщение с новым messageId
5. WHEN Message_Id сохранен в состоянии, THEN THE Attachment_Callback_Handler SHALL логировать DEBUG сообщение с деталями
