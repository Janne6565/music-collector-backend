package com.rekordo.controller.v1.implementation;

import com.rekordo.controller.v1.schema.ProfileApi;
import com.rekordo.model.core.ProfileDto;
import com.rekordo.model.core.ProfileSummaryDto;
import com.rekordo.model.core.SharedCollectionDto;
import com.rekordo.model.core.SharedWishlistDto;
import com.rekordo.security.CurrentUser;
import com.rekordo.services.social.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Open endpoints that still know who is asking: every method resolves the viewer as an
 * optional, and a null one is simply a stranger.
 */
@RestController
@RequiredArgsConstructor
public class ProfileController implements ProfileApi {

    private final ProfileService profileService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<List<ProfileSummaryDto>> searchProfiles(String query) {
        return ResponseEntity.ok(profileService.search(viewerId(), query));
    }

    @Override
    public ResponseEntity<ProfileDto> profile(String handle) {
        return ResponseEntity.ok(profileService.profile(viewerId(), handle));
    }

    @Override
    public ResponseEntity<SharedCollectionDto> collection(String handle) {
        return ResponseEntity.ok(profileService.collection(viewerId(), handle));
    }

    @Override
    public ResponseEntity<SharedWishlistDto> wishlist(String handle) {
        return ResponseEntity.ok(profileService.wishlist(viewerId(), handle));
    }

    private UUID viewerId() {
        return currentUser.optionalId().orElse(null);
    }
}
