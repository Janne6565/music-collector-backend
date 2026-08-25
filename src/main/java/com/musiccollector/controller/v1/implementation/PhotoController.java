package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.PhotoApi;
import com.musiccollector.model.core.PhotoUploadDto;
import com.musiccollector.security.CurrentUser;
import com.musiccollector.services.storage.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PhotoController implements PhotoApi {

    private final PhotoService photoService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<PhotoUploadDto> upload(UUID photoId, UUID copyId, UUID wishId, MultipartFile file) {
        return ResponseEntity.ok(
                photoService.upload(currentUser.require().getId(), photoId, copyId, wishId, file));
    }

    @Override
    public ResponseEntity<Resource> content(UUID id) {
        // Optional, not required: this endpoint is open so that a public shelf renders for
        // somebody with no account. Who the viewer is decides what they get, not whether
        // they get in.
        PhotoService.Download download = photoService.download(currentUser.optionalId().orElse(null), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.byteSize())
                // The bytes never change: a photo id points at one immutable object, so a
                // client that has it can keep it. Private, because it is one user's picture.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .body(new InputStreamResource(download.stream()));
    }
}
