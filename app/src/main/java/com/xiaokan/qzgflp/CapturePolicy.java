package com.xiaokan.qzgflp;

/* JADX INFO: loaded from: classes.dex */
final class CapturePolicy {
    CapturePolicy() {
    }

    static boolean eligible(String str, String str2, int i, int i2, int i3, int i4, boolean z, int i5) {
        return "com.oplus.screenrecorder".equals(str) && "OPLUSScreenRecording".equals(str2) && i == 0 && i2 == 0 && i3 > 0 && i3 == i4 && !z && i5 == -1;
    }
}
