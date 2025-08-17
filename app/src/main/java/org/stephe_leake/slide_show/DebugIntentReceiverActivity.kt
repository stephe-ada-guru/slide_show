package org.stephe_leake.slide_show

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
      
class DebugIntentReceiverActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
    
        val receivedIntent = intent
        if (receivedIntent != null)
           {
            logIntentDetails(receivedIntent)
           } else {
              Log.d("IntentDebug", "No intent received.")
           }
        finish() // Close this activity immediately after logging
    }

    private fun logIntentDetails(intent: Intent)
    {
        val sb = StringBuilder()
        sb.append("Received Intent:\n")
        sb.append("Action: ${intent.action}\n")
        sb.append("Data: ${intent.dataString}\n")
        sb.append("Type (MIME): ${intent.type}\n") // Explicit MIME type
        intent.data?.let { uri ->
            sb.append("Resolved Type (from data URI): ${contentResolver.getType(uri)}\n")
        }
        sb.append("Scheme: ${intent.scheme}\n")
        sb.append("Flags: ${intent.flags}\n")
        sb.append("Categories: ${intent.categories}\n")

        val extras = intent.extras
        if (extras != null) {
            sb.append("Extras:\n")
            for (key in extras.keySet()) {
                val value = extras.get(key)
                sb.append("  $key: $value (${value?.javaClass?.name})\n")
                // Special handling for common sharing extras
                if (key == Intent.EXTRA_STREAM) {
                    sb.append("    EXTRA_STREAM URI: ${extras.getParcelable<android.net.Uri>(Intent.EXTRA_STREAM)}\n")
                } else if (key == Intent.EXTRA_TEXT) {
                    sb.append("    EXTRA_TEXT: ${extras.getString(Intent.EXTRA_TEXT)}\n")
                }
            }
        } else {
            sb.append("No Extras.\n")
        }

        Log.d("IntentDebug", sb.toString())
        // You could also show this in a Toast or TextView if you add a layout
        // Toast.makeText(this, "Intent logged. Check Logcat (tag: IntentDebug)", Toast.LENGTH_LONG).show()
    }
}

