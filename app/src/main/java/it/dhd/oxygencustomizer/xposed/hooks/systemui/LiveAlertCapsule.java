package it.dhd.oxygencustomizer.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.SYSTEM_UI;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.DEFAULT_BG_COLOR;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.DEFAULT_CORNER_RADIUS_DP;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.DEFAULT_MAX_WIDTH_DP;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.DEFAULT_MOON_COLOR;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.DEFAULT_STROKE_COLOR;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.DEFAULT_STROKE_WIDTH_DP;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_BG_COLOR;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_BG_CUSTOM;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_CORNER_RADIUS;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_ENABLED;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_MAX_WIDTH;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_MAX_WIDTH_CUSTOM;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_MOON_COLOR;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_MOON_MODE;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_OFFSET_X;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_OFFSET_Y;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_STROKE_COLOR;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_STROKE_MODE;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.LA_STROKE_WIDTH;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.MODE_CUSTOM;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.MODE_DEFAULT;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.MODE_HIDDEN;
import static it.dhd.oxygencustomizer.utils.LiveAlertPrefs.PREFIX;
import static it.dhd.oxygencustomizer.xposed.XPrefs.Xprefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.xposed.XposedMods;

/**
 * Live Alerts ("dynamic island") capsule appearance for OxygenOS 16.
 * <p>
 * The capsule is not drawn by SystemUI itself but by the Seedling plugin
 * (com.oplus.systemui.plugins / SystemUIPlugin.apk), which SystemUI loads with its own
 * ClassLoader. We catch that ClassLoader when SeedlingPluginManager connects the plugin
 * and hook the plugin's view classes from there. The view classes we touch keep their
 * real names (they are referenced from layout XML); the one obfuscated method we need
 * (dark-intensity update, a single float argument) is located by signature.
 * <p>
 * Mapped against SystemUI 16.99.12 + SystemUIPlugin 16.001.002 (CPH2771_16.0.9.401).
 * Every hook fails soft: if a class or method is missing, that option just does nothing.
 */
public class LiveAlertCapsule extends XposedMods {

    private static final String listenPackage = SYSTEM_UI;
    private static final String TAG = "LiveAlertCapsule";

    // SystemUI side (not obfuscated)
    private static final String SEEDLING_PLUGIN_MANAGER = "com.oplus.systemui.statusbar.seeding.SeedlingPluginManager";
    private static final String STATUSBAR_CAPSULE_HOST = "com.oplus.systemui.statusbar.seeding.CapsulePluginContainer";

    // Seedling plugin side
    private static final String PLUGIN_PKG = "com.oplus.systemui.plugins";
    private static final String CAPSULE_VIEW = "com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView";
    private static final String CAPSULE_CONTAINER = "com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleContainer";
    private static final String[] MAX_WIDTH_DIMENS = {
            "capsule_view_max_width",
            "capsule_view_max_width_left_corner",
            "capsule_view_max_width_tablet"
    };

    // Settings
    private volatile boolean mEnabled = false;
    private volatile boolean mBgCustom = false;
    private volatile int mBgColor = DEFAULT_BG_COLOR;
    private volatile int mCornerRadiusDp = DEFAULT_CORNER_RADIUS_DP;
    private volatile int mStrokeMode = MODE_DEFAULT;
    private volatile int mStrokeWidthDp = DEFAULT_STROKE_WIDTH_DP;
    private volatile int mStrokeColor = DEFAULT_STROKE_COLOR;
    private volatile boolean mMaxWidthCustom = false;
    private volatile int mMaxWidthDp = DEFAULT_MAX_WIDTH_DP;
    private volatile int mMoonMode = MODE_DEFAULT;
    private volatile int mMoonColor = DEFAULT_MOON_COLOR;
    private volatile int mOffsetXDp = 0;
    private volatile int mOffsetYDp = 0;

