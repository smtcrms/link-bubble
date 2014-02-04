package com.linkbubble.ui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import com.linkbubble.Constant;
import com.linkbubble.MainController;
import com.linkbubble.util.ActionItem;
import com.linkbubble.Config;
import com.linkbubble.MainApplication;
import com.linkbubble.R;
import com.linkbubble.Settings;
import com.linkbubble.util.Util;
import com.squareup.otto.Bus;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Vector;


/**
 * Created by gw on 11/09/13.
 */
public class SettingsFragment extends PreferenceFragment {

    private Preference mAutoContentDisplayPreference;
    private Handler mHandler = new Handler();

    public static class IncognitoModeChangedEvent {
        public IncognitoModeChangedEvent(boolean value) {
            mIncognito = value;
        }
        public boolean mIncognito;
    }

    public interface IncognitoModeChangedEventHandler {
        public void onIncognitoModeChanged(boolean incognito);
    }

    private void configureDefaultAppsList() {
        PreferenceCategory preferenceCategory = (PreferenceCategory)findPreference("preference_category_other_apps");
        preferenceCategory.removeAll();

        Preference noticePreference = new Preference(getActivity());

        PackageManager packageManager = getActivity().getPackageManager();
        TreeMap<String, ComponentName> defaultAppsMap = Settings.get().getDefaultAppsMap();
        if (defaultAppsMap != null && defaultAppsMap.size() > 0) {
            noticePreference.setSummary(R.string.preference_default_apps_notice_summary);
            preferenceCategory.addPreference(noticePreference);

            for (String key : defaultAppsMap.keySet()) {
                ComponentName componentName = defaultAppsMap.get(key);
                try {
                    ActivityInfo info = packageManager.getActivityInfo(componentName, 0);
                    final CharSequence label = info.loadLabel(packageManager);
                    final String host = key;
                    Preference preference = new Preference(getActivity());
                    preference.setTitle(label);
                    preference.setIcon(info.loadIcon(packageManager));
                    preference.setSummary(key);
                    preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            Resources resources = getActivity().getResources();
                            AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
                            alertDialog.setTitle(R.string.remove_default_title);
                            alertDialog.setMessage(String.format(resources.getString(R.string.remove_default_message), label, host, host));
                            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, resources.getString(R.string.action_remove),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Settings.get().removeDefaultApp(host);
                                            configureDefaultAppsList();
                                        }
                                    });
                            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, resources.getString(R.string.action_cancel),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    });
                            alertDialog.show();
                            return true;
                        }
                    });
                    preferenceCategory.addPreference(preference);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } else {
            noticePreference.setSummary(R.string.preference_default_apps_notice_no_defaults_summary);
            preferenceCategory.addPreference(noticePreference);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MainApplication app = (MainApplication) getActivity().getApplicationContext();
        Bus bus = app.getBus();
        bus.register(this);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.prefs);

        PreferenceCategory generalCategory = (PreferenceCategory) findPreference("preference_category_general");

        mAutoContentDisplayPreference = findPreference(Settings.PREFERENCE_AUTO_CONTENT_DISPLAY_TYPE);
        mAutoContentDisplayPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog alertDialog = getAutoContentDisplayDialog();
                alertDialog.show();
                return true;
            }
        });
        updateAutoContentDisplayPreference();

        Preference incognitoButton = findPreference("preference_incognito");
        if (incognitoButton != null) {
            incognitoButton.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {

                    MainApplication app = (MainApplication) getActivity().getApplication();
                    Bus bus = app.getBus();
                    bus.post(new IncognitoModeChangedEvent((Boolean)newValue));

                    return true;
                }
            });
        }

        Preference crashButton = findPreference("debug_crash");
        if (crashButton != null) {
            crashButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    throw new RuntimeException("CRASH BUTTON PRESSED!");                }
            });
        }

        final Preference leftConsumeBubblePreference = findPreference(Settings.PREFERENCE_LEFT_CONSUME_BUBBLE);
        leftConsumeBubblePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog alertDialog = ActionItem.getConfigureBubbleAlert(getActivity(), new ActionItem.OnActionItemSelectedListener() {
                    @Override
                    public void onSelected(ActionItem actionItem) {
                        Settings.get().setConsumeBubble(Config.BubbleAction.ConsumeLeft, actionItem.mType, actionItem.getLabel(),
                                actionItem.mPackageName, actionItem.mActivityClassName);
                        leftConsumeBubblePreference.setSummary(Settings.get().getConsumeBubbleLabel(Config.BubbleAction.ConsumeLeft));
                    }
                });
                alertDialog.show();
                return true;
            }
        });
        leftConsumeBubblePreference.setSummary(Settings.get().getConsumeBubbleLabel(Config.BubbleAction.ConsumeLeft));

        final Preference rightConsumeBubblePreference = findPreference(Settings.PREFERENCE_RIGHT_CONSUME_BUBBLE);
        rightConsumeBubblePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog alertDialog = ActionItem.getConfigureBubbleAlert(getActivity(), new ActionItem.OnActionItemSelectedListener() {
                    @Override
                    public void onSelected(ActionItem actionItem) {
                        Settings.get().setConsumeBubble(Config.BubbleAction.ConsumeRight, actionItem.mType, actionItem.getLabel(),
                                actionItem.mPackageName, actionItem.mActivityClassName);
                        rightConsumeBubblePreference.setSummary(Settings.get().getConsumeBubbleLabel(Config.BubbleAction.ConsumeRight));
                    }
                });
                alertDialog.show();
                return true;
            }
        });
        rightConsumeBubblePreference.setSummary(Settings.get().getConsumeBubbleLabel(Config.BubbleAction.ConsumeRight));

        Preference defaultBrowserPreference = findPreference(Settings.PREFERENCE_DEFAULT_BROWSER);
        defaultBrowserPreference.setTitle(Settings.get().getDefaultBrowserLabel());
        Drawable defaultBrowserIcon = Settings.get().getDefaultBrowserIcon(getActivity());
        if (defaultBrowserIcon != null) {
            defaultBrowserPreference.setIcon(defaultBrowserIcon);
        }
        defaultBrowserPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                AlertDialog alertDialog = ActionItem.getDefaultBrowserAlert(getActivity(), new ActionItem.OnActionItemSelectedListener() {
                    @Override
                    public void onSelected(ActionItem actionItem) {
                        Settings.get().setDefaultBrowser(actionItem.getLabel(), actionItem.mPackageName);
                        preference.setTitle(Settings.get().getDefaultBrowserLabel());
                        Drawable defaultBrowserIcon = Settings.get().getDefaultBrowserIcon(getActivity());
                        if (defaultBrowserIcon != null) {
                            preference.setIcon(defaultBrowserIcon);
                        }
                    }
                });
                alertDialog.show();
                return true;
            }
        });

        configureDefaultAppsList();

        findPreference("preference_clear_browser_cache").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                return onClearBrowserCachePreferenceClick();
            }
        });

        Preference sayThanksPreference = findPreference("preference_say_thanks");
        if (sayThanksPreference != null) {
            sayThanksPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = Config.getStoreIntent(getActivity(), Config.STORE_PRO_URL);
                    if (intent != null) {
                        startActivity(intent);
                        Settings.get().setSayThanksClicked(true);
                        return true;
                    }
                    return false;
                }
            });
        }

        Preference getProPreference = findPreference("preference_get_pro");
        if (getProPreference != null) {
            getProPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = Config.getStoreIntent(getActivity(), Config.STORE_PRO_URL);
                    if (intent != null) {
                        startActivity(intent);
                        return true;
                    }
                    return false;
                }
            });
        }

        if (Constant.IS_LICENSED) {
            generalCategory.removePreference(getProPreference);
            if (Settings.get().getSayThanksClicked()) {
                generalCategory.removePreference(sayThanksPreference);
                PreferenceScreen helpScreen = (PreferenceScreen) getPreferenceScreen().findPreference("preference_screen_help");
                helpScreen.addPreference(sayThanksPreference);
            }
        } else {
            generalCategory.removePreference(sayThanksPreference);
        }

        findPreference("preference_credits").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                getCreditDialog().show();
                return true;
            }
        });
        

        findPreference("preference_faq").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FAQDialog dialog = new FAQDialog(getActivity());
                dialog.show();
                return true;
            }
        });

        findPreference("preference_osl").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                WebView webView = new WebView(getActivity());
                webView.loadUrl("file:///android_asset/open_source_licenses.html");
                webView.setWebViewClient(new WebViewClient() {
                    public boolean shouldOverrideUrlLoading(WebView view, String url){
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                        return true;
                    }
                });

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setIcon(0);
                builder.setNegativeButton(R.string.action_ok, null);
                builder.setView(webView);
                builder.setTitle(R.string.preference_osl_title);

                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                return true;
            }
        });

        findPreference("preference_privacy_policy").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                MainApplication.openLink(getActivity(), Constant.PRIVACY_POLICY_URL);
                return true;
            }
        });

        findPreference("preference_terms_of_service").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                MainApplication.openLink(getActivity(), Constant.TERMS_OF_SERVICE_URL);
                return true;
            }
        });

        Preference versionPreference = findPreference("preference_version");
        try {
            PackageInfo packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
            versionPreference.setTitle(getString(R.string.preference_version_title) + " " + packageInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
        }
        versionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                ChangeLogDialog changelogDialog = new ChangeLogDialog(getActivity());
                changelogDialog.show();
                //FAQDialog faqDialog = new FAQDialog(SettingsActivity.this);
                //faqDialog.show();
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        checkDefaultBrowser();
        configureDefaultAppsList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        MainApplication app = (MainApplication) getActivity().getApplicationContext();
        Bus bus = app.getBus();
        bus.unregister(this);
    }

    //@Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);

        //Settings.get().refresh(key);

        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }
    }

    void checkDefaultBrowser() {

        Preference setDefaultPreference = getPreferenceScreen().findPreference("preference_set_default_browser");
        // Will be null if onResume() is called after the preference has already been removed.
        if (setDefaultPreference != null) {
            if (Util.isDefaultBrowser(getActivity().getPackageName(), getActivity().getPackageManager())) {
                getPreferenceScreen().removePreference(setDefaultPreference);
            } else {
                setDefaultPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        // Via http://stackoverflow.com/a/13239706/328679
                        PackageManager packageManager = getActivity().getPackageManager();

                        ComponentName dummyComponentName = new ComponentName(getActivity().getApplication(),
                                                                DefaultBrowserResetActivity.class);
                        packageManager.setComponentEnabledSetting(dummyComponentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(Config.SET_DEFAULT_BROSWER_URL));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        getActivity().startActivity(intent);

                        packageManager.setComponentEnabledSetting(dummyComponentName, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
                        return true;
                    }
                });
            }
        }
    }

    AlertDialog getAutoContentDisplayDialog() {

        ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_multiple_choice);
        listAdapter.add(getString(R.string.preference_auto_content_display_app_redirect));
        listAdapter.add(getString(R.string.preference_auto_content_display_link_loaded));

        final ListView listView = new ListView(getActivity());
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(listAdapter);
        listView.setItemChecked(0, Settings.get().getAutoContentDisplayAppRedirect());
        listView.setItemChecked(1, Settings.get().getAutoContentDisplayLinkLoaded());

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(listView);
        builder.setIcon(0);
        builder.setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Settings.get().setAutoContentDisplayAppRedirect(listView.isItemChecked(0));
                Settings.get().setAutoContentDisplayLinkLoaded(listView.isItemChecked(1));
                updateAutoContentDisplayPreference();
            }
        });
        builder.setTitle(R.string.preference_auto_content_display_title);

        return builder.create();
    }

    void updateAutoContentDisplayPreference() {
        boolean appRedirect = Settings.get().getAutoContentDisplayAppRedirect();
        boolean linkLoaded = Settings.get().getAutoContentDisplayLinkLoaded();
        if (appRedirect && linkLoaded) {
            mAutoContentDisplayPreference.setSummary(R.string.preference_auto_content_display_always);
        } else if (appRedirect) {
            mAutoContentDisplayPreference.setSummary(R.string.preference_auto_content_display_app_redirect);
        } else if (linkLoaded) {
            mAutoContentDisplayPreference.setSummary(R.string.preference_auto_content_display_link_loaded);
        } else {
            mAutoContentDisplayPreference.setSummary(R.string.preference_auto_content_display_never);
        }
    }

    AlertDialog getCreditDialog() {
        final View layout = View.inflate(getActivity(), R.layout.view_credits, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIcon(0);
        builder.setNegativeButton(R.string.action_ok, null);
        builder.setView(layout);
        builder.setTitle(R.string.credits_title);

        AlertDialog alertDialog = builder.create();
        return alertDialog;
    }

    private boolean onClearBrowserCachePreferenceClick() {

        final String clearCache = getString(R.string.preference_clear_cache);
        final String clearCookies = getString(R.string.preference_clear_cookies);
        final String clearFavicons = getString(R.string.preference_clear_favicons);
        final String clearFormData = getString(R.string.preference_clear_form_data);
        final String clearHistory = getString(R.string.preference_clear_history);
        final String clearPasswords = getString(R.string.preference_clear_passwords);

        final ArrayList<String> items = new ArrayList<String>();
        items.add(clearCache);
        items.add(clearCookies);
        items.add(clearFavicons);
        items.add(clearFormData);
        items.add(clearHistory);
        items.add(clearPasswords);

        ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_multiple_choice, items);

        final ListView listView = new ListView(getActivity());
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(listAdapter);
        for (int i = 0; i < items.size(); i++) {
            listView.setItemChecked(i, items.get(i).equals(clearFavicons) ? false : true);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(listView);
        builder.setIcon(0);
        builder.setPositiveButton(R.string.action_clear_data, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

                WebView webView = new WebView(getActivity());
                WebViewDatabase webViewDatabase = WebViewDatabase.getInstance(getActivity().getApplicationContext());
                boolean dataCleared = false;
                int count = listView.getCount();
                for (int i = 0; i < count; i++) {
                    if (listView.isItemChecked(i)) {
                        String item = items.get(i);
                        if (item.equals(clearCache)) {
                            webView.clearCache(true);
                            dataCleared = true;
                        } else if (item.equals(clearCookies)) {
                            CookieManager cookieManager = CookieManager.getInstance();
                            if (cookieManager != null && cookieManager.hasCookies()) {
                                cookieManager.removeAllCookie();
                            }
                            dataCleared = true;
                        } else if (item.equals(clearFavicons)) {
                            MainApplication.sDatabaseHelper.deleteAllFavicons();
                            MainApplication.recreateFaviconCache();
                            dataCleared = true;
                        } else if (item.equals(clearFormData)) {
                            if (webViewDatabase != null) {
                                webViewDatabase.clearFormData();
                                dataCleared = true;
                            }
                        } else if (item.equals(clearHistory)) {
                            webView.clearHistory();
                            MainApplication.sDatabaseHelper.deleteAllHistoryRecords();
                            Settings.get().saveCurrentBubbles(null);
                            dataCleared = true;
                        } else if (item.equals(clearPasswords)) {
                            if (webViewDatabase != null) {
                                webViewDatabase.clearHttpAuthUsernamePassword();
                                webViewDatabase.clearUsernamePassword();
                                dataCleared = true;
                            }
                        }
                    }
                }

                if (dataCleared) {
                    boolean reloaded = false;

                    if (MainController.get() != null) {
                        MainController.get().closeAllBubbles(false);
                        final Vector<String> urls = Settings.get().loadCurrentBubbles();
                        if (urls.size() > 0) {
                            for (String url : urls) {
                                MainApplication.openLink(getActivity().getApplicationContext(), url);
                                reloaded = true;
                            }
                        }
                    }

                    Toast.makeText(getActivity(), reloaded ? R.string.private_data_cleared_reloading_current : R.string.private_data_cleared,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setTitle(R.string.preference_clear_browser_cache_title);

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        return true;
    }
}
