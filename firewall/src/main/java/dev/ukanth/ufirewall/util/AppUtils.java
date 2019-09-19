package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;

import com.cxsz.framework.tool.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import dev.ukanth.ufirewall.Api;

public class AppUtils {

    public AppUtils() {

    }

    private static final AppUtils appUtils = new AppUtils();

    public static AppUtils getInstance() {
        return appUtils;
    }

    private  final String FIX_START_LEAK = "fixLeak";
    private  final String REG_DO = "ipurchaseddonatekey";
    private  final String ENABLE_ROAM = "enableRoam";
    private  final String ENABLE_VPN = "enableVPN";
    private  final String ENABLE_LAN = "enableLAN";
    private  final String ENABLE_TOR = "enableTor";
    private  final String ENABLE_IPV6 = "enableIPv6";
    private  final String CONTROL_IPV6 = "controlIPv6";
    private  final String ENABLE_INBOUND = "enableInbound";
    private  final String ENABLE_LOG_SERVICE = "enableLogService";
    private  final String DUAL_APPS = "supportDualApps";
    private  final String ENABLE_MULTI_PROFILE = "enableMultiProfile";
    private  final String SHOW_UID = "showUid";
    private  final String DISABLE_ICONS = "disableIcons";
    private  final String IPTABLES_PATH = "ip_path";
    private  final String BUSYBOX_PATH = "bb_path";
    private  final String LANGUAGE = "locale";
    private  final String SORT_BY = "sort";
    private  final String LAST_STORED_PROFILE = "storedProfile";
    private  final String SYSTEM_APP_COLOR = "sysColor";
    private  final String ACTIVE_NOTIFICATION = "activeNotification";
    private  final String LOG_TARGET = "logTarget";
    private  final String APP_VERSION = "appVersion";
    private  final String DNS_PROXY = "dns_value";
    private  final String MULTI_USER = "multiUser";
    private  final String MULTI_USER_ID = "multiUserId";
    private  final String PATTERN_MAX_TRY = "patternMax";
    private  final String PATTERN_STEALTH = "stealthMode";
    private  final String NOTIFICATION_PRIORITY = "notification_priority";
    private  final String RUN_NOTIFICATION = "runNotification";

    private  final String THEME = "theme";
    /**
     * FIXME
     **/
    private  final String AFWALL_STATUS = "AFWallStaus";
    /* Profiles */
    private  final String ADDITIONAL_PROFILES = "plusprofiles";
    private  final String PROFILES_MIGRATED = "profilesmigrated";
    //ippreference
    private  final String IP4_INPUT = "input_chain";
    private  final String IP4_OUTPUT = "output_chain";
    private  final String IP4_FWD = "forward_chain";

    private  final String IP6_INPUT = "input_chain_v6";
    private  final String IP6_OUTPUT = "output_chain_v6";
    private  final String IP6_FWD = "forward_chain_v6";

    public  String[] default_profiles = {"AFWallProfile1", "AFWallProfile2", "AFWallProfile3"};
    public  SharedPreferences gPrefs;
    public  SharedPreferences pPrefs;
    public  SharedPreferences sPrefs;

    public  Set<String> storedPid() {
        return gPrefs.getStringSet("storedPid", null);
    }

    public  void storedPid(Set store) {
        gPrefs.edit().putStringSet("storedPid", store).commit();
    }

    public  boolean supportDual() {
        return gPrefs.getBoolean(DUAL_APPS, false);
    }

    public  boolean isRun() {
        return gPrefs.getBoolean(RUN_NOTIFICATION, true);
    }

    public  boolean ipv4Input() {
        return gPrefs.getBoolean(IP4_INPUT, true);
    }

    public  boolean ipv4Fwd() {
        return gPrefs.getBoolean(IP4_FWD, true);
    }

    public  boolean ipv4Output() {
        return gPrefs.getBoolean(IP4_OUTPUT, true);
    }

    public  boolean ipv6Fwd() {
        return gPrefs.getBoolean(IP6_FWD, true);
    }

    public  boolean ipv6Input() {
        return gPrefs.getBoolean(IP6_INPUT, true);
    }

    public  boolean ipv6Output() {
        return gPrefs.getBoolean(IP6_OUTPUT, true);
    }

    public  String getSelectedTheme() {
        return gPrefs.getString(THEME, "D");
    }

    public  int getNotificationPriority() {
        return Integer.parseInt(gPrefs.getString(NOTIFICATION_PRIORITY, "0"));
    }

    public  boolean isProfileMigrated() {
        return gPrefs.getBoolean(PROFILES_MIGRATED, false);
    }

    public  boolean isProfileMigrated(boolean val) {
        gPrefs.edit().putBoolean(PROFILES_MIGRATED, val).commit();
        return val;
    }

    public  boolean activeNotification() {
        return gPrefs.getBoolean(ACTIVE_NOTIFICATION, true);
    }

