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

    private byte VALUE_COMMAND;
    private byte TEST_COMMAND;

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

    public void sendCommand(String mode, String frequency, String time, boolean on) {

        if (mode == "swept") {
            VALUE_COMMAND = 0x00;
        } else if (mode == "stepped") {
            if (time == "400") {
                VALUE_COMMAND = 0x20;
            } else if (time == "700") {
                VALUE_COMMAND = 0x24;
            } else if (time == "800") {
                VALUE_COMMAND = 0x28;
            } else if (time == "1000") {
                VALUE_COMMAND = 0x2C;
            }
        } else if (mode == "constant") {
            if (time == "400") {
                if (frequency == "50") {
                    VALUE_COMMAND = 0x10;
                } else if (frequency == "75") {
                    VALUE_COMMAND = 0x11;
                } else if (frequency == "100") {
                    VALUE_COMMAND = 0x12;
                }
            } else if (time == "700") {
                if (frequency == "50") {
                    VALUE_COMMAND = 0x14;
                } else if (frequency == "75") {
                    VALUE_COMMAND = 0x15;
                } else if (frequency == "100") {
                    VALUE_COMMAND = 0x16;
                }
            } else if (time == "800") {
                if (frequency == "50") {
                    VALUE_COMMAND = 0x18;
                } else if (frequency == "75") {
                    VALUE_COMMAND = 0x19;
                } else if (frequency == "100") {
                    VALUE_COMMAND = 0x1A;
                }
            } else if (time == "1000") {
                if (frequency == "50") {
                    VALUE_COMMAND = 0x1C;
                } else if (frequency == "75") {
                    VALUE_COMMAND = 0x1D;
                } else if (frequency == "100") {
                    VALUE_COMMAND = 0x1E;
                }
            }
        }

        this.bleController.sendData(createControlWord(LED_COMMAND, on?VALUE_COMMAND:VALUE_OFF));
    }

    public void testFrequency(String frequency, boolean on) {

        /*
        Now changed to tutorial mode (14/06/23)
         */
        if (frequency == "50") {
            TEST_COMMAND = (byte) 0xFD;
        } else if (frequency == "75") {
            TEST_COMMAND = (byte) 0xFE;
        } else if (frequency == "100") {
            TEST_COMMAND = (byte) 0xFF;
        }

        this.bleController.sendData(createControlWord(LED_COMMAND, on?TEST_COMMAND:VALUE_OFF));
    }

}
