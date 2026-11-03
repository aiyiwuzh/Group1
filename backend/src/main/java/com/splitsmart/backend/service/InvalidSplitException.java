
package com.splitsmart.backend.service;

// A split request that cannot be used. The message is safe to show to the user.
public class InvalidSplitException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidSplitException(String message) {
        super(message);
    }
}
