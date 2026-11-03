
package com.splitsmart.backend.entity;

// How an expense is divided between its participants.
public enum SplitMethod {

    // Divided as evenly as possible; leftover cents go to some participants.
    EQUAL,

    // The creator enters a dollar amount for every participant.
    EXACT,

    // The creator enters a percentage for every participant.
    PERCENTAGE
}
