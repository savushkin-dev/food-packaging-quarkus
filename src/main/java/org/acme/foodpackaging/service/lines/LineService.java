package org.acme.foodpackaging.service.lines;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.factory.LineFactory;
import org.acme.foodpackaging.repository.lines.LineRepository;

import java.util.List;

@ApplicationScoped
public class LineService {

    @Inject
    LineRepository lineRepository;
    @Inject
    LineFactory lineFactory;

    public List<Line> getLines() {
        return lineRepository.loadLines().entrySet().stream()
                .map(e -> lineFactory.createLine(e.getKey(), e.getValue()))
                .toList();
    }
}

