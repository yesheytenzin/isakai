// SPDX-License-Identifier: GPL-3.0-or-later
package android.security.keystore;

import java.security.spec.AlgorithmParameterSpec;
import javax.security.auth.x500.X500Principal;

/** Key-generation parameters shell. */
public final class KeyGenParameterSpec implements AlgorithmParameterSpec {
    private final String alias;
    private final int purposes;
    private KeyGenParameterSpec(Builder b) { alias = b.alias; purposes = b.purposes; }
    public String getKeystoreAlias() { return alias; }
    public int getPurposes() { return purposes; }
    public String[] getDigests() { return new String[0]; }
    public String[] getBlockModes() { return new String[0]; }
    public static final class Builder {
        private final String alias;
        private final int purposes;
        private String[] digests = new String[0];
        private String[] blockModes = new String[0];
        public Builder(String keystoreAlias, int purposes) { alias = keystoreAlias; this.purposes = purposes; }
        public Builder setDigests(String... digests) { this.digests = digests == null ? new String[0] : digests.clone(); return this; }
        public Builder setBlockModes(String... blockModes) { this.blockModes = blockModes == null ? new String[0] : blockModes.clone(); return this; }
        public Builder setCertificateSubject(X500Principal subject) { return this; }
        public Builder setCertificateSerialNumber(java.math.BigInteger serial) { return this; }
        public KeyGenParameterSpec build() { return new KeyGenParameterSpec(this); }
    }
}
