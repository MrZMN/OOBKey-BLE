/*
 * (c) Matey Nenov (https://www.thinker-talk.com)
 *
 * Licensed under Creative Commons: By Attribution 3.0
 * http://creativecommons.org/licenses/by/3.0/
 *
 */
package com.nenoff.connecttoarduinoble

//import com.nenoff.connecttoarduinoble.BLEController.sendData
import com.nenoff.connecttoarduinoble.BLEController
import com.nenoff.connecttoarduinoble.RemoteControl

class RemoteControl(private val bleController: BLEController) {
    private fun createControlWord(type: Byte, vararg args: Byte): ByteArray {
        val command = ByteArray(args.size)
        for (i in 0 until args.size) command[i] = args[i]
        return command
    }

    fun switchLED(on: Boolean) {
        bleController.sendData(createControlWord(LED_COMMAND, if (on) VALUE_ON else VALUE_OFF))
    }

    companion object {
        private const val LED_COMMAND: Byte = 0x4
        private const val VALUE_OFF: Byte = 0x0
        private const val VALUE_ON = 0xFF.toByte()
    }
}