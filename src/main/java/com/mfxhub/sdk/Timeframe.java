package com.mfxhub.sdk;

/** A bar's length, in the contract's words: the candles a subscription builds and the history reads take. */
public enum Timeframe {
    /** One minute. */
    M1,
    /** Five minutes. */
    M5,
    /** Fifteen minutes. */
    M15,
    /** Thirty minutes. */
    M30,
    /** One hour. */
    H1,
    /** Four hours. */
    H4,
    /** One day, opening at the broker's midnight. */
    D1,
    /** One week. */
    W1,
    /** One month. */
    MN1
}
