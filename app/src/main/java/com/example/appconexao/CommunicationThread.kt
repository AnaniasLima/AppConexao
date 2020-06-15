package com.example.appconexao

import timber.log.Timber

class CommunicationThread internal constructor(
    private val request: String,
    private val curEvent: Event
)
    : Thread() {


    override fun run() {

        UsbController.usbSerialDevice?.let {

            try {
                val pktStr: String = Event.getCommandData(curEvent) // Estava chamando o metodo getCommandData duas vezes
                Timber.d("$request!: $pktStr")
                it.write(pktStr.toByteArray())
            } catch (e: Exception) {
                Timber.d("$request!: Ocorreu uma Exception ")
            }
        }
    }
}
