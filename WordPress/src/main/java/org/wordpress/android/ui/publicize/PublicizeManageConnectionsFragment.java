package org.wordpress.android.ui.publicize;

import android.os.Bundle;
import android.os.Handler;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.models.Blog;
import org.wordpress.android.ui.prefs.DetailListPreference;
import org.wordpress.android.ui.prefs.SiteSettingsFragment;
import org.wordpress.android.ui.prefs.SiteSettingsInterface;
import org.wordpress.android.ui.prefs.SummaryEditTextPreference;
import org.wordpress.android.ui.prefs.WPSwitchPreference;
import org.wordpress.android.util.CoreEvents;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.ToastUtils;
import org.wordpress.android.util.WPPrefUtils;

import de.greenrobot.event.EventBus;


public class PublicizeManageConnectionsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener, SiteSettingsInterface.SiteSettingsListener {
    private static final String TWITTER_PREFIX = "@";
    private SummaryEditTextPreference mLabelPreference;
    private SiteSettingsInterface mSiteSettings;
    private DetailListPreference mButtonStylePreference;
    private WPSwitchPreference mReblogButtonPreference;
    private WPSwitchPreference mLikeButtonPreference;
    private WPSwitchPreference mCommentLikesPreference;
    private SummaryEditTextPreference mTwitterUsernamePreference;
    private Blog mBlog;
    private boolean mShouldFetch;

    public PublicizeManageConnectionsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.publicize_preferences);

        mBlog = WordPress.currentBlog;
        if (mBlog == null || !NetworkUtils.checkConnection(getActivity())) {
            getActivity().finish();
            return;
        }

        mShouldFetch = true;
        mSiteSettings = SiteSettingsInterface.getInterface(getActivity(), mBlog, this);
        setRetainInstance(true);

        mLabelPreference = (SummaryEditTextPreference) getChangePref(R.string.publicize_label);
        mButtonStylePreference = (DetailListPreference) getChangePref(R.string.publicize_button_style);
        setDetailListPreferenceValue(mButtonStylePreference, mSiteSettings.getSharingButtonStyle(getActivity()), mSiteSettings.getSharingButtonStyleDisplayText(getActivity()));
        mButtonStylePreference.setEntries(getResources().getStringArray(R.array.sharing_button_style_display_array));
        mButtonStylePreference.setEntryValues(getResources().getStringArray(R.array.sharing_button_style_array));
        mReblogButtonPreference = (WPSwitchPreference) getChangePref(R.string.pref_key_reblog);
        mLikeButtonPreference = (WPSwitchPreference) getChangePref(R.string.pref_key_like);
        mCommentLikesPreference = (WPSwitchPreference) getChangePref(R.string.pref_key_comment_likes);
        mTwitterUsernamePreference = (SummaryEditTextPreference) getChangePref(R.string.pref_key_twitter_username);
    }

    private void setDetailListPreferenceValue(DetailListPreference pref, String value, String summary) {
        pref.setValue(value);
        pref.setSummary(summary);
        pref.refreshAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();

        // always load cached settings
        mSiteSettings.init(false);

        if (mShouldFetch) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    // initialize settings with locally cached values, fetch remote on first pass
                    mSiteSettings.init(true);
                }
            }, 1000);
            mShouldFetch = false;
        }
    }

    private Preference getChangePref(int id) {
        return WPPrefUtils.getPrefAndSetChangeListener(this, id, this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mLabelPreference) {
            mSiteSettings.setSharingLabel(newValue.toString());
            changeEditTextPreferenceValue(mLabelPreference, newValue.toString());
        } else if (preference == mButtonStylePreference) {
            mSiteSettings.setSharingButtonStyle(newValue.toString());
            setDetailListPreferenceValue(mButtonStylePreference,
                    mSiteSettings.getSharingButtonStyle(getActivity()),
                    mSiteSettings.getSharingButtonStyleDisplayText(getActivity()));
        } else if (preference == mReblogButtonPreference) {
            mSiteSettings.setAllowReblogButton((Boolean) newValue);
        } else if (preference == mLikeButtonPreference) {
            mSiteSettings.setAllowLikeButton((Boolean) newValue);
        } else if (preference == mCommentLikesPreference) {
            mSiteSettings.setAllowCommentLikes((Boolean) newValue);
        } else if (preference == mTwitterUsernamePreference) {
            saveAndSetTwitterUsername(newValue.toString());
        } else {
            return false;
        }

        mSiteSettings.saveSettings();

        return true;
    }

    private void saveAndSetTwitterUsername(String username) {
        if (username == null) {
            return;
        }

        if (username.startsWith(TWITTER_PREFIX)) {
            username = username.substring(1, username.length());
        }

        mSiteSettings.setTwitterUsername(username);
        changeEditTextPreferenceValue(mTwitterUsernamePreference, username);
    }

    private void changeEditTextPreferenceValue(EditTextPreference pref, String newValue) {
        if (newValue == null || pref == null || pref.getEditText().isInEditMode()) return;

        if (pref == mTwitterUsernamePreference && !newValue.isEmpty()) {
            newValue = TWITTER_PREFIX + newValue;
        }

        if (!newValue.equals(pref.getSummary())) {
            String formattedValue = StringUtils.unescapeHTML(newValue.replaceFirst(SiteSettingsFragment.ADDRESS_FORMAT_REGEX, ""));

            pref.setText(formattedValue);
            pref.setSummary(formattedValue);
        }
    }

    private void setPreferencesFromSiteSettings() {
        changeEditTextPreferenceValue(mLabelPreference, mSiteSettings.getSharingLabel());
        setDetailListPreferenceValue(mButtonStylePreference, mSiteSettings.getSharingButtonStyle(getActivity()), mSiteSettings.getSharingButtonStyleDisplayText(getActivity()));
        mReblogButtonPreference.setChecked(mSiteSettings.getAllowReblogButton());
        mLikeButtonPreference.setChecked(mSiteSettings.getAllowLikeButton());
        mCommentLikesPreference.setChecked(mSiteSettings.getAllowCommentLikes());
        changeEditTextPreferenceValue(mTwitterUsernamePreference, mSiteSettings.getTwitterUsername());
    }

    private boolean shouldShowListPreference(DetailListPreference preference) {
        return preference != null && preference.getEntries() != null && preference.getEntries().length > 0;
    }

    @Override
    public void onSettingsUpdated(Exception error) {
        if (isAdded()) setPreferencesFromSiteSettings();
    }

    @Override
    public void onSettingsSaved(Exception error) {
        if (error != null) {
            ToastUtils.showToast(WordPress.getContext(), R.string.error_post_remote_site_settings);
            return;
        }
        mBlog.setBlogName(mSiteSettings.getTitle());
        WordPress.wpDB.saveBlog(mBlog);

        // update the global current Blog so WordPress.getCurrentBlog() callers will get the updated object
        WordPress.setCurrentBlog(mBlog.getLocalTableBlogId());

        EventBus.getDefault().post(new CoreEvents.BlogListChanged());
    }

    @Override
    public void onCredentialsValidated(Exception error) {
        if (error != null) {
            ToastUtils.showToast(WordPress.getContext(), R.string.username_or_password_incorrect);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        return preference == mButtonStylePreference && !shouldShowListPreference((DetailListPreference) preference);
    }
}
