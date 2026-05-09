package domain;

import org.acme.foodpackaging.domain.Product;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.wildfly.common.Assert.assertTrue;

public class ProductTest {

    @Test
    void getCleanupDuration_whenDurationExists_returnsDuration() {
        Product previous = new Product();
        Product current = new Product();
        current.setCleaningDurations(new HashMap<>());

        Duration expected = Duration.ofMinutes(15);
        current.getCleaningDurations().put(previous, expected);
        Duration result = current.getCleanupDuration(previous);

        assertEquals(expected, result);
    }

    @Test
    void getCleanupDuration_whenDurationMissing_throwsException() {
        Product previous = new Product();
        Product current = new Product();
        current.setCleaningDurations(new HashMap<>());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> current.getCleanupDuration(previous)
        );

        assertTrue(exception.getMessage().contains("Cleanup duration"));
    }

    @Test
    void getType_whenTypeIsNull_returnsEmptyString() {
        Product product = new Product();

        assertEquals("", product.getType());
    }

    @Test
    void getType_whenTypeExists_returnsType() {
        Product product = new Product();
        product.setType("10003");

        assertEquals("10003", product.getType());
    }

    @Test
    void getGlaze_whenGlazeIsNull_returnsEmptyString() {
        Product product = new Product();

        assertEquals("", product.getGlaze());
    }

    @Test
    void getGlaze_whenGlazeExists_returnsGlaze() {
        Product product = new Product();
        product.setGlaze("Chocolate");

        assertEquals("Chocolate", product.getGlaze());
    }

    @Test
    void getFilling_whenFillingIsNull_returnsEmptyString() {
        Product product = new Product();

        assertEquals("", product.getFilling());
    }

    @Test
    void getFilling_whenFillingExists_returnsFilling() {
        Product product = new Product();
        product.setFilling("Vanilla");

        assertEquals("Vanilla", product.getFilling());
    }

    @Test
    void getCurdMass_whenCurdMassIsNull_returnsEmptyString() {
        Product product = new Product();

        assertEquals("", product.getCurdMass());
    }

    @Test
    void getCurdMass_whenCurdMassExists_returnsCurdMass() {
        Product product = new Product();
        product.setCurdMass("Mass 5%");

        assertEquals("Mass 5%", product.getCurdMass());
    }

    @Test
    void toString_returnsName() {
        Product product = new Product();
        product.setName("Milk");

        assertEquals("Milk", product.toString());
    }

    @Test
    void equals_whenSameReference_returnsTrue() {
        Product product = new Product();

        assertEquals(product, product);
    }

    @Test
    void equals_whenObjectIsNotProduct_returnsFalse() {
        Product product = new Product();

        assertNotEquals("string", product);
    }

    @Test
    void equals_whenIdsAreEqual_returnsTrue() {
        Product first = new Product("P1", "product");
        Product second = new Product();
        second.setId("P1");

        assertEquals(first, second);
    }

    @Test
    void equals_whenIdsAreDifferent_returnsFalse() {
        Product first = new Product("P1", "product");
        Product second = new Product("P2", "product");

        assertNotEquals(first, second);
    }

    @Test
    void equals_whenIdIsNull_returnsFalse() {
        Product first = new Product();
        Product second = new Product("P1", "product");

        assertNotEquals(first, second);
    }

    @Test
    void hashCode_whenIdExists_returnsIdHashCode() {
        Product product = new Product("P1", "product");
        assertEquals("P1".hashCode(), product.hashCode());
    }

    @Test
    void hashCode_whenIdIsNull_returnsIdentityHashCode() {
        Product product = new Product();
        assertEquals(System.identityHashCode(product), product.hashCode());
    }
}
