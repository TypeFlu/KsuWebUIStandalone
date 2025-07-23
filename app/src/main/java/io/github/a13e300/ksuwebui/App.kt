package io.github.a13e300.ksuwebui

import android.app.Application
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
}
