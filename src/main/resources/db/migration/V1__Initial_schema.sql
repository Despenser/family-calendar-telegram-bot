-- ============================================================================
-- МИГРАЦИЯ V1: Cхема базы данных
-- ============================================================================

-- ============================================================================
-- ТАБЛИЦА FAMILIES: Семьи пользователей
-- ============================================================================

CREATE TABLE families (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ограничения
    CONSTRAINT families_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

-- Комментарии для таблицы families
COMMENT ON TABLE families IS 'Семьи пользователей с общим календарем событий';
COMMENT ON COLUMN families.id IS 'Уникальный идентификатор семьи';
COMMENT ON COLUMN families.name IS 'Название семьи (не может быть пустым)';
COMMENT ON COLUMN families.created_at IS 'Дата и время создания семьи';

-- ============================================================================
-- ТАБЛИЦА USERS: Пользователи Telegram бота
-- ============================================================================

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT NOT NULL,
    username VARCHAR(255),
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255),
    family_id BIGINT,
    event_filter VARCHAR(20) DEFAULT 'ALL',
    timezone VARCHAR(50) DEFAULT 'Europe/Moscow',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ограничения
    CONSTRAINT users_telegram_id_unique UNIQUE (telegram_id),
    CONSTRAINT users_first_name_not_empty CHECK (LENGTH(TRIM(first_name)) > 0),
    CONSTRAINT users_event_filter_valid CHECK (event_filter IN ('ALL', 'FAMILY', 'PERSONAL')),
    CONSTRAINT users_family_fk FOREIGN KEY (family_id) 
        REFERENCES families(id) ON DELETE SET NULL
);

-- Комментарии для таблицы users
COMMENT ON TABLE users IS 'Пользователи Telegram бота с привязкой к семьям';
COMMENT ON COLUMN users.id IS 'Уникальный идентификатор пользователя в системе';
COMMENT ON COLUMN users.telegram_id IS 'Уникальный идентификатор пользователя в Telegram';
COMMENT ON COLUMN users.username IS 'Username пользователя в Telegram (может отсутствовать)';
COMMENT ON COLUMN users.first_name IS 'Имя пользователя (обязательное поле)';
COMMENT ON COLUMN users.last_name IS 'Фамилия пользователя (опционально)';
COMMENT ON COLUMN users.family_id IS 'Ссылка на семью пользователя (NULL для пользователей без семьи)';
COMMENT ON COLUMN users.event_filter IS 'Фильтр отображения событий: ALL (все), FAMILY (семейные), PERSONAL (личные)';
COMMENT ON COLUMN users.timezone IS 'Часовой пояс пользователя в формате IANA (например, Europe/Moscow)';
COMMENT ON COLUMN users.created_at IS 'Дата и время регистрации пользователя в системе';

-- ============================================================================
-- ТАБЛИЦА EVENTS: События семейного календаря
-- ============================================================================

CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    title VARCHAR(255),
    description TEXT,
    event_date DATE,
    event_time TIME,
    end_time TIME,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    is_personal BOOLEAN NOT NULL DEFAULT FALSE,
    series_id VARCHAR(255),
    completion_note TEXT,
    deleted_at TIMESTAMP,
    completed_at TIMESTAMP,
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    message_id BIGINT,
    is_my_events_header BOOLEAN NOT NULL DEFAULT FALSE,
    is_trash_header BOOLEAN NOT NULL DEFAULT FALSE,
    is_from_add_event_command BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ограничения
    CONSTRAINT events_title_not_empty CHECK (title IS NULL OR LENGTH(TRIM(title)) > 0),
    CONSTRAINT events_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'DELETED')),
    CONSTRAINT events_time_interval_check CHECK (
        end_time IS NULL OR event_time IS NULL OR end_time > event_time
    ),
    CONSTRAINT events_completion_logic CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR
        (status != 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT events_deletion_logic CHECK (
        (status = 'DELETED' AND deleted_at IS NOT NULL) OR
        (status != 'DELETED' AND deleted_at IS NULL)
    ),
    CONSTRAINT events_draft_logic CHECK (
        (status = 'DRAFT') OR 
        (status != 'DRAFT' AND title IS NOT NULL AND event_date IS NOT NULL)
    ),
    CONSTRAINT events_user_fk FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT events_family_fk FOREIGN KEY (family_id) 
        REFERENCES families(id) ON DELETE CASCADE
);

