
package dev.ukanth.ufirewall;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import com.cxsz.framework.tool.LogUtil;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Iterator;

import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.AppUtils;

public final class InterfaceTracker {

    public static final String TAG = "AFWall";

    public static final String ITFS_WIFI[] = {"eth+", "wlan+", "tiwlan+", "ra+", "bnep+"};

    public static final String ITFS_3G[] = {"rmnet+", "pdp+", "uwbr+", "wimax+", "vsnet+",
            "rmnet_sdio+", "ccmni+", "qmi+", "svnet0+", "ccemni+",
            "wwan+", "cdma_rmnet+", "usb+", "rmnet_usb+", "clat4+", "cc2mni+", "bond1+", "rmnet_smux+", "ccinet+",
            "v4-rmnet+", "seth_w+", "v4-rmnet_data+", "rmnet_ipa+", "rmnet_data+", "r_rmnet_data+"};

    public static final String ITFS_VPN[] = {"tun+", "ppp+", "tap+"};

    public static final String BOOT_COMPLETED = "BOOT_COMPLETED";
    public static final String CONNECTIVITY_CHANGE = "CONNECTIVITY_CHANGE";


    private static InterfaceDetails currentCfg = null;

    private static String truncAfter(String in, String regexp) {
        return in.split(regexp)[0];
    }

    private static void getTetherStatus(Context context, InterfaceDetails d) {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Method[] wmMethods = wifi.getClass().getDeclaredMethods();

        d.isTethered = false;
        d.tetherStatusKnown = false;

        for (Method method : wmMethods) {
            if (method.getName().equals("isWifiApEnabled")) {
                try {
                    d.isTethered = ((Boolean) method.invoke(wifi)).booleanValue();
                    d.tetherStatusKnown = true;
                    LogUtil.setTagI(TAG, "isWifiApEnabled is " + d.isTethered);
                } catch (Exception e) {
                    LogUtil.setTagE(Api.TAG, android.util.Log.getStackTraceString(e));
                }
            }
        }
    }

    private static InterfaceDetails getInterfaceDetails(Context context) {

        InterfaceDetails ret = new InterfaceDetails();

        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo info = cm.getActiveNetworkInfo();

        if (info == null || info.isConnected() == false) {
            return ret;
        }

        switch (info.getType()) {
            case ConnectivityManager.TYPE_MOBILE:
            case ConnectivityManager.TYPE_MOBILE_DUN:
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
            case ConnectivityManager.TYPE_MOBILE_MMS:
            case ConnectivityManager.TYPE_MOBILE_SUPL:
            case ConnectivityManager.TYPE_WIMAX:
                ret.isRoaming = info.isRoaming();
                ret.netType = ConnectivityManager.TYPE_MOBILE;
                ret.netEnabled = true;
                break;
            case ConnectivityManager.TYPE_WIFI:
            case ConnectivityManager.TYPE_BLUETOOTH:
            case ConnectivityManager.TYPE_ETHERNET:
                ret.netType = ConnectivityManager.TYPE_WIFI;
                ret.netEnabled = true;
                break;
        }
        try {
            getTetherStatus(context, ret);
        } catch (Exception e) {
            LogUtil.setTagI(Api.TAG, "Exception in  getInterfaceDetails.checkTether" + e.getLocalizedMessage());
        }
        NewInterfaceScanner.populateLanMasks(ret);
        return ret;
    }

    public static boolean checkForNewCfg(Context context) {
        InterfaceDetails newCfg = getInterfaceDetails(context);

        //always check for new config
        if (currentCfg != null && currentCfg.equals(newCfg)) {
            return false;
        }
        LogUtil.setTagI(TAG, "Getting interface details...");

        currentCfg = newCfg;

        if (!newCfg.netEnabled) {
            LogUtil.setTagI(TAG, "Now assuming NO connection (all interfaces down)");
        } else {
            if (newCfg.netType == ConnectivityManager.TYPE_WIFI) {
                LogUtil.setTagI(TAG, "Now assuming wifi connection");
            } else if (newCfg.netType == ConnectivityManager.TYPE_MOBILE) {
                LogUtil.setTagI(TAG, "Now assuming 3G connection (" +
                        (newCfg.isRoaming ? "roaming, " : "") +
                        (newCfg.isTethered ? "tethered" : "non-tethered") + ")");
            }

            if (!newCfg.lanMaskV4.equals("")) {
                LogUtil.setTagI(TAG, "IPv4 LAN netmask on " + newCfg.wifiName + ": " + newCfg.lanMaskV4);
            }
            if (!newCfg.lanMaskV6.equals("")) {
                LogUtil.setTagI(TAG, "IPv6 LAN netmask on " + newCfg.wifiName + ": " + newCfg.lanMaskV6);
            }
            if (newCfg.lanMaskV6.equals("") && newCfg.lanMaskV4.equals("")) {
                LogUtil.setTagI(TAG, "No ipaddress found");
            }
        }
        return true;
    }

    public static InterfaceDetails getCurrentCfg(Context context, boolean force) {
        if (currentCfg == null || force) {
            currentCfg = getInterfaceDetails(context);
        }
        return currentCfg;
    }

