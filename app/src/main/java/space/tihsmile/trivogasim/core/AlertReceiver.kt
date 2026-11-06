package space.tihsmile.trivogasim.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val actionId = intent.getStringExtra("timed_id")
        val id = intent.getStringExtra("alert_id")
        if (actionId == null && id == null) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = AlertRepository(context)
                if (actionId != null) repo.runTimedAction(actionId)
                else if (id != null) repo.activate(id)
            }
            finally { result.finish() }
        }
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { AlertRepository(context).restore() }
            finally { result.finish() }
        }
    }
}