-- Комментарии для таблицы events
COMMENT ON TABLE events IS 'События семейного календаря с поддержкой черновиков, завершения и корзины';
COMMENT ON COLUMN events.id IS 'Уникальный идентификатор события';
COMMENT ON COLUMN events.user_id IS 'Создатель события (ссылка на пользователя)';
COMMENT ON COLUMN events.family_id IS 'Семья, к которой относится событие';
COMMENT ON COLUMN events.title IS 'Название события (может быть NULL для черновиков)';
COMMENT ON COLUMN events.description IS 'Подробное описание события (опционально)';
COMMENT ON COLUMN events.event_date IS 'Дата события (может быть NULL для черновиков)';
COMMENT ON COLUMN events.event_time IS 'Время начала события (может быть NULL)';
COMMENT ON COLUMN events.end_time IS 'Время окончания события для временных интервалов (опционально)';
COMMENT ON COLUMN events.status IS 'Статус события: DRAFT (черновик), ACTIVE (активное), COMPLETED (завершенное), DELETED (в корзине)';
COMMENT ON COLUMN events.is_personal IS 'Флаг персонального события: true - видно только создателю, false - видно всей семье';
COMMENT ON COLUMN events.series_id IS 'UUID серии для связи повторяющихся событий (NULL для обычных событий)';
COMMENT ON COLUMN events.completion_note IS 'Заметка пользователя о том, как прошло завершенное событие';
COMMENT ON COLUMN events.deleted_at IS 'Дата и время перемещения события в корзину (NULL для активных событий)';
COMMENT ON COLUMN events.completed_at IS 'Дата и время автоматического или ручного завершения события';
COMMENT ON COLUMN events.notified IS 'Флаг отправки уведомления о событии (используется планировщиком)';
COMMENT ON COLUMN events.message_id IS 'Идентификатор сообщения Telegram для обновления при редактировании события';
COMMENT ON COLUMN events.is_my_events_header IS 'Флаг шапки списка "Мои события" для корректного обновления первого события';
COMMENT ON COLUMN events.is_trash_header IS 'Флаг шапки корзины для корректного обновления первого события в корзине';
COMMENT ON COLUMN events.is_from_add_event_command IS 'Флаг, указывающий что создание события началось из команды /add_event (используется для различения флоу создания)';
COMMENT ON COLUMN events.created_at IS 'Дата и время создания записи о событии';

-- ============================================================================
-- ТАБЛИЦА ATTACHMENTS: Вложения событий
-- ============================================================================

CREATE TABLE attachments (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    file_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(255),
    file_type VARCHAR(50),
    file_size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ограничения
    CONSTRAINT attachments_event_fk FOREIGN KEY (event_id) 
        REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT attachments_file_id_not_empty CHECK (LENGTH(TRIM(file_id)) > 0),
    CONSTRAINT attachments_file_size_positive CHECK (file_size IS NULL OR file_size > 0),
    CONSTRAINT attachments_file_type_valid CHECK (
        file_type IS NULL OR file_type IN ('document', 'photo', 'video', 'audio', 'voice', 'video_note')
    )
);

-- Комментарии для таблицы attachments
COMMENT ON TABLE attachments IS 'Вложения событий: файлы, документы, изображения, прикрепленные к событиям';
COMMENT ON COLUMN attachments.id IS 'Уникальный идентификатор вложения';
COMMENT ON COLUMN attachments.event_id IS 'Ссылка на событие, к которому прикреплен файл';
COMMENT ON COLUMN attachments.file_id IS 'Telegram file_id для получения файла через Bot API';
COMMENT ON COLUMN attachments.file_name IS 'Оригинальное имя файла (может отсутствовать)';
COMMENT ON COLUMN attachments.file_type IS 'Тип файла: document, photo, video, audio';
COMMENT ON COLUMN attachments.file_size IS 'Размер файла в байтах (может отсутствовать)';
COMMENT ON COLUMN attachments.uploaded_at IS 'Дата и время загрузки файла';

