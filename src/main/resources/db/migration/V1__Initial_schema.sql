-- ============================================================================
-- Миграция V1: Начальная схема базы данных
-- ============================================================================
-- Описание: Создание таблиц families, users, events с индексами и constraints
-- Требования: 11.1, 11.2, 11.3, 11.4
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Таблица families: Хранит информацию о семьях
-- ----------------------------------------------------------------------------
CREATE TABLE families (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT families_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

-- Комментарии для таблицы families
COMMENT ON TABLE families IS 'Семьи пользователей с общим календарем';
COMMENT ON COLUMN families.id IS 'Уникальный идентификатор семьи';
COMMENT ON COLUMN families.name IS 'Название семьи';
COMMENT ON COLUMN families.created_at IS 'Дата и время создания семьи';

-- ----------------------------------------------------------------------------
-- Таблица users: Хранит информацию о пользователях Telegram
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT NOT NULL,
    username VARCHAR(255),
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255),
    family_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT users_telegram_id_unique UNIQUE (telegram_id),
    CONSTRAINT users_first_name_not_empty CHECK (LENGTH(TRIM(first_name)) > 0),
    CONSTRAINT users_family_fk FOREIGN KEY (family_id) 
        REFERENCES families(id) ON DELETE SET NULL
);

-- Комментарии для таблицы users
COMMENT ON TABLE users IS 'Пользователи бота с привязкой к Telegram ID';
COMMENT ON COLUMN users.id IS 'Уникальный идентификатор пользователя';
COMMENT ON COLUMN users.telegram_id IS 'Уникальный идентификатор пользователя в Telegram';
COMMENT ON COLUMN users.username IS 'Username пользователя в Telegram (может отсутствовать)';
COMMENT ON COLUMN users.first_name IS 'Имя пользователя';
COMMENT ON COLUMN users.last_name IS 'Фамилия пользователя (опционально)';
COMMENT ON COLUMN users.family_id IS 'Ссылка на семью пользователя';
COMMENT ON COLUMN users.created_at IS 'Дата и время регистрации пользователя';

-- Индексы для таблицы users
CREATE INDEX idx_users_telegram_id ON users(telegram_id);
CREATE INDEX idx_users_family_id ON users(family_id);

-- ----------------------------------------------------------------------------
-- Таблица events: Хранит события календаря
-- ----------------------------------------------------------------------------
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    event_date DATE NOT NULL,
    event_time TIME NOT NULL,
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT events_title_not_empty CHECK (LENGTH(TRIM(title)) > 0),
    CONSTRAINT events_user_fk FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT events_family_fk FOREIGN KEY (family_id) 
        REFERENCES families(id) ON DELETE CASCADE
);

-- Комментарии для таблицы events
COMMENT ON TABLE events IS 'События семейного календаря';
COMMENT ON COLUMN events.id IS 'Уникальный идентификатор события';
COMMENT ON COLUMN events.user_id IS 'Создатель события';
COMMENT ON COLUMN events.family_id IS 'Семья, к которой относится событие';
COMMENT ON COLUMN events.title IS 'Название события';
COMMENT ON COLUMN events.description IS 'Подробное описание события';
COMMENT ON COLUMN events.event_date IS 'Дата события';
COMMENT ON COLUMN events.event_time IS 'Время события';
COMMENT ON COLUMN events.notified IS 'Флаг отправки уведомления о событии';
COMMENT ON COLUMN events.created_at IS 'Дата и время создания записи о событии';

-- Индексы для таблицы events
-- Составной индекс для быстрого поиска событий семьи в диапазоне дат
CREATE INDEX idx_events_family_date ON events(family_id, event_date);

-- Индекс для поиска событий пользователя
CREATE INDEX idx_events_user_id ON events(user_id);

-- Составной индекс для поиска событий, требующих уведомления
-- Используется NotificationService для поиска событий через 1 час
CREATE INDEX idx_events_notification ON events(notified, event_date, event_time) 
    WHERE notified = FALSE;

-- Индекс для сортировки событий по дате и времени
CREATE INDEX idx_events_datetime ON events(event_date, event_time);

-- ============================================================================
-- Конец миграции V1
-- ============================================================================