    public  boolean fixLeak() {
        return gPrefs.getBoolean(FIX_START_LEAK, false);
    }

    public  boolean enableIPv6() {
        return gPrefs.getBoolean(ENABLE_IPV6, true);
    }

    public  boolean controlIPv6() {
        return gPrefs.getBoolean(CONTROL_IPV6, false);
    }

    public  boolean enableInbound() {
        return gPrefs.getBoolean(ENABLE_INBOUND, false);
    }

    public  boolean enableLogService() {
        return gPrefs.getBoolean(ENABLE_LOG_SERVICE, false);
    }

    public  boolean enableMultiProfile() {
        return gPrefs.getBoolean(ENABLE_MULTI_PROFILE, false);
    }

    public  boolean showUid() {
        return gPrefs.getBoolean(SHOW_UID, false);
    }

    public  boolean disableIcons() {
        return gPrefs.getBoolean(DISABLE_ICONS, false);
    }

    public  String ip_path() {
        return gPrefs.getString(IPTABLES_PATH, "auto");
    }

    public  String dns_proxy() {
        return gPrefs.getString(DNS_PROXY, "auto");
    }

    public  String bb_path() {
        return gPrefs.getString(BUSYBOX_PATH, "builtin");
    }

    public  String locale() {
        return PreferenceManager.getDefaultSharedPreferences(AppUtils.getInstance().getContext()).getString(LANGUAGE, "en");
    }

    public  String sortBy() {
        return gPrefs.getString(SORT_BY, "s0");
    }

    public  String storedProfile() {
        return gPrefs.getString(LAST_STORED_PROFILE, "AFWallPrefs");
    }

    public  int userColor() {
        if (getSelectedTheme().equals("L")) {
            return Color.parseColor("#000000");
        } else {
            return Color.parseColor("#FFFFFF");
        }
    }

    public  int sysColor() {
        if (getSelectedTheme().equals("L")) {
            return gPrefs.getInt(SYSTEM_APP_COLOR, Color.parseColor("#000000"));
        } else {
            return gPrefs.getInt(SYSTEM_APP_COLOR, Color.parseColor("#0F9D58"));
        }
    }

    public  boolean enableStealthPattern() {
        return gPrefs.getBoolean(PATTERN_STEALTH, false);
    }

    public  int getMaxPatternTry() {
        return Integer.parseInt(gPrefs.getString(PATTERN_MAX_TRY, "3"));
    }

    public  boolean isMultiUser() {
        return gPrefs.getBoolean(MULTI_USER, false);
    }

    public  Long getMultiUserId() {
        return gPrefs.getLong(MULTI_USER_ID, 0);
    }

    public  String logTarget() {
        return gPrefs.getString(LOG_TARGET, "");
    }

    public  int appVersion() {
        return gPrefs.getInt(APP_VERSION, 0);
    }

    public  int appVersion(int val) {
        gPrefs.edit().putInt(APP_VERSION, val).commit();
        return val;
    }

    public  boolean isDo(boolean val) {
        gPrefs.edit().putBoolean(REG_DO, val).commit();
        return val;
    }

    public  boolean enableRoam() {
        return gPrefs.getBoolean(ENABLE_ROAM, false);
    }

    public  boolean enableVPN() {
        return gPrefs.getBoolean(ENABLE_VPN, false);
    }

    public  boolean enableLAN() {
        return gPrefs.getBoolean(ENABLE_LAN, true);
    }

    public  boolean enableTor() {
        return gPrefs.getBoolean(ENABLE_TOR, false);
    }

    public  void reloadPrefs() {
        gPrefs = PreferenceManager.getDefaultSharedPreferences(AppUtils.getInstance().getContext());
        String profileName = Api.DEFAULT_PREFS_NAME;
        if (enableMultiProfile()) {
            profileName = storedProfile();
        }

        LogUtil.setTagI(Api.TAG, "Selected Profile: " + profileName);
        Api.PREFS_NAME = profileName;

        pPrefs = AppUtils.getInstance().getContext().getSharedPreferences(profileName, Context.MODE_PRIVATE);
        sPrefs = AppUtils.getInstance().getContext().getSharedPreferences(AFWALL_STATUS/* sic */, Context.MODE_PRIVATE);
    }

    public  List<String> getAdditionalProfiles() {
        String previousProfiles = gPrefs.getString(ADDITIONAL_PROFILES, "");
        List<String> items = new ArrayList<>();
        if (!previousProfiles.isEmpty()) {
            items = new ArrayList<String>(Arrays.asList(previousProfiles.split("\\s*,\\s*")));
        }
        return items;
    }

    public  List<String> getDefaultProfiles() {
        List<String> items = new ArrayList<String>(Arrays.asList(default_profiles));
        return items;
    }

    private Context context;

    public  Context getContext() {
        return context;
    }

    public void init(Context context) {
        this.context = context;
    }
}
