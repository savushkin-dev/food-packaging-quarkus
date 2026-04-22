package org.acme.foodpackaging.exception.service;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String kmc) {
        super("Продукт с кодом "  + kmc +
                "отсутствует в справочнике Реализации:  «Документы» -- «Производство» -- «Планировщик линий» -- «Продукция для планировщика»");
    }
}

