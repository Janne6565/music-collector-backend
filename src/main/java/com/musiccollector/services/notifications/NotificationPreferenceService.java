package com.musiccollector.services.notifications;

import com.musiccollector.entity.NotificationPreferenceEntity;
import com.musiccollector.entity.UserEntity;
import com.musiccollector.model.core.NotificationCategory;
import com.musiccollector.model.core.NotificationPreferenceDto;
import com.musiccollector.model.core.NotificationPreferencesDto;
import com.musiccollector.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What may reach somebody outside the app (design 22a).
 *
 * <p>Two rules shape this, and both are about where a choice belongs:
 *
 * <ul>
 *   <li><b>The grid follows the account, not the device.</b> Deliberately unlike everything
 *       on Settings, which stays on the browser or phone that set it. Set once, and every
 *       device signed in inherits it — a preference about what the world may send you is not
 *       a fact about the screen you happened to set it on. Which <em>device</em> may buzz is
 *       a separate, shorter question, and it is not answered here.
 *   <li><b>Only the differences are stored.</b> An account with no rows is one that has
 *       never opened the screen. Storing the defaults would mean a new category needs a
 *       backfill before anybody's screen reads right.
 * </ul>
 *
 * <p>Security mail cannot be switched off, and the refusal is here rather than only in the
 * UI: a notice you can silence is not a notice, and a client is not where that is enforced.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPreferenceService.class);

    private final NotificationPreferenceRepository repository;
    private final NotificationDeviceService deviceService;

    @Transactional(readOnly = true)
    public NotificationPreferencesDto forUser(UserEntity user) {
        return describe(stored(user), deviceService.anyDevice(user));
    }

    /**
     * Flips one row of the grid. The screen saves as you go, so a request is never the whole
     * thing — and the answer is, so that a client never has to re-derive what it now reads.
     */
    @Transactional
    public NotificationPreferencesDto update(
            UserEntity user, NotificationCategory category, boolean mail, boolean push) {
        Map<NotificationCategory, NotificationPreferenceEntity> current = stored(user);

        NotificationPreferenceEntity row = current.get(category);
        if (row == null) {
            row = new NotificationPreferenceEntity();
            row.setUserId(user.getId());
            row.setCategory(category);
            current.put(category, row);
        }
        // A locked category keeps its mail switch wherever the design put it, whatever the
        // request says. Refusing loudly would make an honest client handle an error for a
        // switch it never renders, and a dishonest one learn nothing either way.
        row.setMail(category.mailLocked() ? category.mailByDefault() : mail);
        row.setPush(push);
        repository.save(row);

        log.debug("Notification preference {} updated for user {}", category, user.getId());
        return describe(current, deviceService.anyDevice(user));
    }

    /** True when this category may go out by mail for this account. */
    @Transactional(readOnly = true)
    public boolean mailEnabled(UserEntity user, NotificationCategory category) {
        if (category.mailLocked()) {
            return true;
        }
        NotificationPreferenceEntity row = stored(user).get(category);
        return row == null ? category.mailByDefault() : row.isMail();
    }

    /** True when this category may buzz for this account. The device's own mute is separate. */
    @Transactional(readOnly = true)
    public boolean pushEnabled(UserEntity user, NotificationCategory category) {
        NotificationPreferenceEntity row = stored(user).get(category);
        return row == null ? category.pushByDefault() : row.isPush();
    }

    private Map<NotificationCategory, NotificationPreferenceEntity> stored(UserEntity user) {
        Map<NotificationCategory, NotificationPreferenceEntity> map = new EnumMap<>(NotificationCategory.class);
        for (NotificationPreferenceEntity row : repository.findAllByUserId(user.getId())) {
            map.put(row.getCategory(), row);
        }
        return map;
    }

    private NotificationPreferencesDto describe(
            Map<NotificationCategory, NotificationPreferenceEntity> stored, boolean pushAvailable) {
        List<NotificationPreferenceDto> categories = java.util.Arrays.stream(NotificationCategory.values())
                .map(category -> {
                    NotificationPreferenceEntity row = stored.get(category);
                    boolean mail = category.mailLocked()
                            ? true
                            : row == null ? category.mailByDefault() : row.isMail();
                    boolean push = row == null ? category.pushByDefault() : row.isPush();
                    return new NotificationPreferenceDto(category, mail, push, category.mailLocked());
                })
                .toList();
        // True once anything on this account could be buzzed at all. Until then the column
        // says so rather than offering switches that would quietly do nothing (22a) -- and
        // the stored choices survive either way, so nobody sets them twice.
        return new NotificationPreferencesDto(categories, pushAvailable);
    }
}
