# Requirements Document

## Introduction

Данная спецификация описывает унификацию всплывающих сообщений (answerCallbackQuery) в Telegram боте семейного календаря. В настоящее время в приложении используется большое количество однотипных сообщений с различными формулировками для одних и тех же ситуаций, что усложняет поддержку и снижает консистентность пользовательского опыта.

## Glossary

- **Popup_Message_System**: Система всплывающих сообщений Telegram бота, использующая метод answerCallbackQuery
- **Message_Category**: Категория сообщения (успех, ошибка, информация, отмена, подтверждение)
- **Message_Constants**: Централизованное хранилище констант сообщений
- **Callback_Handler**: Обработчик callback-запросов от пользователя

## Requirements

### Requirement 1: Централизованное хранилище сообщений

**User Story:** Как разработчик, я хочу иметь централизованное хранилище всех всплывающих сообщений, чтобы легко управлять ими и обеспечить консистентность.

#### Acceptance Criteria

1. THE Message_Constants SHALL provide a single source of truth for all popup messages
2. WHEN a developer needs a popup message, THE Message_Constants SHALL provide categorized access to messages
3. THE Message_Constants SHALL organize messages by category (success, error, info, cancellation, confirmation)
4. THE Message_Constants SHALL use descriptive constant names that reflect message purpose
5. THE Message_Constants SHALL include all emoji prefixes as part of message constants

### Requirement 2: Унификация сообщений об успехе

**User Story:** Как пользователь, я хочу видеть консистентные сообщения об успешных операциях, чтобы понимать, что действие выполнено.

#### Acceptance Criteria

1. WHEN an operation completes successfully, THE Popup_Message_System SHALL display a message with ✅ emoji prefix
2. THE Popup_Message_System SHALL use unified success messages for similar operations
3. WHEN a generic success is needed, THE Popup_Message_System SHALL display "✅ Готово"
4. WHEN a specific success message is needed, THE Popup_Message_System SHALL follow pattern "✅ {Действие} выполнено"
5. THE Popup_Message_System SHALL replace all variations of success messages with unified versions

### Requirement 3: Унификация сообщений об ошибках

**User Story:** Как пользователь, я хочу видеть понятные и консистентные сообщения об ошибках, чтобы понимать, что пошло не так.

#### Acceptance Criteria

1. WHEN an error occurs, THE Popup_Message_System SHALL display a message with ❌ emoji prefix
2. WHEN a generic error occurs, THE Popup_Message_System SHALL display "❌ Произошла ошибка"
3. WHEN a specific error occurs, THE Popup_Message_System SHALL follow pattern "❌ Ошибка: {описание}"
4. WHEN an access error occurs, THE Popup_Message_System SHALL display "❌ Нет прав доступа"
5. WHEN an entity is not found, THE Popup_Message_System SHALL follow pattern "❌ {Сущность} не найдена"
6. WHEN validation fails, THE Popup_Message_System SHALL follow pattern "❌ Ошибка: {причина валидации}"
7. THE Popup_Message_System SHALL replace all variations of error messages with unified versions

### Requirement 4: Унификация сообщений об отмене

**User Story:** Как пользователь, я хочу видеть консистентные сообщения при отмене операций, чтобы понимать, что действие отменено.

#### Acceptance Criteria

1. WHEN a user cancels an operation, THE Popup_Message_System SHALL display a message with appropriate emoji
2. WHEN a generic cancellation occurs, THE Popup_Message_System SHALL display "🚫 Отменено"
3. WHEN a specific cancellation occurs, THE Popup_Message_System SHALL follow pattern "🚫 {Действие} отменено"
4. THE Popup_Message_System SHALL replace all variations of cancellation messages with unified versions

### Requirement 5: Унификация информационных сообщений

**User Story:** Как пользователь, я хочу видеть консистентные информационные сообщения, чтобы получать подсказки и инструкции.

#### Acceptance Criteria

1. WHEN displaying informational message, THE Popup_Message_System SHALL use ℹ️ emoji prefix for hints
2. WHEN displaying selection prompt, THE Popup_Message_System SHALL use clear and consistent wording
3. WHEN displaying validation requirement, THE Popup_Message_System SHALL provide actionable guidance
4. THE Popup_Message_System SHALL replace all variations of informational messages with unified versions

### Requirement 6: Унификация сообщений подтверждения

**User Story:** Как пользователь, я хочу видеть консистентные сообщения подтверждения выбора, чтобы понимать, что мой выбор принят.

#### Acceptance Criteria

1. WHEN a user makes a selection, THE Popup_Message_System SHALL display confirmation message
2. WHEN a generic confirmation is needed, THE Popup_Message_System SHALL display "✅ Выбрано"
3. WHEN a specific confirmation is needed, THE Popup_Message_System SHALL follow pattern "✅ {Элемент} выбран"
4. THE Popup_Message_System SHALL replace all variations of confirmation messages with unified versions

### Requirement 7: Обратная совместимость

**User Story:** Как разработчик, я хочу обеспечить плавный переход к новой системе сообщений, чтобы не нарушить существующую функциональность.

#### Acceptance Criteria

1. WHEN migrating to unified messages, THE Popup_Message_System SHALL maintain semantic equivalence with old messages
2. THE Popup_Message_System SHALL preserve all functional behavior of callback handlers
3. WHEN replacing messages, THE Popup_Message_System SHALL ensure no callback handler is left without proper message
4. THE Popup_Message_System SHALL maintain empty string responses where UI updates are sufficient

### Requirement 8: Документация изменений

**User Story:** Как разработчик, я хочу иметь документацию маппинга старых и новых сообщений, чтобы понимать, что было изменено.

#### Acceptance Criteria

1. THE Message_Constants SHALL include documentation comments for each message constant
2. WHEN messages are unified, THE System SHALL provide mapping documentation from old to new messages
3. THE Documentation SHALL list all affected callback handlers for each unified message
4. THE Documentation SHALL explain rationale for message unification decisions
