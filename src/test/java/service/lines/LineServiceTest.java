package service.lines;

import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.persistence.load.LoadDataService;
import org.acme.foodpackaging.service.lines.LineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineServiceTest {

    @InjectMocks
    LineService lineService;

    @Mock
    LoadDataService loadDataService;

    @Test
    void getLines() {
        when(loadDataService.getLines())
                .thenReturn(new ConcurrentHashMap<>(Map.of("L1", "Line 1")));

        List<Line> result = lineService.getLines();

        assertEquals(1, result.size());
        assertEquals("L1", result.getFirst().getId());
        assertEquals("Line 1", result.getFirst().getName());

        verify(loadDataService).getLines();
    }
}

