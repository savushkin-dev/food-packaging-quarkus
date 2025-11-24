package org.acme.foodpackaging.dto;

import java.time.LocalDate;
import java.util.Map;

public class UpdatePDayDTO {

    private LoadDTO loadDTO;
    private Map<String, LocalDate> mapsnpz;

    public UpdatePDayDTO() {

    }

    public UpdatePDayDTO(LoadDTO loadDTO, Map<String, LocalDate> mapsnpz) {
        this.loadDTO = loadDTO;
        this.mapsnpz = mapsnpz;
    }

    public LoadDTO getLoadDTO() {
        return loadDTO;
    }

    public void setLoadDTO(LoadDTO loadDTO) {
        this.loadDTO = loadDTO;
    }

    public Map<String, LocalDate> getMapsnpz() {
        return mapsnpz;
    }

    public void setMapsnpz(Map<String, LocalDate> mapsnpz) {
        this.mapsnpz = mapsnpz;
    }
}
