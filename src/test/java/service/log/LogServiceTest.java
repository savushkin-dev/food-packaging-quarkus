package service.log;

import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.acme.foodpackaging.entity.RequestLog;
import org.acme.foodpackaging.repository.RequestLogRepository;
import org.acme.foodpackaging.service.log.LogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @InjectMocks
    LogService logService;

    @Mock
    RequestLogRepository requestLogRepository;

    @Mock
    ContainerRequestContext requestContext;

    @Mock
    HttpServerRequest vertxRequest;

    @Mock
    io.vertx.core.net.SocketAddress socketAddress;

    private static final String DEFAULT_SESSION_ID = "default_session_id";

    // ==================== Тесты для logRequest ====================

    @Test
    void logRequest_shouldPersistLog() {
        String login = "testuser";
        String ip = "192.168.1.100";
        String method = "POST";
        String query = "{}";

        logService.logRequest(login, ip, method, query);

        ArgumentCaptor<RequestLog> logCaptor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository, times(1)).persist(logCaptor.capture());

        RequestLog savedLog = logCaptor.getValue();
        assertEquals(login, savedLog.getLogin());
        assertEquals(ip, savedLog.getIp());
        assertEquals(method, savedLog.getMethod());
        assertEquals(query, savedLog.getQuery());
        assertNotNull(savedLog.getDateTime());
    }

    @Test
    void logRequest_withLongQuery_shouldTrimTo7000() {
        String longQuery = "a".repeat(8000);

        logService.logRequest("user", "127.0.0.1", "GET", longQuery);

        ArgumentCaptor<RequestLog> logCaptor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).persist(logCaptor.capture());

        assertEquals(7000, logCaptor.getValue().getQuery().length());
        assertEquals("a".repeat(7000), logCaptor.getValue().getQuery());
    }

    @Test
    void logRequest_withNullQuery_shouldPersistNull() {
        logService.logRequest("user", "127.0.0.1", "GET", null);

        ArgumentCaptor<RequestLog> logCaptor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).persist(logCaptor.capture());

        assertNull(logCaptor.getValue().getQuery());
    }

    // ==================== Тесты для trimToColumnLength ====================

    @Test
    void trimToColumnLength_whenShorter_returnsSame() {
        String result = logService.trimToColumnLength("Short", 100);
        assertEquals("Short", result);
    }

    @Test
    void trimToColumnLength_whenLonger_truncates() {
        String value = "a".repeat(150);
        String result = logService.trimToColumnLength(value, 100);

        assertEquals(100, result.length());
        assertEquals("a".repeat(100), result);
    }

    @Test
    void trimToColumnLength_whenNull_returnsNull() {
        assertNull(logService.trimToColumnLength(null, 100));
    }

    // ==================== Тесты для getIp ====================

    @Test
    void getIp_withXRealIP_returnsXRealIP() {
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn("10.0.0.1");

        String result = logService.getIp(requestContext, vertxRequest);

        assertEquals("10.0.0.1", result);
        verify(vertxRequest, never()).remoteAddress();
    }

    @Test
    void getIp_withXForwardedForWhenXRealIPMissing_returnsXForwardedFor() {
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn(null);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("10.0.0.2");

        String result = logService.getIp(requestContext, vertxRequest);

        assertEquals("10.0.0.2", result);
    }

    @Test
    void getIp_withRemoteAddressWhenHeadersMissing_returnsRemoteAddress() {
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn(null);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn(null);
        when(vertxRequest.remoteAddress()).thenReturn(socketAddress);
        when(socketAddress.host()).thenReturn("192.168.1.100");

        String result = logService.getIp(requestContext, vertxRequest);

        assertEquals("192.168.1.100", result);
    }

    @Test
    void getIp_whenAllHeadersEmptyAndRemoteAddressEmpty_returnsUnknown() {
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn("");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("");
        when(vertxRequest.remoteAddress()).thenReturn(socketAddress);
        when(socketAddress.host()).thenReturn("");

        String result = logService.getIp(requestContext, vertxRequest);

        assertEquals("unknown", result);
    }

    @Test
    void getIp_whenXRealIPBlankAndXForwardedForValid_returnsXForwardedFor() {
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn("");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("10.0.0.2");

        String result = logService.getIp(requestContext, vertxRequest);

        assertEquals("10.0.0.2", result);
    }

    // ==================== Тесты для getLoginIdentifier ====================

    @Test
    void getLoginIdentifier_withUsername_returnsUsername() {
        String result = logService.getLoginIdentifier("john", "id_123", DEFAULT_SESSION_ID);
        assertEquals("john", result);
    }

    @Test
    void getLoginIdentifier_withoutUsernameButValidSessionId_returnsSessionId() {
        String result = logService.getLoginIdentifier(null, "id_123", DEFAULT_SESSION_ID);
        assertEquals("id_123", result);
    }

    @Test
    void getLoginIdentifier_withEmptyUsernameAndValidSessionId_returnsSessionId() {
        String result = logService.getLoginIdentifier("", "id_123", DEFAULT_SESSION_ID);
        assertEquals("id_123", result);
    }

    @Test
    void getLoginIdentifier_withBlankUsernameAndValidSessionId_returnsSessionId() {
        String result = logService.getLoginIdentifier("   ", "id_123", DEFAULT_SESSION_ID);
        assertEquals("id_123", result);
    }

    @Test
    void getLoginIdentifier_withoutUsernameAndDefaultSessionId_returnsDefault() {
        String result = logService.getLoginIdentifier(null, DEFAULT_SESSION_ID, DEFAULT_SESSION_ID);
        assertEquals(DEFAULT_SESSION_ID, result);
    }

    @Test
    void getLoginIdentifier_withUsernameAndDefaultSessionId_returnsUsername() {
        String result = logService.getLoginIdentifier("john", DEFAULT_SESSION_ID, DEFAULT_SESSION_ID);
        assertEquals("john", result);
    }

    @Test
    void getLoginIdentifier_withoutUsernameAndNullSessionId_returnsDefault() {
        String result = logService.getLoginIdentifier(null, null, DEFAULT_SESSION_ID);
        assertEquals(DEFAULT_SESSION_ID, result);
    }

    @Test
    void getLoginIdentifier_withoutUsernameAndEmptySessionId_returnsDefault() {
        String result = logService.getLoginIdentifier(null, "", DEFAULT_SESSION_ID);
        assertEquals(DEFAULT_SESSION_ID, result);
    }

    @Test
    void getLoginIdentifier_withoutUsernameAndBlankSessionId_returnsDefault() {
        String result = logService.getLoginIdentifier(null, "   ", DEFAULT_SESSION_ID);
        assertEquals(DEFAULT_SESSION_ID, result);
    }

    @Test
    void getLoginIdentifier_withDefaultSessionIdCaseInsensitive_returnsDefault() {
        String result = logService.getLoginIdentifier(null, "DEFAULT_SESSION_ID", DEFAULT_SESSION_ID);
        assertEquals(DEFAULT_SESSION_ID, result);
    }

    @Test
    void getIp_whenXRealIPNullAndXForwardedForNullAndRemoteAddressHasBlankHost_returnsUnknown() {
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn(null);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn(null);
        when(vertxRequest.remoteAddress()).thenReturn(socketAddress);
        when(socketAddress.host()).thenReturn("");

        String result = logService.getIp(requestContext, vertxRequest);

        assertEquals("unknown", result);
    }

    @Test
    void getIp_whenXRealIPBlankAndXForwardedForBlankAndRemoteAddressHasBlankHost_returnsUnknown() {
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn("");
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("");
        when(vertxRequest.remoteAddress()).thenReturn(socketAddress);
        when(socketAddress.host()).thenReturn("");

        String result = logService.getIp(requestContext, vertxRequest);

        assertEquals("unknown", result);
    }

    @Test
    void getLoginIdentifier_whenSessionIdEqualsDefaultSessionId_returnsDefault() {
        String result = logService.getLoginIdentifier(null, DEFAULT_SESSION_ID, DEFAULT_SESSION_ID);
        assertEquals(DEFAULT_SESSION_ID, result);
    }
}