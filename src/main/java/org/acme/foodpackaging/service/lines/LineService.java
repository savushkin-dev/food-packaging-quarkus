package org.acme.foodpackaging.service.lines;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.factory.LineFactory;
import org.acme.foodpackaging.repository.lines.LineRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class LineService {

    @Inject
    LineRepository lineRepository;
    @Inject
    LineFactory lineFactory;

    public List<Line> getLines() {
        return lineRepository.loadLines().entrySet().stream()
                .sorted(lineNameComparator())
                .map(e -> lineFactory.createLine(e.getKey(), e.getValue()))
                .toList();
    }

    private Comparator<Map.Entry<String, String>> lineNameComparator() {
        return Comparator
                .comparingInt((Map.Entry<String, String> e) -> extractLineNumber(e.getValue()))
                .thenComparing(Map.Entry::getValue);
    }

    private int extractLineNumber(String name) {
        Matcher matcher = Pattern.compile("№\\s*(\\d+)").matcher(name);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return Integer.MAX_VALUE; // names without a number go to the end
    }
}

