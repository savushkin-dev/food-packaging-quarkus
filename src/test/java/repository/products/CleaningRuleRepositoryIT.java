package repository.products;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.entity.products.PlrChange;
import org.acme.foodpackaging.record.CleaningRule;
import org.acme.foodpackaging.repository.products.CleaningRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CleaningRuleRepository.
 * Tests actual database loading using H2 in-memory database.
 */
@QuarkusTest
@Tag("database")
class CleaningRuleRepositoryIT {

    @Inject
    CleaningRuleRepository cleaningRuleRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        PlrChange.deleteAll();
    }

    @Test
    @Transactional
    void loadRules() {
        // Set up test data
        PlrChange rule1 = new PlrChange();
        rule1.id = UUID.randomUUID();
        rule1.parameter = "1";
        rule1.from = "Type1";
        rule1.to = "Type2";
        rule1.duration = 10;
        rule1.deletedFlag = 0;
        rule1.lineId = "test";
        rule1.persist();

        PlrChange rule2 = new PlrChange();
        rule2.id = UUID.randomUUID();
        rule2.parameter = "2";
        rule2.from = "Glaze1";
        rule2.to = "Glaze2";
        rule2.duration = 5;
        rule2.deletedFlag = 0;
        rule2.lineId = "test";
        rule2.persist();

        PlrChange deletedRule = new PlrChange();
        deletedRule.id = UUID.randomUUID();
        deletedRule.parameter = "3";
        deletedRule.from = "Type2";
        deletedRule.to = "Type3";
        deletedRule.duration = 15;
        deletedRule.deletedFlag = 1; // Deleted, should be filtered
        deletedRule.lineId = "test";
        deletedRule.persist();

        PlrChange differentLineRule = new PlrChange();
        differentLineRule.id = UUID.randomUUID();
        differentLineRule.parameter = "4";
        differentLineRule.from = "Type1";
        differentLineRule.to = "Type3";
        differentLineRule.duration = 20;
        differentLineRule.deletedFlag = 0;
        differentLineRule.lineId = "other"; // Different lineId, should be filtered
        differentLineRule.persist();

        PlrChange nullDurationRule = new PlrChange();
        nullDurationRule.id = UUID.randomUUID();
        nullDurationRule.parameter = "5";
        nullDurationRule.from = "Type1";
        nullDurationRule.to = "Type4";
        nullDurationRule.duration = null; // Null duration, should be filtered
        nullDurationRule.deletedFlag = 0;
        nullDurationRule.lineId = "test";
        nullDurationRule.persist();

        List<CleaningRule> rules = cleaningRuleRepository.loadRules();

        assertNotNull(rules);
        assertEquals(2, rules.size(), "Should only load rules with deletedFlag=0, matching lineId, and non-null duration");

        CleaningRule loadedRule1 = rules.getFirst();
        assertNotNull(loadedRule1);
        assertEquals("1", loadedRule1.parameter());
        assertEquals("Type1", loadedRule1.from());
        assertEquals("Type2", loadedRule1.to());
        assertEquals(10, loadedRule1.duration());

        CleaningRule loadedRule2 = rules.get(1);
        assertNotNull(loadedRule2);
        assertEquals("2", loadedRule2.parameter());
        assertEquals("Glaze1", loadedRule2.from());
        assertEquals("Glaze2", loadedRule2.to());
        assertEquals(5, loadedRule2.duration());
    }

    @Test
    @Transactional
    void loadRulesOrderedByParameter() {
        PlrChange rule3 = new PlrChange();
        rule3.id = UUID.randomUUID();
        rule3.parameter = "3";
        rule3.from = "Type1";
        rule3.to = "Type2";
        rule3.duration = 10;
        rule3.deletedFlag = 0;
        rule3.lineId = "test";
        rule3.persist();

        PlrChange rule1 = new PlrChange();
        rule1.id = UUID.randomUUID();
        rule1.parameter = "1";
        rule1.from = "Type2";
        rule1.to = "Type3";
        rule1.duration = 5;
        rule1.deletedFlag = 0;
        rule1.lineId = "test";
        rule1.persist();

        PlrChange rule2 = new PlrChange();
        rule2.id = UUID.randomUUID();
        rule2.parameter = "2";
        rule2.from = "Type3";
        rule2.to = "Type4";
        rule2.duration = 15;
        rule2.deletedFlag = 0;
        rule2.lineId = "test";
        rule2.persist();

        List<CleaningRule> rules = cleaningRuleRepository.loadRules();

        assertEquals(3, rules.size());
        assertEquals("1", rules.get(0).parameter(), "Rules should be ordered by parameter");
        assertEquals("2", rules.get(1).parameter());
        assertEquals("3", rules.get(2).parameter());
    }

    @Test
    @Transactional
    void loadRulesEmptyResult() {
        List<CleaningRule> rules = cleaningRuleRepository.loadRules();

        assertNotNull(rules);
        assertTrue(rules.isEmpty());
    }
}
