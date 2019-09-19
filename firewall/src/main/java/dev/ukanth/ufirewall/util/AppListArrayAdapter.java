package dev.ukanth.ufirewall.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.cxsz.framework.tool.LogUtil;

import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.FireWallActivity;
import dev.ukanth.ufirewall.R;
public class AppListArrayAdapter extends ArrayAdapter<PackageInfoData> {

    public static final String TAG = "AFWall";
    private final Context context;
    private final List<PackageInfoData> listApps;
    private Activity activity;

    public AppListArrayAdapter(FireWallActivity activity, Context context, List<PackageInfoData> apps) {
        super(context, R.layout.main_list, apps);
        this.activity = activity;
        this.context = context;
        this.listApps = apps;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        AppStateHolder holder;
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            // Inflate a new view
            convertView = inflater.inflate(R.layout.main_list, parent, false);
            holder = new AppStateHolder();
            holder.box_wifi = (CheckBox) convertView.findViewById(R.id.itemcheck_wifi);

            if (Api.isMobileNetworkSupported(context)) {
                holder.box_3g = addSupport(convertView, true, R.id.itemcheck_3g);
            } else {
                removeSupport(convertView, R.id.itemcheck_3g);
            }

            if (AppUtils.getInstance().enableRoam()) {
                holder.box_roam = addSupport(convertView, true, R.id.itemcheck_roam);
            }
            if (AppUtils.getInstance().enableVPN()) {
                holder.box_vpn = addSupport(convertView, true, R.id.itemcheck_vpn);
            }
            if (AppUtils.getInstance().enableLAN()) {
                holder.box_lan = addSupport(convertView, true, R.id.itemcheck_lan);
            }
            if (AppUtils.getInstance().enableTor()) {
                holder.box_tor = addSupport(convertView, true, R.id.itemcheck_tor);
            }

            holder.text = (TextView) convertView.findViewById(R.id.itemtext);
            holder.icon = (ImageView) convertView.findViewById(R.id.itemicon);

            if (AppUtils.getInstance().disableIcons()) {
                holder.icon.setVisibility(View.GONE);
            }
            convertView.setTag(holder);
        } else {
            // Convert an existing view
            holder = (AppStateHolder) convertView.getTag();
            holder.box_wifi = (CheckBox) convertView.findViewById(R.id.itemcheck_wifi);
            if (Api.isMobileNetworkSupported(context)) {
                holder.box_3g = addSupport(convertView, true, R.id.itemcheck_3g);
            } else {
                removeSupport(convertView, R.id.itemcheck_3g);
            }
            if (AppUtils.getInstance().enableRoam()) {
                addSupport(convertView, false, R.id.itemcheck_roam);
            }
            if (AppUtils.getInstance().enableVPN()) {
                addSupport(convertView, false, R.id.itemcheck_vpn);
            }
            if (AppUtils.getInstance().enableLAN()) {
                addSupport(convertView, false, R.id.itemcheck_lan);
            }
            if (AppUtils.getInstance().enableTor()) {
                addSupport(convertView, false, R.id.itemcheck_tor);
            }

            holder.text = (TextView) convertView.findViewById(R.id.itemtext);
            holder.icon = (ImageView) convertView.findViewById(R.id.itemicon);
            if (AppUtils.getInstance().disableIcons()) {
                holder.icon.setVisibility(View.GONE);
            }
        }


        holder.app = listApps.get(position);

        if (AppUtils.getInstance().showUid()) {
            holder.text.setText(holder.app.toStringWithUID());
        } else {
            holder.text.setText(holder.app.toString());
        }

        final int id = holder.app.uid;
        holder.text.setOnClickListener(v -> {

        });

        ApplicationInfo info = holder.app.appinfo;
        if (info != null && (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            //user app
            holder.text.setTextColor(AppUtils.getInstance().userColor());
        } else {
            //system app
            holder.text.setTextColor(AppUtils.getInstance().sysColor());
        }

        if (!AppUtils.getInstance().disableIcons()) {
            if(holder.app.pkgName.startsWith("dev.afwall.special.")) {
                holder.icon.setImageDrawable(convertView.getContext().getResources().getDrawable(R.mipmap.ic_launcher));
            } else {
                holder.icon.setImageDrawable(holder.app.cached_icon);
                if (!holder.app.icon_loaded && info != null) {
                    try {
                        new LoadIconTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, holder.app,
                                context.getPackageManager(), convertView);
                    } catch (Exception r) {
                    }
                }
            }

        } else {
            holder.icon.setVisibility(View.GONE);

        }

        holder.box_wifi.setTag(holder.app);
        holder.box_wifi.setChecked(holder.app.selected_wifi);


        if (Api.isMobileNetworkSupported(context)) {
            holder.box_3g.setTag(holder.app);
            holder.box_3g.setChecked(holder.app.selected_3g);
        }

