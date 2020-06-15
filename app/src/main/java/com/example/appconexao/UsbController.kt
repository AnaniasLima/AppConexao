package com.example.appconexao

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class UsbController(private val context: Context, main: AppCompatActivity): UsbSerialInterface.UsbReadCallback  {

    private var STATUS_REQUEST_INTERVAL = 1000L

    var mainActivity: AppCompatActivity = main

    private var usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
//    private var callback: UsbSerialInterface.UsbReadCallback? = null
//    private var connectionListener: GenericListener<Boolean>? = null

    private var statusRequestHandler = Handler()
    private var noteiroRequestHandler = Handler()
    private var eventsRequestHandler = Handler()

    private var eventReturned = false

    private lateinit var statusThread: CommunicationThread

    var receivedBytes = ByteArray(512)
    var pktInd:Int=0

    var serialIsConnected = false

    private var permissionIntent: PendingIntent? = null

    private fun mostraNaTela(str:String) {
        ScreenLog.add(LogType.TO_LOG, str)
    }



    //----------------------------------------------
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            mostraNaTela("ZZZ ====> entrando em usbPermissionReceiver / onReceive context=${context} action=${action}")

            if ( UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                mostraNaTela("ZZZ ACTION_USB_DEVICE_DETACHED")
                disconnectFromDevice()
            }

            if (USB_PERMISSION_ACTION == action) {
                synchronized(this) {
                    var usbDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    val permissionGranted =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if ( (usbDevice != null) && permissionGranted) {
                        mostraNaTela("ZZZ Se serial fechada, Podemos tentar abrir usbDevice")
                    }
                }
            }
        }
    }

    init {
        println("WWW Init de UsbController")
    }



    // WWW Mateus, Quando chama essa função passando NULL no listener não dveriamos ter um tratamento diferenciado?
    fun connectToDevice() {
        val deviceList = usbManager.deviceList
        val deviceIterator = deviceList.values.iterator()
        var usbCommDevice: UsbDevice? = null

        disconnectFromDevice()

        var device: UsbDevice?
        while (deviceIterator.hasNext() && (usbCommDevice == null) ) {
            device = deviceIterator.next()
            val count = device!!.interfaceCount
            for (i in 0 until count) {
                val intf = device.getInterface(i)
                mostraNaTela("ZZZ intf.interfaceClass=${intf.interfaceClass}")
                if ( intf.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_COMM  ) {
                    mostraNaTela("ZZZ vai executar usbManager.requestPermission(device, permissionIntent)")
                    usbManager.requestPermission(device, permissionIntent)
                    usbCommDevice = device
                    break
                }
            }
        }


        if (usbCommDevice != null) {
            mostraNaTela("ZZZ Achou COMM device")
            try {
                synchronized(this) {
                    mostraNaTela("ZZZ connectToDevice vai chamar openDevice() usbManager=${usbManager}")

                    if ( usbManager != null ) {
                        usbConnection = usbManager.openDevice(usbCommDevice)
                        if ( usbConnection != null) {
                            mostraNaTela("ZZZ connectToDevice vai chamar createUsbSerialDevice()")
                            usbSerialDevice = UsbSerialDevice.createUsbSerialDevice(usbCommDevice, usbConnection)
                            if ( usbSerialDevice != null) {
                                mostraNaTela("ZZZ connectToDevice vai chamar startListening()")
                                if ( startListening() ) {
                                    serialIsConnected = true

                                    mostraNaTela("ZZZ startListening OK")
                                } else {
                                    mostraNaTela("ZZZ startListening FALHOU")
                                }
                            } else {
                                mostraNaTela("ZZZ connectToDevice usbSerialDevice == NULL")
                            }
                        } else {
                            mostraNaTela("ZZZ connectToDevice usbConnection == NULL")

                        }
                    } else {
                        mostraNaTela("ZZZ connectToDevice usbManager == NULL")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            mostraNaTela("ZZZ Não achou COMM usbDevice")
        }
    }




    // onde chegam as respostas do Arduino
    override fun onReceivedData(pkt: ByteArray) {
        val tam:Int = pkt.size
        var ch:Byte

        if ( tam == 0) {
            return
        }

        for ( i in 0 until tam) {
            ch  =   pkt[i]
            if ( ch.toInt() == 0 ) break
            if ( ch.toChar() == '{') {
                if ( pktInd > 0 ) {
                    Timber.d("Vai desprezar: ${String(
                        receivedBytes, 0,
                        pktInd
                    )}")
                }
                pktInd = 0
            }
            if ( ch.toInt() in 32..126 ) {
                if (pktInd < (receivedBytes.size - 1)) {
                    receivedBytes[pktInd++] = ch
                    receivedBytes[pktInd] = 0
                    if (ch.toChar() == '}') {

                        if ( receivedBytes[1].toChar() == '@' ) {
                            mostraNaTela("ARDUINO ==> ${String(receivedBytes, 0, pktInd)}")
                        } else {
                            mostraNaTela(String(receivedBytes))
                        }
                        pktInd = 0
                    }
                } else {
                    // ignora tudo
                    pktInd = 0
                }
            }
        }
    }

    fun disconnectFromDevice() {

        println("WWW Entrei na funcao disconnectFromDevice")
        println("WWW usbSerialDevice = $usbSerialDevice?")
        println("WWW usbConnection   = $usbConnection?")

        mostraNaTela("ZZZ disconnectFromDevice ")

        try {

            if (usbSerialDevice != null) {
                mostraNaTela("ZZZ disconnectFromDevice vai fechar usbSerialDevice")
                usbSerialDevice?.close()
            }
            if (usbConnection != null) {
                mostraNaTela("ZZZ disconnectFromDevice vai fechar usbConnection")
                usbConnection?.close()
            }

        } catch (e: Exception) {
            usbSerialDevice = null
            usbConnection = null
            e.printStackTrace()
        }

        serialIsConnected = false
    }

    private fun startListening() : Boolean{
        var ret:Boolean = false

        try {
            mostraNaTela("ZZZ usbSerialDevice!!.open() e vamos ver se volta...")
            ret = usbSerialDevice!!.open()
            mostraNaTela("ZZZ ret de open ${ret}")
            usbSerialDevice!!.setBaudRate(115200)
            usbSerialDevice!!.read(this)

            mainActivity.runOnUiThread {
                (mainActivity as MainActivity).btnSend.isEnabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ret
    }


    // ZZZ_3
    fun onEventResponse(eventResponse: EventResponse) {
        println("onEventResponset action=${eventResponse.action}")
    }

    // AAA2
    fun initiateStartupSequence() {
//        this.callback = callback

        mostraNaTela("ZZZ initiateStartupSequence")

        permissionIntent =
            PendingIntent.getBroadcast(context, 0, Intent(USB_PERMISSION_ACTION), 0)

        mostraNaTela("ZZZ criando  filter = IntentFilter()")
        val filter = IntentFilter()

        filter.addAction(USB_PERMISSION_ACTION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)

        mostraNaTela("ZZZ fazendo filter.addAction(USB_PERMISSION_ACTION)")

        mostraNaTela("ZZZ fazendo registro do receiver context.registerReceiver(usbPermissionReceiver, filter)")

        context.registerReceiver(usbPermissionReceiver, filter)


        println("WWW Vamos criar thread que vai executar assertDeviceConnection")
        Thread {
            assertDeviceConnection( )
        }.start()
    }

    private fun assertDeviceConnection() {

        synchronized(this) {
            mostraNaTela("ZZZ assertDeviceConnection vai chamar connectToDevice")
            connectToDevice()
        }

    }


    fun send( curEvent: Event) {

//        Timber.i("Send ${curEvent.eventType} action:${curEvent.action}: listSize=${EVENT_LIST.size}")

        try {

            val pktStr: String = Event.getCommandData(curEvent)

            // "numPktResp":11838,"packetNumber":11880 com temporização de 250L
            // Ou seja o arduin perdeu 38 pacotes e 11.000
            // TODO: Colocar wack e parar medição ultrason durante pacote

            val startBytes =  byteArrayOf( 2, 2, 2) // STX
            val endBytes =  byteArrayOf( 3, 3, 3) // ETX

            usbSerialDevice?.write(startBytes)
            usbSerialDevice?.write(pktStr.toByteArray())
            usbSerialDevice?.write(endBytes)

            Timber.d("TX: $pktStr")

        } catch (e: Exception) {
            Timber.d("Exception in send: ${e.message} ")
        }
    }


    companion object {
        private val USB_PERMISSION_ACTION = "USB_PERMISSION"
        var usbConnection : UsbDeviceConnection? = null
        var usbSerialDevice: UsbSerialDevice? = null
    }
}

