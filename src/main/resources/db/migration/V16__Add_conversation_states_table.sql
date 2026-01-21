-- Создание таблицы для хранения состояния диалогов пользователей
-- Используется для сохранения контекста сообщений с вложениями между операциями

CREATE TABLE conversation_states (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    
    -- Контекст сообщения с вложениями
    attachment_event_id BIGINT,
    attachment_chat_id BIGINT,
    attachment_message_id INTEGER,
    attachment_context_created_at TIMESTAMP,
    
    -- Временные метки
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ограничения
    CONSTRAINT fk_conversation_states_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_states_event FOREIGN KEY (attachment_event_id) REFERENCES events(id) ON DELETE CASCADE
);

-- Индекс для быстрого поиска по user_id (один пользователь = одно состояние)
CREATE UNIQUE INDEX idx_conversation_states_user_id ON conversation_states(user_id);

-- Индекс для поиска по событию
CREATE INDEX idx_conversation_states_event_id ON conversation_states(attachment_event_id);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE conversation_states IS 'Хранит состояние диалогов пользователей с ботом';
COMMENT ON COLUMN conversation_states.user_id IS 'Идентификатор пользователя';
COMMENT ON COLUMN conversation_states.attachment_event_id IS 'Идентификатор события для контекста вложений';
COMMENT ON COLUMN conversation_states.attachment_chat_id IS 'Идентификатор чата для контекста вложений';
COMMENT ON COLUMN conversation_states.attachment_message_id IS 'Идентификатор сообщения для редактирования при работе с вложениями';
COMMENT ON COLUMN conversation_states.attachment_context_created_at IS 'Время создания контекста вложений (для проверки истечения 47 часов)';
