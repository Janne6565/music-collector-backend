package com.musiccollector.model.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ReleaseNotFoundException extends BaseException {

    public ReleaseNotFoundException(UUID mbid) {
        super(HttpStatus.NOT_FOUND, "No release found for MusicBrainz id " + mbid);
    }
}
