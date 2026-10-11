package be.anton.curatedphone
import android.app.Application
import androidx.room.Room
import be.anton.curatedphone.data.*
import be.anton.curatedphone.work.PolicyReconcileWorker

class CuratedApplication : Application() {
    val repository by lazy { PhoneRepository(this, Room.databaseBuilder(this, AppDatabase::class.java, "curated-phone.db").build()) }
    override fun onCreate() { super.onCreate(); PolicyReconcileWorker.schedule(this) }
}
