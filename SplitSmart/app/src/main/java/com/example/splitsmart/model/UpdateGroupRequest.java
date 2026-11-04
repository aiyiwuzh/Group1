package com.example.splitsmart.model;

public class UpdateGroupRequest {
    private final String name;
    private final String description;

    public UpdateGroupRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
