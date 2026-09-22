package it.dhd.oxygencustomizer.ui.fragments.mods;

import static it.dhd.oxygencustomizer.utils.Constants.Packages.SYSTEM_UI;

import it.dhd.oxygencustomizer.R;
import it.dhd.oxygencustomizer.ui.base.ControlledPreferenceFragmentCompat;

/**
 * Live Alerts ("dynamic island") capsule customizations.
 */
public class LiveAlerts extends ControlledPreferenceFragmentCompat {

    @Override
    public String getTitle() {
        return getString(R.string.live_alerts_title);
    }

    @Override
    public boolean backButtonEnabled() {
        return true;
    }

    @Override
    public int getLayoutResource() {
        return R.xml.live_alerts_prefs;
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    @Override
    public String[] getScopes() {
        return new String[]{SYSTEM_UI};
    }
}
