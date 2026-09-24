package org.acme.foodpackaging.service.materials;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.acme.foodpackaging.dto.materials.OneCRequest;
import org.acme.foodpackaging.dto.materials.OneCResponse;
import org.acme.foodpackaging.dto.materials.ProductWithMaterialsDto;
import org.acme.foodpackaging.dto.materials.SinvDto;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class OneCSyncService {

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
            OneCRequest request = buildRequest(kpp, kppc, data);

            String responseBody = sendHttpRequest(request);

            OneCResponse oneCResponse = objectMapper.readValue(responseBody, OneCResponse.class);

            if (oneCResponse.getCode() == null || oneCResponse.getCode() != 0) {
                throw new RuntimeException("1C error: " + oneCResponse.getDescription());
            }

            return oneCResponse.getTasknumber();
            
        } catch (Exception e) {
            log.error("Failed to send order to 1C", e);
            throw new RuntimeException("Failed to send order to 1C: " + e.getMessage(), e);
        }
    }

    /**
     * Формирует запрос для 1С
     */
    private OneCRequest buildRequest(String kpp, String kppc, List<ProductWithMaterialsDto> data) {
        Map<String, Double> materialsMap = data.stream()
                .flatMap(p -> p.getMaterials().stream())
                .filter(m -> m.getOrderFinal() != null && m.getOrderFinal() > 0)
                .collect(Collectors.toMap(
                        SinvDto::getKmt,
                        SinvDto::getOrderFinal,
                        Double::sum
                ));

        List<OneCRequest.OneCMaterial> materials = materialsMap.entrySet().stream()
                .map(e -> OneCRequest.OneCMaterial.builder()
                        .KMT(e.getKey())
                        .KOLE(e.getValue())
                        .build())
                .collect(Collectors.toList());

        return OneCRequest.builder()
                .KPP1(kppc)
                .KPP2(kpp)
                .MATERIALS(materials)
                .build();
    }

    /**
     * Отправляет HTTP-запрос в 1С
     */
    private String sendHttpRequest(OneCRequest request) throws Exception {
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

        if (response.statusCode() == 401) {
            throw new RuntimeException("1C authorization failed (401)");
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("1C returned status " + response.statusCode());
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