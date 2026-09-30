package org.acme.foodpackaging.service.materials;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.acme.foodpackaging.dto.materials.OneCRemoteReq;
import org.acme.foodpackaging.dto.materials.OneCRemoteResp;
import org.acme.foodpackaging.dto.materials.ProductWithMaterialsDto;
import org.acme.foodpackaging.dto.materials.SinvDto;
import org.acme.foodpackaging.exception.materials.OneCSyncException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class OneCSyncService {

    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_OK = 200;

    @ConfigProperty(name = "one-c.url")
    String oneCUrl;

    @ConfigProperty(name = "one-c.username")
    String oneCUsername;

    @ConfigProperty(name = "one-c.password")
    String oneCPassword;

    private final ObjectMapper objectMapper;

    @Inject
    public OneCSyncService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Отправляет заявку в 1С и возвращает номер заявки (tasknumber)
     */
    public String sendOrder(String kpp, String kppc, List<ProductWithMaterialsDto> data) {
        try {
            OneCRemoteReq request = buildRequest(kpp, kppc, data);
            String responseBody = sendHttpRequest(request);

            OneCRemoteResp oneCResponse = objectMapper.readValue(responseBody, OneCRemoteResp.class);

            if (oneCResponse.getCode() == null || oneCResponse.getCode() != 0) {
                throw new OneCSyncException("1C error: " + oneCResponse.getDescription());
            }

            return oneCResponse.getTasknumber();

        } catch (Exception e) {
            log.error("Failed to send order to 1C", e);
            throw new OneCSyncException("Failed to send order to 1C: " + e.getMessage(), e);
        }
    }

    /**
     * Формирует запрос для 1С
     */
    private OneCRemoteReq buildRequest(String kpp, String kppc, List<ProductWithMaterialsDto> data) {
        Map<String, Double> materialsMap = data.stream()
                .flatMap(p -> p.getMaterials().stream())
                .filter(m -> m.getOrderFinal() != null && m.getOrderFinal() > 0)
                .collect(Collectors.toMap(
                        SinvDto::getKmt,
                        SinvDto::getOrderFinal,
                        (a, b) -> a
                ));

        List<OneCRemoteReq.OneCMaterial> materials = materialsMap.entrySet().stream()
                .map(e -> OneCRemoteReq.OneCMaterial.builder()
                        .kmt(e.getKey())
                        .kole(e.getValue())
                        .build())
                .collect(Collectors.toList());

        return OneCRemoteReq.builder()
                .kpp1(kppc)
                .kpp2(kpp)
                .materials(materials)
                .build();
    }

    /**
     * Отправляет HTTP-запрос в 1С
     */
    private String sendHttpRequest(OneCRemoteReq request) throws IOException, InterruptedException {
        String jsonBody = objectMapper.writeValueAsString(request);
        log.info("Sending order to 1C: {}", jsonBody);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(oneCUrl))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("Authorization", buildBasicAuthHeader())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        log.info("1C response status: {}", response.statusCode());
        log.info("1C response body: {}", response.body());

        if (response.statusCode() == HTTP_UNAUTHORIZED) {
            throw new OneCSyncException("1C authorization failed (401)");
        }

        if (response.statusCode() != HTTP_OK) {
            throw new OneCSyncException("1C returned status " + response.statusCode());
        }

        return response.body();
    }

    private String buildBasicAuthHeader() {
        String credentials = oneCUsername + ":" + oneCPassword;
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
        );
    }
}