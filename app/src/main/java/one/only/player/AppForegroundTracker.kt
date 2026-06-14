package one.only.player

import android.app.Activity
import android.app.Application
import android.os.Bundle

internal object AppForegroundTracker {
    @Volatile
    private var resumedActivityCount = 0

    val hasResumedActivity: Boolean
        get() = resumedActivityCount > 0

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    resumedActivityCount += 1
                }

                override fun onActivityPaused(activity: Activity) {
                    resumedActivityCount = (resumedActivityCount - 1).coerceAtLeast(0)
                }

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
