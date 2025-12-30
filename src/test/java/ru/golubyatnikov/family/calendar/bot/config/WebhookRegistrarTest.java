package ru.golubyatnikov.family.calendar.bot.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit тесты для {@link WebhookRegistrar}.
 * 
 * <p>Проверяет корректность регистрации webhook при старте приложения.
 * Тестируются только успешные сценарии, так как неуспешные приводят к System.exit().
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookRegistrar Unit Tests")
class WebhookRegistrarTest {

    @Mock
    private BotConfig botConfig;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private RestTemplate restTemplate;

    private WebhookRegistrar webhookRegistrar;

    @BeforeEach
    void setUp() {
        // Настраиваем моки для BotConfig
        when(botConfig.getToken()).thenReturn("test-bot-token");
        when(botConfig.getUsername()).thenReturn("TestBot");
        when(botConfig.getWebhookUrl()).thenReturn("https://example.com/webhook");

        webhookRegistrar = new WebhookRegistrar(botConfig, applicationContext, restTemplate);
    }

    @Test
    @DisplayName("Должен успешно зарегистрировать webhook при корректном ответе от Telegram API")
    void shouldSuccessfullyRegisterWebhook() {
        // Given
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("ok", true);
        successResponse.put("result", true);
        successResponse.put("description", "Webhook was set");

        ResponseEntity<Map> responseEntity = new ResponseEntity<>(successResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(responseEntity);

        // When
        webhookRegistrar.registerWebhook();

        // Then
        verify(restTemplate, times(1)).exchange(
                contains("setWebhook"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        );

        // Проверяем, что URL содержит токен
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        );

        String capturedUrl = urlCaptor.getValue();
        assertTrue(capturedUrl.contains("test-bot-token"), 
                "URL должен содержать токен бота");
        assertTrue(capturedUrl.contains("setWebhook"), 
                "URL должен содержать метод setWebhook");
    }

    @Test
    @DisplayName("Должен проверять, что webhook URL передается в запросе")
    void shouldSendWebhookUrlInRequest() {
        // Given
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("ok", true);

        ResponseEntity<Map> responseEntity = new ResponseEntity<>(successResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(responseEntity);

        // When
        webhookRegistrar.registerWebhook();

        // Then
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(Map.class)
        );

        HttpEntity<Map<String, String>> capturedRequest = requestCaptor.getValue();
        Map<String, String> requestBody = capturedRequest.getBody();

        assertNotNull(requestBody, "Тело запроса не должно быть null");
        assertEquals("https://example.com/webhook", requestBody.get("url"),
                "URL webhook должен совпадать с конфигурацией");
    }

    @Test
    @DisplayName("Должен использовать правильный формат URL для Telegram API")
    void shouldUseCorrectTelegramApiUrl() {
        // Given
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("ok", true);

        ResponseEntity<Map> responseEntity = new ResponseEntity<>(successResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(responseEntity);

        // When
        webhookRegistrar.registerWebhook();

        // Then
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        );

        String capturedUrl = urlCaptor.getValue();
        assertTrue(capturedUrl.startsWith("https://api.telegram.org/bot"),
                "URL должен начинаться с https://api.telegram.org/bot");
        assertTrue(capturedUrl.contains("/setWebhook"),
                "URL должен содержать /setWebhook");
    }

    @Test
    @DisplayName("Должен использовать правильные HTTP заголовки")
    void shouldUseCorrectHttpHeaders() {
        // Given
        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("ok", true);

        ResponseEntity<Map> responseEntity = new ResponseEntity<>(successResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(responseEntity);

        // When
        webhookRegistrar.registerWebhook();

        // Then
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(Map.class)
        );

        HttpEntity<?> capturedRequest = requestCaptor.getValue();
        assertNotNull(capturedRequest.getHeaders(), "Заголовки не должны быть null");
        assertTrue(capturedRequest.getHeaders().getContentType().toString().contains("application/json"),
                "Content-Type должен быть application/json");
    }
}
