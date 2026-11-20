package com.nossouniverso.launcher.data

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import com.nossouniverso.launcher.model.AppInfo

class AppRepository(
    private val context: Context,
    private val iconPackManager: IconPackManager
) {
    private val packageManager: PackageManager = context.packageManager

    fun loadInstalledApps(): List<AppInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val ownPackage = context.packageName
        return resolveList
            .filter { it.activityInfo.packageName != ownPackage }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val act = info.activityInfo.name
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    .ifEmpty { pkg }
                val rawIcon = info.loadIcon(packageManager)
                val styledIcon = iconPackManager.resolveAppIcon(pkg, act, rawIcon)
                AppInfo(
                    packageName = pkg,
                    activityName = act,
                    label = label,
                    icon = styledIcon
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launchApp(packageName: String, activityName: String? = null): Boolean {
        return try {
            val intent = if (!activityName.isNullOrBlank()) {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(packageName, activityName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
            } else {
                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } ?: return false

            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun createDefaultLauncherIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun registerPackageReceiver(onPackagesChanged: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                onPackagesChanged()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        return receiver
    }
}
