package org.acme.foodpackaging.domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.time.Duration;
import java.util.Map;

public class Product {

    @PlanningId
    private String id;
    private String name;
    private ProductType type;
    private GlazeType glaze;
    private FillingType filling;
    private CurdMassType curdMass;
    private boolean allergen;
    private String type_s;
    private String glaze_s;
    private String filling_s;
    private String curdMass_s;
    /** The map key is previous product on assembly line. */
    private Map<Product, Duration> cleaningDurations;

    public Product() {
    }

    public Product(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Product(String id, String name, ProductType type, boolean allergen) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.filling = FillingType.fromProduct(id);
        this.curdMass = CurdMassType.fromProduct(id);
        this.glaze = GlazeType.fromProduct(id, type);
        this.allergen = allergen;
    }

    public Product(String id,  String ean13, String type, String glaze, String curdMass, String filling) {
        this.id = id;
        this.type_s = type;
        this.glaze_s = glaze;
        this.curdMass_s = curdMass;
        this.filling_s = filling;


    }


    public ProductType getType() { return type; }

    public GlazeType getGlaze(){ return glaze; }

    public boolean is_allergen() { return allergen; }

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

    public FillingType getFilling(){ return filling; }

    public CurdMassType getCurdMass() { return curdMass; }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Product, Duration> getCleaningDurations() {
        return cleaningDurations;
    }

    public void setCleaningDurations(Map<Product, Duration> cleaningDurations) {
        this.cleaningDurations = cleaningDurations;
    }

}