-- ============================================================================
-- ТАБЛИЦА EVENT_HISTORY: История изменений событий
-- ============================================================================

CREATE TABLE event_history (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    field_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ограничения
    CONSTRAINT event_history_user_fk FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT event_history_action_type_valid CHECK (
        action_type IN ('CREATED', 'UPDATED', 'DELETED', 'RESTORED')
    ),
    CONSTRAINT event_history_field_logic CHECK (
        (action_type IN ('CREATED', 'DELETED') AND field_name IS NULL) OR
        (action_type = 'UPDATED' AND field_name IS NOT NULL) OR
        (action_type = 'RESTORED')
    ),
    CONSTRAINT event_history_update_values_logic CHECK (
        (action_type != 'UPDATED') OR
        (action_type = 'UPDATED' AND (old_value IS NOT NULL OR new_value IS NOT NULL))
    )
);

-- Комментарии для таблицы event_history
COMMENT ON TABLE event_history IS 'История изменений событий для отслеживания всех действий пользователей';
COMMENT ON COLUMN event_history.id IS 'Уникальный идентификатор записи истории';
COMMENT ON COLUMN event_history.event_id IS 'Идентификатор события (может быть удалено, поэтому без FK)';
COMMENT ON COLUMN event_history.user_id IS 'Пользователь, выполнивший действие';
COMMENT ON COLUMN event_history.action_type IS 'Тип действия: CREATED, UPDATED, DELETED, RESTORED';
COMMENT ON COLUMN event_history.field_name IS 'Название измененного поля (только для action_type = UPDATED)';
COMMENT ON COLUMN event_history.old_value IS 'Старое значение поля (для UPDATED действий)';
COMMENT ON COLUMN event_history.new_value IS 'Новое значение поля (для UPDATED действий)';
COMMENT ON COLUMN event_history.changed_at IS 'Дата и время выполнения действия';

-- ============================================================================
-- ТАБЛИЦА REMINDERS: Напоминания о событиях
-- ============================================================================

CREATE TABLE reminders (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    reminder_type VARCHAR(50) NOT NULL,
    reminder_time TIMESTAMP NOT NULL,
    sent BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP,
    
    -- Ограничения
    CONSTRAINT reminders_event_fk FOREIGN KEY (event_id) 
        REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT reminders_type_valid CHECK (
        reminder_type IN ('EVENING_BEFORE', 'ONE_HOUR_BEFORE', 'FIFTEEN_MINUTES_BEFORE')
    ),
    CONSTRAINT reminders_sent_logic CHECK (
        (sent = FALSE AND sent_at IS NULL) OR
        (sent = TRUE AND sent_at IS NOT NULL)
    ),
    CONSTRAINT reminders_time_future CHECK (
        reminder_time > CURRENT_TIMESTAMP - INTERVAL '1 hour'
    )
);

-- Комментарии для таблицы reminders
COMMENT ON TABLE reminders IS 'Напоминания о событиях с гибкими настройками времени отправки';
COMMENT ON COLUMN reminders.id IS 'Уникальный идентификатор напоминания';
COMMENT ON COLUMN reminders.event_id IS 'Ссылка на событие, для которого настроено напоминание';
COMMENT ON COLUMN reminders.reminder_type IS 'Тип напоминания: EVENING_BEFORE, ONE_HOUR_BEFORE, FIFTEEN_MINUTES_BEFORE';
COMMENT ON COLUMN reminders.reminder_time IS 'Рассчитанное время отправки напоминания';
COMMENT ON COLUMN reminders.sent IS 'Флаг отправки напоминания: true - отправлено, false - ожидает отправки';
COMMENT ON COLUMN reminders.sent_at IS 'Дата и время фактической отправки напоминания';

