package cn.starry.narcissus.util;

import cn.starry.narcissus.Narcissus;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ConsoleUtil {

    public static void sendMessage(String message) {
        String date = new SimpleDateFormat("HH':'mm':'ss").format(new Date());
        Narcissus.LOGGER.info(TextUtil.replaceChar("[" + date + " Info] " + message));
    }
}
