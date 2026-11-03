
package com.example.splitsmart.model;

import java.util.List;

public class Group {

    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private List<Member> members;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public List<Member> getMembers() {
        return members;
    }
}
