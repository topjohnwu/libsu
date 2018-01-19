package com.topjohnwu.superuser;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

class Utils {

    static final String LOWER_CASE = "abcdefghijklmnopqrstuvwxyz";
    static final String UPPER_CASE = LOWER_CASE.toUpperCase();
    static final String NUMBERS = "0123456789";
    static final String ALPHANUM = LOWER_CASE + UPPER_CASE + NUMBERS;

    static CharSequence genRandomAlphaNumString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; ++i) {
            builder.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
        }
        return builder;
    }

    static boolean hasFlag(int flag) {
        return hasFlag(Shell.flags, flag);
    }

    static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }

    static int inToOut(InputStream in, OutputStream out) throws IOException {
        int read, total = 0;
        byte buffer[] = new byte[4096];
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            total += read;
        }
        out.flush();
        return total;
    }

    static void log(String tag, CharSequence log) {
        if (hasFlag(Shell.FLAG_VERBOSE_LOGGING))
            Log.d(tag, log.toString());
    }

    static void stackTrace(Throwable t) {
        if (hasFlag(Shell.FLAG_VERBOSE_LOGGING))
            t.printStackTrace();
    }
}
