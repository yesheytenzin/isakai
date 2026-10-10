// SPDX-License-Identifier: GPL-3.0-or-later
package android.security.identity;

/** Identity credential shell: no provisioning. */
public abstract class IdentityCredential implements AutoCloseable {
    public abstract byte[] getCredentialKeyPair();
    public abstract void deleteCredential();
    @Override public void close() { }
}
