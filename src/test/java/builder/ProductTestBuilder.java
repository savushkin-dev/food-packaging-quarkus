package builder;

import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.CleaningResult;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ProductTestBuilder {
    private final Product product;

    private ProductTestBuilder(String id) {
        this.product = new Product(id, "Product " + id);
    }

    public static ProductTestBuilder aProduct(String id) {
        return new ProductTestBuilder(id);
    }

    public ProductTestBuilder withCleaningResult(Product previous, CleaningResult result) {
        Map<Product, CleaningResult> results = product.getCleaningResults();
        Map<Product, Duration> cleaningDurations = product.getCleaningDurations();
        if(previous == null) return this;
        if (results == null) {
            results = new HashMap<>();
            cleaningDurations = new HashMap<>();
        }
        results.put(previous, result);
        cleaningDurations.put(previous, Duration.ofMinutes(result.minutes()));
        product.setCleaningDurations(cleaningDurations);
        product.setCleaningResults(results);
        return this;
    }

    public ProductTestBuilder withPLRLC(Product previous) {
        Map<Product, Duration> durations = new HashMap<>();
        durations.put(previous, Duration.ofMinutes(10));
        product.setCleaningDurations(durations);

        Map<Product, CleaningResult> results = new HashMap<>();
        results.put(previous, new CleaningResult(0, true));
        product.setCleaningResults(results);

        return this;
    }

    public ProductTestBuilder withoutCleaning() {
        product.setCleaningDurations(null);
        product.setCleaningResults(null);
        return this;
    }

    public ProductTestBuilder withType(String type) {
        product.setType(type);
        return this;
    }

    public Product build() {
        return product;
    }
}
