package com.rekordo.controller.v1.schema;

import com.rekordo.model.core.AvatarDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * The profile picture — turn 27.
 *
 * <p>Setting one needs an account, because it is account data; reading one needs nothing at
 * all, because it is public wherever the handle resolves. That asymmetry is the whole
 * shape of this file, and it is deliberate rather than convenient: 27f draws the picture
 * on a profile whose shelf is locked, and 27b tells the person that before they commit.
 */
@RequestMapping("/api/v1/avatar")
@Tag(name = "Avatar")
public interface AvatarApi {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Set the picture on this account",
            description = "The original picture plus the square the user framed, in the picture's own "
                    + "pixels. The server renders it, so the circle is identical on every device. "
                    + "Replaces whatever was there; there is no gallery and no history.")
    @ApiResponse(responseCode = "200", description = "Rendered and stored")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    @ApiResponse(responseCode = "413", description = "Too large, or too many pixels to decode")
    @ApiResponse(responseCode = "507", description = "The account is out of picture storage")
    @ApiResponse(responseCode = "415", description = "Not a picture this app can render")
    @ApiResponse(responseCode = "502", description = "Object storage did not answer; the old picture is unchanged")
    ResponseEntity<AvatarDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("x") int x,
            @RequestParam("y") int y,
            @RequestParam("size") int size);

    @DeleteMapping
    @Operation(
            summary = "Go back to the initials circle",
            description = "Idempotent: an account that has no picture answers 204 as well, since "
                    + "there is nothing to report and nothing went wrong.")
    @ApiResponse(responseCode = "204", description = "Removed")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<Void> removePicture();

    @GetMapping("/{userId}")
    @Operation(
            summary = "One collector's picture",
            description = "Open, and answered the same for everybody. Cached hard: the URL carries "
                    + "the moment the picture landed, so replacing one produces a different URL.")
    @ApiResponse(responseCode = "200", description = "The picture, as a JPEG")
    @ApiResponse(responseCode = "404", description = "No such account, or no picture on it")
    ResponseEntity<Resource> content(@PathVariable UUID userId);
}
