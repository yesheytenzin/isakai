// SPDX-License-Identifier: Apache-2.0
/*
 * Copyright 2006, The Android Open Source Project
 * Derived from AOSP default resources, adapted to a Java lookup table.
 * Licensed under the Apache License, Version 2.0 (see LICENSES/Apache-2.0.txt).
 */
package android.content.res;
/** AOSP Android 13 default public color values; wallpaper overlays are unavailable.
 * Source: https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/core/res/res/values/colors.xml
 * IDs: https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/core/res/res/values/public-final.xml
 */
final class SystemColors {
    private SystemColors() {}
    static Integer color(int id) {
        return switch(id) {
            case 0x01060000 -> 0xffaaaaaa; // darker_gray
            case 0x0106000b -> 0xffffffff; // white
            case 0x0106000c -> 0xff000000; // black
            case 0x0106000d -> 0x00000000; // transparent
            case 0x0106000e -> 0xff000000; // background_dark
            case 0x0106000f -> 0xffffffff; // background_light
            case 0x0106001c -> 0x00000000; // system_notification_accent_color
            case 0x0106001d -> 0xffffffff; // system_neutral1_0
            case 0x0106001e -> 0xffFCFCFF; // system_neutral1_10
            case 0x0106001f -> 0xffF0F0F3; // system_neutral1_50
            case 0x01060020 -> 0xffE1E3E5; // system_neutral1_100
            case 0x01060021 -> 0xffC5C7C9; // system_neutral1_200
            case 0x01060022 -> 0xffAAABAE; // system_neutral1_300
            case 0x01060023 -> 0xff8F9193; // system_neutral1_400
            case 0x01060024 -> 0xff747679; // system_neutral1_500
            case 0x01060025 -> 0xff5C5F61; // system_neutral1_600
            case 0x01060026 -> 0xff454749; // system_neutral1_700
            case 0x01060027 -> 0xff2E3133; // system_neutral1_800
            case 0x01060028 -> 0xff191C1E; // system_neutral1_900
            case 0x01060029 -> 0xff000000; // system_neutral1_1000
            case 0x0106002a -> 0xffffffff; // system_neutral2_0
            case 0x0106002b -> 0xffF9FCFF; // system_neutral2_10
            case 0x0106002c -> 0xffEBF1F8; // system_neutral2_50
            case 0x0106002d -> 0xffDCE3E9; // system_neutral2_100
            case 0x0106002e -> 0xffC0C7CD; // system_neutral2_200
            case 0x0106002f -> 0xffA5ACB2; // system_neutral2_300
            case 0x01060030 -> 0xff8A9297; // system_neutral2_400
            case 0x01060031 -> 0xff70777C; // system_neutral2_500
            case 0x01060032 -> 0xff585F65; // system_neutral2_600
            case 0x01060033 -> 0xff40484D; // system_neutral2_700
            case 0x01060034 -> 0xff2A3136; // system_neutral2_800
            case 0x01060035 -> 0xff161C20; // system_neutral2_900
            case 0x01060036 -> 0xff000000; // system_neutral2_1000
            case 0x01060037 -> 0xffffffff; // system_accent1_0
            case 0x01060038 -> 0xffF9FCFF; // system_accent1_10
            case 0x01060039 -> 0xffE0F3FF; // system_accent1_50
            case 0x0106003a -> 0xffC1E8FF; // system_accent1_100
            case 0x0106003b -> 0xff76D1FF; // system_accent1_200
            case 0x0106003c -> 0xff4BB6E8; // system_accent1_300
            case 0x0106003d -> 0xff219BCC; // system_accent1_400
            case 0x0106003e -> 0xff007FAC; // system_accent1_500
            case 0x0106003f -> 0xff00668B; // system_accent1_600
            case 0x01060040 -> 0xff004C69; // system_accent1_700
            case 0x01060041 -> 0xff003549; // system_accent1_800
            case 0x01060042 -> 0xff001E2C; // system_accent1_900
            case 0x01060043 -> 0xff000000; // system_accent1_1000
            case 0x01060044 -> 0xffffffff; // system_accent2_0
            case 0x01060045 -> 0xffF9FCFF; // system_accent2_10
            case 0x01060046 -> 0xffE0F3FF; // system_accent2_50
            case 0x01060047 -> 0xffD1E5F4; // system_accent2_100
            case 0x01060048 -> 0xffB5CAD7; // system_accent2_200
            case 0x01060049 -> 0xff9AAEBB; // system_accent2_300
            case 0x0106004a -> 0xff8094A0; // system_accent2_400
            case 0x0106004b -> 0xff657985; // system_accent2_500
            case 0x0106004c -> 0xff4E616C; // system_accent2_600
            case 0x0106004d -> 0xff374955; // system_accent2_700
            case 0x0106004e -> 0xff20333D; // system_accent2_800
            case 0x0106004f -> 0xff091E28; // system_accent2_900
            case 0x01060050 -> 0xff000000; // system_accent2_1000
            case 0x01060051 -> 0xffffffff; // system_accent3_0
            case 0x01060052 -> 0xffFFFBFF; // system_accent3_10
            case 0x01060053 -> 0xffF5EEFF; // system_accent3_50
            case 0x01060054 -> 0xffE6DEFF; // system_accent3_100
            case 0x01060055 -> 0xffCAC1EA; // system_accent3_200
            case 0x01060056 -> 0xffAEA6CE; // system_accent3_300
            case 0x01060057 -> 0xff938CB1; // system_accent3_400
            case 0x01060058 -> 0xff787296; // system_accent3_500
            case 0x01060059 -> 0xff605A7C; // system_accent3_600
            case 0x0106005a -> 0xff484264; // system_accent3_700
            case 0x0106005b -> 0xff322C4C; // system_accent3_800
            case 0x0106005c -> 0xff1D1736; // system_accent3_900
            case 0x0106005d -> 0xff000000; // system_accent3_1000
            default -> null;
        };
    }
}
