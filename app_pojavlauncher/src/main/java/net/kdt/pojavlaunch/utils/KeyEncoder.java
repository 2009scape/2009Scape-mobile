package net.kdt.pojavlaunch.utils;

import net.kdt.pojavlaunch.AWTInputBridge;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;

import java.util.HashMap;
import java.util.Map;

public class KeyEncoder {

    private static final Map<Character, Character> specialCharMap = createSpecialCharMap();
    private static final char MODIFIER = 123; // F12 key as a modifier for caps lock
    private static final char BACKSPACE_ANDROID = 67;
    private static final char BACKSPACE_UNICODE = 8;

    // Initialize the mapping of special characters to their respective keys
    private static Map<Character, Character> createSpecialCharMap() {
        Map<Character, Character> map = new HashMap<>();
        map.put('!', '1');
        map.put('@', '2');
        map.put('#', '3');
        map.put('$', '4');
        map.put('%', '5');
        map.put('^', '6');
        map.put('&', '7');
        map.put('*', '8');
        map.put('(', '9');
        map.put(')', '0');
        map.put('_', '-');
        map.put('+', '=');
        map.put('{', '[');
        map.put('}', ']');
        map.put(':', ';');
        map.put('"', '\'');
        map.put('<', ',');
        map.put('>', '.');
        map.put('?', '/');
        map.put('|', '\\');
        return map;
    }

    public static void sendUnicodeBackspace(){
        AWTInputBridge.sendKey(BACKSPACE_UNICODE, BACKSPACE_UNICODE);
    }

    public static void sendEncodedChar(int keyCode, char c) {
        if (keyCode == BACKSPACE_ANDROID && !TouchCharInput.softKeyboardIsActive) {
            sendUnicodeBackspace();
        } else if (specialCharMap.containsKey(c)) {
            AWTInputBridge.sendKey(MODIFIER, MODIFIER);
            AWTInputBridge.sendKey(specialCharMap.get(c), specialCharMap.get(c));
        } else if (Character.isDigit(c)) {
            AWTInputBridge.sendKey(c, c);
        } else if (Character.isLowerCase(c)){
            AWTInputBridge.sendKey(Character.toUpperCase(c),Character.toUpperCase(c));
        } else if (Character.isUpperCase(c)) {
            AWTInputBridge.sendKey(MODIFIER, MODIFIER);
            AWTInputBridge.sendKey(c, c);
        } else {
            AWTInputBridge.sendKey(c, keyCode);
        }
    }
}
