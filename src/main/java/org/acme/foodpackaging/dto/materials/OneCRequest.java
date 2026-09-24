package org.acme.foodpackaging.dto.materials;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCRequest {
    private String KPP1;
    private String KPP2;
    private List<OneCMaterial> MATERIALS;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OneCMaterial {
        private String KMT;
        private Double KOLE;
    }
}