package com.rekordo.controller.v1.implementation;

import com.rekordo.controller.v1.schema.AvatarApi;
import com.rekordo.model.core.AvatarCropDto;
import com.rekordo.model.core.AvatarDto;
import com.rekordo.security.CurrentUser;
import com.rekordo.services.storage.AvatarService;
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
public class AvatarController implements AvatarApi {

    private final AvatarService avatarService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<AvatarDto> upload(MultipartFile file, int x, int y, int size) {
        return ResponseEntity.ok(
                avatarService.upload(currentUser.require().getId(), file, new AvatarCropDto(x, y, size)));
    }

    @Override
    public ResponseEntity<Void> remove() {
        avatarService.remove(currentUser.require().getId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Resource> content(UUID userId) {
        AvatarService.Download download = avatarService.download(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                // The `v` in the URL is the moment these bytes landed, so this URL's answer
                // never changes: replacing a picture asks for a different one. Public
                // rather than private, unlike a sleeve photo — this is the one image in the
                // app that is the same for every viewer, so a shared cache may hold it.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new InputStreamResource(download.stream()));
    }
}
