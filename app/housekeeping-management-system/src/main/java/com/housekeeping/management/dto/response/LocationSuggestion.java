package com.housekeeping.management.dto.response;

/** Provider-neutral location autocomplete result exposed by service contracts. */
public record LocationSuggestion(String refId, String display) { }
