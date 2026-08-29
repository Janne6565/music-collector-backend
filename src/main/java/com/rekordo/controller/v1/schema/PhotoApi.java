package com.rekordo.controller.v1.schema;

import com.rekordo.model.core.PhotoUploadDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Sleeve photos — the pictures you take of your own copy, as opposed to the catalogue
 * artwork the metadata proxy serves.
 *
 * Requires an account, unlike the rest of the API. A photo taken without one stays on the
 * device, which is the same bargain as the collection itself: local always works, an
 * account adds sync.
 */
@RequestMapping("/api/v1/photos")
@Tag(name = "Photos")
public interface PhotoApi {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload the bytes for a photo the client has already created",
            description = "A photo pictures either a copy or a wishlist entry, so exactly one of "
                    + "`copyId` and `wishId` is given. Naming both, or neither, is a 400.")
    @ApiResponse(responseCode = "200", description = "Stored")
    @ApiResponse(responseCode = "400", description = "No owner, or two owners")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "413", description = "Too large")
    @ApiResponse(responseCode = "507", description = "The account is out of picture storage")
    @ApiResponse(responseCode = "415", description = "Not an image this app stores")
    ResponseEntity<PhotoUploadDto> upload(
            @RequestParam("photoId") UUID photoId,
            @RequestParam(value = "copyId", required = false) UUID copyId,
            @RequestParam(value = "wishId", required = false) UUID wishId,
            @RequestParam("file") MultipartFile file);

    @GetMapping("/{id}/content")
    @Operation(
            summary = "The photo's bytes",
            description = "Clients download once and keep a local copy, so photos work offline.")
    @ApiResponse(responseCode = "200", description = "The image")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "404", description = "No such photo")
    ResponseEntity<Resource> content(@PathVariable UUID id);
}
