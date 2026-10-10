package com.xiaokan.qzgflp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 轻量 DEX 方法定位器（替代 DexKit 的最小实现，纯 Java 零依赖）。
 * 在目标进程内解析宿主 APK 的 classes*.dex，按「方法名字符串常量引用 / 调用关系 /
 * 参数个数 / 返回类型」定位混淆方法，再用反射解析为 Method。
 *
 * 支持：const-string(-jumbo) 字符串集合、invoke-* 调用集合与被调用者（caller）反查。
 */
public final class DexLocator {

    /** 候选方法信息（dex 层） */
    public static final class Candidate {
        public String name;
        public int paramCount;
        public boolean returnsVoid;
        public String returnType;
        public List<String> paramTypes = new ArrayList<>();
        public Set<String> strings = new HashSet<>();
        public Set<Integer> invokes = new HashSet<>();
        public Set<Integer> callers = new HashSet<>();
        public Set<String> callerNames = new HashSet<>();
        public Set<String> fieldTypes = new HashSet<>();

        public boolean hasStrings(String... all) {
            for (String s : all) if (!strings.contains(s)) return false;
            return true;
        }

        public boolean hasFieldTypes(String... all) {
            for (String s : all) if (!fieldTypes.contains(s)) return false;
            return true;
        }

        public boolean hasCallerNamed(String name) {
            return callerNames.contains(name);
        }
    }

    /** 规则：返回 true 表示命中 */
    public interface Rule {
        boolean accept(Candidate c);
    }

