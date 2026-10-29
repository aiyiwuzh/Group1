
package com.splitsmart.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "memberships")
@IdClass(MembershipId.class)
public class Membership {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private Group group;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    protected Membership() {
    }

    public Membership(Group group, User user) {
        this.group = group;
        this.user = user;
    }

    public Group getGroup() { return group; }
    public User getUser() { return user; }
}
