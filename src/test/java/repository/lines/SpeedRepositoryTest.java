package repository.lines;

import org.acme.foodpackaging.repository.lines.SpeedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SpeedRepositoryTest {

    @InjectMocks
    SpeedRepository speedRepository;

    @Test
    void RawSpeedsToNestedMap() {
    
        Map<SpeedRepository.LineTypeKey, Integer> rawSpeeds = Map.of(
                new SpeedRepository.LineTypeKey("L1", "TypeA"), 100,
                new SpeedRepository.LineTypeKey("L1", "TypeB"), 200,
                new SpeedRepository.LineTypeKey("L2", "TypeA"), 150
        );

        Map<String, Map<String, Integer>> result = SpeedRepository.createSpeedMap(rawSpeeds);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("L1"));
        assertTrue(result.containsKey("L2"));
        
        Map<String, Integer> l1Speeds = result.get("L1");
        assertEquals(100, l1Speeds.get("TypeA"));
        assertEquals(200, l1Speeds.get("TypeB"));
        
        Map<String, Integer> l2Speeds = result.get("L2");
        assertEquals(150, l2Speeds.get("TypeA"));
    }

    @Test
    void FillMissingTypesWithZero() {
        Map<SpeedRepository.LineTypeKey, Integer> rawSpeeds = Map.of(
                new SpeedRepository.LineTypeKey("L1", "TypeA"), 100,
                new SpeedRepository.LineTypeKey("L1", "TypeB"), 200,
                new SpeedRepository.LineTypeKey("L2", "TypeA"), 150
        );

        Map<String, Map<String, Integer>> result = SpeedRepository.createSpeedMap(rawSpeeds);

        Map<String, Integer> l1Speeds = result.get("L1");
        assertEquals(100, l1Speeds.get("TypeA"));
        assertEquals(200, l1Speeds.get("TypeB"));
        
        Map<String, Integer> l2Speeds = result.get("L2");
        assertEquals(150, l2Speeds.get("TypeA"));
        assertEquals(0, l2Speeds.get("TypeB"));
    }

    @Test
    void HandleEmptyInput() {

        Map<SpeedRepository.LineTypeKey, Integer> rawSpeeds = new HashMap<>();
        Map<String, Map<String, Integer>> result = SpeedRepository.createSpeedMap(rawSpeeds);

        assertTrue(result.isEmpty());
    }

    @Test
    void MultipleLinesWithAllTypes() {
        Map<SpeedRepository.LineTypeKey, Integer> rawSpeeds = Map.of(
                new SpeedRepository.LineTypeKey("L1", "TypeA"), 100,
                new SpeedRepository.LineTypeKey("L1", "TypeB"), 200,
                new SpeedRepository.LineTypeKey("L1", "TypeC"), 300,
                new SpeedRepository.LineTypeKey("L2", "TypeA"), 150,
                new SpeedRepository.LineTypeKey("L2", "TypeB"), 250,
                new SpeedRepository.LineTypeKey("L3", "TypeA"), 120
        );

        Map<String, Map<String, Integer>> result = SpeedRepository.createSpeedMap(rawSpeeds);

        assertEquals(3, result.size());
        
        Map<String, Integer> l1Speeds = result.get("L1");
        assertEquals(100, l1Speeds.get("TypeA"));
        assertEquals(200, l1Speeds.get("TypeB"));
        assertEquals(300, l1Speeds.get("TypeC"));
        
        Map<String, Integer> l2Speeds = result.get("L2");
        assertEquals(150, l2Speeds.get("TypeA"));
        assertEquals(250, l2Speeds.get("TypeB"));
        assertEquals(0, l2Speeds.get("TypeC"));
        
        Map<String, Integer> l3Speeds = result.get("L3");
        assertEquals(120, l3Speeds.get("TypeA"));
        assertEquals(0, l3Speeds.get("TypeB")); // Missing type filled with 0
        assertEquals(0, l3Speeds.get("TypeC")); // Missing type filled with 0
    }

    @Test
    void HandleSingleLineSingleType() {
        Map<SpeedRepository.LineTypeKey, Integer> rawSpeeds = Map.of(
                new SpeedRepository.LineTypeKey("L1", "TypeA"), 100
        );

        Map<String, Map<String, Integer>> result = SpeedRepository.createSpeedMap(rawSpeeds);

        assertEquals(1, result.size());
        Map<String, Integer> l1Speeds = result.get("L1");
        assertEquals(1, l1Speeds.size());
        assertEquals(100, l1Speeds.get("TypeA"));
    }

    @Test
    void lineTypeKeyWorkCorrectly() {
    
        SpeedRepository.LineTypeKey key1 = new SpeedRepository.LineTypeKey("L1", "TypeA");
        SpeedRepository.LineTypeKey key2 = new SpeedRepository.LineTypeKey("L1", "TypeA");
        SpeedRepository.LineTypeKey key3 = new SpeedRepository.LineTypeKey("L2", "TypeA");

        assertEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertEquals("L1", key1.line());
        assertEquals("TypeA", key1.type());
    }
}

