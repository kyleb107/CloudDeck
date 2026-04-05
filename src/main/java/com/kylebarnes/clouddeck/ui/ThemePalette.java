package com.kylebarnes.clouddeck.ui;

import com.kylebarnes.clouddeck.model.ThemePreset;

record ThemePalette(
        String appBackground,
        String appBackgroundAlt,
        String surfaceBackground,
        String surfaceBackgroundAlt,
        String borderColor,
        String accentBlue,
        String accentGold,
        String successGreen,
        String warningRed,
        String cautionOrange,
        String unknownGray,
        String textPrimary,
        String textMuted,
        String controlBackground,
        String metricBackground,
        String metricBorder,
        String listBackground,
        String primaryGradientStart,
        String primaryGradientEnd,
        String bannerGo,
        String bannerCaution,
        String bannerNoGo,
        String favoriteRemoveColor
) {
    static ThemePalette forPreset(ThemePreset preset) {
        if (preset == ThemePreset.CLEARSKY) {
            return new ThemePalette(
                    "#eef5fb",
                    "#dbe9f4",
                    "#ffffff",
                    "#f7fbff",
                    "#b7cddd",
                    "#1577c8",
                    "#b97400",
                    "#1c8f55",
                    "#c94d4d",
                    "#bb7a13",
                    "#658096",
                    "#132130",
                    "#4d6476",
                    "rgba(255,255,255,0.9)",
                    "#edf4f9",
                    "#d8e4ee",
                    "#ffffff",
                    "#1388d4",
                    "#7fd3ff",
                    "rgba(56, 153, 108, 0.18)",
                    "rgba(194, 142, 44, 0.18)",
                    "rgba(199, 85, 85, 0.18)",
                    "#70879b"
            );
        }

        return new ThemePalette(
                "#0e1623",
                "#122033",
                "#172537",
                "#1d3048",
                "#27425f",
                "#5ec2ff",
                "#ffd166",
                "#5ee18b",
                "#ff6b6b",
                "#ffb454",
                "#90a4b7",
                "#eaf3ff",
                "#bfd0e0",
                "rgba(255,255,255,0.08)",
                "rgba(255,255,255,0.05)",
                "rgba(255,255,255,0.07)",
                "#162538",
                "#3ea7f5",
                "#6fd3ff",
                "rgba(36, 118, 77, 0.35)",
                "rgba(167, 102, 28, 0.35)",
                "rgba(153, 50, 50, 0.35)",
                "#8ca2b8"
        );
    }
}
