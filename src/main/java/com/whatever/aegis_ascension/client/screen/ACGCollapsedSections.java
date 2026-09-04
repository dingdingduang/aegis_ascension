package com.whatever.aegis_ascension.client.screen;

import com.whatever.aegis_ascension.client.ClientSettings;

import java.util.ArrayList;
import java.util.List;

// When rememberCollapsedTabs is enabled: The collapsed state is saved to disk and persists across restarts
// When rememberCollapsedTabs is disabled: Collapse state stays in memory and is never saved to disk.
final class ACGCollapsedSections {
    private final List<String> collapsed = new ArrayList<>();

    ACGCollapsedSections() {
        ClientSettings settings = ClientSettings.get();
        if (settings.rememberCollapsedTabs) {
            collapsed.addAll(settings.collapsedSettingSections);
        }
    }

    boolean isCollapsed(String id) {
        return collapsed.contains(id);
    }

    void toggle(String id) {
        if (!collapsed.remove(id)) {
            collapsed.add(id);
        }
        ClientSettings settings = ClientSettings.get();
        if (!settings.rememberCollapsedTabs) {
            return;
        }
        settings.collapsedSettingSections.clear();
        settings.collapsedSettingSections.addAll(collapsed);
        settings.save();
    }
}
