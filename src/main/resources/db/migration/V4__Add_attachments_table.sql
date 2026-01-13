-- ============================================================================
-- Миграция V4: Создание таблицы вложений
-- ============================================================================
-- Описание: Создание таблицы attachments для хранения файлов, прикрепленных
--           к событиям (билеты, документы, изображения)
-- Требования: 20.2, 20.3
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Таблица attachments: Хранит вложения событий
-- ----------------------------------------------------------------------------
CREATE TABLE attachments (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    file_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(255),
    file_type VARCHAR(50),
    file_size BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT attachments_event_fk FOREIGN KEY (event_id) 
        REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT attachments_file_id_not_empty CHECK (LENGTH(TRIM(file_id)) > 0),
    CONSTRAINT attachments_file_size_positive CHECK (file_size IS NULL OR file_size > 0)
);

-- Комментарии для таблицы attachments
COMMENT ON TABLE attachments IS 
    'Вложения событий: файлы, документы, изображения, прикрепленные к событиям';

COMMENT ON COLUMN attachments.id IS 
    'Уникальный идентификатор вложения';

COMMENT ON COLUMN attachments.event_id IS 
    'Ссылка на событие, к которому прикреплен файл';

COMMENT ON COLUMN attachments.file_id IS 
    'Telegram file_id для получения файла через Bot API';

COMMENT ON COLUMN attachments.file_name IS 
    'Оригинальное имя файла';

COMMENT ON COLUMN attachments.file_type IS 
    'Тип файла: document, photo, video, audio';

COMMENT ON COLUMN attachments.file_size IS 
    'Размер файла в байтах';

COMMENT ON COLUMN attachments.uploaded_at IS 
    'Дата и время загрузки файла';

-- ----------------------------------------------------------------------------
-- Индексы для таблицы attachments
-- ----------------------------------------------------------------------------

-- Индекс для быстрого получения всех вложений события
CREATE INDEX idx_attachments_event_id ON attachments(event_id);

COMMENT ON INDEX idx_attachments_event_id IS 
    'Индекс для быстрого получения всех вложений конкретного события';

-- Составной индекс для получения вложений события с сортировкой по дате
CREATE INDEX idx_attachments_event_uploaded ON attachments(event_id, uploaded_at);

COMMENT ON INDEX idx_attachments_event_uploaded IS 
    'Индекс для получения вложений события с сортировкой по дате загрузки';

-- ============================================================================
-- Конец миграции V4
-- ============================================================================
