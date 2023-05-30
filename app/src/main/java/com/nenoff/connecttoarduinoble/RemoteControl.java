/*
 * (c) Matey Nenov (https://www.thinker-talk.com)
 *
 * Licensed under Creative Commons: By Attribution 3.0
 * http://creativecommons.org/licenses/by/3.0/
 *
 */

package com.nenoff.connecttoarduinoble;

public class RemoteControl {
    private final static byte LED_COMMAND = 0x4;

    private final static byte VALUE_OFF = 0x0;
    private final static byte VALUE_ON = (byte)0xFF;
    private final static byte VALUE_END = (byte)0xFE;

    private BLEController bleController;

    public RemoteControl(BLEController bleController) {
        this.bleController = bleController;
    }

    private byte [] createControlWord(byte type, byte ... args) {
        byte [] command = new byte[args.length];
        for(int i = 0; i < args.length; i++)
            command[i] = args[i];
        return command;
    }

    public void switchLED(boolean on) {
        this.bleController.sendData(createControlWord(LED_COMMAND, on?VALUE_ON:VALUE_OFF));
    }

    public void endDatalog(boolean end) {
        this.bleController.sendData(createControlWord(LED_COMMAND, end?VALUE_END:VALUE_OFF));
    }

}
