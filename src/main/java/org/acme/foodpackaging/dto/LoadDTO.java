package org.acme.foodpackaging.dto;

public class LoadDTO {

    private String date;

    public LoadDTO() {
        
    }

    public LoadDTO(String date) {
        this.date = date;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
