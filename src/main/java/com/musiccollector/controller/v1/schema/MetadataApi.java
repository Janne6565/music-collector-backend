package com.musiccollector.controller.v1.schema;

import com.musiccollector.model.core.ReleaseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Release metadata from MusicBrainz, mirrored and cached locally.
 *
 * <p>Unauthenticated by design: the app is local-first and someone with no account must
 * still be able to search and scan. Abuse is bounded by a per-IP quota and the cache, not
 * by a login.
 */
@RequestMapping("/api/v1/metadata")
@Tag(name = "Metadata")
public interface MetadataApi {

    @GetMapping("/search")
    @Operation(summary = "Search releases by artist, title or catalog number",
            description = "One result per release and format, as the add flow lists them.")
    @ApiResponse(responseCode = "200", description = "Matching releases, possibly empty")
    @ApiResponse(responseCode = "429", description = "Per-IP rate limit exceeded")
    @ApiResponse(responseCode = "502", description = "MusicBrainz is unreachable")
    ResponseEntity<List<ReleaseDto>> search(
            @RequestParam("q") @NotBlank @Size(max = 200) String query,
            @RequestParam(value = "limit", defaultValue = "25") @Min(1) @Max(50) int limit);

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Look up releases by barcode",
            description = "Answered from the local mirror when the barcode has been seen before.")
    @ApiResponse(responseCode = "200", description = "Matching releases, possibly empty")
    @ApiResponse(responseCode = "429", description = "Per-IP rate limit exceeded")
    ResponseEntity<List<ReleaseDto>> findByBarcode(
            @PathVariable @Pattern(regexp = "\\d{8,14}", message = "A barcode is 8 to 14 digits") String barcode);

    @GetMapping("/releases/{mbid}")
    @Operation(summary = "Full detail for one release, including its cover theme",
            description = "The cover palette is sampled on the first lookup and reused afterwards.")
    @ApiResponse(responseCode = "200", description = "The release")
    @ApiResponse(responseCode = "404", description = "No such release")
    ResponseEntity<ReleaseDto> getRelease(@PathVariable UUID mbid);
}
