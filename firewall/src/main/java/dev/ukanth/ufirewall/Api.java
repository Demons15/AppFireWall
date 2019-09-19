package dev.ukanth.ufirewall;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.cxsz.framework.tool.LogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import dev.ukanth.ufirewall.FireWallActivity.GetAppList;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.AppUtils;

/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 */
public final class Api {
    /**
     * application logcat tag
     */
    public static final String TAG = "AFWall";

    /**
     * special application UID used to indicate "any application"
     */
    public static final int SPECIAL_UID_ANY = -10;
    /**
     * special application UID used to indicate the Linux Kernel
     */
    public static final int SPECIAL_UID_KERNEL = -11;
    /**
     * special application UID used for dnsmasq DHCP/DNS
     */
    public static final int SPECIAL_UID_TETHER = -12;
    /** special application UID used for netd DNS proxy */
    /**
     * special application UID used for NTP
     */
    public static final int SPECIAL_UID_NTP = -14;

    public static final int NOTIFICATION_ID = 1;
    public static final String PREF_FIREWALL_STATUS = "AFWallStaus";
    public static final String DEFAULT_PREFS_NAME = "AFWallPrefs";
    //revertback to old approach for performance
    public static final String PREF_3G_PKG_UIDS = "AllowedPKG3G_UIDS";
    public static final String PREF_WIFI_PKG_UIDS = "AllowedPKGWifi_UIDS";
    public static final String PREF_ROAMING_PKG_UIDS = "AllowedPKGRoaming_UIDS";
    public static final String PREF_VPN_PKG_UIDS = "AllowedPKGVPN_UIDS";
    public static final String PREF_LAN_PKG_UIDS = "AllowedPKGLAN_UIDS";
    public static final String PREF_TOR_PKG_UIDS = "AllowedPKGTOR_UIDS";
    public static final String PREF_CUSTOMSCRIPT = "CustomScript";
    public static final String PREF_MODE = "BlockMode";
    public static final String PREF_ENABLED = "Enabled";
    // Modes
    public static final String MODE_WHITELIST = "whitelist";
    public static final int ERROR_NOTIFICATION_ID = 9;
    private static final String ITFS_WIFI[] = InterfaceTracker.ITFS_WIFI;
    private static final String ITFS_3G[] = InterfaceTracker.ITFS_3G;
    private static final String ITFS_VPN[] = InterfaceTracker.ITFS_VPN;
    // iptables can exit with status 4 if two processes tried to update the same table
    private static final int IPTABLES_TRY_AGAIN = 4;
    private static final String dynChains[] = {"-3g-postcustom", "-3g-fork", "-wifi-postcustom", "-wifi-fork"};
    private static final String natChains[] = {"", "-tor-check", "-tor-filter"};
    private static final String staticChains[] = {"", "-input", "-3g", "-wifi", "-reject", "-vpn", "-3g-tether", "-3g-home", "-3g-roam", "-wifi-tether", "-wifi-wan", "-wifi-lan", "-tor", "-tor-reject"};
    /**
     * @brief Special user/group IDs that aren't associated with
     * any particular app.
     * <p>
     * See:
     * include/private/android_filesystem_config.h
     * in platform/system/core.git.
     * <p>
     * The accounts listed below are the only ones from
     * android_filesystem_config.h that are known to be used as
     * the UID of a process that uses the network.  The other
     * accounts in that .h file are either:
     * * used as supplemental group IDs for granting extra
     * privileges to apps,
     * * used as UIDs of processes that don't need the network,
     * or
     * * have not yet been reported by users as needing the
     * network.
     * <p>
     * The list is sorted in ascending UID order.
     */
    private static final String[] specialAndroidAccounts = {
            "root",
            "adb",
            "media",
            "vpn",
            "drm",
            "gps",
            "shell",
    };
    private static final Pattern p = Pattern.compile("UserHandle\\{(.*)\\}");
    // Preferences
    public static String PREFS_NAME = "AFWallPrefs";
    // Cached applications
    public static List<PackageInfoData> applications = null;
    public static Set<String> recentlyInstalled = new HashSet<>();
    //for custom scripts
    public static String bbPath = null;
    private static String AFWALL_CHAIN_NAME = "afwall";
    private static Map<String, Integer> specialApps = null;
    private static boolean rulesUpToDate = false;

    public static void setRulesUpToDate(boolean rulesUpToDate) {
        Api.rulesUpToDate = rulesUpToDate;
    }

    public static String getSpecialDescription(Context ctx, String acct) {
        int rid = ctx.getResources().getIdentifier(acct + "_item", "string", ctx.getPackageName());
        return ctx.getString(rid);
    }

    /**
     * Display a simple alert box
     *
     * @param ctx     context
     * @param msgText message
     */
    public static void toast(final Context ctx, final CharSequence msgText) {
        if (ctx != null) {
            Handler mHandler = new Handler(Looper.getMainLooper());
            mHandler.post(() -> Toast.makeText(AppUtils.getInstance().getContext(), msgText, Toast.LENGTH_SHORT).show());
        }
    }

    public static String getBinaryPath(Context ctx, boolean setv6) {
        boolean builtin = true;
        String pref = AppUtils.getInstance().ip_path();

        if (pref.equals("system") || !setv6) {
            builtin = false;
        }

        String dir = "";
        if (builtin) {
            dir = ctx.getDir("bin", 0).getAbsolutePath() + "/";
        }
        String ipPath = dir + (setv6 ? "ip6tables" : "iptables");
        if (Api.bbPath == null) {
            Api.bbPath = getBusyBoxPath(ctx, true);
        }
        return ipPath;
    }

