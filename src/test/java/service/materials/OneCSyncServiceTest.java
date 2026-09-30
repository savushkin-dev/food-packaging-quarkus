package service.materials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.acme.foodpackaging.dto.materials.ProductWithMaterialsDto;
import org.acme.foodpackaging.dto.materials.SinvDto;
import org.acme.foodpackaging.exception.materials.OneCSyncException;
import org.acme.foodpackaging.service.materials.OneCSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OneCSyncServiceTest {

    private HttpServer server;
    private OneCSyncService oneCSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";

        oneCSyncService = new OneCSyncService(objectMapper, baseUrl, "user", "pass");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ==================== success ====================

    @Test
    void testSendOrder_Success_ReturnsTasknumber() {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        registerHandler(200, "{\"code\":0,\"tasknumber\":\"TN-123\"}", body, auth);

        List<ProductWithMaterialsDto> data = List.of(
                createProduct("0307060046", "1002051408", 10.0)
        );

        String result = oneCSyncService.sendOrder("KPP-2", "KPP-1", data);

        assertEquals("TN-123", result);
        assertTrue(body.get().contains("\"KPP1\":\"KPP-1\""), body.get());
        assertTrue(body.get().contains("\"KPP2\":\"KPP-2\""), body.get());
        assertTrue(body.get().contains("\"KMT\":\"1002051408\""), body.get());
        assertTrue(body.get().contains("\"KOLE\":10.0"), body.get());

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, auth.get());
    }

    @Test
    void testSendOrder_EmptyMaterials_SendsEmptyArray() {
        AtomicReference<String> body = new AtomicReference<>();
        registerHandler(200, "{\"code\":0,\"tasknumber\":\"TN-1\"}", body, new AtomicReference<>());

        oneCSyncService.sendOrder("KPP-2", "KPP-1", List.of());

        assertTrue(body.get().contains("\"MATERIALS\":[]"), body.get());
    }

    // ==================== buildRequest ====================

    @Test
    void testSendOrder_FilterOutZeroAndNegativeOrderFinal() {
        AtomicReference<String> body = new AtomicReference<>();
        registerHandler(200, "{\"code\":0,\"tasknumber\":\"TN-1\"}", body, new AtomicReference<>());

        List<ProductWithMaterialsDto> data = List.of(
                createProduct("0307060046", List.of(
                        createMaterial("1002051408", 10.0),
                        createMaterial("1002110286", 0.0),
                        createMaterial("1002999999", -5.0),
                        createMaterial("1002888888", null)
                ))
        );

        oneCSyncService.sendOrder("KPP-2", "KPP-1", data);

        assertTrue(body.get().contains("1002051408"), body.get());
        assertFalse(body.get().contains("1002110286"), body.get());
        assertFalse(body.get().contains("1002999999"), body.get());
        assertFalse(body.get().contains("1002888888"), body.get());
    }

    @Test
    void testSendOrder_DuplicateKmt_KeepsFirst() {
        AtomicReference<String> body = new AtomicReference<>();
        registerHandler(200, "{\"code\":0,\"tasknumber\":\"TN-1\"}", body, new AtomicReference<>());

        List<ProductWithMaterialsDto> data = List.of(
                createProduct("0307060046", List.of(
                        createMaterial("1002051408", 10.0),
                        createMaterial("1002051408", 99.0)
                ))
        );

        oneCSyncService.sendOrder("KPP-2", "KPP-1", data);

        assertTrue(body.get().contains("\"KOLE\":10.0"), body.get());
        assertFalse(body.get().contains("\"KOLE\":99.0"), body.get());
    }

    // ==================== business errors ====================

    @Test
    void testSendOrder_CodeIsNull_ThrowsOneCSyncException() {
        registerHandler(200, "{\"description\":\"some description\"}",
                new AtomicReference<>(), new AtomicReference<>());

        OneCSyncException ex = assertThrows(OneCSyncException.class,
                () -> oneCSyncService.sendOrder("KPP-2", "KPP-1", List.of()));

        assertTrue(ex.getMessage().contains("1C error"), ex.getMessage());
        assertTrue(ex.getMessage().contains("some description"), ex.getMessage());
    }

    @Test
    void testSendOrder_CodeNonZero_ThrowsOneCSyncException() {
        registerHandler(200, "{\"code\":5,\"description\":\"ошибка валидации\"}",
                new AtomicReference<>(), new AtomicReference<>());

        OneCSyncException ex = assertThrows(OneCSyncException.class,
                () -> oneCSyncService.sendOrder("KPP-2", "KPP-1", List.of()));

        assertTrue(ex.getMessage().contains("1C error"), ex.getMessage());
        assertTrue(ex.getMessage().contains("ошибка валидации"), ex.getMessage());
    }

    // ==================== HTTP errors ====================

    @Test
    void testSendOrder_Http401_ThrowsOneCSyncException() {
        registerHandler(401, "Unauthorized",
                new AtomicReference<>(), new AtomicReference<>());

        OneCSyncException ex = assertThrows(OneCSyncException.class,
                () -> oneCSyncService.sendOrder("KPP-2", "KPP-1", List.of()));

        assertTrue(ex.getMessage().contains("401"), ex.getMessage());
    }

    @Test
    void testSendOrder_Http500_ThrowsOneCSyncException() {
        registerHandler(500, "Internal Server Error",
                new AtomicReference<>(), new AtomicReference<>());

        OneCSyncException ex = assertThrows(OneCSyncException.class,
                () -> oneCSyncService.sendOrder("KPP-2", "KPP-1", List.of()));

        assertTrue(ex.getMessage().contains("500"), ex.getMessage());
    }

    @Test
    void testSendOrder_InvalidJson_ThrowsOneCSyncException() {
        registerHandler(200, "not-a-json",
                new AtomicReference<>(), new AtomicReference<>());

        OneCSyncException ex = assertThrows(OneCSyncException.class,
                () -> oneCSyncService.sendOrder("KPP-2", "KPP-1", List.of()));

        assertTrue(ex.getMessage().contains("Failed to send order to 1C"), ex.getMessage());
        assertNotNull(ex.getCause());
    }

    // ==================== helpers ====================

    private void registerHandler(int status, String responseBody,
                                 AtomicReference<String> capturedBody,
                                 AtomicReference<String> capturedAuth) {
        server.createContext("/api", exchange -> {
            try {
                capturedBody.set(new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));

                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ProductWithMaterialsDto createProduct(String kmc, String kmt, Double orderFinal) {
        return createProduct(kmc, List.of(createMaterial(kmt, orderFinal)));
    }

    private ProductWithMaterialsDto createProduct(String kmc, List<SinvDto> materials) {
        return ProductWithMaterialsDto.builder()
                .dt(LocalDate.of(2026, 2, 15))
                .kpp("01020391")
                .kmc(kmc)
                .kt("2201040296")
                .ean13("4810268043727")
                .emk(18.0)
                .name("Тестовый продукт")
                .sumMass(1000.0)
                .sumKolev(1000.0)
                .krkmc(2743.0)
                .materials(new ArrayList<>(materials))
                .build();
    }

    private SinvDto createMaterial(String kmt, Double orderFinal) {
        return SinvDto.builder()
                .dt(LocalDate.of(2026, 2, 15))
                .kpp("01020391")
                .kmc("0307060046")
                .kt("2201040296")
                .kmt(kmt)
                .norm(10.0)
                .normf(10.0)
                .kolf(0.0)
                .insurancePerc(10.0)
                .roundStep(1.0)
                .orderFinal(orderFinal)
                .build();
    }
}