        if (AppUtils.getInstance().enableRoam()) {
            holder.box_roam = addSupport(holder.box_roam, holder.app, 0);
        }
        if (AppUtils.getInstance().enableVPN()) {
            holder.box_vpn = addSupport(holder.box_vpn, holder.app, 1);
        }
        if (AppUtils.getInstance().enableLAN()) {
            holder.box_lan = addSupport(holder.box_lan, holder.app, 2);
        }
        if (AppUtils.getInstance().enableTor()) {
            holder.box_tor = addSupport(holder.box_tor, holder.app, 3);
        }

        addEventListenter(holder);

        return convertView;
    }

    private void addEventListenter(final AppStateHolder holder) {
        if (holder.box_lan != null) {
            holder.box_lan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_lan != isChecked) {
                            holder.app.selected_lan = isChecked;
                            FireWallActivity.dirty = true;
                            notifyDataSetChanged();
                        }
                    }

                }
            });
        }

        if (holder.box_wifi != null) {
            holder.box_wifi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_wifi != isChecked) {
                            holder.app.selected_wifi = isChecked;
                            FireWallActivity.dirty = true;
                            notifyDataSetChanged();
                        }
                    }
                }
            });
        }

        if (holder.box_3g != null) {
            holder.box_3g.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_3g != isChecked) {
                            holder.app.selected_3g = isChecked;
                            FireWallActivity.dirty = true;
                            notifyDataSetChanged();
                        }
                    }
                }
            });
        }

        if (holder.box_roam != null) {
            holder.box_roam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_roam != isChecked) {
                            holder.app.selected_roam = isChecked;
                            FireWallActivity.dirty = true;
                            notifyDataSetChanged();
                        }
                    }
                }
            });
        }

        if (holder.box_vpn != null) {
            holder.box_vpn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_vpn != isChecked) {
                            holder.app.selected_vpn = isChecked;
                            FireWallActivity.dirty = true;
                            notifyDataSetChanged();
                        }
                    }
                }
            });
        }

        if (holder.box_tor != null) {
            holder.box_tor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    if(compoundButton.isPressed()) {
                        if (holder.app.selected_tor != isChecked) {
                            holder.app.selected_tor = isChecked;
                            FireWallActivity.dirty = true;
                            notifyDataSetChanged();
                        }
                    }
                }
            });
        }
    }

    private CheckBox addSupport(CheckBox check, PackageInfoData app, int flag) {
        if (check != null) {
            check.setTag(app);
            switch (flag) {
                case 0:
                    check.setChecked(app.selected_roam);
                    break;
                case 1:
                    check.setChecked(app.selected_vpn);
                    break;
                case 2:
                    check.setChecked(app.selected_lan);
                    break;
                case 3:
                    check.setChecked(app.selected_tor);
                    break;
            }
        }
        return check;
    }

    private CheckBox addSupport(View convertView, boolean action, int id) {
        CheckBox check = (CheckBox) convertView.findViewById(id);
        check.setVisibility(View.VISIBLE);
        return check;
    }

    private CheckBox removeSupport(View convertView, int id) {
        CheckBox check = (CheckBox) convertView.findViewById(id);
        check.setVisibility(View.GONE);
        return check;
    }


    static class AppStateHolder {
        private CheckBox box_lan;
        private CheckBox box_wifi;
        private CheckBox box_3g;
        private CheckBox box_roam;
        private CheckBox box_vpn;
        private CheckBox box_tor;
        private TextView text;
        private ImageView icon;
        private PackageInfoData app;
    }

    /**
     * Asynchronous task used to load icons in a background thread.
     */
    private static class LoadIconTask extends AsyncTask<Object, Void, View> {
        @Override
        protected View doInBackground(Object... params) {
            try {
                final PackageInfoData app = (PackageInfoData) params[0];
                final PackageManager pkgMgr = (PackageManager) params[1];
                final View viewToUpdate = (View) params[2];
                if (!app.icon_loaded) {
                    Drawable d = new ScaleDrawable(pkgMgr.getApplicationIcon(app.appinfo), 0, 32, 32).getDrawable();
                    d.setBounds(0, 0, 32, 32);
                    app.cached_icon = d;
                    app.icon_loaded = true;
                }
                return viewToUpdate;
            } catch (Exception e) {
                LogUtil.setTagE(TAG, "Error loading icon:"+e.getMessage());
                return null;
            }
        }

        protected void onPostExecute(View viewToUpdate) {
            try {
                final AppStateHolder entryToUpdate = (AppStateHolder) viewToUpdate.getTag();
                entryToUpdate.icon.setImageDrawable(entryToUpdate.app.cached_icon);
            } catch (Exception e) {
                LogUtil.setTagE(TAG, "Error showing icon:"+e.getMessage());
            }
        }
    }
}