-- ============================================================================
-- ТАБЛИЦА CONVERSATION_STATES: Состояния диалогов пользователей
-- ============================================================================

CREATE TABLE conversation_states (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    
    -- Контекст сообщения с вложениями
    attachment_event_id BIGINT,
    attachment_chat_id BIGINT,
    attachment_message_id INTEGER,
    attachment_context_created_at TIMESTAMP,
    
    -- Контекст шапки события
    event_has_my_events_header BOOLEAN DEFAULT NULL,
    event_count_for_header INTEGER DEFAULT NULL,
    
    -- Контекст поиска событий
    search_chat_id BIGINT,
    search_message_id INTEGER,
    
    -- Временные метки
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ограничения
    CONSTRAINT fk_conversation_states_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_states_event FOREIGN KEY (attachment_event_id) 
        REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT conversation_states_attachment_context_logic CHECK (
        (attachment_event_id IS NULL AND attachment_chat_id IS NULL AND 
         attachment_message_id IS NULL AND attachment_context_created_at IS NULL) OR
        (attachment_event_id IS NOT NULL AND attachment_chat_id IS NOT NULL AND 
         attachment_message_id IS NOT NULL AND attachment_context_created_at IS NOT NULL)
    ),
    CONSTRAINT conversation_states_search_context_logic CHECK (
        (search_chat_id IS NULL AND search_message_id IS NULL) OR
        (search_chat_id IS NOT NULL AND search_message_id IS NOT NULL)
    ),
    CONSTRAINT conversation_states_header_logic CHECK (
        (event_has_my_events_header IS NULL AND event_count_for_header IS NULL) OR
        (event_has_my_events_header IS NOT NULL AND event_count_for_header IS NOT NULL AND event_count_for_header >= 0)
    )
);

-- Комментарии для таблицы conversation_states
COMMENT ON TABLE conversation_states IS 'Хранит состояние диалогов пользователей с ботом для сохранения контекста между операциями';
COMMENT ON COLUMN conversation_states.id IS 'Уникальный идентификатор состояния диалога';
COMMENT ON COLUMN conversation_states.user_id IS 'Идентификатор пользователя (один пользователь = одно состояние)';
COMMENT ON COLUMN conversation_states.attachment_event_id IS 'Идентификатор события для контекста вложений';
COMMENT ON COLUMN conversation_states.attachment_chat_id IS 'Идентификатор чата для контекста вложений';
COMMENT ON COLUMN conversation_states.attachment_message_id IS 'Идентификатор сообщения для редактирования при работе с вложениями';
COMMENT ON COLUMN conversation_states.attachment_context_created_at IS 'Время создания контекста вложений (для проверки истечения 47 часов)';
COMMENT ON COLUMN conversation_states.event_has_my_events_header IS 'Флаг наличия шапки "Мои события" у отображаемого события';
COMMENT ON COLUMN conversation_states.event_count_for_header IS 'Количество событий пользователя для формирования шапки "Мои события"';
COMMENT ON COLUMN conversation_states.search_chat_id IS 'ID чата для поиска событий';
COMMENT ON COLUMN conversation_states.search_message_id IS 'ID сообщения для редактирования при поиске';
COMMENT ON COLUMN conversation_states.created_at IS 'Дата и время создания состояния диалога';
COMMENT ON COLUMN conversation_states.updated_at IS 'Дата и время последнего обновления состояния диалога';

-- ============================================================================
-- ОПТИМИЗИРОВАННЫЕ ИНДЕКСЫ ДЛЯ ПРОИЗВОДИТЕЛЬНОСТИ
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Индексы для таблицы users
-- ----------------------------------------------------------------------------

-- Индекс для поиска пользователей семьи
CREATE INDEX idx_users_family_id ON users(family_id) WHERE family_id IS NOT NULL;
COMMENT ON INDEX idx_users_family_id IS 'Частичный индекс для быстрого получения пользователей семьи (исключает NULL значения)';

-- ----------------------------------------------------------------------------
-- Оптимизированные индексы для таблицы events
-- ----------------------------------------------------------------------------

