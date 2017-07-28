package com.github.m3l3m01t.snapit

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Created by 34372 on 2017/7/21.
 */

data class ActivityStatus(val activity: WeakReference<Activity>, var status: Int)

class SnapApplication : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        val STATUS_RESUMED = 0
        val STATUS_PAUSED = 1
        val STATUS_CREATED = 2
        val STATUS_DESTROYED = 3
        val STATUS_STARTED = 4
        val STATUS_STOPPED = 5
    }

    val activityStack = mutableListOf<ActivityStatus>()

    private fun removeActivity(activity: Activity) {
        var v = activityStack.find { it.activity == activity }
        if (v != null) {
            activityStack.remove(v)
        }
    }

    private fun updateActivityStatus(activity: Activity, status: Int) {
        var v = activityStack.find { it.activity.get() == activity }
        if (v != null) {
            v.status == STATUS_RESUMED
        } else {
            activityStack.add(ActivityStatus(WeakReference(activity), STATUS_RESUMED))
        }
    }


    override fun onActivityDestroyed(activity: Activity?) {
        removeActivity(activity!!)
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity?) {
        updateActivityStatus(activity!!, STATUS_STARTED)
    }

    override fun onActivityStopped(activity: Activity?) {
        removeActivity(activity!!)
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        updateActivityStatus(activity!!, STATUS_CREATED)
    }

    override fun onActivityPaused(activity: Activity?) {
        updateActivityStatus(activity!!, STATUS_PAUSED)
    }

    override fun onActivityResumed(activity: Activity?) {
        updateActivityStatus(activity!!, STATUS_RESUMED)
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(this)
    }

    fun takeSnapshot() {
        var activity = activityStack.last { it.status == STATUS_RESUMED }.activity.get()
        if (activity != null) {
            ScreenShot.shoot(activity)
        }
    }
}

