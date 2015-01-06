package com.boundary.metrics.ipmi.client.meter.manager;

public class MeterNameConflictException extends RuntimeException {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private final String name;

    public MeterNameConflictException(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