    public static void applyRulesOnChange(Context context, final String reason) {
        final Context ctx = context.getApplicationContext();
        if (!checkForNewCfg(ctx)) {
            LogUtil.setTagI(TAG, reason + ": interface state has not changed, ignoring");
            return;
        } else if (!Api.isEnabled(ctx)) {
            LogUtil.setTagI(TAG, reason + ": firewall is disabled, ignoring");
            return;
        }
        // update Api.PREFS_NAME so we pick up the right profile
        // REVISIT: this can be removed once we're confident that G is in sync with profile changes
        AppUtils.getInstance().reloadPrefs();

        if (reason.equals(InterfaceTracker.BOOT_COMPLETED)) {
            applyBootRules(reason);
        } else {
            applyRules(reason);
        }
    }

    public static void applyRules(final String reason) {
        Api.fastApply(AppUtils.getInstance().getContext(), new RootCommand()
                .setFailureToast(R.string.error_apply)
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        if (state.exitCode == 0) {
                            LogUtil.setTagI(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                            Api.applyDefaultChains(AppUtils.getInstance().getContext(), new RootCommand()
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode != 0) {
                                                Api.errorNotification(AppUtils.getInstance().getContext());
                                            }
                                        }
                                    }));
                        } else {
                            //lets try applying all rules
                            Api.setRulesUpToDate(false);
                            Api.fastApply(AppUtils.getInstance().getContext(), new RootCommand()
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            if (state.exitCode == 0) {
                                                LogUtil.setTagI(TAG, reason + ": applied rules at " + System.currentTimeMillis());
                                            } else {
                                                LogUtil.setTagE(TAG, reason + ": applySavedIptablesRules() returned an error");
                                                Api.errorNotification(AppUtils.getInstance().getContext());
                                            }
                                            Api.applyDefaultChains(AppUtils.getInstance().getContext(), new RootCommand()
                                                    .setFailureToast(R.string.error_apply)
                                                    .setCallback(new RootCommand.Callback() {
                                                        @Override
                                                        public void cbFunc(RootCommand state) {
                                                            if (state.exitCode != 0) {
                                                                Api.errorNotification(AppUtils.getInstance().getContext());
                                                            }
                                                        }
                                                    }));
                                        }
                                    }));
                        }
                    }
                }));
    }

    public static void applyBootRules(final String reason) {
        Api.applySavedIptablesRules(AppUtils.getInstance().getContext(), true, new RootCommand()
                .setFailureToast(R.string.error_apply)
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        Api.applyDefaultChains(AppUtils.getInstance().getContext(), new RootCommand()
                                .setCallback(new RootCommand.Callback() {
                                    @Override
                                    public void cbFunc(RootCommand state) {
                                        if (state.exitCode != 0) {
                                            Api.errorNotification(AppUtils.getInstance().getContext());
                                        }
                                    }
                                }));

//                        if (state.exitCode == 0) {
//                            LogUtil.setTagI(TAG, reason + ": applied rules at " + System.currentTimeMillis());
//
//                        } else {
//                            //lets try applying all rules
//                            Api.setRulesUpToDate(false);
//                            Api.applySavedIptablesRules(AppUtils.getInstance().getContext(), true, new RootCommand()
//                                    .setCallback(new RootCommand.Callback() {
//                                        @Override
//                                        public void cbFunc(RootCommand state) {
//                                            if (state.exitCode == 0) {
//                                                LogUtil.setTagI(TAG, reason + ": applied rules at " + System.currentTimeMillis());
//                                            } else {
//                                                LogUtil.setTagE(TAG, reason + ": applySavedIptablesRules() returned an error");
//                                                Api.errorNotification(AppUtils.getInstance().getContext());
//                                            }
//                                            Api.applyDefaultChains(AppUtils.getInstance().getContext(), new RootCommand()
//                                                    .setFailureToast(R.string.error_apply)
//                                                    .setCallback(new RootCommand.Callback() {
//                                                        @Override
//                                                        public void cbFunc(RootCommand state) {
//                                                            if (state.exitCode != 0) {
//                                                                Api.errorNotification(AppUtils.getInstance().getContext());
//                                                            }
//                                                        }
//                                                    }));
//                                        }
//                                    }));
//                        }
                    }
                }));
    }

    private static class NewInterfaceScanner {

        public static void populateLanMasks(InterfaceDetails ret) {
            try {
                Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

                while (en.hasMoreElements()) {
                    NetworkInterface intf = en.nextElement();
                    boolean match = false;

                    if (!intf.isUp() || intf.isLoopback()) {
                        continue;
                    }

                    for (String pattern : ITFS_WIFI) {
                        if (intf.getName().startsWith(truncAfter(pattern, "\\+"))) {
                            match = true;
                            break;
                        }
                    }
                    if (!match)
                        continue;
                    ret.wifiName = intf.getName();

                    Iterator<InterfaceAddress> addrList = intf.getInterfaceAddresses().iterator();
                    while (addrList.hasNext()) {
                        InterfaceAddress addr = addrList.next();
                        InetAddress ip = addr.getAddress();
                        String mask = truncAfter(ip.getHostAddress(), "%") + "/" +
                                addr.getNetworkPrefixLength();

                        if (ip instanceof Inet4Address) {
                            LogUtil.setTagI(TAG, "Found ipv4: " + mask );
                            ret.lanMaskV4 = mask;
                        } else if (ip instanceof Inet6Address) {
                            LogUtil.setTagI(TAG, "Found ipv6: " + mask );
                            ret.lanMaskV6 = mask;
                        }
                    }
                    if (ret.lanMaskV4.equals("") && ret.lanMaskV6.equals("")) {
                        ret.noIP = true;
                    }
                }
            } catch (Exception e) {
                LogUtil.setTagI(TAG, "Error fetching network interface list: " + android.util.Log.getStackTraceString(e));
            }
        }
    }
}
