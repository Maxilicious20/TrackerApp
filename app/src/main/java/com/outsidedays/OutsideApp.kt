package com.outsidedays

import android.app.Application
import com.outsidedays.data.Repository
import kotlinx.coroutines.*

class OutsideApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository by lazy { Repository(this) }
}
