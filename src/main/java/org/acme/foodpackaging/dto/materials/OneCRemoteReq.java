package org.acme.foodpackaging.dto.materials;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCRemoteReq {
    @JsonProperty("KPP1")
    private String kpp1;

    @JsonProperty("KPP2")
    private String kpp2;

    @JsonProperty("MATERIALS")
    private List<OneCMaterial> materials;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OneCMaterial {
        @JsonProperty("KMT")
        private String kmt;

        @JsonProperty("KOLE")
        private Double kole;
    }
}