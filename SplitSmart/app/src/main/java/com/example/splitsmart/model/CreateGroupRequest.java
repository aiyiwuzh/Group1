package com.example.splitsmart.model;

public class CreateGroupRequest {
    private final String name;
    private final String description;

    public CreateGroupRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
