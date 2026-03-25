package org.acme.foodpackaging.persistence.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.exception.service.InvalidSolutionException;
import org.acme.foodpackaging.exception.service.SolutionNotFoundException;
import org.acme.foodpackaging.exception.service.SolutionParsingException;
import org.acme.foodpackaging.record.SolutionByVersion;
import org.acme.foodpackaging.service.products.ProductService;

@ApplicationScoped
public class SolutionImporter {

    private final ObjectMapper objectMapper;
    private final ProductService productService;

    @Inject
    public SolutionImporter(ObjectMapper objectMapper,
                            ProductService productService) {
        this.objectMapper = objectMapper;
        this.productService = productService;
    }

    public PackagingSchedule importFromJson(SolutionByVersion solutionWrapper) {

        if (solutionWrapper == null) {
            throw new SolutionNotFoundException("Solution wrapper is null");
        }

        if (solutionWrapper.solution() == null) {
            throw new InvalidSolutionException(
                    "JSON is null for version: " + solutionWrapper.version()
            );
        }

        try {
            PackagingSchedule solution =
                    objectMapper.readValue(
                            solutionWrapper.solution(),
                            PackagingSchedule.class
                    );

            solution.setProducts(productService.getProductList(solution));

            return solution;

        } catch (JsonProcessingException e) {
            throw new SolutionParsingException(
                    "Failed to parse JSON for version: " + solutionWrapper.version(),
                    e
            );
        }
    }
}