-- Основной составной индекс для календарных запросов
CREATE INDEX idx_events_family_date_status ON events(family_id, event_date, status);
COMMENT ON INDEX idx_events_family_date_status IS 'Основной индекс для календарных запросов семьи с фильтрацией по статусу';

-- Составной индекс для событий пользователя с сортировкой
CREATE INDEX idx_events_user_date_time ON events(user_id, event_date, event_time) 
    WHERE status != 'DELETED';
COMMENT ON INDEX idx_events_user_date_time IS 'Индекс для получения активных событий пользователя с сортировкой по дате и времени';

-- Составной индекс для поиска событий, требующих уведомления
CREATE INDEX idx_events_notification ON events(status, notified, event_date, event_time) 
    WHERE status = 'ACTIVE' AND notified = FALSE;
COMMENT ON INDEX idx_events_notification IS 'Оптимизированный индекс для планировщика уведомлений';

-- Составной индекс для поиска событий пользователя по статусу
CREATE INDEX idx_events_user_status_date ON events(user_id, status, event_date, event_time);
COMMENT ON INDEX idx_events_user_status_date IS 'Индекс для получения событий пользователя по статусу с сортировкой';

-- Индекс для поиска событий по series_id (повторяющиеся события)
CREATE INDEX idx_events_series_id ON events(series_id) 
    WHERE series_id IS NOT NULL;
COMMENT ON INDEX idx_events_series_id IS 'Частичный индекс для быстрого поиска событий одной серии';

-- Составной индекс для фильтрации по типу события и семье
CREATE INDEX idx_events_family_personal_status ON events(family_id, is_personal, status, event_date, event_time);
COMMENT ON INDEX idx_events_family_personal_status IS 'Индекс для фильтрации семейных/персональных событий с сортировкой';

-- Составной индекс для корзины пользователя
CREATE INDEX idx_events_user_deleted ON events(user_id, deleted_at DESC) 
    WHERE status = 'DELETED';
COMMENT ON INDEX idx_events_user_deleted IS 'Частичный индекс для корзины пользователя с сортировкой по дате удаления';

-- Индекс для автоматической очистки старых удаленных событий
CREATE INDEX idx_events_cleanup_deleted ON events(status, deleted_at) 
    WHERE status = 'DELETED' AND deleted_at IS NOT NULL;
COMMENT ON INDEX idx_events_cleanup_deleted IS 'Индекс для планировщика очистки старых удаленных событий';

-- Индекс для поиска события с шапкой корзины
CREATE INDEX idx_events_trash_header ON events(user_id, is_trash_header) 
    WHERE status = 'DELETED' AND is_trash_header = TRUE;
COMMENT ON INDEX idx_events_trash_header IS 'Частичный индекс для поиска первого события в корзине с шапкой';

-- Составной индекс для поиска предстоящих событий
CREATE INDEX idx_events_upcoming ON events(family_id, event_date, event_time, is_personal, user_id) 
    WHERE status = 'ACTIVE';
COMMENT ON INDEX idx_events_upcoming IS 'Частичный индекс для быстрого поиска активных событий';

-- Индекс для полнотекстового поиска по названию и описанию
CREATE INDEX idx_events_search_text ON events(family_id, status, is_personal, user_id) 
    WHERE status = 'ACTIVE';
COMMENT ON INDEX idx_events_search_text IS 'Индекс для поддержки текстового поиска событий';

-- ----------------------------------------------------------------------------
-- Оптимизированные индексы для таблицы attachments
-- ----------------------------------------------------------------------------

-- Составной индекс для получения вложений события с сортировкой
CREATE INDEX idx_attachments_event_uploaded ON attachments(event_id, uploaded_at DESC);
COMMENT ON INDEX idx_attachments_event_uploaded IS 'Индекс для получения вложений события с сортировкой по дате загрузки (новые первыми)';

-- ----------------------------------------------------------------------------
-- Оптимизированные индексы для таблицы event_history
-- ----------------------------------------------------------------------------

