package com.canton.platform.exception;

import org.springframework.http.HttpStatus;

/** Mirrors a DAML "controller/signatory" authorization failure. */
public class UnauthorizedActionException extends PlatformException {
    public UnauthorizedActionException(String party, String action) {
        super(HttpStatus.FORBIDDEN, "NOT_AUTHORIZED",
                "Party '" + party + "' is not authorized to perform: " + action);
    }
}
