package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.MetadataApi;
import com.musiccollector.model.core.ArtistDto;
import com.musiccollector.model.core.DiscographyDto;
import com.musiccollector.model.core.ReleaseDto;
import com.musiccollector.services.metadata.MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
public class MetadataController implements MetadataApi {

    private final MetadataService metadataService;

    @Override
    public ResponseEntity<List<ReleaseDto>> search(String query, int limit) {
        return ResponseEntity.ok(metadataService.search(query.trim(), limit));
    }

    @Override
    public ResponseEntity<List<ReleaseDto>> findByBarcode(String barcode) {
        return ResponseEntity.ok(metadataService.findByBarcode(barcode));
    }

    @Override
    public ResponseEntity<List<ArtistDto>> searchArtists(String query, int limit) {
        return ResponseEntity.ok(metadataService.searchArtists(query.trim(), limit));
    }

    @Override
    public ResponseEntity<DiscographyDto> albumsOfArtist(UUID mbid, String primaryType, int limit) {
        MetadataService.Discography discography = metadataService.albumsOfArtist(mbid, primaryType, limit);
        return ResponseEntity.ok(new DiscographyDto(discography.albums(), discography.total()));
    }

    @Override
    public ResponseEntity<List<ReleaseDto>> releasesInGroup(String albumId, int limit) {
        return ResponseEntity.ok(metadataService.releasesInGroup(albumId, limit));
    }

    @Override
    public ResponseEntity<ReleaseDto> getRelease(String releaseId) {
        return ResponseEntity.ok(metadataService.getRelease(releaseId));
    }
}