-- Составной индекс для получения истории события с сортировкой
CREATE INDEX idx_event_history_event_changed ON event_history(event_id, changed_at DESC);
COMMENT ON INDEX idx_event_history_event_changed IS 'Индекс для получения истории события с сортировкой по дате (новые изменения первыми)';

-- Индекс для системного аудита по дате
CREATE INDEX idx_event_history_changed_at ON event_history(changed_at DESC);
COMMENT ON INDEX idx_event_history_changed_at IS 'Индекс для поиска последних действий по всем событиям (системный аудит)';

-- Составной индекс для фильтрации по типу действия
CREATE INDEX idx_event_history_action_changed ON event_history(action_type, changed_at DESC);
COMMENT ON INDEX idx_event_history_action_changed IS 'Индекс для фильтрации действий по типу с сортировкой по дате';

-- ----------------------------------------------------------------------------
-- Оптимизированные индексы для таблицы reminders
-- ----------------------------------------------------------------------------

-- Составной индекс для планировщика напоминаний
CREATE INDEX idx_reminders_scheduler ON reminders(sent, reminder_time) 
    WHERE sent = FALSE;
COMMENT ON INDEX idx_reminders_scheduler IS 'Оптимизированный частичный индекс для планировщика напоминаний';

-- Составной индекс для получения напоминаний события
CREATE INDEX idx_reminders_event_time ON reminders(event_id, reminder_time);
COMMENT ON INDEX idx_reminders_event_time IS 'Индекс для получения напоминаний события с сортировкой по времени отправки';

-- ----------------------------------------------------------------------------
-- Оптимизированные индексы для таблицы conversation_states
-- ----------------------------------------------------------------------------

-- Уникальный индекс уже создан в определении таблицы
-- CREATE UNIQUE INDEX idx_conversation_states_user_id ON conversation_states(user_id);

-- Индекс для поиска по событию (может быть NULL)
CREATE INDEX idx_conversation_states_event_id ON conversation_states(attachment_event_id) 
    WHERE attachment_event_id IS NOT NULL;
COMMENT ON INDEX idx_conversation_states_event_id IS 'Частичный индекс для поиска состояний диалога по связанному событию';

-- Индекс для очистки старых контекстов вложений
CREATE INDEX idx_conversation_states_cleanup ON conversation_states(attachment_context_created_at) 
    WHERE attachment_context_created_at IS NOT NULL;
COMMENT ON INDEX idx_conversation_states_cleanup IS 'Индекс для планировщика очистки старых контекстов вложений (старше 47 часов)';

-- ============================================================================
-- ДОПОЛНИТЕЛЬНЫЕ ОГРАНИЧЕНИЯ И КОММЕНТАРИИ
-- ============================================================================

-- Комментарии к ограничениям для лучшего понимания бизнес-логики

COMMENT ON CONSTRAINT families_name_not_empty ON families IS 
    'Бизнес-правило: название семьи не может быть пустым или состоять только из пробелов';

COMMENT ON CONSTRAINT users_telegram_id_unique ON users IS 
    'Бизнес-правило: каждый Telegram пользователь может иметь только одну учетную запись в системе';

COMMENT ON CONSTRAINT users_first_name_not_empty ON users IS 
    'Бизнес-правило: имя пользователя обязательно и не может быть пустым';

COMMENT ON CONSTRAINT users_event_filter_valid ON users IS 
    'Бизнес-правило: фильтр событий может быть только ALL, FAMILY или PERSONAL';

COMMENT ON CONSTRAINT users_family_fk ON users IS 
    'Связь пользователя с семьей. При удалении семьи пользователи остаются, но теряют связь с семьей';

COMMENT ON CONSTRAINT events_title_not_empty ON events IS 
    'Бизнес-правило: название события не может быть пустым (NULL разрешен для черновиков)';

COMMENT ON CONSTRAINT events_status_check ON events IS 
    'Бизнес-правило: статус события может быть только DRAFT, ACTIVE, COMPLETED или DELETED';

COMMENT ON CONSTRAINT events_time_interval_check ON events IS 
    'Бизнес-правило: время окончания события должно быть позже времени начала';

