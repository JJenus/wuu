package com.surf.tracker.notification.application;

import com.surf.tracker.notification.domain.entity.NotificationPreference;
import com.surf.tracker.notification.domain.entity.NotificationTemplate;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.infrastructure.repository.NotificationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PreferenceResolver {

    private static final Logger logger = LoggerFactory.getLogger(PreferenceResolver.class);

    private final NotificationPreferenceRepository preferenceRepository;

    public PreferenceResolver(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    public Map<NotificationChannel, ChannelPreference> resolvePreferences(
            String userId, String category, NotificationTemplate template) {

        Map<NotificationChannel, ChannelPreference> result = new EnumMap<>(NotificationChannel.class);

        for (NotificationChannel channel : NotificationChannel.values()) {
            result.put(channel, new ChannelPreference(true, false));
        }

        if (template != null && template.isMandatory()) {
            logger.debug("Template {} is mandatory — bypassing preference check", template.getTemplateId());
            for (NotificationChannel channel : result.keySet()) {
                result.put(channel, new ChannelPreference(true, true));
            }
            return result;
        }

        Optional<NotificationPreference> prefOpt = preferenceRepository
            .findByUserIdAndCategory(userId, category);

        if (prefOpt.isPresent()) {
            NotificationPreference pref = prefOpt.get();
            if (!pref.isEnabled()) {
                for (NotificationChannel channel : NotificationChannel.values()) {
                    result.put(channel, new ChannelPreference(false, false));
                }
            } else {
                for (NotificationChannel channel : NotificationChannel.values()) {
                    boolean enabled = pref.isChannelEnabled(channel);
                    result.put(channel, new ChannelPreference(enabled, false));
                }
            }
        }

        return result;
    }

    public boolean isChannelEnabled(String userId, String category,
                                   NotificationChannel channel,
                                   NotificationTemplate template) {
        Map<NotificationChannel, ChannelPreference> prefs = resolvePreferences(userId, category, template);
        ChannelPreference pref = prefs.getOrDefault(channel, new ChannelPreference(true, false));
        return pref.enabled();
    }

    public record ChannelPreference(boolean enabled, boolean mandatory) {
    }
}