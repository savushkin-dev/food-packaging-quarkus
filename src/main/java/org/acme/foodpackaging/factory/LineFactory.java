package org.acme.foodpackaging.factory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDateTime;

@ApplicationScoped
public class LineFactory {

    @Inject
    LoadDataService loadDataService;

    public Line createLine(String lineId, String name) {
        return new Line(lineId, name);
    }
}
