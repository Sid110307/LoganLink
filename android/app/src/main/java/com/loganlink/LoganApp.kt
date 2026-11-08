package com.loganlink

import android.app.Application
import com.loganlink.core.VehicleHub
import com.loganlink.core.data.Prefs
import com.loganlink.core.data.Repository

class LoganApp : Application() {
    lateinit var prefs: Prefs; private set
    lateinit var repo: Repository; private set
    lateinit var hub: VehicleHub; private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repo = Repository(this, prefs)
        repo.ensureDefaultService(prefs.vehicle.value.odometerKm)
        hub = VehicleHub(this, prefs, repo)
    }
}
