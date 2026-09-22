package it.dhd.oxygencustomizer.utils;

/**
 * Preference keys for the Live Alerts (dynamic island) capsule customizations.
 * Shared by the settings UI and the SystemUI hook.
 */
public final class LiveAlertPrefs {

    private LiveAlertPrefs() {}

    public static final String PREFIX = "live_alert_";

    // Master switch
    public static final String LA_ENABLED = "live_alert_capsule_custom";

    // Background
    public static final String LA_BG_CUSTOM = "live_alert_bg_custom";
    public static final String LA_BG_COLOR = "live_alert_bg_color";
    public static final String LA_CORNER_RADIUS = "live_alert_corner_radius";

    // Outline
    public static final String LA_STROKE_MODE = "live_alert_stroke_mode";
    public static final String LA_STROKE_WIDTH = "live_alert_stroke_width";
    public static final String LA_STROKE_COLOR = "live_alert_stroke_color";

    // Size & position
    public static final String LA_MAX_WIDTH_CUSTOM = "live_alert_max_width_custom";
    public static final String LA_MAX_WIDTH = "live_alert_max_width";
    public static final String LA_OFFSET_X = "live_alert_offset_x";
    public static final String LA_OFFSET_Y = "live_alert_offset_y";

    // Edge crescents ("moons") on either side of the capsule
    public static final String LA_MOON_MODE = "live_alert_moon_mode";
    public static final String LA_MOON_COLOR = "live_alert_moon_color";

    // Mode values (stored as strings by OplusMenuPreference)
    public static final int MODE_DEFAULT = 0;
    public static final int MODE_HIDDEN = 1;
    public static final int MODE_CUSTOM = 2;

    // Stock values from SystemUIPlugin 16.001.002 (OnePlus 15R, OxygenOS 16.0.9)
    public static final int DEFAULT_BG_COLOR = 0xFF000000;      // capsule_background_color
    public static final int DEFAULT_CORNER_RADIUS_DP = 20;      // capsule_corner_radius
    public static final int DEFAULT_STROKE_COLOR = 0x40959595;  // capsule_stroke_default_color
    public static final int DEFAULT_STROKE_WIDTH_DP = 1;        // capsule_stroke_width is 1.3dp
    public static final int DEFAULT_MAX_WIDTH_DP = 202;         // capsule_view_max_width
    public static final int DEFAULT_MOON_COLOR = 0xFF141414;    // capsule_moon_dark_color
}
