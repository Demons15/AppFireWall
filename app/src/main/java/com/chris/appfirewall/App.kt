package com.chris.appfirewall

import android.app.Application
import com.cxsz.framework.tool.SystemManager
import dev.ukanth.ufirewall.util.AppUtils

class App:Application(){
    override fun onCreate() {
        super.onCreate()
        val apkRoot = "chmod 777 $packageCodePath"
        SystemManager.RootCommand(apkRoot)

        AppUtils.getInstance().init(this)
        AppUtils.getInstance().reloadPrefs()
    }
}