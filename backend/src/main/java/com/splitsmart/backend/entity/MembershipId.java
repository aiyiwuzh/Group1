
package com.splitsmart.backend.entity;

import java.io.Serializable;
import java.util.Objects;

public class MembershipId implements Serializable {

    private Long group;
    private Long user;

    public MembershipId() {
    }

    public MembershipId(Long group, Long user) {
        this.group = group;
        this.user = user;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MembershipId)) return false;

        MembershipId other = (MembershipId) obj;

        return Objects.equals(group, other.group)
                && Objects.equals(user, other.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, user);
    }
}
