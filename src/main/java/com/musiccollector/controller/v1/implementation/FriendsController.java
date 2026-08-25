package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.FriendsApi;
import com.musiccollector.model.action.SendFriendRequest;
import com.musiccollector.model.core.FriendsOverviewDto;
import com.musiccollector.security.CurrentUser;
import com.musiccollector.services.social.FriendshipService;
import com.musiccollector.services.social.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FriendsController implements FriendsApi {

    private final FriendshipService friendshipService;
    private final ProfileService profileService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<FriendsOverviewDto> overview() {
        return ResponseEntity.ok(profileService.friendsOverview(currentUser.require().getId()));
    }

    @Override
    public ResponseEntity<Void> request(SendFriendRequest request) {
        friendshipService.request(currentUser.require(), request.handle());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> accept(UUID id) {
        friendshipService.accept(currentUser.require().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> decline(UUID id) {
        friendshipService.decline(currentUser.require().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> remove(UUID userId) {
        friendshipService.remove(currentUser.require().getId(), userId);
        return ResponseEntity.noContent().build();
    }
}
