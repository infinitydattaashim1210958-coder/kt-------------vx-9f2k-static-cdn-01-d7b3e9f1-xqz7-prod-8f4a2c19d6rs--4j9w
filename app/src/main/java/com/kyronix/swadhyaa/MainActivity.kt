package com.kyronix.swadhyaa

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal placeholder Activity.
 * Real UI belongs to M4+. Do not expand this class yet.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "স্বাধ্যায়\nDatabase layer initialized"
            textSize = 20f
            setPadding(48, 48, 48, 48)
        }
        setContentView(tv)
    }
}
