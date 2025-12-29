package service;

import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.factory.LineFactory;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LineServiceTest {

    @InjectMocks
    LineService lineService;

    @Mock
    LoadDataService loadDataService;
    @Mock
    LineFactory lineFactory;

    @Test
    void getLines() {
        when(loadDataService.getLines())
                .thenReturn(new ConcurrentHashMap<>(Map.of("L1", "Line 1")));

        Line line = new Line("L1", "Line 1");
        when(lineFactory.createLine("L1", "Line 1"))
                .thenReturn(line);

        List<Line> result = lineService.getLines();

        assertEquals(1, result.size());
        assertSame(line, result.getFirst());

        verify(lineFactory).createLine("L1", "Line 1");
    }
}

