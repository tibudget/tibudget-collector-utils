package com.tibudget.utils;

import com.tibudget.api.exceptions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AbstractCollectorPluginTest {

    @Mock
    Logger logger;

    AbstractCollectorPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new AbstractCollectorPlugin() {
            @Override
            public void collect() throws CollectError, AccessDeny, TemporaryUnavailable, ConnectionFailure, ParameterError {
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
            protected Logger getLogger() {
                return logger;
            }
        };

        plugin.addHeader("Authorization", "Bearer secret");
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
