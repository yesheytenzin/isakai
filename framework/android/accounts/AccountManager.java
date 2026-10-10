// SPDX-License-Identifier: GPL-3.0-or-later
package android.accounts;

/** Account manager stub: no accounts registered. */
public class AccountManager {
    public static final String LOGIN_ACCOUNTS_CHANGED_ACTION = "android.accounts.LOGIN_ACCOUNTS_CHANGED";

    public AccountManager() {
    }

    public String[] getAccountTypes() {
        return new String[0];
    }

    public String[] getAccountsByType(String type) {
        return new String[0];
    }

    public static AccountManager get(android.content.Context context) {
        return new AccountManager();
    }
}
