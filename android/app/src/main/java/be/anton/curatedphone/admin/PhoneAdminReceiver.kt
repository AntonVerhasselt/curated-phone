package be.anton.curatedphone.admin
import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import be.anton.curatedphone.MainActivity
import be.anton.curatedphone.work.PolicyReconcileWorker

class PhoneAdminReceiver : DeviceAdminReceiver() {
    override fun onReceive(context: Context, intent: Intent) { super.onReceive(context, intent); if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_USER_UNLOCKED)) PolicyReconcileWorker.enqueue(context) }
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val allowed = if (android.os.Build.VERSION.SDK_INT >= 31) intent.getIntegerArrayListExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES) else null
        if (allowed != null && DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE !in allowed) setResult(RESULT_CANCELED)
        else setResult(RESULT_OK, Intent().putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE))
        finish()
    }
}
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val owner = getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(packageName)
        setResult(if (owner) RESULT_OK else RESULT_CANCELED); if (owner) startActivity(Intent(this, MainActivity::class.java)); finish()
    }
}
