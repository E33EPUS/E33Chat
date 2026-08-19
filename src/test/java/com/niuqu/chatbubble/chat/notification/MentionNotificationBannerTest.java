package com.niuqu.chatbubble.chat.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** banner_opacity math (2.4.0 sync): opacity fades only shadow + background. */
class MentionNotificationBannerTest {

    @Test
    void defaultOpacityLeavesBackgroundUnchanged() {
        // null (old config) and 100 both mean fully opaque background
        assertEquals(1.0f, MentionNotificationBanner.bannerBgAlphaMul(1f, null));
        assertEquals(1.0f, MentionNotificationBanner.bannerBgAlphaMul(1f, 100));
    }

    @Test
    void opacityFadesBackgroundProportionally() {
        assertEquals(0.5f, MentionNotificationBanner.bannerBgAlphaMul(1f, 50));
        assertEquals(0.0f, MentionNotificationBanner.bannerBgAlphaMul(1f, 0));
        assertEquals(0.75f, MentionNotificationBanner.bannerBgAlphaMul(1f, 75), 1e-6f);
    }

    @Test
    void animAlphaMultipliesWithOpacity() {
        // during the slide-in, animAlpha<1 compounds with the opacity setting
        assertEquals(0.4f, MentionNotificationBanner.bannerBgAlphaMul(0.8f, 50), 1e-6f);
        assertEquals(0.64f, MentionNotificationBanner.bannerBgAlphaMul(0.8f, 80), 1e-6f);
    }
}
