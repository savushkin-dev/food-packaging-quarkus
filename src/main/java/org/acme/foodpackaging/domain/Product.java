package org.acme.foodpackaging.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.acme.foodpackaging.record.CleaningResult;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.Map;

@Setter
@Getter
public class Product {

    @PlanningId
    private String id;
    private String name;
    private String krKmc;
    private String type;
    private String glaze;
    private String filling;
    private String curdMass;
    private String ean13;
    private Double mass;
    /** The map key is previous product on assembly line. */
    @JsonIgnore
    private Map<Product, Duration> cleaningDurations;
    @JsonIgnore
    private Map<Product, CleaningResult> cleaningResults;

    public Product() {
    }

    public Product(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Product(String name, String id, String krKmc,  String type, String glaze, String curdMass, String filling) {
        this.name = name;
        this.id = id;
        this.krKmc = krKmc;
        this.type = type;
        this.glaze = glaze;
        this.curdMass = curdMass;
        this.filling = filling;
    }

    @Override
    public String toString() {
        return name;
    }

    public Duration getCleanupDuration(Product previousProduct) {
        Duration cleanupDuration = cleaningDurations.get(previousProduct);
        if (cleanupDuration == null) {
            throw new IllegalArgumentException("Cleanup duration previousProduct (" + previousProduct
                    + ") to toProduct (" + this + ") is missing.");
        }
        return cleanupDuration;
    }
    // ************************************************************************
    // Getters and setters
    // ************************************************************************
    public String getType() { return type == null ? "" : type; }

    public String getGlaze() { return glaze == null ? "" : glaze; }

    public String getFilling() { return filling == null ? "" : filling; }

    public String getCurdMass() { return curdMass == null ? "" : curdMass; }

    @JsonIgnore
    public Map<Product, Duration> getCleaningDurations() {
        return cleaningDurations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product product)) return false;
        return id != null && id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

}