    // Live views we have touched, so preference changes apply without a SystemUI restart.
    private final Set<View> mCapsuleViews = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> mContainers = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> mHosts = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<View, CapsuleOriginals> mOriginals = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, Float> mDarkIntensity = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<View, Boolean> mContainerMoonShown = Collections.synchronizedMap(new WeakHashMap<>());
    // Views whose outline / moon tint we overrode, so switching back to "System default" restores them
    private final Set<View> mStrokeTouched = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<View> mMoonTinted = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<ClassLoader> mHookedLoaders = Collections.newSetFromMap(new WeakHashMap<>());

    // Max-width override: only active while a CapsuleView is measuring itself (UI thread only).
    private Resources mMeasuringRes = null;
    private final Set<Integer> mMaxWidthIds = Collections.synchronizedSet(new HashSet<>());
    private Set<XC_MethodHook.Unhook> mResourcesUnhooks = null;
    private volatile Class<?> mPluginResClass = null;
    private Method mViewDarkMethod = null;
    private Method mContainerDarkMethod = null;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private static final class CapsuleOriginals {
        ColorStateList bgColor;
        float bgRadius = -1f;
        float fgRadius = -1f;
    }

    public LiveAlertCapsule(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        mEnabled = Xprefs.getBoolean(LA_ENABLED, false);
        mBgCustom = Xprefs.getBoolean(LA_BG_CUSTOM, false);
        mBgColor = Xprefs.getInt(LA_BG_COLOR, DEFAULT_BG_COLOR);
        mCornerRadiusDp = Xprefs.getSliderInt(LA_CORNER_RADIUS, DEFAULT_CORNER_RADIUS_DP);
        mStrokeMode = parseMode(Xprefs.getString(LA_STROKE_MODE, "0"));
        mStrokeWidthDp = Xprefs.getSliderInt(LA_STROKE_WIDTH, DEFAULT_STROKE_WIDTH_DP);
        mStrokeColor = Xprefs.getInt(LA_STROKE_COLOR, DEFAULT_STROKE_COLOR);
        mMaxWidthCustom = Xprefs.getBoolean(LA_MAX_WIDTH_CUSTOM, false);
        mMaxWidthDp = Xprefs.getSliderInt(LA_MAX_WIDTH, DEFAULT_MAX_WIDTH_DP);
        mMoonMode = parseMode(Xprefs.getString(LA_MOON_MODE, "0"));
        mMoonColor = Xprefs.getInt(LA_MOON_COLOR, DEFAULT_MOON_COLOR);
        mOffsetXDp = Xprefs.getSliderInt(LA_OFFSET_X, 0);
        mOffsetYDp = Xprefs.getSliderInt(LA_OFFSET_Y, 0);

        if (Key.length > 0 && Key[0] != null && Key[0].startsWith(PREFIX)) {
            mMainHandler.post(this::reapplyAll);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        ClassLoader cl = lpparam.classLoader;

        Class<?> SeedlingPluginManager = findClassIfExists(SEEDLING_PLUGIN_MANAGER, cl);
        if (SeedlingPluginManager == null) {
            log("SeedlingPluginManager not found; Live Alerts capsule hooks disabled");
            return;
        }

        // The plugin instance tells us which ClassLoader holds the capsule views.
        XC_MethodHook pluginCatcher = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object plugin = param.args.length > 0 ? param.args[0] : null;
                if (plugin != null) hookPlugin(plugin.getClass().getClassLoader());
            }
        };
        hookAllMethods(SeedlingPluginManager, "onPluginConnected", pluginCatcher);
        hookAllMethods(SeedlingPluginManager, "setSeedlingPlugin", pluginCatcher);

