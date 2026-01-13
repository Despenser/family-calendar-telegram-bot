package ru.golubyatnikov.family.calendar.bot.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты для {@link MessageToneValidator}.
 * 
 * <p>Проверяет корректность валидации тона сообщений на соответствие
 * требованиям позитивного и конструктивного тона.</p>
 * 
 * @author Family Calendar Bot Team
 * @version 1.0.0
 * @since 2026-01-12
 */
@DisplayName("MessageToneValidator Tests")
class MessageToneValidatorTest {
    
    @Test
    @DisplayName("Должен успешно валидировать сообщение с позитивным тоном")
    void shouldValidatePositiveMessage() {
        // Given
        String message = "Эта функция доступна только зарегистрированным пользователям семейного календаря.";
        
        // When
        MessageToneValidator.ValidationResult result = MessageToneValidator.validate(message);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getIssues()).isEmpty();
    }
    
    @Test
    @DisplayName("Должен обнаружить негативные формулировки")
    void shouldDetectNegativePhrases() {
        // Given
        String message = "Доступ запрещен. Вы не можете использовать эту функцию.";
        
        // When
        MessageToneValidator.ValidationResult result = MessageToneValidator.validate(message);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).isNotEmpty();
        assertThat(result.getIssues().get(0)).contains("негативные формулировки");
    }
    
    @Test
    @DisplayName("Должен обнаружить отсутствие конструктивных фраз")
    void shouldDetectMissingConstructivePhrases() {
        // Given
        String message = "Эта функция требует регистрации.";
        
        // When
        MessageToneValidator.ValidationResult result = MessageToneValidator.validate(message);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).isNotEmpty();
        assertThat(result.getIssues().get(0)).contains("конструктивные фразы");
    }
    
    @Test
    @DisplayName("Должен валидировать сообщение с фразой 'станет доступно'")
    void shouldValidateMessageWithWillBeAvailable() {
        // Given
        String message = "Эта функция станет доступна после регистрации в семейном календаре.";
        
        // When
        MessageToneValidator.ValidationResult result = MessageToneValidator.validate(message);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getIssues()).isEmpty();
    }
    
    @Test
    @DisplayName("Должен валидировать сообщение с фразой 'членам семейного календаря'")
    void shouldValidateMessageWithFamilyMembers() {
        // Given
        String message = "Просмотр событий доступен только членам семейного календаря.";
        
        // When
        MessageToneValidator.ValidationResult result = MessageToneValidator.validate(message);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getIssues()).isEmpty();
    }
    
    @Test
    @DisplayName("Должен выбросить исключение для null сообщения")
    void shouldThrowExceptionForNullMessage() {
        // When & Then
        assertThatThrownBy(() -> MessageToneValidator.validate(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("не может быть null");
    }
    
    @Test
    @DisplayName("Должен выбросить исключение для пустого сообщения")
    void shouldThrowExceptionForEmptyMessage() {
        // When & Then
        assertThatThrownBy(() -> MessageToneValidator.validate("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("не может быть null или пустым");
    }
    
    @Test
    @DisplayName("Должен обнаружить несколько негативных формулировок")
    void shouldDetectMultipleNegativePhrases() {
        // Given
        String message = "Доступ запрещен. Вы не можете использовать эту функцию. Это невозможно.";
        
        // When
        MessageToneValidator.ValidationResult result = MessageToneValidator.validate(message);
        
        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).hasSize(2); // негативные фразы + отсутствие конструктивных
        assertThat(result.getIssues().get(0))
            .contains("запрещен")
            .contains("не можете")
            .contains("невозможно");
    }
    
    @Test
    @DisplayName("Должен игнорировать регистр при проверке")
    void shouldBeCaseInsensitive() {
        // Given
        String message = "Эта функция ДОСТУПНА только ЗАРЕГИСТРИРОВАННЫМ ПОЛЬЗОВАТЕЛЯМ.";
        
        // When
        MessageToneValidator.ValidationResult result = MessageToneValidator.validate(message);
        
        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getIssues()).isEmpty();
    }
    
    @Test
    @DisplayName("ValidationResult должен корректно отображаться в toString")
    void validationResultShouldHaveCorrectToString() {
        // Given
        String validMessage = "Эта функция доступна зарегистрированным пользователям.";
        String invalidMessage = "Доступ запрещен.";
        
        // When
        MessageToneValidator.ValidationResult validResult = MessageToneValidator.validate(validMessage);
        MessageToneValidator.ValidationResult invalidResult = MessageToneValidator.validate(invalidMessage);
        
        // Then
        assertThat(validResult.toString()).contains("valid=true");
        assertThat(invalidResult.toString()).contains("valid=false").contains("issues=");
    }
}
