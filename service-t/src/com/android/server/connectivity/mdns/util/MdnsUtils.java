/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.connectivity.mdns.util;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Network;
import android.os.Handler;

import com.android.server.connectivity.mdns.MdnsConstants;
import com.android.server.connectivity.mdns.MdnsRecord;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Mdns utility functions.
 */
public class MdnsUtils {

    private MdnsUtils() { }

    /**
     * Convert the string to DNS case-insensitive lowercase
     *
     * Per rfc6762#page-46, accented characters are not defined to be automatically equivalent to
     * their unaccented counterparts. So the "DNS lowercase" should be if character is A-Z then they
     * transform into a-z. Otherwise, they are kept as-is.
     */
    public static String toDnsLowerCase(@NonNull String string) {
        final char[] outChars = new char[string.length()];
        for (int i = 0; i < string.length(); i++) {
            outChars[i] = toDnsLowerCase(string.charAt(i));
        }
        return new String(outChars);
    }

    /**
     * Convert the array of labels to DNS case-insensitive lowercase.
     */
    public static String[] toDnsLabelsLowerCase(@NonNull String[] labels) {
        final String[] outStrings = new String[labels.length];
        for (int i = 0; i < labels.length; ++i) {
            outStrings[i] = toDnsLowerCase(labels[i]);
        }
        return outStrings;
    }

    /**
     * Compare two strings by DNS case-insensitive lowercase.
     */
    public static boolean equalsIgnoreDnsCase(@NonNull String a, @NonNull String b) {
        if (a.length() != b.length()) return false;
        for (int i = 0; i < a.length(); i++) {
            if (toDnsLowerCase(a.charAt(i)) != toDnsLowerCase(b.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compare two set of DNS labels by DNS case-insensitive lowercase.
     */
    public static boolean equalsDnsLabelIgnoreDnsCase(@NonNull String[] a, @NonNull String[] b) {
        if (a == b) {
            return true;
        }
        int length = a.length;
        if (b.length != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (!equalsIgnoreDnsCase(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compare labels a equals b or a is suffix of b.
     *
     * @param a the type or subtype.
     * @param b the base type
     */
    public static boolean typeEqualsOrIsSubtype(@NonNull String[] a,
            @NonNull String[] b) {
        return MdnsUtils.equalsDnsLabelIgnoreDnsCase(a, b)
                || ((b.length == (a.length + 2))
                && MdnsUtils.equalsIgnoreDnsCase(b[1], MdnsConstants.SUBTYPE_LABEL)
                && MdnsRecord.labelsAreSuffix(a, b));
    }

    private static char toDnsLowerCase(char a) {
        return a >= 'A' && a <= 'Z' ? (char) (a + ('a' - 'A')) : a;
    }

    /*** Ensure that current running thread is same as given handler thread */
    public static void ensureRunningOnHandlerThread(@NonNull Handler handler) {
        if (handler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on Handler thread: " + Thread.currentThread().getName());
        }
    }

    /*** Check whether the target network is matched current network */
    public static boolean isNetworkMatched(@Nullable Network targetNetwork,
            @Nullable Network currentNetwork) {
        return targetNetwork == null || targetNetwork.equals(currentNetwork);
    }

    /**
     * Truncate a service name to up to maxLength UTF-8 bytes.
     */
    public static String truncateServiceName(@NonNull String originalName, int maxLength) {
        // UTF-8 is at most 4 bytes per character; return early in the common case where
        // the name can't possibly be over the limit given its string length.
        if (originalName.length() <= maxLength / 4) return originalName;

        final Charset utf8 = StandardCharsets.UTF_8;
        final CharsetEncoder encoder = utf8.newEncoder();
        final ByteBuffer out = ByteBuffer.allocate(maxLength);
        // encode will write as many characters as possible to the out buffer, and just
        // return an overflow code if there were too many characters (no need to check the
        // return code here, this method truncates the name on purpose).
        encoder.encode(CharBuffer.wrap(originalName), out, true /* endOfInput */);
        return new String(out.array(), 0, out.position(), utf8);
    }
}