        // Status bar host view for the capsule: used for the position offset.
        Class<?> CapsuleHost = findClassIfExists(STATUSBAR_CAPSULE_HOST, cl);
        if (CapsuleHost != null) {
            XC_MethodHook hostHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View host = (View) param.thisObject;
                    synchronized (mHosts) {
                        mHosts.add(host);
                    }
                    applyHostOffset(host);
                }
            };
            hookAllMethods(CapsuleHost, "onFinishInflate", hostHook);
            hookAllMethods(CapsuleHost, "onAttachedToWindow", hostHook);
        }
    }

    private void hookPlugin(ClassLoader pluginCl) {
        if (pluginCl == null) return;
        synchronized (mHookedLoaders) {
            if (!mHookedLoaders.add(pluginCl)) return;
        }

        Class<?> CapsuleView = findClassIfExists(CAPSULE_VIEW, pluginCl);
        Class<?> CapsuleContainer = findClassIfExists(CAPSULE_CONTAINER, pluginCl);
        log("Seedling plugin connected: CapsuleView=" + (CapsuleView != null)
                + " CapsuleContainer=" + (CapsuleContainer != null));

        if (CapsuleView != null) {
            try {
                hookAllConstructors(CapsuleView, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        View v = (View) param.thisObject;
                        synchronized (mCapsuleViews) {
                            mCapsuleViews.add(v);
                        }
                        resolveMaxWidthIds(v.getResources());
                        applyCapsuleView(v);
                    }
                });

                hookAllMethods(CapsuleView, "onFinishInflate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        applyCapsuleView((View) param.thisObject);
                    }
                });

                // Dark-intensity update re-sets the outline; re-apply ours afterwards.
                mViewDarkMethod = findSingleFloatSetter(CapsuleView);
                if (mViewDarkMethod != null) {
                    hookMethod(mViewDarkMethod, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            View v = (View) param.thisObject;
                            mDarkIntensity.put(v, (Float) param.args[0]);
                            applyStroke(v);
                        }
                    });
                } else {
                    log("CapsuleView dark-intensity method not found; outline may reset on theme changes");
                }

                hookAllMethods(CapsuleView, "onMeasure", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (mEnabled && mMaxWidthCustom) {
                            mMeasuringRes = ((View) param.thisObject).getResources();
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        mMeasuringRes = null;
                    }
                });
            } catch (Throwable t) {
                log(t);
            }
        }

        if (CapsuleContainer != null) {
            try {
                hookAllMethods(CapsuleContainer, "onFinishInflate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        View c = (View) param.thisObject;
                        synchronized (mContainers) {
                            mContainers.add(c);
                        }
                        applyMoons(c);
                    }
                });

                hookAllMethods(CapsuleContainer, "setMoonViewShown", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        View c = (View) param.thisObject;
                        mContainerMoonShown.put(c, (Boolean) param.args[0]);
                        applyMoons(c);
                    }
                });

                mContainerDarkMethod = findSingleFloatSetter(CapsuleContainer);
                if (mContainerDarkMethod != null) {
                    hookMethod(mContainerDarkMethod, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            View c = (View) param.thisObject;
                            mDarkIntensity.put(c, (Float) param.args[0]);
                            applyMoons(c);
                        }
                    });
                }
            } catch (Throwable t) {
                log(t);
            }
        }

        updateResourcesHook();
    }

    // ---------------------------------------------------------------------------------------
    // Apply

    private void reapplyAll() {
        updateResourcesHook();

        for (View v : snapshot(mCapsuleViews)) {
            applyCapsuleView(v);
            v.requestLayout();
        }
        for (View c : snapshot(mContainers)) {
            applyMoons(c);
            c.requestLayout();
        }
        for (View h : snapshot(mHosts)) {
            applyHostOffset(h);
        }
    }

    private void applyCapsuleView(View v) {
        try {
            GradientDrawable bg = mutableGradient(v.getBackground());
            GradientDrawable fg = mutableGradient(v.getForeground());
            if (bg == null && fg == null) return;

            CapsuleOriginals o = mOriginals.get(v);
            if (o == null) {
                o = new CapsuleOriginals();
                if (bg != null) {
                    o.bgColor = bg.getColor();
                    o.bgRadius = bg.getCornerRadius();
                }
                if (fg != null) {
                    o.fgRadius = fg.getCornerRadius();
                }
                mOriginals.put(v, o);
            }

            if (bg != null) {
                if (mEnabled && mBgCustom) {
                    bg.setColor(mBgColor);
                } else if (o.bgColor != null) {
                    bg.setColor(o.bgColor);
                }
                if (mEnabled) {
                    bg.setCornerRadius(dp(v.getResources(), mCornerRadiusDp));
                } else if (o.bgRadius >= 0) {
                    bg.setCornerRadius(o.bgRadius);
                }
            }
            if (fg != null) {
                if (mEnabled) {
                    fg.setCornerRadius(dp(v.getResources(), mCornerRadiusDp));
                } else if (o.fgRadius >= 0) {
                    fg.setCornerRadius(o.fgRadius);
                }
            }
            applyStroke(v);
            v.invalidate();
        } catch (Throwable t) {
            log(t);
        }
    }

    private void applyStroke(View v) {
        try {
            GradientDrawable fg = mutableGradient(v.getForeground());
            if (fg == null) return;
            Resources res = v.getResources();

            int mode = mEnabled ? mStrokeMode : MODE_DEFAULT;
            if (mode == MODE_HIDDEN) {
                mStrokeTouched.add(v);
                fg.setStroke(0, Color.TRANSPARENT);
            } else if (mode == MODE_CUSTOM) {
                mStrokeTouched.add(v);
                fg.setStroke(Math.max(1, (int) dp(res, mStrokeWidthDp)), mStrokeColor);
            } else if (mStrokeTouched.remove(v)) {
                // Back to system default: let the plugin recompute its own outline.
                Float dark = mDarkIntensity.get(v);
                if (dark != null && mViewDarkMethod != null) {
                    mViewDarkMethod.invoke(v, dark);
                } else {
                    int w = pluginDimenPx(res, "capsule_stroke_width");
                    int c = pluginColor(res, "capsule_stroke_default_color");
                    if (w >= 0) fg.setStroke(w, c);
                }
            }
            v.invalidate();
        } catch (Throwable t) {
            log(t);
        }
    }

    @SuppressLint("DiscouragedApi")
    private void applyMoons(View container) {
        try {
            ImageView left = (ImageView) callMethod(container, "getLeftMoon");
            ImageView right = (ImageView) callMethod(container, "getRightMoon");
            if (left == null || right == null) return;

            int mode = mEnabled ? mMoonMode : MODE_DEFAULT;

            Boolean shown = mContainerMoonShown.get(container);
            if (mode == MODE_HIDDEN) {
                left.setVisibility(View.GONE);
                right.setVisibility(View.GONE);
            } else if (shown != null) {
                int vis = shown ? View.VISIBLE : View.GONE;
                left.setVisibility(vis);
                right.setVisibility(vis);
            }

            if (mode == MODE_CUSTOM) {
                mMoonTinted.add(container);
                ColorStateList tint = ColorStateList.valueOf(mMoonColor);
                left.setImageTintList(tint);
                right.setImageTintList(tint);
            } else if (mMoonTinted.remove(container)) {
                // Back to system default: let the plugin recompute its own tint.
                Float dark = mDarkIntensity.get(container);
                if (dark != null && mContainerDarkMethod != null) {
                    mContainerDarkMethod.invoke(container, dark);
                } else {
                    ColorStateList tint = ColorStateList.valueOf(
                            pluginColor(container.getResources(), "capsule_moon_dark_color"));
                    left.setImageTintList(tint);
                    right.setImageTintList(tint);
                }
            }
        } catch (Throwable t) {
            log(t);
        }
    }

    private void applyHostOffset(View host) {
        try {
            Resources res = host.getResources();
            float x = mEnabled ? dp(res, mOffsetXDp) : 0f;
            float y = mEnabled ? dp(res, mOffsetYDp) : 0f;
            host.setTranslationX(x);
            host.setTranslationY(y);
        } catch (Throwable t) {
            log(t);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Max width: CapsuleView.onMeasure caps its width with a plugin dimen. While (and only while)
    // a CapsuleView is measuring, answer that dimen lookup with our own value.

    private void updateResourcesHook() {
        boolean want = mEnabled && mMaxWidthCustom;
        synchronized (this) {
            if (want && mResourcesUnhooks == null) {
                XC_MethodHook dimenHook = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Resources measuring = mMeasuringRes;
                        if (measuring == null || param.thisObject != measuring) return;
                        if (!mEnabled || !mMaxWidthCustom) return;
                        Object id = param.args[0];
                        if (id instanceof Integer && mMaxWidthIds.contains(id)) {
                            param.setResult((int) dp(measuring, mMaxWidthDp));
                        }
                    }
                };
                mResourcesUnhooks = new HashSet<>();
                // Hook the base method, plus any override in the plugin's Resources subclass chain
                // (an override that doesn't call super would otherwise bypass the base hook).
                Class<?> c = mPluginResClass;
                while (c != null && c != Resources.class && Resources.class.isAssignableFrom(c)) {
                    try {
                        c.getDeclaredMethod("getDimensionPixelOffset", int.class);
                        mResourcesUnhooks.addAll(hookAllMethods(c, "getDimensionPixelOffset", dimenHook));
                    } catch (NoSuchMethodException ignored) {
                    }
                    c = c.getSuperclass();
                }
                mResourcesUnhooks.addAll(hookAllMethods(Resources.class, "getDimensionPixelOffset", dimenHook));
                log("max width override installed");
            } else if (!want && mResourcesUnhooks != null) {
                for (XC_MethodHook.Unhook u : mResourcesUnhooks) u.unhook();
                mResourcesUnhooks = null;
                log("max width override removed");
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private void resolveMaxWidthIds(Resources pluginRes) {
        Class<?> prev = mPluginResClass;
        mPluginResClass = pluginRes.getClass();
        if (prev != mPluginResClass) {
            // Re-install so a Resources subclass override is covered too.
            synchronized (this) {
                if (mResourcesUnhooks != null) {
                    for (XC_MethodHook.Unhook u : mResourcesUnhooks) u.unhook();
                    mResourcesUnhooks = null;
                }
            }
            updateResourcesHook();
        }
        if (!mMaxWidthIds.isEmpty()) return;
        for (String name : MAX_WIDTH_DIMENS) {
            int id = pluginRes.getIdentifier(name, "dimen", PLUGIN_PKG);
            if (id != 0) mMaxWidthIds.add(id);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers

    /** The plugin's obfuscated dark-intensity setter: the only public void method(float). */
    private static Method findSingleFloatSetter(Class<?> clazz) {
        Method found = null;
        for (Method m : clazz.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0] == float.class && m.getReturnType() == void.class
                    && Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers())
                    && !m.getName().startsWith("set")) {
                if (found != null) return null; // ambiguous: better do nothing than hook the wrong one
                found = m;
            }
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private static GradientDrawable mutableGradient(Drawable d) {
        if (d instanceof GradientDrawable) {
            return (GradientDrawable) d.mutate();
        }
        return null;
    }

    private static float dp(Resources res, int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, res.getDisplayMetrics());
    }

    @SuppressLint("DiscouragedApi")
    private static int pluginDimenPx(Resources res, String name) {
        int id = res.getIdentifier(name, "dimen", PLUGIN_PKG);
        return id == 0 ? -1 : res.getDimensionPixelSize(id);
    }

    @SuppressLint("DiscouragedApi")
    private static int pluginColor(Resources res, String name) {
        int id = res.getIdentifier(name, "color", PLUGIN_PKG);
        return id == 0 ? 0 : res.getColor(id, null);
    }

    private static int parseMode(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Throwable t) {
            return MODE_DEFAULT;
        }
    }

    private static List<View> snapshot(Set<View> set) {
        synchronized (set) {
            return new ArrayList<>(set);
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return listenPackage.equals(packageName);
    }
}