    /** 按类名 + 规则定位，返回反射 Method（可能多个命中，全返回） */
    public static List<Method> find(ClassLoader cl, String className, Rule rule) {
        List<Method> out = new ArrayList<>();
        try {
            long t0 = android.os.SystemClock.uptimeMillis();
            Apk apks = openClassApk(cl);
            if (apks == null) return out;
            String desc = "L" + className.replace('.', '/') + ";";
            for (byte[] dex : apks.dexBytes) {
                Dex d = new Dex(dex);
                int typeIdx = d.findTypeIdx(desc);
                if (typeIdx < 0) continue;
                for (Candidate c : d.methodsOf(typeIdx)) {
                    if (rule.accept(c)) {
                        Method m = resolve(cl, className, c.name, c.paramCount);
                        if (m != null) out.add(m);
                    }
                }
                if (!out.isEmpty()) {
                    android.util.Log.d("ForceCapture", "DexLocator hit " + className
                            + " in " + (android.os.SystemClock.uptimeMillis() - t0) + "ms");
                    break;
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("ForceCapture", "DexLocator failed: " + className, t);
        }
        return out;
    }

    /** 全 dex 扫描：方法规则命中即返回（首个命中类优先） */
    public static List<Method> findGlobal(ClassLoader cl, Rule rule) {
        List<Method> out = new ArrayList<>();
        try {
            long t0 = android.os.SystemClock.uptimeMillis();
            Apk apks = openClassApk(cl);
            if (apks == null) return out;
            for (byte[] dex : apks.dexBytes) {
                Dex d = new Dex(dex);
                for (int i = 0; i < d.classDefsSize; i++) {
                    int off = d.classDefsOff + i * 32;
                    int typeIdx = d.u4(off);
                    String td = d.typeDesc(typeIdx);
                    if (td == null || td.startsWith("Landroid/") || td.startsWith("Ljava/")) continue;
                    String cn = td.substring(1, td.length() - 1).replace('/', '.');
                    for (Candidate c : d.methodsOf(typeIdx)) {
                        if (rule.accept(c)) {
                            Method m = resolve(cl, cn, c.name, c.paramCount);
                            if (m != null) out.add(m);
                        }
                    }
                }
                if (!out.isEmpty()) {
                    android.util.Log.d("ForceCapture", "DexLocator global hit in "
                            + (android.os.SystemClock.uptimeMillis() - t0) + "ms");
                    break;
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("ForceCapture", "DexLocator global failed", t);
        }
        return out;
    }

    /** 按简单类名（混淆/改名容错）+ 规则定位 */
    public static List<Method> findBySimpleName(ClassLoader cl, String simpleName, Rule rule) {
        List<Method> out = new ArrayList<>();
        try {
            Apk apks = openClassApk(cl);
            if (apks == null) return out;
            String suffix = "/" + simpleName + ";";
            for (byte[] dex : apks.dexBytes) {
                Dex d = new Dex(dex);
                for (int i = 0; i < d.classDefsSize; i++) {
                    int off = d.classDefsOff + i * 32;
                    int typeIdx = d.u4(off);
                    String td = d.typeDesc(typeIdx);
                    if (td == null || !td.endsWith(suffix)) continue;
                    String cn = td.substring(1, td.length() - 1).replace('/', '.');
                    for (Candidate c : d.methodsOf(typeIdx)) {
                        if (rule.accept(c)) {
                            Method m = resolve(cl, cn, c.name, c.paramCount);
                            if (m != null) out.add(m);
                        }
                    }
                    if (!out.isEmpty()) return out;
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("ForceCapture", "DexLocator simpleName failed: " + simpleName, t);
        }
        return out;
    }

    public static Method first(ClassLoader cl, String className, Rule rule) {
        List<Method> l = find(cl, className, rule);
        return l.isEmpty() ? null : l.get(0);
    }

    /** 在已知 Class 上按规则定位（dex 描述符取自 Class 名） */
    public static List<Method> findInClass(ClassLoader cl, Class<?> clazz, Rule rule) {
        List<Method> out = new ArrayList<>();
        try {
            Apk apks = openClassApk(cl);
            if (apks == null) return out;
            String desc = "L" + clazz.getName().replace('.', '/') + ";";
            for (byte[] dex : apks.dexBytes) {
                Dex d = new Dex(dex);
                int typeIdx = d.findTypeIdx(desc);
                if (typeIdx < 0) continue;
                for (Candidate c : d.methodsOf(typeIdx)) {
                    if (!rule.accept(c)) continue;
                    Method m = resolveIn(clazz, c.name, c.paramCount);
                    if (m != null) out.add(m);
                }
                if (!out.isEmpty()) break;
            }
        } catch (Throwable t) {
            android.util.Log.w("ForceCapture", "DexLocator class failed: " + clazz.getName(), t);
        }
        return out;
    }

    public static Method firstInClass(ClassLoader cl, Class<?> clazz, Rule rule) {
        List<Method> l = findInClass(cl, clazz, rule);
        return l.isEmpty() ? null : l.get(0);
    }

    public static Method firstBySimpleName(ClassLoader cl, String simpleName, Rule rule) {
        List<Method> l = findBySimpleName(cl, simpleName, rule);
        return l.isEmpty() ? null : l.get(0);
    }

    private static Method resolveIn(Class<?> clazz, String name, int paramCount) {
        Method fallback = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterTypes().length == paramCount) return m;
            if (fallback == null) fallback = m;
        }
        return fallback;
    }

    public static Method firstGlobal(ClassLoader cl, Rule rule) {
        List<Method> l = findGlobal(cl, rule);
        return l.isEmpty() ? null : l.get(0);
    }

    /** 反射解析：按名 + 参数个数（找不到精确个数时放宽到同名首个） */
    private static Method resolve(ClassLoader cl, String className, String name, int paramCount) {
        try {
            Class<?> clazz = cl.loadClass(className);
            Method fallback = null;
            for (Method m : clazz.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterTypes().length == paramCount) return m;
                if (fallback == null) fallback = m;
            }
            return fallback;
        } catch (Throwable t) {
            return null;
        }
    }

    // ── 定位宿主 APK ──

    private static final class Apk {
        List<byte[]> dexBytes = new ArrayList<>();
    }

    private static Apk openClassApk(ClassLoader cl) throws Exception {
        String path = null;
        try {
            Object pathList = field(cl, "pathList");
            Object[] elements = (Object[]) field(pathList, "dexElements");
            for (Object e : elements) {
                Object p = tryField(e, "path");
                if (p instanceof File) {
                    path = ((File) p).getAbsolutePath();
                    break;
                }
                Object df = tryField(e, "dexFile");
                if (df != null) {
                    Object mFileName = tryField(df, "mFileName");
                    if (mFileName instanceof String) {
                        path = (String) mFileName;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (path == null) {
            try {
                Object app = Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null);
                if (app instanceof android.content.Context) {
                    path = ((android.content.Context) app).getApplicationInfo().sourceDir;
                }
            } catch (Throwable ignored) {
            }
        }
        if (path == null || !new File(path).canRead()) return null;
        Apk apk = new Apk();
        try (ZipFile z = new ZipFile(path)) {
            for (int i = 0; i < 9; i++) {
                String entry = i == 0 ? "classes.dex" : ("classes" + (i + 1) + ".dex");
                ZipEntry e = z.getEntry(entry);
                if (e == null) continue;
                try (InputStream in = z.getInputStream(e)) {
                    apk.dexBytes.add(readAll(in));
                }
            }
        }
        return apk.dexBytes.isEmpty() ? null : apk;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    private static Object field(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object tryField(Object o, String name) {
        try {
            return field(o, name);
        } catch (Throwable t) {
            return null;
        }
    }

    // ── DEX 解析 ──

    private static final class Dex {
        final byte[] b;
        final int stringIdsSize, stringIdsOff, typeIdsSize, typeIdsOff;
        final int protoIdsSize, protoIdsOff, fieldIdsOff, methodIdsSize, methodIdsOff;
        final int classDefsSize, classDefsOff;
        final String[] strings;       // 懒解码：null = 未解码
        final String[] typeDescs;
        final int[][] protos;         // [returnTypeIdx, paramOffset, paramSize]
        final int[][] methodIds;      // [classIdx, protoIdx, nameIdx]

        Dex(byte[] data) {
            b = data;
            stringIdsSize = u4(0x38);
            stringIdsOff = u4(0x3C);
            typeIdsSize = u4(0x40);
            typeIdsOff = u4(0x44);
            protoIdsSize = u4(0x48);
            protoIdsOff = u4(0x4C);
            u4(0x50); // fieldIdsSize
            fieldIdsOff = u4(0x54);
            methodIdsSize = u4(0x58);
            methodIdsOff = u4(0x5C);
            classDefsSize = u4(0x60);
            classDefsOff = u4(0x64);
            strings = new String[stringIdsSize];
            typeDescs = new String[typeIdsSize];
            protos = new int[protoIdsSize][];
            methodIds = new int[methodIdsSize][];
        }

        int u4(int off) {
            return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                    | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
        }

        int u2(int off) {
            return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
        }

        int uleb(int[] pos) {
            int p = pos[0];
            int result = b[p] & 0x7F;
            int shift = 7;
            while ((b[p] & 0x80) != 0) {
                p++;
                result |= (b[p] & 0x7F) << shift;
                shift += 7;
            }
            pos[0] = p + 1;
            return result;
        }

        String string(int idx) {
            if (idx < 0 || idx >= stringIdsSize) return null;
            if (strings[idx] != null) return strings[idx];
            int off = u4(stringIdsOff + idx * 4);
            int[] pos = {off};
            uleb(pos); // utf16 size
            ByteArrayOutputStream bo = new ByteArrayOutputStream(32);
            int p = pos[0];
            while (b[p] != 0) {
                bo.write(b[p]);
                p++;
            }
            String s = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            strings[idx] = s;
            return s;
        }

        String typeDesc(int idx) {
            if (idx < 0 || idx >= typeIdsSize) return null;
            if (typeDescs[idx] != null) return typeDescs[idx];
            typeDescs[idx] = string(u4(typeIdsOff + idx * 4));
            return typeDescs[idx];
        }

        int findTypeIdx(String desc) {
            int lo = 0, hi = typeIdsSize - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                String d = typeDesc(mid);
                int cmp = d == null ? 1 : d.compareTo(desc);
                if (cmp == 0) return mid;
                if (cmp < 0) lo = mid + 1;
                else hi = mid - 1;
            }
            return -1;
        }

        int[] proto(int idx) {
            if (idx < 0 || idx >= protoIdsSize) return null;
            if (protos[idx] != null) return protos[idx];
            int off = protoIdsOff + idx * 12;
            int shorty = u4(off);
            int returnIdx = u4(off + 4);
            int paramsOff = u4(off + 8);
            int paramSize = 0;
            int[] params = null;
            if (paramsOff != 0) {
                paramSize = u4(paramsOff);
                params = new int[paramSize];
                for (int i = 0; i < paramSize; i++) params[i] = u2(paramsOff + 4 + i * 2);
            }
            int[] v = {returnIdx, paramSize, params == null ? 0 : 1};
            protos[idx] = v;
            return v;
        }

        int[] methodId(int idx) {
            if (idx < 0 || idx >= methodIdsSize) return null;
            if (methodIds[idx] != null) return methodIds[idx];
            int off = methodIdsOff + idx * 8;
            int[] v = {u2(off), u2(off + 2), u4(off + 4)};
            methodIds[idx] = v;
            return v;
        }

        boolean returnsVoid(int protoIdx) {
            int[] p = proto(protoIdx);
            return p != null && "V".equals(typeDesc(p[0]));
        }

        int paramCount(int protoIdx) {
            int[] p = proto(protoIdx);
            return p == null ? -1 : p[1];
        }

        /** 指定类的全部带码方法（含调用关系反查） */
        List<Candidate> methodsOf(int classTypeIdx) {
            int classDataOff = -1;
            for (int i = 0; i < classDefsSize; i++) {
                int off = classDefsOff + i * 32;
                if (u4(off) == classTypeIdx) {
                    classDataOff = u4(off + 24);
                    break;
                }
            }
            List<Candidate> out = new ArrayList<>();
            if (classDataOff <= 0) return out;
            int[] pos = {classDataOff};
            int staticFields = uleb(pos);
            int instanceFields = uleb(pos);
            int directMethods = uleb(pos);
            int virtualMethods = uleb(pos);
            int skip = staticFields + instanceFields;
            for (int i = 0; i < skip; i++) {
                uleb(pos);
                uleb(pos);
            }
            int prev = 0;
            int[] methodIdxs = new int[directMethods + virtualMethods];
            int[] codeOffs = new int[directMethods + virtualMethods];
            int n = 0;
            for (int round = 0; round < 2; round++) {
                int count = round == 0 ? directMethods : virtualMethods;
                prev = 0;
                for (int i = 0; i < count; i++) {
                    int diff = uleb(pos);
                    int idx = prev + diff;
                    prev = idx;
                    uleb(pos); // access flags
                    int codeOff = uleb(pos);
                    methodIdxs[n] = idx;
                    codeOffs[n] = codeOff;
                    n++;
                }
            }
            // 先全类扫描调用关系（caller 反查需要）
            Map<Integer, Set<Integer>> callers = new HashMap<>();
            for (int i = 0; i < n; i++) {
                if (codeOffs[i] <= 0) continue;
                Set<Integer> inv = scanInvokes(codeOffs[i]);
                for (int target : inv) {
                    Set<Integer> s = callers.get(target);
                    if (s == null) {
                        s = new HashSet<>();
                        callers.put(target, s);
                    }
                    s.add(methodIdxs[i]);
                }
            }
            for (int i = 0; i < n; i++) {
                if (codeOffs[i] <= 0) continue;
                int midx = methodIdxs[i];
                int[] mi = methodId(midx);
                Candidate c = new Candidate();
                c.name = string(mi[2]);
                int protoIdx = mi[1];
                c.paramCount = paramCount(protoIdx);
                c.returnsVoid = returnsVoid(protoIdx);
                int[] pr = proto(protoIdx);
                if (pr != null) {
                    String rt = typeDesc(pr[0]);
                    c.returnType = rt == null ? "" : rt;
                    if (pr[2] == 1) {
                        int pOff = protoIdsOff + protoIdx * 12;
                        int paramsOff = u4(pOff + 8);
                        for (int k = 0; k < pr[1]; k++) {
                            String pt = typeDesc(u2(paramsOff + 4 + k * 2));
                            c.paramTypes.add(pt == null ? "" : pt);
                        }
                    }
                }
                c.strings = scanStrings(codeOffs[i]);
                c.invokes = scanInvokes(codeOffs[i]);
                c.fieldTypes = scanFieldTypes(codeOffs[i]);
                Set<Integer> cl2 = callers.get(midx);
                if (cl2 != null) {
                    c.callers.addAll(cl2);
                    for (int cm : cl2) {
                        int[] cmi = methodId(cm);
                        if (cmi != null) c.callerNames.add(orEmpty(string(cmi[2])));
                    }
                }
                out.add(c);
            }
            return out;
        }

        /** dalvik 指令长度（码元单位，2 字节）；用于线性扫描对齐 */
        static int sizeOf(int op) {
            if (op == 0x02 || op == 0x05 || op == 0x08 || op == 0x13 || op == 0x15
                    || op == 0x16 || op == 0x19 || op == 0x1a || op == 0x1c || op == 0x1f
                    || op == 0x20 || op == 0x22 || op == 0x23 || op == 0x29
                    || (op >= 0x2d && op <= 0x37) || (op >= 0x38 && op <= 0x3d)
                    || (op >= 0x44 && op <= 0x5f) || (op >= 0x60 && op <= 0x6d)
                    || (op >= 0x90 && op <= 0xaf) || (op >= 0xd0 && op <= 0xe2)
                    || op == 0xfe || op == 0xff) return 2;
            if (op == 0x03 || op == 0x06 || op == 0x09 || op == 0x14 || op == 0x17
                    || op == 0x1b || op == 0x24 || op == 0x25 || op == 0x26 || op == 0x2a
                    || (op >= 0x2b && op <= 0x2c) || (op >= 0x6e && op <= 0x72)
                    || (op >= 0x74 && op <= 0x78) || op == 0xfc || op == 0xfd) return 3;
            if (op == 0x18 || op == 0xfa || op == 0xfb) return op == 0x18 ? 5 : 4;
            return 1;
        }

        Set<String> scanStrings(int codeOff) {
            Set<String> out = new HashSet<>();
            int insnsOff = codeOff + 16;
            int insnsSize = u4(codeOff + 12);
            int p = insnsOff;
            int end = insnsOff + insnsSize;
            while (p < end) {
                int unit = u2(p);
                int op = unit & 0xFF;
                if (op == 0x1a) {
                    if (p + 2 < end) out.add(orEmpty(string(u2(p + 2))));
                } else if (op == 0x1b) {
                    if (p + 4 < end) {
                        int idx = u2(p + 2) | (u2(p + 4) << 16);
                        out.add(orEmpty(string(idx)));
                    }
                } else if (op == 0x00 && (unit == 0x0100 || unit == 0x0200 || unit == 0x0300)) {
                    break; // payload 数据区，正常线性流不会到达，防御性跳出
                }
                p += sizeOf(op) * 2;
            }
            return out;
        }

        Set<Integer> scanInvokes(int codeOff) {
            Set<Integer> out = new HashSet<>();
            int insnsOff = codeOff + 16;
            int insnsSize = u4(codeOff + 12);
            int p = insnsOff;
            int end = insnsOff + insnsSize;
            while (p < end) {
                int unit = u2(p);
                int op = unit & 0xFF;
                if ((op >= 0x6e && op <= 0x72) || (op >= 0x74 && op <= 0x78)) {
                    if (p + 2 < end) out.add(u2(p + 2));
                } else if (op == 0x00 && (unit == 0x0100 || unit == 0x0200 || unit == 0x0300)) {
                    break;
                }
                p += sizeOf(op) * 2;
            }
            return out;
        }

        /** iget/iput/sget/sput 引用的字段类型描述符集合 */
        Set<String> scanFieldTypes(int codeOff) {
            Set<String> out = new HashSet<>();
            int insnsOff = codeOff + 16;
            int insnsSize = u4(codeOff + 12);
            int p = insnsOff;
            int end = insnsOff + insnsSize;
            while (p < end) {
                int unit = u2(p);
                int op = unit & 0xFF;
                if ((op >= 0x52 && op <= 0x6d)) {
                    if (p + 2 < end) {
                        int fidx = u2(p + 2);
                        int foff = fieldIdsOff + fidx * 8;
                        String t = typeDesc(u2(foff + 2));
                        if (t != null) out.add(t);
                    }
                } else if (op == 0x00 && (unit == 0x0100 || unit == 0x0200 || unit == 0x0300)) {
                    break;
                }
                p += sizeOf(op) * 2;
            }
            return out;
        }

        private String orEmpty(String s) {
            return s == null ? "" : s;
        }
    }

    private DexLocator() {
    }
}
