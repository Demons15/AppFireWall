package com.chris.appfirewall

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import dev.ukanth.ufirewall.FireWallActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        control()
    }

    fun control() {
        startActivity(Intent().setClass(this, FireWallActivity().javaClass))
    }
}
