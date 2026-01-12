package repository.products;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.entity.products.NsMc;
import org.acme.foodpackaging.entity.products.PlrMc;
import org.acme.foodpackaging.repository.products.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ProductRepository.
 * Tests actual database loading using H2 in-memory database.
 */
@QuarkusTest
@Tag("database")
class ProductRepositoryIT {

    @Inject
    ProductRepository productRepository;
    @Inject
    EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clear existing data
        entityManager.createQuery("DELETE FROM PlrMc").executeUpdate();
        NsMc.deleteAll();
    }

    @Test
    @Transactional
    void loadProducts() {
        // Set up test data
        NsMc ns1 = new NsMc();
        ns1.id = UUID.randomUUID();
        ns1.kmc = "KMC1";
        ns1.shortName = "Product 1";
        ns1.krkmc = "KRKMC1";
        ns1.ean13 = "EAN001";
        ns1.persist();

        NsMc ns2 = new NsMc();
        ns2.id = UUID.randomUUID();
        ns2.kmc = "KMC2";
        ns2.shortName = "Product 2";
        ns2.krkmc = "KRKMC2";
        ns2.ean13 = "EAN002";
        ns2.persist();

        PlrMc product1 = new PlrMc();
        product1.id = UUID.randomUUID();
        product1.kmc = "KMC1";
        product1.ean13 = "EAN001";
        product1.type = "Type1";
        product1.glaze = "Glaze1";
        product1.mass = "100";
        product1.filling = "Filling1";
        product1.deletedFlag = 0;
        product1.ns = ns1;
        product1.persist();

        PlrMc product2 = new PlrMc();
        product2.id = UUID.randomUUID();
        product2.kmc = "KMC2";
        product2.ean13 = "EAN002";
        product2.type = "Type2";
        product2.glaze = "Glaze2";
        product2.mass = "200";
        product2.filling = "Filling2";
        product2.deletedFlag = 0;
        product2.ns = ns2;
        product2.persist();

        PlrMc deletedProduct = new PlrMc();
        deletedProduct.id = UUID.randomUUID();
        deletedProduct.kmc = "KMC3";
        deletedProduct.ean13 = "EAN003";
        deletedProduct.type = "Type3";
        deletedProduct.glaze = "Glaze3";
        deletedProduct.mass = "300";
        deletedProduct.filling = "Filling3";
        deletedProduct.deletedFlag = 1; // Deleted, should be filtered
        deletedProduct.ns = ns2;
        deletedProduct.persist();

        Map<String, Product> products = productRepository.loadProducts();

        assertNotNull(products);
        assertEquals(2, products.size(), "Should only load products with deletedFlag=0");

        Product loadedProduct1 = products.get("KMC1");
        assertNotNull(loadedProduct1);
        assertEquals("Product 1", loadedProduct1.getName());
        assertEquals("KMC1", loadedProduct1.getId());
        assertEquals("KRKMC1", loadedProduct1.getKrKmc());
        assertEquals("Type1", loadedProduct1.getType());
        assertEquals("Glaze1", loadedProduct1.getGlaze());
        assertEquals("Filling1", loadedProduct1.getFilling());

        Product loadedProduct2 = products.get("KMC2");
        assertNotNull(loadedProduct2);
        assertEquals("Product 2", loadedProduct2.getName());
        assertEquals("KMC2", loadedProduct2.getId());
        assertEquals("Type2", loadedProduct2.getType());

        assertFalse(products.containsKey("KMC3"), "KMC3 should be excluded because deletedFlag=1");
    }

    @Test
    @Transactional
    void loadProductsWithJoin() {
        // Test that the JPQL query with join works correctly
        NsMc ns1 = new NsMc();
        ns1.id = UUID.randomUUID();
        ns1.kmc = "KMC1";
        ns1.shortName = "Product Name From NS";
        ns1.krkmc = "KRKMC1";
        ns1.ean13 = "EAN001";
        ns1.persist();

        PlrMc product1 = new PlrMc();
        product1.id = UUID.randomUUID();
        product1.kmc = "KMC1";
        product1.ean13 = "EAN001";
        product1.type = "Type1";
        product1.glaze = "Glaze1";
        product1.mass = "100";
        product1.filling = "Filling1";
        product1.deletedFlag = 0;
        product1.ns = ns1;
        product1.persist();

        Map<String, Product> products = productRepository.loadProducts();

        assertNotNull(products);
        Product product = products.get("KMC1");
        assertNotNull(product);
        assertEquals("Product Name From NS", product.getName(), "Short name should come from NsMc table via JOIN");
        assertEquals("KRKMC1", product.getKrKmc(), "KRKMC should come from NsMc table via JOIN");
    }

    @Test
    @Transactional
    void loadProductsEmptyResult() {
        Map<String, Product> products = productRepository.loadProducts();

        assertNotNull(products);
        assertTrue(products.isEmpty());
    }
}
