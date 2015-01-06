package com.boundary.metrics.ipmi.client.meter.manager;

public class MeterNameConflictException extends RuntimeException {

    private final String name;

    public MeterNameConflictException(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
