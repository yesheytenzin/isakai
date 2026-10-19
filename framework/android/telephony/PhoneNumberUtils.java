// SPDX-License-Identifier: GPL-3.0-or-later
package android.telephony;

import java.util.regex.Pattern;

/**
 * Phone-number predicates. AOSP shape: {@link #isGlobalPhoneNumber} accepts a
 * leading plus and the separators AOSP's dialable pattern allows, matching
 * the same regex AOSP uses before offering a number to the dialer.
 */
public final class PhoneNumberUtils {
    private static final Pattern GLOBAL_PHONE_NUMBER_PATTERN = Pattern.compile("[\\+]?[0-9.-]+");

    private PhoneNumberUtils() {}

    public static boolean isGlobalPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return false;
        return GLOBAL_PHONE_NUMBER_PATTERN.matcher(phoneNumber).matches();
    }
}
