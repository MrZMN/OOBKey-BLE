/*
 * (c) Matey Nenov (https://www.thinker-talk.com)
 *
 * Licensed under Creative Commons: By Attribution 3.0
 * http://creativecommons.org/licenses/by/3.0/
 *
 */
package com.nenoff.connecttoarduinoble

interface BLEControllerListener {
    fun BLEControllerConnected()
    fun BLEControllerDisconnected()
    fun BLEDeviceFound(name: String?, address: String?)
}