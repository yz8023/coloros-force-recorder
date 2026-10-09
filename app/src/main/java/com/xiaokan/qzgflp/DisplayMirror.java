package com.xiaokan.qzgflp;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.view.SurfaceControl;
import de.robv.android.xposed.XposedHelpers;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * jadx 产物的手工规范化版本：反编译器在失败路径上生成了无法编译的异常控制流
 * （`th = th3` 引用未声明变量），此处按原语义重排清理逻辑，保留完全一致的
 * Parcel 布局、反射目标、错误文案与抛出顺序。
 */
final class DisplayMirror {

    private static final String CLIENT = "android.gui.ISurfaceComposerClient";
    private static final String COMPOSER = "android.gui.ISurfaceComposer";
    private static final int CREATE_CONNECTION = 3;
    private static final int MIRROR_DISPLAY = 5;

    private static Method writeString8;

    private DisplayMirror() {
    }

    static void preflight() throws Exception {
        writeString8 = Parcel.class.getDeclaredMethod("writeString8", String.class);
        writeString8.setAccessible(true);
        Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
        SurfaceControl.Transaction.class.getDeclaredMethod("setLayerStack", SurfaceControl.class, Integer.TYPE);
        SurfaceControl.Transaction.class.getDeclaredMethod("show", SurfaceControl.class);
        SurfaceControl.Transaction.class.getDeclaredMethod("remove", SurfaceControl.class);
    }

    static SurfaceControl create(long physicalDisplayId, int layerStack) throws Exception {
        if (Process.myUid() != 1000) {
            throw new SecurityException("Display mirror requires system_server");
        }
        long identity = Binder.clearCallingIdentity();
        try {
            IBinder composer = (IBinder) XposedHelpers.callStaticMethod(
                    Class.forName("android.os.ServiceManager"), "getService", "SurfaceFlingerAIDL");
            if (composer == null || !COMPOSER.equals(composer.getInterfaceDescriptor())) {
                throw new IOException("Android 16 SurfaceFlingerAIDL interface unavailable");
            }

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(COMPOSER);
                if (!composer.transact(CREATE_CONNECTION, data, reply, 0)) {
                    throw new IOException("SurfaceFlinger createConnection unsupported");
                }
                reply.readException();
                IBinder client = reply.readStrongBinder();
                if (client == null || !CLIENT.equals(client.getInterfaceDescriptor())) {
                    throw new IOException("SurfaceFlinger client interface unavailable");
                }

                SurfaceControl created;
                try {
                    created = mirrorLayer(client, physicalDisplayId);
                } catch (Throwable failure) {
                    throw new IOException("Physical display source unavailable", failure);
                }
                try {
                    SurfaceControl.Transaction t = new SurfaceControl.Transaction();
                    try {
                        XposedHelpers.callMethod(t, "setLayerStack", created, layerStack);
                        XposedHelpers.callMethod(t, "show", created);
                        t.apply();
                        t.close();
                        return created;
                    } catch (Throwable inner) {
                        try {
                            t.close();
                        } catch (Throwable suppressed) {
                            inner.addSuppressed(suppressed);
                        }
                        throw inner;
                    }
                } catch (Throwable failure) {
                    remove(created);
                    throw new IOException("Physical display source unavailable", failure);
                }
            } finally {
                data.recycle();
                reply.recycle();
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private static SurfaceControl mirrorLayer(IBinder client, long physicalDisplayId) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(CLIENT);
            data.writeLong(physicalDisplayId);
            if (!client.transact(MIRROR_DISPLAY, data, reply, 0)) {
                throw new IOException("SurfaceFlinger mirrorDisplay unsupported");
            }
            reply.readException();
            if (reply.readInt() != 1) {
                throw new IOException("No CreateSurfaceResult");
            }
            int pos = reply.dataPosition();
            int size = reply.readInt();
            if (size < 20 || size > reply.dataSize() - pos) {
                throw new IOException("Unexpected Android 16 CreateSurfaceResult layout");
            }
            IBinder handle = reply.readStrongBinder();
            int id = reply.readInt();
            String name = reply.readString();
            int flags = reply.readInt();
            if (reply.dataPosition() > pos + size || handle == null || id < 0 || name == null) {
                throw new IOException("Invalid mirror layer result");
            }

            Parcel scData = Parcel.obtain();
            try {
                writeString8.invoke(scData, name);
                scData.writeInt(0);
                scData.writeInt(0);
                scData.writeInt(1);
                scData.writeStrongBinder(client);
                scData.writeStrongBinder(handle);
                scData.writeInt(id);
                scData.writeString(name);
                scData.writeInt(flags);
                scData.writeInt(0);
                scData.writeInt(0);
                scData.writeInt(0);
                scData.setDataPosition(0);
                SurfaceControl sc = (SurfaceControl) SurfaceControl.CREATOR.createFromParcel(scData);
                if (sc == null || !sc.isValid()) {
                    throw new IOException("Invalid mirrored SurfaceControl");
                }
                return sc;
            } finally {
                scData.recycle();
            }
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static void remove(SurfaceControl sc) {
        try {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            try {
                XposedHelpers.callMethod(t, "remove", sc);
                t.apply();
                t.close();
            } catch (Throwable inner) {
                try {
                    t.close();
                } catch (Throwable suppressed) {
                    inner.addSuppressed(suppressed);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            sc.release();
        } catch (Throwable ignored) {
        }
    }
}
