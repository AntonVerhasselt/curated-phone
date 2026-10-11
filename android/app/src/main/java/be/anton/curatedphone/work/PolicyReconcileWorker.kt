package be.anton.curatedphone.work
import android.content.Context
import androidx.work.*
import be.anton.curatedphone.CuratedApplication
import java.util.concurrent.TimeUnit

class PolicyReconcileWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!applicationContext.getSystemService(android.os.UserManager::class.java).isUserUnlocked) return Result.retry()
        val repo = (applicationContext as CuratedApplication).repository
        var failed = runCatching { repo.reconcile(); repo.dao.saveMetadata(be.anton.curatedphone.data.MetadataEntity("reconcileFailure", "")) }.onFailure { error ->
            repo.dao.saveMetadata(be.anton.curatedphone.data.MetadataEntity("reconcileFailure", error.message?.take(500) ?: "Policy reconciliation failed"))
        }.isFailure
        if (repo.vault.token() != null) runCatching {
            val state = repo.sync()
            if (state.approval != null && state.pending != null) repo.acceptAndApply(state.approval, state.pending)
        }.onFailure { failed = true }
        return if (failed) Result.retry() else Result.success()
    }
    companion object {
        fun enqueue(context: Context) { WorkManager.getInstance(context).enqueueUniqueWork("policy-reconcile", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<PolicyReconcileWorker>().setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()) }
        fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("policy-and-feed-refresh", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<PolicyReconcileWorker>(12, TimeUnit.HOURS).build()); enqueue(context) }
    }
}
