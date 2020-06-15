package com.example.appconexao

import android.content.Intent
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import timber.log.Timber
import kotlinx.android.synthetic.main.activity_main.*



class MainActivity : AppCompatActivity() {
    var contaxxx = 0

    lateinit var usbController : UsbController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var intent = intent

        var chamadoPeloBoot = intent.getIntExtra("CHAMANDO_DO_BOOT", 0)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }



        ScreenLog.start(this, applicationContext, log_recycler_view, history_recycler_view)

        ScreenLog.add(LogType.TO_LOG, "contaxxx = ${contaxxx}")
        ScreenLog.add(LogType.TO_LOG, "chamadoPeloBoot = ${chamadoPeloBoot}")

        var tempoSegundosDesdeUltimoBoot = SystemClock.elapsedRealtime() / 1000
        ScreenLog.add(LogType.TO_LOG, String.format("tempoSegundosDesdeUltimoBoot = %02d:%02d:%02d", tempoSegundosDesdeUltimoBoot / 3600, (tempoSegundosDesdeUltimoBoot%3600)/60,tempoSegundosDesdeUltimoBoot%60))

        usbController = UsbController(applicationContext, this)

        button.setOnClickListener {
            Timber.i("Vivo")
        }

        btnConnect.setOnClickListener {
            Timber.i("Connect")
            Thread {
                usbController.connectToDevice()
            }.start()

        }

        btnDisconnect.setOnClickListener {
            Timber.i("Connect")
            Thread {
                usbController.disconnectFromDevice()
            }.start()
        }

        btnSend.setOnClickListener {
            val event = Event(EventType.FW_STATUS_RQ, Event.QUESTION)
            Timber.i("Send")
            Thread {
                usbController.send(event)
            }.start()

        }

        btnTag.setOnClickListener {
            contaxxx++
            ScreenLog.add(LogType.TO_LOG, " ")
            ScreenLog.add(LogType.TO_LOG, "---")
            ScreenLog.add(LogType.TO_LOG, "---")
            ScreenLog.add(LogType.TO_LOG, " ")
        }

        if ( tempoSegundosDesdeUltimoBoot < 80) {
            linearLayout.visibility = View.GONE
            log_recycler_view.visibility = View.GONE
            history_recycler_view.visibility = View.GONE
            telaWait.visibility = View.VISIBLE
        } else {
            telaWait.visibility = View.GONE
        }

        usbController.initiateStartupSequence()
    }




    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ScreenLog.add(LogType.TO_LOG, "WWW Entrei na funcao onNewIntent action=${intent.action}")

        if (intent.action != null && intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            ScreenLog.add(LogType.TO_LOG, "WWW ACTION_USB_DEVICE_ATTACHED")
            if ( ! usbController.serialIsConnected ) {
                usbController.connectToDevice()
            }
        }

        if (intent.action != null && intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
            println("WWW ACTION_USB_DEVICE_DETACHED")
            ScreenLog.add(LogType.TO_LOG, "WWW ACTION_USB_DEVICE_DETACHED")
            if ( usbController.serialIsConnected ) {
                usbController.disconnectFromDevice()
            }
        }
    }


}



