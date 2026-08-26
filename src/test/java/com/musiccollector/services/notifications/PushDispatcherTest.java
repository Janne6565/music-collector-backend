package com.musiccollector.services.notifications;

import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.core.NotificationCategory;
import com.musiccollector.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushDispatcherTest {

    @Mock private UserRepository userRepository;
    @Mock private NotificationPreferenceService preferenceService;
    @Mock private NotificationDeviceService deviceService;
    @Mock private PushPort pushPort;

    private PushDispatcher dispatcher;
    private UserEntity recipient;

    @BeforeEach
    void setUp() {
        dispatcher = new PushDispatcher(userRepository, preferenceService, deviceService, pushPort);
        recipient = new UserEntity();
        recipient.setId(UUID.randomUUID());
        recipient.setEmail("jonas@example.test");
        recipient.setPasswordHash("hash");
        recipient.setTokenVersion(0);
        recipient.setCreatedAt(Instant.now());
        recipient.setUpdatedAt(Instant.now());
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(preferenceService.pushEnabled(any(), any())).thenReturn(true);
        when(deviceService.reachableTokens(any())).thenReturn(List.of("ExponentPushToken[aaa]"));
        when(pushPort.send(any())).thenReturn(List.of());
    }

    private PushEvent.FriendRequested request() {
        return new PushEvent.FriendRequested(recipient.getId(), "Milan Weiss", "milanw", 84);
    }

    @Test
    void namesThePersonAndWhatTheyHave() {
        dispatcher.on(request());

        ArgumentCaptor<List<PushMessage>> sent = ArgumentCaptor.forClass(List.class);
        verify(pushPort).send(sent.capture());
        PushMessage message = sent.getValue().getFirst();
        // 22c: iOS gives the title roughly one line, so the name goes first and nothing
        // else competes with it.
        assertThat(message.title()).isEqualTo("Milan Weiss wants to be friends");
        assertThat(message.body()).isEqualTo("@milanw · 84 copies");
        // A payload rides through Apple's and Google's servers, so nothing private is in it.
        assertThat(message.data()).containsEntry("type", "FRIEND_REQUEST");
    }

    @Test
    void theAccountsGridIsTheFirstGate() {
        when(preferenceService.pushEnabled(recipient, NotificationCategory.FRIEND_REQUEST)).thenReturn(false);

        dispatcher.on(request());

        verify(pushPort, never()).send(any());
    }

    @Test
    void aMutedDeviceIsDroppedRatherThanSentTo() {
        // Dropped here rather than sent and ignored, because "sent" is what Expo would
        // report back and the row would look like it had buzzed.
        when(deviceService.reachableTokens(recipient.getId())).thenReturn(List.of());

        dispatcher.on(request());

        verify(pushPort, never()).send(any());
    }

    @Test
    void aTokenThePushServiceCallsDeadIsForgotten() {
        when(deviceService.reachableTokens(recipient.getId()))
                .thenReturn(List.of("ExponentPushToken[live]", "ExponentPushToken[dead]"));
        when(pushPort.send(any())).thenReturn(List.of("ExponentPushToken[dead]"));

        dispatcher.on(request());

        // A wiped phone would otherwise keep its row forever, and every later send pays.
        verify(deviceService).forget("ExponentPushToken[dead]");
        verify(deviceService, never()).forget("ExponentPushToken[live]");
    }

    @Test
    void anAccountThatIsGoneIsNotAnError() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        dispatcher.on(request());

        verify(pushPort, never()).send(any());
    }
}
