package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppleRemoteShortcutsTest {
    @Test public void physicalButtonsUseRequestedTargets() {
        assertEquals("com.google.android.youtube.tv", AppleRemoteShortcuts.forKey(393, 1001));
        assertEquals("top.rootu.lampa", AppleRemoteShortcuts.forKey(400, 185));
        assertEquals("hub_plus", AppleRemoteShortcuts.forKey(582, 0));
        assertEquals("hub_plus", AppleRemoteShortcuts.forKey(0, 231));
        assertNull(AppleRemoteShortcuts.forKey(0, 24)); // Volume unchanged.
    }
    @Test public void firmwareFallbacksAgreeAndDoNotLoop() {
        assertEquals("com.google.android.youtube.tv", AppleRemoteShortcuts.forWindow("com.netflix.ninja"));
        assertEquals("top.rootu.lampa", AppleRemoteShortcuts.forWindow("com.amazon.amazonvideo.livingroom"));
        assertEquals("hub_plus", AppleRemoteShortcuts.forWindow("com.google.android.katniss"));
        assertNull(AppleRemoteShortcuts.forWindow("com.google.android.youtube.tv"));
        assertNull(AppleRemoteShortcuts.forWindow("top.rootu.lampa"));
        assertNull(AppleRemoteShortcuts.forWindow("com.android.settings"));
    }
}
