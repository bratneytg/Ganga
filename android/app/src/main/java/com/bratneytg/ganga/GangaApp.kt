package com.bratneytg.ganga

import android.app.Application
import com.bratneytg.ganga.data.Store
import com.bratneytg.ganga.net.Fx
import com.bratneytg.ganga.work.DealNotifier
import com.bratneytg.ganga.work.Scheduling
import com.bratneytg.ganga.work.SearchEngine
import java.io.File

class GangaApp : Application() {
    lateinit var store: Store
        private set
    lateinit var fx: Fx
        private set
    lateinit var engine: SearchEngine
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(File(filesDir, "ganga.json"))
        fx = Fx(File(filesDir, "fx.json"))
        engine = SearchEngine(this, store, fx)
        DealNotifier.ensureChannel(this)
        Scheduling.schedule(this, store.data.value.settings, replace = false)
    }
}
