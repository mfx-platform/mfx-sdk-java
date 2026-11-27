package com.mfxhub.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * One record the expert wrote. {@code (segment, line)} is its place in the expert's files: a record seen
 * twice (delivery is at least once) has the same pair.
 *
 * @param segment its file
 * @param line its line in the file
 * @param record what the expert wrote, as it wrote it
 */
public record EaRecord(long segment, long line, Map<String, JsonNode> record) {
    /** The record, copied so it cannot be changed. */
    public EaRecord {
        record = Map.copyOf(record);
    }
}
