// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware;

/** IR blaster; absent shell. */
public class ConsumerIrManager {
    public static class CarrierFrequencyRange {
        public CarrierFrequencyRange(int min, int max) { }
        public int getMinFrequency() { return 0; }
        public int getMaxFrequency() { return 0; }
    }
    public boolean hasIrEmitter() { return false; }
    public CarrierFrequencyRange[] getCarrierFrequencies() { return new CarrierFrequencyRange[0]; }
    public void transmit(int carrierFrequency, int[] pattern) { }
}
