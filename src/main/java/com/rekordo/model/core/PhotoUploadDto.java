package com.rekordo.model.core;

/** What the client needs to record locally once an upload lands. */
public record PhotoUploadDto(String id, String storageKey, String contentType, long byteSize) {}
