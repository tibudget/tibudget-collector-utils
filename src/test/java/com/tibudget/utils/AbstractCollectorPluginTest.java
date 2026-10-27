package com.tibudget.utils;

import com.tibudget.api.exceptions.*;
import com.tibudget.api.InternetProvider;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AbstractCollectorPluginTest {

    @Mock
    Logger logger;

    AbstractCollectorPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new AbstractCollectorPlugin() {
            @Override
            public boolean collect() throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure, ParameterError {
                return true;
            }

            @Override
            public String getConfigurationIdHash() {
                return "123";
            }

            @Override
            public String getDomain() {
                return "https://example.com";
            }

            @Override
            public Logger getLogger() {
                return logger;
            }
        };

        plugin.addHeader("Authorization", "Bearer secret");
    }

    @Test
    void shouldRejectMissingAndFailedFormResponses() throws Exception {
        InternetProvider internet = mock(InternetProvider.class);
        plugin.internetProvider = internet;
        plugin.setMinIntervalBetweenRequestInMs(0);
        assertThrows(TemporaryUnavailable.class, () -> plugin.postForm("/login", Map.of()));
        when(internet.post(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new InternetProvider.Response(503, "Unavailable", "POST", "https://example.com/login", "http/1.1", "error", Map.of()));
        assertThrows(TemporaryUnavailable.class, () -> plugin.postForm("/login", Map.of()));
        when(internet.post(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new InternetProvider.Response(200, "OK", "POST", "https://example.com/home", "http/1.1", "<p>Welcome</p>", Map.of()));
        assertEquals("Welcome", plugin.postForm("/login", Map.of()).selectFirst("p").text());
        assertEquals("https://example.com/home", plugin.getCurrentUrl());
    }

    @Test
    void shouldValidateJsonPostResponseBeforeReadingItsLocation() throws Exception {
        InternetProvider internet = mock(InternetProvider.class);
        plugin.internetProvider = internet;
        plugin.setMinIntervalBetweenRequestInMs(0);
        assertThrows(TemporaryUnavailable.class, () -> plugin.postJson("/api", Map.class, new PostData().asJSON()));
        when(internet.post(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new InternetProvider.Response(403, "Forbidden", "POST", "https://example.com/api", "http/1.1", "denied", Map.of()));
        assertThrows(AccessDeny.class, () -> plugin.postJson("/api", Map.class, new PostData().asJSON()));
        assertNull(plugin.getCurrentUrl());
    }

    @Test
    void shouldReportMissingRequiredElementsWithoutACollectorSpecificMessageKey() throws CollectError {
        assertThrows(CollectError.class, () -> plugin.required(null, ".order"));
        assertThrows(CollectError.class, () -> plugin.required(Jsoup.parse("<p>empty</p>"), ".order"));
        assertEquals("found", plugin.required(Jsoup.parse("<p class='order'>found</p>"), ".order").text());
    }

    @Test
    void shouldLogSingleLineJsonWithStackTrace() {
        Exception exception = new IOException("Connection reset");

        plugin.logNetworkError(
                "GET_JSON",
                "/api/accounts?token=SECRET",
                exception
        );

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(1)).severe(captor.capture());

        String logLine = captor.getValue();

        // One line only
        assertFalse(logLine.contains("\n"));
        assertFalse(logLine.contains("\r"));

        // JSON structure
        assertTrue(logLine.startsWith("{"));
        assertTrue(logLine.endsWith("}"));

        // Mandatory fields
        assertTrue(logLine.contains("\"type\":\"network_error\""));
        assertTrue(logLine.contains("\"operation\":\"GET_JSON\""));
        assertTrue(logLine.contains("\"exception\":\"java.io.IOException\""));
        assertTrue(logLine.contains("\"stackTrace\""));

        // Security checks
        assertFalse(logLine.contains("SESSION"));
        assertFalse(logLine.contains("Bearer"));
        assertFalse(logLine.contains("token="));
    }
}
