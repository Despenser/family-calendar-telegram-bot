package ru.golubyatnikov.family.calendar.bot.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Утилита для валидации тона сообщений.
 * 
 * <p>MessageToneValidator проверяет, что сообщения используют позитивный и
 * конструктивный тон, избегая негативных формулировок.</p>
 * 
 * <p>Валидатор выполняет следующие проверки:</p>
 * <ul>
 *   <li>Отсутствие негативных формулировок ("запрещен", "не можете", "нельзя")</li>
 *   <li>Наличие конструктивных фраз ("доступно", "станет доступно", "будет доступен")</li>
 *   <li>Использование позитивного и приветливого тона</li>
 * </ul>
 * 
 * <p><b>Требования:</b> 7.1, 7.2, 7.3, 7.4, 7.5</p>
 * 
 * <p><b>Пример использования:</b></p>
 * <pre>{@code
 * String message = "Эта функция доступна после регистрации";
 * ValidationResult result = MessageToneValidator.validate(message);
 * 
 * if (!result.isValid()) {
 *     System.out.println("Проблемы: " + result.getIssues());
 * }
 * }</pre>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-12
 */
@Slf4j
public class MessageToneValidator {
    
    /**
     * Негативные формулировки, которых следует избегать в сообщениях.
     * 
     * <p>Эти слова и фразы создают негативное впечатление и могут
     * оттолкнуть пользователей.</p>
     */
    private static final Set<String> NEGATIVE_PHRASES = Set.of(
        "запрещен",
        "запрещено",
        "запрещена",
        "не можете",
        "не может",
        "нельзя",
        "недоступно",
        "отказано",
        "отклонено",
        "заблокирован",
        "ограничен",
        "невозможно"
    );
    
    /**
     * Конструктивные фразы, которые должны присутствовать в сообщениях.
     * 
     * <p>Эти слова и фразы создают позитивное впечатление и мотивируют
     * пользователей к регистрации.</p>
     */
    private static final Set<String> CONSTRUCTIVE_PHRASES = Set.of(
        "доступно",
        "доступен",
        "доступна",
        "станет доступно",
        "станет доступен",
        "станет доступна",
        "будет доступно",
        "будет доступен",
        "будет доступна",
        "после регистрации",
        "зарегистрированным пользователям",
        "членам семейного календаря"
    );
    
    /**
     * Валидирует тон сообщения.
     * 
     * <p>Проверяет сообщение на соответствие требованиям позитивного тона:</p>
     * <ol>
     *   <li>Отсутствие негативных формулировок</li>
     *   <li>Наличие хотя бы одной конструктивной фразы</li>
     * </ol>
     * 
     * @param message текст сообщения для проверки
     * @return результат валидации с информацией о найденных проблемах
     * @throws IllegalArgumentException если message равен null или пустой
     */
    public static ValidationResult validate(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Сообщение не может быть null или пустым");
        }
        
        String lowerMessage = message.toLowerCase();
        ValidationResult result = new ValidationResult();
        
        // Проверка на негативные формулировки
        List<String> foundNegative = NEGATIVE_PHRASES.stream()
            .filter(lowerMessage::contains)
            .toList();
        
        if (!foundNegative.isEmpty()) {
            result.addIssue("Найдены негативные формулировки: " + String.join(", ", foundNegative));
            log.warn("Сообщение содержит негативные формулировки: {}", foundNegative);
        }
        
        // Проверка на наличие конструктивных фраз
        boolean hasConstructive = CONSTRUCTIVE_PHRASES.stream()
            .anyMatch(lowerMessage::contains);
        
        if (!hasConstructive) {
            result.addIssue("Отсутствуют конструктивные фразы. Добавьте фразы типа 'доступно', 'станет доступно', 'после регистрации'");
            log.warn("Сообщение не содержит конструктивных фраз");
        }
        
        if (result.isValid()) {
            log.debug("Сообщение прошло валидацию тона");
        }
        
        return result;
    }
    
    /**
     * Результат валидации сообщения.
     * 
     * <p>Содержит информацию о том, прошло ли сообщение валидацию,
     * и список найденных проблем.</p>
     */
    public static class ValidationResult {
        private final List<String> issues;
        
        /**
         * Создает новый результат валидации.
         */
        public ValidationResult() {
            this.issues = new java.util.ArrayList<>();
        }
        
        /**
         * Добавляет проблему в результат валидации.
         * 
         * @param issue описание проблемы
         */
        void addIssue(String issue) {
            issues.add(issue);
        }
        
        /**
         * Проверяет, прошло ли сообщение валидацию.
         * 
         * @return true, если проблем не найдено, false в противном случае
         */
        public boolean isValid() {
            return issues.isEmpty();
        }
        
        /**
         * Возвращает список найденных проблем.
         * 
         * @return неизменяемый список проблем
         */
        public List<String> getIssues() {
            return List.copyOf(issues);
        }
        
        @Override
        public String toString() {
            if (isValid()) {
                return "ValidationResult{valid=true}";
            }
            return "ValidationResult{valid=false, issues=" + issues + "}";
        }
    }
}
