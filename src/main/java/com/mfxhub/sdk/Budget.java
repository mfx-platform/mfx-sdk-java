package com.mfxhub.sdk;

/**
 * What the key has left, as of the platform's last answer ({@code X-MFX-Budget-*}).
 *
 * @param remaining what is left
 * @param source {@code counted} when it is a measurement, {@code configured} when it is the key's
 *     allowance, {@code unconfigured} when the key has none. A number whose source is not
 *     {@code counted} is a ceiling, not a reading.
 */
public record Budget(long remaining, String source) {
}