    /**
     * Determine toybox/busybox or built in
     *
     * @param ctx
     * @param considerSystem
     * @return
     */
    public static String getBusyBoxPath(Context ctx, boolean considerSystem) {

        if (AppUtils.getInstance().bb_path().equals("system") && considerSystem) {
            return "busybox ";
        } else {
            String dir = ctx.getDir("bin", 0).getAbsolutePath();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                return dir + "/run_pie " + dir + "/busybox ";
            } else {
                return dir + "/busybox ";
            }
        }
    }

    /**
     * Copies a raw resource file, given its ID to the given location
     *
     * @param ctx   context
     * @param resid resource id
     * @param file  destination file
     * @param mode  file permissions (E.g.: "755")
     * @throws IOException          on error
     * @throws InterruptedException when interrupted
     */
    private static void copyRawFile(Context ctx, int resid, File file, String mode) throws IOException, InterruptedException {
        final String abspath = file.getAbsolutePath();
        // Write the iptables binary
        final FileOutputStream out = new FileOutputStream(file);
        final InputStream is = ctx.getResources().openRawResource(resid);
        byte buf[] = new byte[1024];
        int len;
        while ((len = is.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.close();
        is.close();
        // Change the permissions

        Runtime.getRuntime().exec("chmod " + mode + " " + abspath).waitFor();
    }

    /**
     * Look up uid for each user by name, and if he exists, append an iptables rule.
     *
     * @param listCommands current list of iptables commands to execute
     * @param users        list of users to whom the rule applies
     * @param prefix       "iptables" command and the portion of the rule preceding "-m owner --uid-owner X"
     * @param suffix       the remainder of the iptables rule, following "-m owner --uid-owner X"
     */
    private static void addRuleForUsers(List<String> listCommands, String users[], String prefix, String suffix) {
        for (String user : users) {
            int uid = android.os.Process.getUidForName(user);
            if (uid != -1)
                listCommands.add(prefix + " -m owner --uid-owner " + uid + " " + suffix);
        }
    }

    private static void addRulesForUidlist(List<String> cmds, List<Integer> uids, String chain, boolean whitelist) {
        String action = whitelist ? " -j RETURN" : " -j " + AFWALL_CHAIN_NAME + "-reject";

        if (uids.indexOf(SPECIAL_UID_ANY) >= 0) {
            if (!whitelist) {
                cmds.add("-A " + chain + action);
            }
            // FIXME: in whitelist mode this blocks everything
        } else {
            for (Integer uid : uids) {
                if (uid != null && uid >= 0) {
                    cmds.add("-A " + chain + " -m owner --uid-owner " + uid + action);
                }
            }

            String pref = AppUtils.getInstance().dns_proxy();

            if (whitelist) {
                if (pref.equals("disable")) {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
                } else {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j RETURN");
                }
            } else {
                if (pref.equals("disable")) {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j " + AFWALL_CHAIN_NAME + "-reject");
                } else if (pref.equals("enable")) {
                    addRuleForUsers(cmds, new String[]{"root"}, "-A " + chain + " -p udp --dport 53", " -j RETURN");
                }
            }


            // NTP service runs as "system" user
            if (uids.indexOf(SPECIAL_UID_NTP) >= 0) {
                addRuleForUsers(cmds, new String[]{"system"}, "-A " + chain + " -p udp --dport 123", action);
            }

            boolean kernel_checked = uids.indexOf(SPECIAL_UID_KERNEL) >= 0;
            if (whitelist) {
                if (kernel_checked) {
                    // reject any other UIDs, but allow the kernel through
                    cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j " + AFWALL_CHAIN_NAME + "-reject");
                } else {
                    // kernel is blocked so reject everything
                    cmds.add("-A " + chain + " -j " + AFWALL_CHAIN_NAME + "-reject");
                }
            } else {
                if (kernel_checked) {
                    // allow any other UIDs, but block the kernel
                    cmds.add("-A " + chain + " -m owner --uid-owner 0:999999999 -j RETURN");
                    cmds.add("-A " + chain + " -j " + AFWALL_CHAIN_NAME + "-reject");
                }
            }
        }
    }

    private static void addRejectRules(List<String> cmds) {
        // set up reject chain to log or not log
        // this can be changed dynamically through the Firewall Logs activity

        if (AppUtils.getInstance().enableLogService() && AppUtils.getInstance().logTarget() != null) {
            if (AppUtils.getInstance().logTarget().equals("LOG")) {
                //cmds.add("-A " + AFWALL_CHAIN_NAME  + " -m limit --limit 1000/min -j LOG --log-prefix \"{AFL-ALLOW}\" --log-level 4 --log-uid");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -m limit --limit 1000/min -j LOG --log-prefix \"{AFL}\" --log-level 4 --log-uid");
            } else if (AppUtils.getInstance().logTarget().equals("NFLOG")) {
                //cmds.add("-A " + AFWALL_CHAIN_NAME + " -j NFLOG --nflog-prefix \"{AFL-ALLOW}\" --nflog-group 40");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -j NFLOG --nflog-prefix \"{AFL}\" --nflog-group 40");
            }
        }
        cmds.add("-A " + AFWALL_CHAIN_NAME + "-reject" + " -j REJECT");
    }

    private static void addTorRules(List<String> cmds, List<Integer> uids, Boolean whitelist, Boolean ipv6) {
        for (Integer uid : uids) {
            if (uid != null && uid >= 0) {
                if (AppUtils.getInstance().enableInbound() || ipv6) {
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-tor-reject -m owner --uid-owner " + uid + " -j afwall-reject");
                }
                if (!ipv6) {
                    cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-check -m owner --uid-owner " + uid + " -j " + AFWALL_CHAIN_NAME + "-tor-filter");
                }
            }
        }
        if (ipv6) {
            cmds.add("-A " + AFWALL_CHAIN_NAME + " -j " + AFWALL_CHAIN_NAME + "-tor-reject");
        } else {
            Integer socks_port = 9050;
            Integer http_port = 8118;
            Integer dns_port = 5400;
            Integer tcp_port = 9040;
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -d 127.0.0.1 -p tcp --dport " + socks_port + " -j RETURN");
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -d 127.0.0.1 -p tcp --dport " + http_port + " -j RETURN");
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -p udp --dport 53 -j REDIRECT --to-ports " + dns_port);
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -p tcp --tcp-flags FIN,SYN,RST,ACK SYN -j REDIRECT --to-ports " + tcp_port);
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + "-tor-filter -j MARK --set-mark 0x500");
            cmds.add("-t nat -A " + AFWALL_CHAIN_NAME + " -j " + AFWALL_CHAIN_NAME + "-tor-check");
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-tor -m mark --mark 0x500 -j afwall-reject");
            cmds.add("-A " + AFWALL_CHAIN_NAME + " -j " + AFWALL_CHAIN_NAME + "-tor");
        }
        if (AppUtils.getInstance().enableInbound()) {
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-input -j " + AFWALL_CHAIN_NAME + "-tor-reject");
        }
    }

    private static void addCustomRules(String prefName, List<String> cmds) {
        String[] customRules = AppUtils.getInstance().pPrefs.getString(prefName, "").split("[\\r\\n]+");
        for (String s : customRules) {
            if (s.matches(".*\\S.*")) {
                cmds.add("#LITERAL# " + s);
            }
        }
    }

    /**
     * Reconfigure the firewall rules based on interface changes seen at runtime: tethering
     * enabled/disabled, IP address changes, etc.  This should only affect a small number of
     * rules; we want to avoid calling applyIptablesRulesImpl() too often since applying
     * 100+ rules is expensive.
     *
     * @param ctx  application context
     * @param cmds command list
     */
    private static void addInterfaceRouting(Context ctx, List<String> cmds, boolean ipv6) {
        try {
            final InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(ctx, true);
            final boolean whitelist = AppUtils.getInstance().pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);
            for (String s : dynChains) {
                cmds.add("-F " + AFWALL_CHAIN_NAME + s);
            }

            if (whitelist) {
                // always allow the DHCP client full wifi access
                addRuleForUsers(cmds, new String[]{"dhcp", "wifi"}, "-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom", "-j RETURN");
            }

            if (cfg.isTethered) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom -j " + AFWALL_CHAIN_NAME + "-wifi-tether");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-postcustom -j " + AFWALL_CHAIN_NAME + "-3g-tether");
            } else {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-postcustom -j " + AFWALL_CHAIN_NAME + "-wifi-fork");
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-postcustom -j " + AFWALL_CHAIN_NAME + "-3g-fork");
            }

            if (AppUtils.getInstance().enableLAN() && !cfg.isTethered) {
                if (ipv6) {
                    if (!cfg.lanMaskV6.equals("")) {
                        LogUtil.setTagI(TAG, "ipv6 found: " + AppUtils.getInstance().enableIPv6() + "," + cfg.lanMaskV6);
                        cmds.add("-A afwall-wifi-fork -d " + cfg.lanMaskV6 + " -j afwall-wifi-lan");
                        cmds.add("-A afwall-wifi-fork '!' -d " + cfg.lanMaskV6 + " -j afwall-wifi-wan");
                    } else {
                        LogUtil.setTagI(TAG, "no ipv6 found: " + AppUtils.getInstance().enableIPv6() + "," + cfg.lanMaskV6);
                    }
                } else {
                    if (!cfg.lanMaskV4.equals("")) {
                        LogUtil.setTagI(TAG, "ipv4 found:true," + cfg.lanMaskV4);
                        cmds.add("-A afwall-wifi-fork -d " + cfg.lanMaskV4 + " -j afwall-wifi-lan");
                        cmds.add("-A afwall-wifi-fork '!' -d " + cfg.lanMaskV4 + " -j afwall-wifi-wan");
                    } else {
                        LogUtil.setTagI(TAG, "no ipv4 found:" + AppUtils.getInstance().enableIPv6() + "," + cfg.lanMaskV4);

                    }
                }
                if (cfg.lanMaskV4.equals("") && cfg.lanMaskV6.equals("")) {
                    LogUtil.setTagI(TAG, "No ipaddress found for LAN");
                    // lets find one more time
                    //atleast allow internet - don't block completely
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -j " + AFWALL_CHAIN_NAME + "-wifi-wan");
                }
            } else {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-fork -j " + AFWALL_CHAIN_NAME + "-wifi-wan");
            }

            if (AppUtils.getInstance().enableRoam() && cfg.isRoaming) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-fork -j " + AFWALL_CHAIN_NAME + "-3g-roam");
            } else {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-fork -j " + AFWALL_CHAIN_NAME + "-3g-home");
            }
        } catch (Exception e) {
            LogUtil.setTagI(TAG, "Exception while applying shortRules " + e.getMessage());
        }

    }

    private static void applyShortRules(Context ctx, List<String> cmds, boolean ipv6) {
        LogUtil.setTagI(TAG, "Setting OUTPUT chain to DROP");
        cmds.add("-P OUTPUT DROP");
        addInterfaceRouting(ctx, cmds, ipv6);
        LogUtil.setTagI(TAG, "Setting OUTPUT chain to ACCEPT");
        cmds.add("-P OUTPUT ACCEPT");
    }


    /**
     * Purge and re-add all rules (internal implementation).
     *
     * @param ctx        application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     */
    private static boolean applyIptablesRulesImpl(final Context ctx, RuleDataSet ruleDataSet, final boolean showErrors, List<String> out, boolean ipv6) {
        if (ctx == null) {
            return false;
        }

        assertBinaries(ctx, showErrors);
        if (AppUtils.getInstance().isMultiUser()) {
            //FIXME: after setting this, we need to flush the iptables ?
            if (AppUtils.getInstance().getMultiUserId() > 0) {
                AFWALL_CHAIN_NAME = "afwall" + AppUtils.getInstance().getMultiUserId();
            }
        }
        final boolean whitelist = AppUtils.getInstance().pPrefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);

        List<String> cmds = new ArrayList<String>();

        //check before make them ACCEPT state
        if (AppUtils.getInstance().ipv4Input() || (ipv6 && AppUtils.getInstance().ipv6Input())) {
            cmds.add("-P INPUT ACCEPT");
        }

        if (AppUtils.getInstance().ipv4Fwd() || (ipv6 && AppUtils.getInstance().ipv6Fwd())) {
            cmds.add("-P FORWARD ACCEPT");
        }

        try {
            // prevent data leaks due to incomplete rules
            LogUtil.setTagI(TAG, "Setting OUTPUT to Drop");
            cmds.add("-P OUTPUT DROP");

            for (String s : staticChains) {
                cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + s);
                cmds.add("-F " + AFWALL_CHAIN_NAME + s);
            }
            for (String s : dynChains) {
                cmds.add("#NOCHK# -N " + AFWALL_CHAIN_NAME + s);
            }

            cmds.add("#NOCHK# -D OUTPUT -j " + AFWALL_CHAIN_NAME);
            cmds.add("-I OUTPUT 1 -j " + AFWALL_CHAIN_NAME);

            if (AppUtils.getInstance().enableInbound()) {
                cmds.add("#NOCHK# -D INPUT -j " + AFWALL_CHAIN_NAME + "-input");
                cmds.add("-I INPUT 1 -j " + AFWALL_CHAIN_NAME + "-input");
            }

            if (AppUtils.getInstance().enableTor()) {
                if (!ipv6) {
                    for (String s : natChains) {
                        cmds.add("#NOCHK# -t nat -N " + AFWALL_CHAIN_NAME + s);
                        cmds.add("-t nat -F " + AFWALL_CHAIN_NAME + s);
                    }
                    cmds.add("#NOCHK# -t nat -D OUTPUT -j " + AFWALL_CHAIN_NAME);
                    cmds.add("-t nat -I OUTPUT 1 -j " + AFWALL_CHAIN_NAME);
                }
            }

            // custom rules in afwall-{3g,wifi,reject} supersede everything else
            addCustomRules(Api.PREF_CUSTOMSCRIPT, cmds);
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g -j " + AFWALL_CHAIN_NAME + "-3g-postcustom");
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi -j " + AFWALL_CHAIN_NAME + "-wifi-postcustom");
            addRejectRules(cmds);

            if (AppUtils.getInstance().enableInbound()) {
                // we don't have any rules in the INPUT chain prohibiting inbound traffic, but
                // local processes can't reply to half-open connections without this rule
                cmds.add("-A afwall -m state --state ESTABLISHED -j RETURN");
                cmds.add("-A afwall-input -m state --state ESTABLISHED -j RETURN");
            }

            LogUtil.setTagI(TAG, "Callin interface routing for " + AppUtils.getInstance().enableIPv6());
            addInterfaceRouting(ctx, cmds, ipv6);

            // send wifi, 3G, VPN packets to the appropriate dynamic chain based on interface
            if (AppUtils.getInstance().enableVPN()) {
                // if !enableVPN then we ignore those interfaces (pass all traffic)
                for (final String itf : ITFS_VPN) {
                    cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-vpn");
                }
                // KitKat policy based routing - see:
                // http://forum.xda-developers.com/showthread.php?p=48703545
                // This covers mark range 0x3c - 0x47.  The official range is believed to be
                // 0x3c - 0x45 but this is close enough.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    cmds.add("-A " + AFWALL_CHAIN_NAME + " -m mark --mark 0x3c/0xfffc -g " + AFWALL_CHAIN_NAME + "-vpn");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + " -m mark --mark 0x40/0xfff8 -g " + AFWALL_CHAIN_NAME + "-vpn");
                }
            }
            for (final String itf : ITFS_WIFI) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-wifi");
            }

            for (final String itf : ITFS_3G) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + " -o " + itf + " -j " + AFWALL_CHAIN_NAME + "-3g");
            }

            final boolean any_wifi = ruleDataSet.wifiList.indexOf(SPECIAL_UID_ANY) >= 0;
            final boolean any_3g = ruleDataSet.dataList.indexOf(SPECIAL_UID_ANY) >= 0;

            // special rules to allow 3G<->wifi tethering
            // note that this can only blacklist DNS/DHCP services, not all tethered traffic
            if (((!whitelist && (any_wifi || any_3g)) ||
                    (ruleDataSet.dataList.indexOf(SPECIAL_UID_TETHER) >= 0) || (ruleDataSet.wifiList.indexOf(SPECIAL_UID_TETHER) >= 0))) {

                String users[] = {"root", "nobody"};
                String action = " -j " + (whitelist ? "RETURN" : AFWALL_CHAIN_NAME + "-reject");

                // DHCP replies to client
                addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p udp --sport=67 --dport=68" + action);

                // DNS replies to client
                addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p udp --sport=53" + action);
                addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-wifi-tether", "-p tcp --sport=53" + action);

                // DNS requests to upstream servers
                addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-3g-tether", "-p udp --dport=53" + action);
                addRuleForUsers(cmds, users, "-A " + AFWALL_CHAIN_NAME + "-3g-tether", "-p tcp --dport=53" + action);
            }

            // if tethered, try to match the above rules (if enabled).  no match -> fall through to the
            // normal 3G/wifi rules
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-tether -j " + AFWALL_CHAIN_NAME + "-wifi-fork");
            cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-tether -j " + AFWALL_CHAIN_NAME + "-3g-fork");

            // NOTE: we still need to open a hole to let WAN-only UIDs talk to a DNS server
            // on the LAN
            if (whitelist) {
                cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-lan -p udp --dport 53 -j RETURN");
                //bug fix allow dns to be open on Pie for all connection type
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-wifi-wan" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-home" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-3g-roam" + " -p udp --dport 53" + " -j RETURN");
                    cmds.add("-A " + AFWALL_CHAIN_NAME + "-vpn" + " -p udp --dport 53" + " -j RETURN");
                }
            }

            // now add the per-uid rules for 3G home, 3G roam, wifi WAN, wifi LAN, VPN
            // in whitelist mode the last rule in the list routes everything else to afwall-reject
            addRulesForUidlist(cmds, ruleDataSet.dataList, AFWALL_CHAIN_NAME + "-3g-home", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.roamList, AFWALL_CHAIN_NAME + "-3g-roam", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.wifiList, AFWALL_CHAIN_NAME + "-wifi-wan", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.lanList, AFWALL_CHAIN_NAME + "-wifi-lan", whitelist);
            addRulesForUidlist(cmds, ruleDataSet.vpnList, AFWALL_CHAIN_NAME + "-vpn", whitelist);


            if (AppUtils.getInstance().enableTor()) {
                addTorRules(cmds, ruleDataSet.torList, whitelist, ipv6);
            }

            LogUtil.setTagI(TAG, "Setting OUTPUT to Accept State");
            cmds.add("-P OUTPUT ACCEPT");

        } catch (Exception e) {
            LogUtil.setTagE(e.getClass().getName(), e.getMessage());
        }

        iptablesCommands(cmds, out, ipv6);
        return true;
    }

    /**
     * Add the repetitive parts (ipPath and such) to an iptables command list
     *
     * @param in  Commands in the format: "-A foo ...", "#NOCHK# -A foo ...", or "#LITERAL# <UNIX command>"
     * @param out A list of UNIX commands to execute
     */
    private static void iptablesCommands(List<String> in, List<String> out, boolean ipv6) {
        String ipPath = getBinaryPath(AppUtils.getInstance().getContext(), ipv6);

        boolean firstLit = true;
        for (String s : in) {
            if (s.matches("#LITERAL# .*")) {
                if (firstLit) {
                    // export vars for the benefit of custom scripts
                    // "true" is a dummy command which needs to return success
                    firstLit = false;
                    out.add("export IPTABLES=\"" + ipPath + "\"; "
                            + "export BUSYBOX=\"" + bbPath + "\"; "
                            + "export IPV6=" + (ipv6 ? "1" : "0") + "; "
                            + "true");
                }
                out.add(s.replaceFirst("^#LITERAL# ", ""));
            } else if (s.matches("#NOCHK# .*")) {
                out.add(s.replaceFirst("^#NOCHK# ", "#NOCHK# " + ipPath + " "));
            } else {
                out.add(ipPath + " " + s);
            }
        }
    }

    public static boolean applySavedIptablesRules(Context ctx, boolean showErrors, RootCommand callback) {
        LogUtil.setTagI(TAG, "Using applySavedIptablesRules");
        if (ctx == null) {
            return false;
        }
        RuleDataSet dataSet = getDataSet();
        boolean[] applied = {false, false};

        List<String> ipv4cmds = new ArrayList<String>();
        List<String> ipv6cmds = new ArrayList<String>();
        applyIptablesRulesImpl(ctx, dataSet, showErrors, ipv4cmds, false);

        Thread t1 = new Thread(() -> {
            applied[0] = applySavedIp4tablesRules(ctx, ipv4cmds, showErrors, callback);
        });

        if (AppUtils.getInstance().enableIPv6()) {
            applyIptablesRulesImpl(ctx, dataSet, showErrors, ipv6cmds, true);
        }
        Thread t2 = new Thread(() -> {
            //creare new callback command
            applySavedIp6tablesRules(ctx, ipv6cmds, showErrors, new RootCommand().
                    setIsv6(true).
                    setCallback(new RootCommand.Callback() {
                        @Override
                        public void cbFunc(RootCommand state) {
                            if (state.exitCode == 0) {
                                //ipv6 also applied properly
                                applied[1] = true;
                            }
                        }
                    }));
        });
        t1.start();
        if (AppUtils.getInstance().enableIPv6()) {
            t2.start();
        }
        try {
            t1.join();
            if (AppUtils.getInstance().enableIPv6()) {
                t2.join();
            }
        } catch (InterruptedException e) {
        }
        boolean returnValue = AppUtils.getInstance().enableIPv6() ? (applied[0] && applied[1]) : applied[0];
        rulesUpToDate = true;
        return returnValue;
    }

    private static RuleDataSet getDataSet() {
        initSpecial();

        final String savedPkg_wifi_uid = AppUtils.getInstance().pPrefs.getString(PREF_WIFI_PKG_UIDS, "");
        final String savedPkg_3g_uid = AppUtils.getInstance().pPrefs.getString(PREF_3G_PKG_UIDS, "");
        final String savedPkg_roam_uid = AppUtils.getInstance().pPrefs.getString(PREF_ROAMING_PKG_UIDS, "");
        final String savedPkg_vpn_uid = AppUtils.getInstance().pPrefs.getString(PREF_VPN_PKG_UIDS, "");
        final String savedPkg_lan_uid = AppUtils.getInstance().pPrefs.getString(PREF_LAN_PKG_UIDS, "");
        final String savedPkg_tor_uid = AppUtils.getInstance().pPrefs.getString(PREF_TOR_PKG_UIDS, "");

        RuleDataSet dataSet = new RuleDataSet(getListFromPref(savedPkg_wifi_uid),
                getListFromPref(savedPkg_3g_uid),
                getListFromPref(savedPkg_roam_uid),
                getListFromPref(savedPkg_vpn_uid),
                getListFromPref(savedPkg_lan_uid),
                getListFromPref(savedPkg_tor_uid));

        return dataSet;

    }

    /**
     * Purge and re-add all saved rules (not in-memory ones).
     * This is much faster than just calling "applyIptablesRules", since it don't need to read installed applications.
     *
     * @param ctx        application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     * @param callback   If non-null, use a callback instead of blocking the current thread
     */
    public static boolean applySavedIp4tablesRules(Context ctx, List<String> cmds, boolean showErrors, RootCommand callback) {
        if (ctx == null) {
            return false;
        }
        try {
            LogUtil.setTagI(TAG, "Using applySaved4IptablesRules");
            callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, cmds);
            return true;
        } catch (Exception e) {
            LogUtil.setTagI(TAG, "Exception while applying rules: " + e.getMessage());
            applyDefaultChains(ctx, callback);
            return false;
        }
    }


    public static boolean applySavedIp6tablesRules(Context ctx, List<String> cmds, boolean showErrors, RootCommand callback) {
        if (ctx == null) {
            return false;
        }
        try {
            LogUtil.setTagI(TAG, "Using applySavedIp6tablesRules");
            callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, cmds);
            return true;
        } catch (Exception e) {
            LogUtil.setTagI(TAG, "Exception while applying rules: " + e.getMessage());
            applyDefaultChains(ctx, callback);
            return false;
        }
    }


    public static boolean fastApply(Context ctx, RootCommand callback) {
        try {
            if (!rulesUpToDate) {
                LogUtil.setTagI(TAG, "Using full Apply");
                return applySavedIptablesRules(ctx, true, callback);
            } else {
                LogUtil.setTagI(TAG, "Using fastApply");
                List<String> out = new ArrayList<String>();
                List<String> cmds;
                cmds = new ArrayList<String>();
                applyShortRules(ctx, cmds, false);
                iptablesCommands(cmds, out, false);
                if (AppUtils.getInstance().enableIPv6()) {
                    cmds = new ArrayList<String>();
                    applyShortRules(ctx, cmds, true);
                    iptablesCommands(cmds, out, true);
                }
                callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
            }
        } catch (Exception e) {
            LogUtil.setTagI(TAG, "Exception while applying rules: " + e.getMessage());
            applyDefaultChains(ctx, callback);
        }
        rulesUpToDate = true;
        return true;
    }

    /**
     * Save current rules using the preferences storage.
     *
     * @param ctx application context (mandatory)
     */
    public static RuleDataSet generateRules(Context ctx, List<PackageInfoData> apps, boolean store) {

        rulesUpToDate = false;

        RuleDataSet dataSet = null;

        if (apps != null) {
            // Builds a pipe-separated list of names
            HashSet newpkg_wifi = new HashSet();
            HashSet newpkg_3g = new HashSet();
            HashSet newpkg_roam = new HashSet();
            HashSet newpkg_vpn = new HashSet();
            HashSet newpkg_lan = new HashSet();
            HashSet newpkg_tor = new HashSet();

            for (int i = 0; i < apps.size(); i++) {
                if (apps.get(i) != null) {
                    if (apps.get(i).selected_wifi) {
                        newpkg_wifi.add(apps.get(i).uid);
                    } else {
                        if (!store) newpkg_wifi.add(-apps.get(i).uid);
                    }
                    if (apps.get(i).selected_3g) {
                        newpkg_3g.add(apps.get(i).uid);
                    } else {
                        if (!store) newpkg_3g.add(-apps.get(i).uid);
                    }
                    if (AppUtils.getInstance().enableRoam()) {
                        if (apps.get(i).selected_roam) {
                            newpkg_roam.add(apps.get(i).uid);
                        } else {
                            if (!store) newpkg_roam.add(-apps.get(i).uid);
                        }
                    }
                    if (AppUtils.getInstance().enableVPN()) {
                        if (apps.get(i).selected_vpn) {
                            newpkg_vpn.add(apps.get(i).uid);
                        } else {
                            if (!store) newpkg_vpn.add(-apps.get(i).uid);
                        }
                    }

                    if (AppUtils.getInstance().enableLAN()) {
                        if (apps.get(i).selected_lan) {
                            newpkg_lan.add(apps.get(i).uid);
                        } else {
                            if (!store) newpkg_lan.add(-apps.get(i).uid);
                        }
                    }
                    if (AppUtils.getInstance().enableTor()) {
                        if (apps.get(i).selected_tor) {
                            newpkg_tor.add(apps.get(i).uid);
                        } else {
                            if (!store) newpkg_tor.add(-apps.get(i).uid);
                        }
                    }
                }
            }

            String wifi = android.text.TextUtils.join("|", newpkg_wifi);
            String data = android.text.TextUtils.join("|", newpkg_3g);
            String roam = android.text.TextUtils.join("|", newpkg_roam);
            String vpn = android.text.TextUtils.join("|", newpkg_vpn);
            String lan = android.text.TextUtils.join("|", newpkg_lan);
            String tor = android.text.TextUtils.join("|", newpkg_tor);
            // save the new list of UIDs
            if (store) {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Editor edit = prefs.edit();
                edit.putString(PREF_WIFI_PKG_UIDS, wifi);
                edit.putString(PREF_3G_PKG_UIDS, data);
                edit.putString(PREF_ROAMING_PKG_UIDS, roam);
                edit.putString(PREF_VPN_PKG_UIDS, vpn);
                edit.putString(PREF_LAN_PKG_UIDS, lan);
                edit.putString(PREF_TOR_PKG_UIDS, tor);
                edit.commit();
            } else {
                dataSet = new RuleDataSet(new ArrayList<>(newpkg_wifi),
                        new ArrayList<>(newpkg_3g),
                        new ArrayList<>(newpkg_roam),
                        new ArrayList<>(newpkg_vpn),
                        new ArrayList<>(newpkg_lan),
                        new ArrayList<>(newpkg_tor));
            }
        }
        return dataSet;

    }

    public static void applyIPv6Quick(Context ctx, List<String> cmds, RootCommand callback) {
        List<String> out = new ArrayList<String>();
        ////setBinaryPath(ctx, true);
        iptablesCommands(cmds, out, true);
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
    }

    public static void applyQuick(Context ctx, List<String> cmds, RootCommand callback) {
        List<String> out = new ArrayList<String>();

        //setBinaryPath(ctx, false);
        iptablesCommands(cmds, out, false);

        //related to #511, disable ipv6 but use startup leak.
        if (AppUtils.getInstance().enableIPv6() || AppUtils.getInstance().fixLeak()) {
            //setBinaryPath(ctx, true);
            iptablesCommands(cmds, out, true);
        }
        callback.setRetryExitCode(IPTABLES_TRY_AGAIN).run(ctx, out);
    }

    /**
     * @param ctx application context (mandatory)
     * @return a list of applications
     */
    public static List<PackageInfoData> getApps(Context ctx, GetAppList appList) {

        initSpecial();
        if (applications != null && applications.size() > 0) {
            // return cached instance
            return applications;
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String savedPkg_wifi_uid = prefs.getString(PREF_WIFI_PKG_UIDS, "");
        String savedPkg_3g_uid = prefs.getString(PREF_3G_PKG_UIDS, "");
        String savedPkg_roam_uid = prefs.getString(PREF_ROAMING_PKG_UIDS, "");
        String savedPkg_vpn_uid = prefs.getString(PREF_VPN_PKG_UIDS, "");
        String savedPkg_lan_uid = prefs.getString(PREF_LAN_PKG_UIDS, "");
        String savedPkg_tor_uid = prefs.getString(PREF_TOR_PKG_UIDS, "");

        List<Integer> selected_wifi;
        List<Integer> selected_3g;
        List<Integer> selected_roam = new ArrayList<>();
        List<Integer> selected_vpn = new ArrayList<>();
        List<Integer> selected_lan = new ArrayList<>();
        List<Integer> selected_tor = new ArrayList<>();


        selected_wifi = getListFromPref(savedPkg_wifi_uid);
        selected_3g = getListFromPref(savedPkg_3g_uid);

        if (AppUtils.getInstance().enableRoam()) {
            selected_roam = getListFromPref(savedPkg_roam_uid);
        }
        if (AppUtils.getInstance().enableVPN()) {
            selected_vpn = getListFromPref(savedPkg_vpn_uid);
        }
        if (AppUtils.getInstance().enableLAN()) {
            selected_lan = getListFromPref(savedPkg_lan_uid);
        }
        if (AppUtils.getInstance().enableTor()) {
            selected_tor = getListFromPref(savedPkg_tor_uid);
        }
        //revert back to old approach

        //always use the defaul preferences to store cache value - reduces the application usage size
        SharedPreferences cachePrefs = ctx.getSharedPreferences(DEFAULT_PREFS_NAME, Context.MODE_PRIVATE);

        int count = 0;
        try {
            List<Integer> uid = new ArrayList<>();
            PackageManager pkgmanager = ctx.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //this code will be executed on devices running ICS or later
                final UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
                List<UserHandle> list = um.getUserProfiles();

                for (UserHandle user : list) {
                    Matcher m = p.matcher(user.toString());
                    if (m.find()) {
                        int id = Integer.parseInt(m.group(1));
                        if (id > 0) {
                            uid.add(id);
                        }
                    }
                }
            }


            List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(PackageManager.GET_META_DATA);
            SparseArray<PackageInfoData> syncMap = new SparseArray<>();
            Editor edit = cachePrefs.edit();
            boolean changed = false;
            String name = null;
            String cachekey = null;
            String cacheLabel = "cache.label.";
            PackageInfoData app = null;
            ApplicationInfo apinfo = null;

            Date install = new Date();
            install.setTime(System.currentTimeMillis() - (180000));

            SparseArray<PackageInfoData> multiUserAppsMap = new SparseArray<>();

            for (int i = 0; i < installed.size(); i++) {
                //for (ApplicationInfo apinfo : installed) {
                count = count + 1;
                apinfo = installed.get(i);

                if (appList != null) {
                    appList.doProgress(count);
                }

                boolean firstseen = false;
                app = syncMap.get(apinfo.uid);
                // filter applications which are not allowed to access the Internet
                if (app == null && PackageManager.PERMISSION_GRANTED != pkgmanager.checkPermission(Manifest.permission.INTERNET, apinfo.packageName)) {
                    continue;
                }
                // try to get the application label from our cache - getApplicationLabel() is horribly slow!!!!
                cachekey = cacheLabel + apinfo.packageName;
                name = prefs.getString(cachekey, "");
                if (name.length() == 0 || isRecentlyInstalled(apinfo.packageName)) {
                    // get label and put on cache
                    name = pkgmanager.getApplicationLabel(apinfo).toString();
                    edit.putString(cachekey, name);
                    changed = true;
                    firstseen = true;
                }
                if (app == null) {
                    app = new PackageInfoData();
                    app.uid = apinfo.uid;
                    app.installTime = new File(apinfo.sourceDir).lastModified();
                    app.names = new ArrayList<String>();
                    app.names.add(name);
                    app.appinfo = apinfo;
                    if (app.appinfo != null && (app.appinfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        //user app
                        app.appType = 1;
                    } else {
                        //system app
                        app.appType = 0;
                    }
                    app.pkgName = apinfo.packageName;
                    syncMap.put(apinfo.uid, app);
                } else {
                    app.names.add(name);
                }

                app.firstseen = firstseen;
                // check if this application is selected
                if (!app.selected_wifi && Collections.binarySearch(selected_wifi, app.uid) >= 0) {
                    app.selected_wifi = true;
                }
                if (!app.selected_3g && Collections.binarySearch(selected_3g, app.uid) >= 0) {
                    app.selected_3g = true;
                }
                if (AppUtils.getInstance().enableRoam() && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
                    app.selected_roam = true;
                }
                if (AppUtils.getInstance().enableVPN() && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
                    app.selected_vpn = true;
                }
                if (AppUtils.getInstance().enableLAN() && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
                    app.selected_lan = true;
                }
                if (AppUtils.getInstance().enableTor() && !app.selected_tor && Collections.binarySearch(selected_tor, app.uid) >= 0) {
                    app.selected_tor = true;
                }
                if (AppUtils.getInstance().supportDual()) {
                    checkPartOfMultiUser(apinfo, name, uid, pkgmanager, multiUserAppsMap);
                }
            }

            if (AppUtils.getInstance().supportDual()) {
                //run through multi user map
                for (int i = 0; i < multiUserAppsMap.size(); i++) {
                    app = multiUserAppsMap.valueAt(i);
                    if (!app.selected_wifi && Collections.binarySearch(selected_wifi, app.uid) >= 0) {
                        app.selected_wifi = true;
                    }
                    if (!app.selected_3g && Collections.binarySearch(selected_3g, app.uid) >= 0) {
                        app.selected_3g = true;
                    }
                    if (AppUtils.getInstance().enableRoam() && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
                        app.selected_roam = true;
                    }
                    if (AppUtils.getInstance().enableVPN() && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
                        app.selected_vpn = true;
                    }
                    if (AppUtils.getInstance().enableLAN() && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
                        app.selected_lan = true;
                    }
                    if (AppUtils.getInstance().enableTor() && !app.selected_tor && Collections.binarySearch(selected_tor, app.uid) >= 0) {
                        app.selected_tor = true;
                    }
                    syncMap.put(app.uid, app);
                }
            }

            List<PackageInfoData> specialData = getSpecialData(false);

            if (specialApps == null) {
                specialApps = new HashMap<String, Integer>();
            }
            for (int i = 0; i < specialData.size(); i++) {
                app = specialData.get(i);
                //core apps
                app.appType = 2;
                specialApps.put(app.pkgName, app.uid);
                //default DNS/NTP
                if (app.uid != -1 && syncMap.get(app.uid) == null) {
                    // check if this application is allowed
                    if (!app.selected_wifi && Collections.binarySearch(selected_wifi, app.uid) >= 0) {
                        app.selected_wifi = true;
                    }
                    if (!app.selected_3g && Collections.binarySearch(selected_3g, app.uid) >= 0) {
                        app.selected_3g = true;
                    }
                    if (AppUtils.getInstance().enableRoam() && !app.selected_roam && Collections.binarySearch(selected_roam, app.uid) >= 0) {
                        app.selected_roam = true;
                    }
                    if (AppUtils.getInstance().enableVPN() && !app.selected_vpn && Collections.binarySearch(selected_vpn, app.uid) >= 0) {
                        app.selected_vpn = true;
                    }
                    if (AppUtils.getInstance().enableLAN() && !app.selected_lan && Collections.binarySearch(selected_lan, app.uid) >= 0) {
                        app.selected_lan = true;
                    }
                    if (AppUtils.getInstance().enableTor() && !app.selected_tor && Collections.binarySearch(selected_tor, app.uid) >= 0) {
                        app.selected_tor = true;
                    }
                    syncMap.put(app.uid, app);
                }
            }

            if (changed) {
                edit.commit();
            }
            /* convert the map into an array */
            applications = Collections.synchronizedList(new ArrayList<PackageInfoData>());
            for (int i = 0; i < syncMap.size(); i++) {
                applications.add(syncMap.valueAt(i));
            }
            return applications;
        } catch (Exception e) {
            LogUtil.setTagI(TAG+"Exception in getting app list", e.getMessage());
        }
        return null;
    }

    public static List<PackageInfoData> getSpecialData(boolean additional) {
        List<PackageInfoData> specialData = new ArrayList<>();
        specialData.add(new PackageInfoData(SPECIAL_UID_ANY, AppUtils.getInstance().getContext().getString(R.string.all_item), "dev.afwall.special.any"));
        specialData.add(new PackageInfoData(SPECIAL_UID_KERNEL, AppUtils.getInstance().getContext().getString(R.string.kernel_item), "dev.afwall.special.kernel"));
        specialData.add(new PackageInfoData(SPECIAL_UID_TETHER, AppUtils.getInstance().getContext().getString(R.string.tethering_item), "dev.afwall.special.tether"));
        specialData.add(new PackageInfoData(SPECIAL_UID_NTP, AppUtils.getInstance().getContext().getString(R.string.ntp_item), "dev.afwall.special.ntp"));
        if (additional) {
            specialData.add(new PackageInfoData(1020, "mDNS", "dev.afwall.special.mDNS"));
        }
        for (String acct : specialAndroidAccounts) {
            String dsc = getSpecialDescription(AppUtils.getInstance().getContext(), acct);
            String pkg = "dev.afwall.special." + acct;
            specialData.add(new PackageInfoData(acct, dsc, pkg));
        }
        return specialData;
    }

    private static void checkPartOfMultiUser(ApplicationInfo apinfo, String name, List<Integer> uid1, PackageManager pkgmanager, SparseArray<PackageInfoData> syncMap) {
        try {
            for (Integer integer : uid1) {
                int appUid = Integer.parseInt(integer + "" + apinfo.uid + "");
                String[] pkgs = pkgmanager.getPackagesForUid(appUid);
                if (pkgs != null) {
                    PackageInfoData app = new PackageInfoData();
                    app.uid = appUid;
                    app.installTime = new File(apinfo.sourceDir).lastModified();
                    app.names = new ArrayList<String>();
                    app.names.add(name + "(M)");
                    app.appinfo = apinfo;
                    if (app.appinfo != null && (app.appinfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        //user app
                        app.appType = 0;
                    } else {
                        //system app
                        app.appType = 1;
                    }
                    app.pkgName = apinfo.packageName;
                    syncMap.put(appUid, app);
                }
            }
        } catch (Exception e) {
            LogUtil.setTagE(TAG, e.getMessage());
        }
    }

    private static boolean isRecentlyInstalled(String packageName) {
        boolean isRecent = false;
        try {
            if (recentlyInstalled != null && recentlyInstalled.contains(packageName)) {
                isRecent = true;
                recentlyInstalled.remove(packageName);
            }
        } catch (Exception e) {
        }
        return isRecent;
    }

    private static List<Integer> getListFromPref(String savedPkg_uid) {
        StringTokenizer tok = new StringTokenizer(savedPkg_uid, "|");
        List<Integer> listUids = new ArrayList<Integer>();
        while (tok.hasMoreTokens()) {
            String uid = tok.nextToken();
            if (!uid.equals("")) {
                try {
                    listUids.add(Integer.parseInt(uid));
                } catch (Exception ex) {

                }
            }
        }
        // Sort the array to allow using "Arrays.binarySearch" later
        Collections.sort(listUids);
        return listUids;
    }

    private static boolean installBinary(Context ctx, int resId, String filename) {
        try {
            File f = new File(ctx.getDir("bin", 0), filename);
            if (f.exists()) {
                f.delete();
            }
            copyRawFile(ctx, resId, f, "0755");
            return true;
        } catch (Exception e) {
            LogUtil.setTagE(TAG, "installBinary failed: " + e.getLocalizedMessage());
            return false;
        }
    }

    /**
     * Asserts that the binary files are installed in the cache directory.
     *
     * @param ctx        context
     * @param showErrors indicates if errors should be alerted
     * @return false if the binary files could not be installed
     */
    public static boolean assertBinaries(Context ctx, boolean showErrors) {
        int currentVer = -1, lastVer = -1;
        try {
            currentVer = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
            if (AppUtils.getInstance().appVersion() == currentVer) {
                return true;
            }
        } catch (NameNotFoundException e) {
            LogUtil.setTagE(TAG, "packageManager can't look up versionCode");
        }

        final String[] abis;
        if (Build.VERSION.SDK_INT > 21) {
            abis = Build.SUPPORTED_ABIS;
        } else {
            abis = new String[]{Build.CPU_ABI, Build.CPU_ABI2};
        }

        boolean ret = false;

        for (String abi : abis) {
            if (abi.startsWith("x86")) {
                ret = installBinary(ctx, R.raw.busybox_x86, "busybox") &&
                        installBinary(ctx, R.raw.iptables_x86, "iptables") &&
                        installBinary(ctx, R.raw.ip6tables_x86, "ip6tables") &&
                        installBinary(ctx, R.raw.nflog_x86, "nflog") &&
                        installBinary(ctx, R.raw.run_pie_x86, "run_pie");
            } else if (abi.startsWith("mips")) {
                ret = installBinary(ctx, R.raw.busybox_mips, "busybox") &&
                        installBinary(ctx, R.raw.iptables_mips, "iptables") &&
                        installBinary(ctx, R.raw.ip6tables_mips, "ip6tables") &&
                        installBinary(ctx, R.raw.nflog_mips, "nflog") &&
                        installBinary(ctx, R.raw.run_pie_mips, "run_pie");
            } else {
                // default to ARM
                ret = installBinary(ctx, R.raw.busybox_arm, "busybox") &&
                        installBinary(ctx, R.raw.iptables_arm, "iptables") &&
                        installBinary(ctx, R.raw.ip6tables_arm, "ip6tables") &&
                        installBinary(ctx, R.raw.nflog_arm, "nflog") &&
                        installBinary(ctx, R.raw.run_pie_arm, "run_pie");
            }
            LogUtil.setTagI(TAG, "binary installation for " + abi + (ret ? " succeeded" : " failed"));
        }

        // arch-independent scripts
        ret &= installBinary(ctx, R.raw.afwallstart, "afwallstart");
        //LogUtil.setTagI(TAG, "binary installation for " + abi + (ret ? " succeeded" : " failed"));
        ret &= installBinary(ctx, R.raw.aflogshell, "aflogshell");
        ret &= installBinary(ctx, R.raw.aflogshellb, "aflogshellb");

        if (showErrors) {
            if (ret) {
                toast(ctx, ctx.getString(R.string.toast_bin_installed));
            } else {
                toast(ctx, ctx.getString(R.string.error_binary));
            }
        }
        if (ret == true && currentVer > 0) {
            // this indicates that migration from the old version was successful.
            AppUtils.getInstance().appVersion(currentVer);
        }
        return ret;
    }

    /**
     * Check if the firewall is enabled
     *
     * @param ctx mandatory context
     * @return boolean
     */
    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return false;
        boolean flag = ctx.getSharedPreferences(PREF_FIREWALL_STATUS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false);
        //LogUtil.setTagI(TAG, "Checking for IsEnabled, Flag:" + flag);
        return flag;
    }

    /**
     * Defines if the firewall is enabled and broadcasts the new status
     *
     * @param ctx     mandatory context
     * @param enabled enabled flag
     */
    public static void setEnabled(Context ctx, boolean enabled, boolean showErrors) {
        if (ctx == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREF_FIREWALL_STATUS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_ENABLED, false) == enabled) {
            return;
        }
        rulesUpToDate = false;

        Editor edit = prefs.edit();
        edit.putBoolean(PREF_ENABLED, enabled);
        if (!edit.commit()) {
            if (showErrors) toast(ctx, ctx.getString(R.string.error_write_pref));
            return;
        }
        updateNotification(Api.isEnabled(ctx), ctx);
    }


    public static void errorNotification(Context ctx) {

        String NOTIFICATION_CHANNEL_ID = "firewall.error";
        String channelName = ctx.getString(R.string.error_common);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(ERROR_NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            assert manager != null;
            if (AppUtils.getInstance().getNotificationPriority() == 0) {
                notificationChannel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            }
            notificationChannel.setSound(null, null);
            notificationChannel.setShowBadge(false);
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            manager.createNotificationChannel(notificationChannel);
        }


        Intent appIntent = new Intent(ctx, FireWallActivity.class);
        appIntent.setAction(Intent.ACTION_MAIN);
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Artificial stack so that navigating backward leads back to the Home screen
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(ctx)
                .addParentStack(FireWallActivity.class)
                .addNextIntent(new Intent(ctx, FireWallActivity.class));

        PendingIntent notifyPendingIntent = PendingIntent.getActivity(ctx, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setContentIntent(notifyPendingIntent);

        @SuppressLint("WrongConstant") Notification notification = notificationBuilder.setOngoing(false)
                .setCategory(Notification.CATEGORY_ERROR)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentTitle(ctx.getString(R.string.error_notification_title))
                .setContentText(ctx.getString(R.string.error_notification_text))
                .setTicker(ctx.getString(R.string.error_notification_ticker))
                .setSmallIcon(R.mipmap.notification_warn)
                .setAutoCancel(true)
                .setContentIntent(notifyPendingIntent)
                .build();

        manager.notify(ERROR_NOTIFICATION_ID, notification);
    }

    public static void updateNotification(boolean status, Context ctx) {

        String NOTIFICATION_CHANNEL_ID = "firewall.service";
        String channelName = ctx.getString(R.string.firewall_service);

        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            assert manager != null;
            if (AppUtils.getInstance().getNotificationPriority() == 0) {
                notificationChannel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            }
            notificationChannel.setSound(null, null);
            notificationChannel.setShowBadge(false);
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            manager.createNotificationChannel(notificationChannel);
        }


        Intent appIntent = new Intent(ctx, FireWallActivity.class);
        appIntent.setAction(Intent.ACTION_MAIN);
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);


        /*TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(appIntent);*/

        int icon;
        String notificationText = "";

        if (status) {
            if (AppUtils.getInstance().enableMultiProfile()) {
                String profile = "";
                switch (AppUtils.getInstance().storedProfile()) {
                    case "AFWallPrefs":
                        profile = AppUtils.getInstance().gPrefs.getString("default", ctx.getString(R.string.defaultProfile));
                        break;
                    case "AFWallProfile1":
                        profile = AppUtils.getInstance().gPrefs.getString("profile1", ctx.getString(R.string.profile1));
                        break;
                    case "AFWallProfile2":
                        profile = AppUtils.getInstance().gPrefs.getString("profile2", ctx.getString(R.string.profile2));
                        break;
                    case "AFWallProfile3":
                        profile = AppUtils.getInstance().gPrefs.getString("profile3", ctx.getString(R.string.profile3));
                        break;
                    default:
                        profile = AppUtils.getInstance().storedProfile();
                        break;
                }
                notificationText = ctx.getString(R.string.active) + " (" + profile + ")";
            } else {
                notificationText = ctx.getString(R.string.active);
            }
            //notificationText = context.getString(R.string.active);
            icon = R.mipmap.notification;
        } else {
            notificationText = ctx.getString(R.string.inactive);
            icon = R.mipmap.notification_error;
        }

        int notifyType = AppUtils.getInstance().getNotificationPriority();


        PendingIntent notifyPendingIntent = PendingIntent.getActivity(ctx, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setContentIntent(notifyPendingIntent);

        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setTicker(ctx.getString(R.string.app_name))
                .setSound(null)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentText(notificationText)
                .setSmallIcon(icon)
                .build();

        switch (notifyType) {
            case 0:
                notification.priority = NotificationCompat.PRIORITY_LOW;
                break;
            case 1:
                notification.priority = NotificationCompat.PRIORITY_MIN;
                break;
        }
        if (AppUtils.getInstance().activeNotification()) {
            notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR;
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private static void initSpecial() {
        if (specialApps == null || specialApps.size() == 0) {
            specialApps = new HashMap<String, Integer>();
            specialApps.put("dev.afwall.special.any", SPECIAL_UID_ANY);
            specialApps.put("dev.afwall.special.kernel", SPECIAL_UID_KERNEL);
            specialApps.put("dev.afwall.special.tether", SPECIAL_UID_TETHER);
            //specialApps.put("dev.afwall.special.dnsproxy",SPECIAL_UID_DNSPROXY);
            specialApps.put("dev.afwall.special.ntp", SPECIAL_UID_NTP);
            for (String acct : specialAndroidAccounts) {
                String pkg = "dev.afwall.special." + acct;
                int uid = android.os.Process.getUidForName(acct);
                specialApps.put(pkg, uid);
            }
        }
    }

    public static void updateLanguage(Context context, String lang) {
        if (lang.equals("sys")) {
            Locale defaultLocale = Resources.getSystem().getConfiguration().locale;
            Locale.setDefault(defaultLocale);
            Resources res = context.getResources();
            Configuration conf = res.getConfiguration();
            conf.locale = defaultLocale;
            context.getResources().updateConfiguration(conf, context.getResources().getDisplayMetrics());
        } else if (!"".equals(lang)) {
            Locale locale = new Locale(lang);
            if (lang.contains("_")) {
                locale = new Locale(lang.split("_")[0], lang.split("_")[1]);
            }
            Locale.setDefault(locale);
            Resources res = context.getResources();
            Configuration conf = res.getConfiguration();
            conf.locale = locale;
            context.getResources().updateConfiguration(conf, context.getResources().getDisplayMetrics());
        }
    }

    public static boolean isMobileNetworkSupported(final Context ctx) {
        boolean hasMobileData = true;
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
                    hasMobileData = false;
                }
            }
        } catch (SecurityException e) {
            LogUtil.setTagE(TAG, e.getMessage());
        }
        return hasMobileData;
    }
    /**
     * Apply default chains based on preference
     *
     * @param ctx
     */
    public static void applyDefaultChains(Context ctx, RootCommand callback) {
        List<String> cmds = new ArrayList<String>();
        if (AppUtils.getInstance().ipv4Input()) {
            cmds.add("-P INPUT ACCEPT");
        } else {
            cmds.add("-P INPUT DROP");
        }
        if (AppUtils.getInstance().ipv4Fwd()) {
            cmds.add("-P FORWARD ACCEPT");
        } else {
            cmds.add("-P FORWARD DROP");
        }
        if (AppUtils.getInstance().ipv4Output()) {
            cmds.add("-P OUTPUT ACCEPT");
        } else {
            cmds.add("-P OUTPUT DROP");
        }
        applyQuick(ctx, cmds, callback);
        applyDefaultChainsv6(ctx, callback);
    }

    public static void applyDefaultChainsv6(Context ctx, RootCommand callback) {
        if (AppUtils.getInstance().controlIPv6()) {
            List<String> cmds = new ArrayList<String>();
            if (AppUtils.getInstance().ipv6Input()) {
                cmds.add("-P INPUT ACCEPT");
            } else {
                cmds.add("-P INPUT DROP");
            }
            if (AppUtils.getInstance().ipv6Fwd()) {
                cmds.add("-P FORWARD ACCEPT");
            } else {
                cmds.add("-P FORWARD DROP");
            }
            if (AppUtils.getInstance().ipv6Output()) {
                cmds.add("-P OUTPUT ACCEPT");
            } else {
                cmds.add("-P OUTPUT DROP");
            }
            applyIPv6Quick(ctx, cmds, callback);
        }
    }
    public static Context updateBaseContextLocale(Context context) {
        String language = AppUtils.getInstance().locale(); // Helper method to get saved language from SharedPreferences
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return updateResourcesLocale(context, locale);
        }
        return updateResourcesLocaleLegacy(context, locale);
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Context updateResourcesLocale(Context context, Locale locale) {
        Configuration configuration = context.getResources().getConfiguration();
        configuration.setLocale(locale);
        return context.createConfigurationContext(configuration);
    }

    @SuppressWarnings("deprecation")
    private static Context updateResourcesLocaleLegacy(Context context, Locale locale) {
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        return context;
    }

    static class RuleDataSet {

        List<Integer> wifiList;
        List<Integer> dataList;
        List<Integer> lanList;
        List<Integer> roamList;
        List<Integer> vpnList;
        List<Integer> torList;

        RuleDataSet(List<Integer> uidsWifi, List<Integer> uids3g,
                    List<Integer> uidsRoam, List<Integer> uidsVPN, List<Integer> uidsLAN,
                    List<Integer> uidsTor) {
            this.wifiList = uidsWifi;
            this.dataList = uids3g;
            this.roamList = uidsRoam;
            this.vpnList = uidsVPN;
            this.lanList = uidsLAN;
            this.torList = uidsTor;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(wifiList != null ? android.text.TextUtils.join(",", wifiList) : "");
            builder.append(dataList != null ? android.text.TextUtils.join(",", dataList) : "");
            builder.append(lanList != null ? android.text.TextUtils.join(",", lanList) : "");
            builder.append(roamList != null ? android.text.TextUtils.join(",", roamList) : "");
            builder.append(vpnList != null ? android.text.TextUtils.join(",", vpnList) : "");
            builder.append(torList != null ? android.text.TextUtils.join(",", torList) : "");
            return builder.toString().trim();
        }
    }
    /**
     * Small structure to hold an application info
     */
    public static final class PackageInfoData {

        /**
         * linux user id
         */
        public int uid;
        /**
         * application names belonging to this user id
         */
        public List<String> names;
        /**
         * rules saving & load
         **/
        public String pkgName;

        /**
         * Application Type
         */
        public int appType;

        /**
         * indicates if this application is selected for wifi
         */
        public boolean selected_wifi;
        /**
         * indicates if this application is selected for 3g
         */
        public boolean selected_3g;
        /**
         * indicates if this application is selected for roam
         */
        public boolean selected_roam;
        /**
         * indicates if this application is selected for vpn
         */
        public boolean selected_vpn;
        /**
         * indicates if this application is selected for lan
         */
        public boolean selected_lan;
        /**
         * indicates if this application is selected for tor mode
         */
        public boolean selected_tor;
        /**
         * toString cache
         */
        public String tostr;
        /**
         * application info
         */
        public ApplicationInfo appinfo;
        /**
         * cached application icon
         */
        public Drawable cached_icon;
        /**
         * indicates if the icon has been loaded already
         */
        public boolean icon_loaded;

        /* install time */
        public long installTime;

        /**
         * first time seen?
         */
        public boolean firstseen;

        public PackageInfoData() {
        }

        public PackageInfoData(int uid, String name, String pkgNameStr) {
            this.uid = uid;
            this.names = new ArrayList<String>();
            this.names.add(name);
            this.pkgName = pkgNameStr;
        }

        public PackageInfoData(String user, String name, String pkgNameStr) {
            this(android.os.Process.getUidForName(user), name, pkgNameStr);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof PackageInfoData)) {
                return false;
            }

            PackageInfoData pkg = (PackageInfoData) o;

            return pkg.uid == uid &&
                    pkg.pkgName.equals(pkgName);
        }

        @Override
        public int hashCode() {
            int result = 17;
            if (appinfo != null) {
                result = 31 * result + appinfo.hashCode();
            }
            result = 31 * result + uid;
            result = 31 * result + pkgName.hashCode();
            return result;
        }

        /**
         * Screen representation of this application
         */
        @Override
        public String toString() {
            if (tostr == null) {
                StringBuilder s = new StringBuilder();
                //if (uid > 0) s.append(uid + ": ");
                for (int i = 0; i < names.size(); i++) {
                    if (i != 0) s.append(", ");
                    s.append(names.get(i));
                }
                s.append("\n");
                tostr = s.toString();
            }
            return tostr;
        }

        public String toStringWithUID() {
            if (tostr == null) {
                StringBuilder s = new StringBuilder();
                s.append("[ ");
                s.append(uid);
                s.append(" ] ");
                for (int i = 0; i < names.size(); i++) {
                    if (i != 0) s.append(", ");
                    s.append(names.get(i));
                }
                s.append("\n");
                tostr = s.toString();
            }
            return tostr;
        }
    }

}
