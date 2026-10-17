package com.daklok.crescendo.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Automatická záloha raz denne – len cez Wi-Fi (neúčtovaná sieť) a pri dostatku batérie. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = applicationContext.backupManager
        // na pozadí sa nedá zobraziť prihlásenie – ak treba, záloha sa jednoducho preskočí
        val token = manager.silentToken() ?: return Result.success()
        val ok = manager.backup(token, quiet = true)
        return if (ok) Result.success() else Result.retry()
    }
}

object BackupScheduler {
    private const val NAME = "crescendo-auto-backup"

    fun setEnabled(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
