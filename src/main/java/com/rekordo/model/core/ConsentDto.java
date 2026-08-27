package com.rekordo.model.core;

import java.time.Instant;

/** What the Legal & privacy screen prints under a document: "accepted 4 Mar 2026". */
public record ConsentDto(ConsentDocument document, String version, Instant acceptedAt) {}
