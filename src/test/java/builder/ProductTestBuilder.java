package builder;

import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.CleaningResult;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ProductTestBuilder {
    private final Product product;

    private ProductTestBuilder(String id, String type) {
        this.product = new Product(id, "Product " + id);
        this.product.setType(type);
    }

    public static ProductTestBuilder aProduct(String id, String type) {
        return new ProductTestBuilder(id, type);
    }

    public ProductTestBuilder withCleaningResult(Product previous, CleaningResult result) {
        Map<Product, CleaningResult> results = product.getCleaningResults();
        if (results == null) {
            results = new HashMap<>();
        }
        results.put(previous, result);
        product.setCleaningResults(results);
        return this;
    }

    public ProductTestBuilder withPLRLC(Product previous) {
        Map<Product, Duration> durations = new HashMap<>();
        durations.put(previous, Duration.ofMinutes(10)); // ignored
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
