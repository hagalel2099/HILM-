package com.nossouniverso.launcher.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.nossouniverso.launcher.ui.home.HomeActivity

class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (consumePendingUpdateRelaunch(context)) {
                    relaunchHomeActivity(context)
                }
            }
            ACTION_PACKAGE_INSTALL_STATUS -> {
                val status = intent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE
                )
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirmIntent: Intent? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(Intent.EXTRA_INTENT)
                            }
                        if (confirmIntent != null) {
                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(confirmIntent)
                            } catch (_: Exception) {
                                markPendingUpdateRelaunch(context, false)
                                notifyUiResult(
                                    context,
                                    status,
                                    "Não foi possível abrir a confirmação de instalação do Android."
                                )
                            }
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        if (consumePendingUpdateRelaunch(context)) {
                            relaunchHomeActivity(context)
                        }
                        notifyUiResult(context, status, "Atualização instalada com sucesso.")
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> {
                        markPendingUpdateRelaunch(context, false)
                        notifyUiResult(
                            context,
                            status,
                            "Instalação cancelada pelo usuário. Você pode tentar novamente."
                        )
                    }
                    else -> {
                        markPendingUpdateRelaunch(context, false)
                        val cleanMsg = if (message.isNotBlank()) {
                            "Não foi possível concluir a instalação ($message)."
                        } else {
                            "Não foi possível concluir a instalação no Android."
                        }
                        notifyUiResult(context, status, cleanMsg)
                    }
                }
            }
        }
    }

    private fun relaunchHomeActivity(context: Context) {
        val relaunchIntent = Intent(context, HomeActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(EXTRA_RELAUNCHED_AFTER_UPDATE, true)
        }
        try {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_RELAUNCH,
                relaunchIntent,
                flags
            )
            pendingIntent.send()
        } catch (_: Exception) {
            try {
                context.startActivity(relaunchIntent)
            } catch (_: Exception) {
                // System blocked background activity start when launcher is not default HOME
            }
        }
    }

    private fun notifyUiResult(context: Context, status: Int, message: String) {
        val uiIntent = Intent(ACTION_INSTALL_UI_RESULT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_INSTALL_STATUS, status)
            putExtra(EXTRA_INSTALL_MESSAGE, message)
        }
        context.sendBroadcast(uiIntent)
    }

    companion object {
        const val ACTION_PACKAGE_INSTALL_STATUS =
            "com.nossouniverso.launcher.action.PACKAGE_INSTALL_STATUS"
        const val ACTION_INSTALL_UI_RESULT =
            "com.nossouniverso.launcher.action.INSTALL_UI_RESULT"
        const val EXTRA_INSTALL_STATUS = "extra_install_status"
        const val EXTRA_INSTALL_MESSAGE = "extra_install_message"
        const val EXTRA_RELAUNCHED_AFTER_UPDATE = "extra_relaunched_after_update"

        private const val PREFS_NAME = "nosso_universo_updater_prefs"
        private const val KEY_PENDING_RELAUNCH = "pending_update_relaunch"
        private const val REQUEST_CODE_RELAUNCH = 4201

        fun markPendingUpdateRelaunch(context: Context, pending: Boolean) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING_RELAUNCH, pending)
                .apply()
        }

        fun consumePendingUpdateRelaunch(context: Context): Boolean {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val pending = prefs.getBoolean(KEY_PENDING_RELAUNCH, false)
            if (pending) {
                prefs.edit().putBoolean(KEY_PENDING_RELAUNCH, false).apply()
            }
            return pending
        }
    }
}