COMMENT ON CONSTRAINT events_completion_logic ON events IS 
    'Бизнес-правило: завершенные события должны иметь дату завершения, незавершенные - не должны';

COMMENT ON CONSTRAINT events_deletion_logic ON events IS 
    'Бизнес-правило: удаленные события должны иметь дату удаления, неудаленные - не должны';

COMMENT ON CONSTRAINT events_draft_logic ON events IS 
    'Бизнес-правило: черновики могут не иметь названия и даты, активные события должны иметь и то, и другое';

COMMENT ON CONSTRAINT events_user_fk ON events IS 
    'Связь события с создателем. При удалении пользователя удаляются все его события';

COMMENT ON CONSTRAINT events_family_fk ON events IS 
    'Связь события с семьей. При удалении семьи удаляются все события семьи';

COMMENT ON CONSTRAINT attachments_event_fk ON attachments IS 
    'Связь вложения с событием. При удалении события удаляются все его вложения';

COMMENT ON CONSTRAINT attachments_file_id_not_empty ON attachments IS 
    'Бизнес-правило: file_id Telegram обязателен для получения файла через Bot API';

COMMENT ON CONSTRAINT attachments_file_size_positive ON attachments IS 
    'Бизнес-правило: размер файла должен быть положительным числом (NULL разрешен)';

COMMENT ON CONSTRAINT attachments_file_type_valid ON attachments IS 
    'Бизнес-правило: тип файла должен соответствовать поддерживаемым Telegram типам';

COMMENT ON CONSTRAINT event_history_user_fk ON event_history IS 
    'Связь записи истории с пользователем. При удалении пользователя удаляется история его действий';

COMMENT ON CONSTRAINT event_history_action_type_valid ON event_history IS 
    'Бизнес-правило: тип действия должен быть одним из допустимых значений';

COMMENT ON CONSTRAINT event_history_field_logic ON event_history IS 
    'Бизнес-правило: для действий CREATED/DELETED поле field_name должно быть NULL, для UPDATED - обязательно NOT NULL, для RESTORED - может быть любым';

COMMENT ON CONSTRAINT event_history_update_values_logic ON event_history IS 
    'Бизнес-правило: для действий UPDATED должно быть указано старое или новое значение';

COMMENT ON CONSTRAINT reminders_event_fk ON reminders IS 
    'Связь напоминания с событием. При удалении события удаляются все его напоминания';

COMMENT ON CONSTRAINT reminders_type_valid ON reminders IS 
    'Бизнес-правило: тип напоминания должен быть одним из предопределенных значений';

COMMENT ON CONSTRAINT reminders_sent_logic ON reminders IS 
    'Бизнес-правило: если напоминание отправлено (sent=true), то должна быть указана дата отправки';

COMMENT ON CONSTRAINT reminders_time_future ON reminders IS 
    'Бизнес-правило: время напоминания не должно быть слишком старым (допускается час назад для обработки)';

COMMENT ON CONSTRAINT fk_conversation_states_user ON conversation_states IS 
    'Связь состояния диалога с пользователем. При удалении пользователя удаляется его состояние диалога';

COMMENT ON CONSTRAINT fk_conversation_states_event ON conversation_states IS 
    'Связь состояния диалога с событием для контекста вложений. При удалении события очищается контекст';

COMMENT ON CONSTRAINT conversation_states_attachment_context_logic ON conversation_states IS 
    'Бизнес-правило: контекст вложений должен быть либо полностью заполнен, либо полностью пуст';

COMMENT ON CONSTRAINT conversation_states_search_context_logic ON conversation_states IS 
    'Бизнес-правило: контекст поиска должен содержать и chat_id, и message_id одновременно';

COMMENT ON CONSTRAINT conversation_states_header_logic ON conversation_states IS 
    'Бизнес-правило: контекст шапки событий должен содержать флаг и неотрицательное количество событий';

-- ============================================================================
-- ЗАВЕРШЕНИЕ МИГРАЦИИ
-- ============================================================================
