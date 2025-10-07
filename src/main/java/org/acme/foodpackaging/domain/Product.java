package org.acme.foodpackaging.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;
import java.util.Map;

public class Product {

    @PlanningId
    private String id;
    private String name;
    private String kmc;
    private String krKmc;
    private String ean13;
    private String type;
    private String glaze;
    private String filling;
    private String curdMass;
    /** The map key is previous product on assembly line. */
    @JsonIgnore
    private Map<Product, Duration> cleaningDurations;

    public Product() {
    }

    public Product(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Product(String name, String id, String krKmc, String ean13,  String type, String glaze, String curdMass, String filling ) {
        this.name = name;
        this.id = id;
        this.krKmc = krKmc;
        this.ean13 = ean13;
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

    public String getId() {
        return id;
    }

    public String getEan13() {
        return ean13;
    }

    public String getKrKmc() {
        return krKmc;
    }

    public String getName() {
        return name;
    }

    public String getKmc(){ return kmc;}

    public void setKmc(String kmc){ this.kmc = kmc; }

    public void setKrKmc(String KrKmc){ this.krKmc = krKmc; }

    public void setName(String name) {
        this.name = name;
    }
    @JsonIgnore
    public Map<Product, Duration> getCleaningDurations() {
        return cleaningDurations;
    }

    public void setCleaningDurations(Map<Product, Duration> cleaningDurations) {
        this.cleaningDurations = cleaningDurations;
    }

    public void setType(String type) { this.type = type; }
}
