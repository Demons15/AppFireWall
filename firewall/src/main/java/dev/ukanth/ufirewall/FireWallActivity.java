
package dev.ukanth.ufirewall;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.cxsz.framework.tool.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.AppListArrayAdapter;
import dev.ukanth.ufirewall.util.AppUtils;
import dev.ukanth.ufirewall.util.PackageComparator;
import haibison.android.lockpattern.utils.AlpSettings;

public class FireWallActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {
    private static final int VERIFY_CHECK = 10000;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE_ASSET = 3;
    public static boolean dirty = false;
    private ListView listview = null;
    private MaterialDialog plsWait;
    private SwipeRefreshLayout mSwipeLayout;
    private int index;
    private int top;
    private List<String> mlocalList = new ArrayList<>(new LinkedHashSet<String>());
    private AlertDialog dialogLegend = null;
    private BroadcastReceiver toastReceiver;

    public void setDirty(boolean dirty) {
        FireWallActivity.dirty = dirty;
    }

    /**
     * Called when the activity is first created
     * .
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            final int FLAG_HARDWARE_ACCELERATED = WindowManager.LayoutParams.class
                    .getDeclaredField("FLAG_HARDWARE_ACCELERATED").getInt(null);
            getWindow().setFlags(FLAG_HARDWARE_ACCELERATED,
                    FLAG_HARDWARE_ACCELERATED);
        } catch (Exception e) {
        }
        setContentView(R.layout.main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
        AlpSettings.Display.setStealthMode(getApplicationContext(), AppUtils.getInstance().enableStealthPattern());
        AlpSettings.Display.setMaxRetries(getApplicationContext(), AppUtils.getInstance().getMaxPatternTry());

        Api.assertBinaries(this, true);
        findViewById(R.id.sure).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyOrSaveRules();
                disableOrEnable();
            }
        });
        mSwipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                //开启防火墙
                disableOrEnable();
            }
        }, 500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (action == null) {
            return;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public void onRefresh() {
        index = 0;
        top = 0;
        Api.applications = null;
        showOrLoadApplications();
        mSwipeLayout.setRefreshing(false);
    }

    private void selectFilterGroup() {
        filterApps(-1);
    }

    /**
     * Filter application based on app tpe
     *
     * @param i
     */
    private void filterApps(int i) {
        Set<PackageInfoData> returnList = new HashSet<>();
        List<PackageInfoData> inputList = new ArrayList<>();
        List<PackageInfoData> allApps = Api.getApps(getApplicationContext(), null);
        if (i >= 0) {
            for (PackageInfoData infoData : allApps) {
                if (infoData != null) {
                    if (infoData.appType == i) {
                        returnList.add(infoData);
                    }
                }
            }
            inputList = new ArrayList<>(returnList);
        } else {
            if (null != allApps) {
                inputList = allApps;
            }
        }

        try {
            Collections.sort(inputList, new PackageComparator());
        } catch (Exception e) {
            LogUtil.setTagI(Api.TAG, "Exception in filter Sorting");
        }

        ArrayAdapter appAdapter = new AppListArrayAdapter(this, getApplicationContext(), inputList);
        this.listview.setAdapter(appAdapter);
        appAdapter.notifyDataSetChanged();
        // restore
        this.listview.setSelectionFromTop(index, top);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    private void reloadPreferences() {
        AppUtils.getInstance().reloadPrefs();
        checkPreferences();
        Api.updateLanguage(getApplicationContext(), AppUtils.getInstance().locale());
        if (this.listview == null) {
            this.listview = (ListView) this.findViewById(R.id.listview);
        }
        clearNotification();
        if (AppUtils.getInstance().enableMultiProfile()) {
            setupMultiProfile();
        }
        selectFilterGroup();
    }

    private void clearNotification() {
        NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(100);
    }

    @Override
    public void onStart() {
        super.onStart();
        reloadPreferences();
    }

    private void setupMultiProfile() {
        reloadProfileList(true);
    }

    private void reloadProfileList(boolean reset) {
        if (reset) {
            mlocalList = new ArrayList<>(new LinkedHashSet<String>());
        }

        mlocalList.add(AppUtils.getInstance().gPrefs.getString("default", getString(R.string.defaultProfile)));

        if (!AppUtils.getInstance().isProfileMigrated()) {
            mlocalList.add(AppUtils.getInstance().gPrefs.getString("profile1", getString(R.string.profile1)));
            mlocalList.add(AppUtils.getInstance().gPrefs.getString("profile2", getString(R.string.profile2)));
            mlocalList.add(AppUtils.getInstance().gPrefs.getString("profile3", getString(R.string.profile3)));
            List<String> profilesList = AppUtils.getInstance().getAdditionalProfiles();
            for (String profiles : profilesList) {
                if (profiles != null && profiles.length() > 0) {
                    mlocalList.add(profiles);
                }
            }
        } else {
            List<ProfileData> profilesList = ProfileHelper.getProfiles();
            for (ProfileData data : profilesList) {
                mlocalList.add(data.getName());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if ((plsWait != null) && plsWait.isShowing()) {
                plsWait.dismiss();
            }

        } catch (final IllegalArgumentException e) {
            // Handle or log or ignore
        } catch (final Exception e) {
            // Handle or log or ignore
        } finally {
            plsWait = null;
        }
        index = this.listview.getFirstVisiblePosition();
        View v = this.listview.getChildAt(0);
        top = (v == null) ? 0 : v.getTop();
    }

    /**
     * Check if the stored preferences are OK
     */
    private void checkPreferences() {
        final Editor editor = AppUtils.getInstance().pPrefs.edit();
        boolean changed = false;
        if (AppUtils.getInstance().pPrefs.getString(Api.PREF_MODE, "").length() == 0) {
            editor.putString(Api.PREF_MODE, Api.MODE_WHITELIST);
            changed = true;
        }
        if (changed)
            editor.commit();
    }

    /**
     * If the applications are cached, just show them, otherwise load and show
     */
    private void showOrLoadApplications() {
        //nocache!!
        GetAppList getAppList = new GetAppList();
        if (plsWait == null && (getAppList.getStatus() == AsyncTask.Status.PENDING || getAppList.getStatus() == AsyncTask.Status.FINISHED)) {
            getAppList.setContext(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }
    private void disableFirewall() {
        Api.setEnabled(this, false, true);
    }

    private void disableOrEnable() {
        final boolean enabled = !Api.isEnabled(this);
        Api.setEnabled(this, enabled, true);
        if (enabled) {
            applyOrSaveRules();//打开防火墙
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_STORAGE_ASSET: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Api.assertBinaries(this, true);
                } else {
                    Toast.makeText(this, R.string.permissiondenied_asset, Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case VERIFY_CHECK: {
                LogUtil.setTagI(Api.TAG, "In VERIFY_CHECK");
                switch (resultCode) {
                    case RESULT_OK:
                        AppUtils.getInstance().isDo(true);
                        break;
                    case RESULT_CANCELED:
                        AppUtils.getInstance().isDo(false);
                }
            }
            break;
            default:
                break;
        }
    }

    /**
     * Apply or save iptables rules, showing a visual indication
     */
    private void applyOrSaveRules() {
        final boolean enabled = Api.isEnabled(this);
        final Context ctx = getApplicationContext();

        Api.generateRules(ctx, Api.getApps(ctx, null), true);

        if (!enabled) {
            Api.setEnabled(ctx, false, true);
            setDirty(false);
            return;
        }
        Api.updateNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
        new RunApply().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dialogLegend != null) {
            dialogLegend.dismiss();
            dialogLegend = null;
        }
        if (toastReceiver != null) {
            unregisterReceiver(toastReceiver);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Api.updateBaseContextLocale(base));
    }

    public class GetAppList extends AsyncTask<Void, Integer, Void> {

        Context context = null;

        public GetAppList setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        protected void onPreExecute() {
            plsWait = new MaterialDialog.Builder(context).cancelable(false).
                    title(getString(R.string.reading_apps)).progress(false, getPackageManager().getInstalledApplications(0)
                    .size(), true).show();
            doProgress(0);
        }

        public void doProgress(int value) {
            publishProgress(value);
        }

        @Override
        protected Void doInBackground(Void... params) {
            Api.getApps(FireWallActivity.this, this);
            if (isCancelled())
                return null;
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            selectFilterGroup();
            doProgress(-1);
            try {
                try {
                    if (plsWait != null && plsWait.isShowing()) {
                        plsWait.dismiss();
                    }
                } catch (final IllegalArgumentException e) {
                    // Handle or log or ignore
                } catch (final Exception e) {
                    // Handle or log or ignore
                } finally {
                    plsWait.dismiss();
                    plsWait = null;
                }
                mSwipeLayout.setRefreshing(false);
            } catch (Exception e) {
                // nothing
                if (plsWait != null) {
                    plsWait.dismiss();
                    plsWait = null;
                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {

            if (progress[0] == 0 || progress[0] == -1) {
                //do nothing
            } else {
                if (plsWait != null) {
                    plsWait.incrementProgress(progress[0]);
                }
            }
        }
    }

    private class RunApply extends AsyncTask<Void, Long, Boolean> {
        boolean enabled = Api.isEnabled(getApplicationContext());

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Api.setRulesUpToDate(false);
            Api.applySavedIptablesRules(getApplicationContext(), true, new RootCommand()
                    .setSuccessToast(R.string.rules_applied)
                    .setFailureToast(R.string.error_apply)
                    .setReopenShell(true)
                    .setCallback(new RootCommand.Callback() {
                        public void cbFunc(RootCommand state) {
                            //queue.clear();
                            runOnUiThread(() -> {
                                setDirty(false);
                                Api.setEnabled(FireWallActivity.this, enabled, true);
                            });
                        }
                    }));
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aVoid) {
            super.onPostExecute(aVoid);
            disableFirewall();
        }
    }
}

