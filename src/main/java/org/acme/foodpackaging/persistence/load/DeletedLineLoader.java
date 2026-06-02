package org.acme.foodpackaging.persistence.load;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.repository.lines.LineRepository;

@ApplicationScoped
@RequiredArgsConstructor
public class DeletedLineLoader {

    private final LineRepository lineRepository;

    public Line loadDeletedLine(String lineId) {

        return lineRepository.findLineInfo(lineId)
                .map(entity -> {
                    Line line = new Line();
                    line.setId(entity.getLineId().trim());
                    line.setName(entity.getSnm());
                    line.setDeletedLine(true);
                    return line;
                })
                .orElse(null);
    }
}
