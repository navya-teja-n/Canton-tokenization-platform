package com.canton.platform.exception;

import org.springframework.http.HttpStatus;

public class KycNotApprovedException extends PlatformException {
    public KycNotApprovedException(String party) {
        super(HttpStatus.PRECONDITION_FAILED, "KYC_NOT_APPROVED",
                "Party '" + party + "' does not have an approved KYC record");
    }
}
