package com.chris.appfirewall

import android.app.Application
import android.database.sqlite.SQLiteCantOpenDatabaseException
import com.cxsz.framework.tool.LogUtil
import com.cxsz.framework.tool.SystemManager
import com.raizlabs.android.dbflow.config.FlowConfig
import com.raizlabs.android.dbflow.config.FlowManager
import dev.ukanth.ufirewall.util.AppUtils

class App:Application(){
    val TAG = "AFWall"
    override fun onCreate() {
        super.onCreate()
        val apkRoot = "chmod 777 $packageCodePath"
        SystemManager.RootCommand(apkRoot)
        try {
            FlowManager.init(
                FlowConfig.Builder(this)
                    .openDatabasesOnInit(true).build()
            )
        } catch (e: SQLiteCantOpenDatabaseException) {
            LogUtil.setTagI(TAG, "unable to open database - exception")
        }

        AppUtils.getInstance().init(this)
        AppUtils.getInstance().reloadPrefs()
        AppUtils.getInstance().init(this)
        AppUtils.getInstance().reloadPrefs()
    }
}