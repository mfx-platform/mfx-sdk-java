package com.mfxhub.sdk;

import java.net.http.HttpHeaders;

/** What one attempt got back. */
final class MfxAnswer {
    private final int status;
    private final HttpHeaders headers;
    private final byte[] body;

    MfxAnswer(int status, HttpHeaders headers, byte[] body) {
        this.status = status;
        this.headers = headers;
        this.body = body.clone();
    }

    int status() {
        return status;
    }

    HttpHeaders headers() {
        return headers;
    }

    byte[] body() {
        return body.clone();
    }
}
