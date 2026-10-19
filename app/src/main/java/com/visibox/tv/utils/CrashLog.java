package com.visibox.tv.utils;

import android.content.Context;
import android.util.Log;

import com.visibox.tv.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 崩溃现场的本地留档。
 *
 * <p>以前崩溃信息只活在 {@code CrashActivity} 的弹窗里，用户重启一次就没了，
 * 反馈「闪退」时完全拿不到堆栈。这里在崩溃发生时落一份文件，设置页可以随时查看/复制。
 *
 * <p>只保留最近 {@link #MAX_FILES} 份，避免长期运行把存储写满。
 */
public final class CrashLog {

    private static final String TAG = "CrashLog";
    private static final String DIR = "crash";
    private static final int MAX_FILES = 10;
    private static final String PREFIX = "crash-";
    private static final String SUFFIX = ".log";

    private CrashLog() {
    }

    public static File dir() {
        return dir(App.get());
    }

    public static File dir(Context context) {
        File file = new File(context.getFilesDir(), DIR);
        if (!file.exists() && !file.mkdirs()) Log.w(TAG, "cannot create crash dir: " + file);
        return file;
    }

    /**
     * 写入一份崩溃记录，返回落盘文件（失败返回 null，绝不抛出——本身就在崩溃路径上）。
     */
    public static File save(String content) {
        if (content == null || content.isEmpty()) return null;
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File file = new File(dir(), PREFIX + stamp + SUFFIX);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write(content);
            }
            trim();
            return file;
        } catch (Throwable e) {
            Log.w(TAG, "save crash log failed", e);
            return null;
        }
    }

    /**
     * 全部崩溃记录，按时间倒序（最新的在前）。
     */
    public static List<File> list() {
        File[] files = dir().listFiles((d, name) -> name.startsWith(PREFIX) && name.endsWith(SUFFIX));
        if (files == null) return new ArrayList<>();
        List<File> result = new ArrayList<>(Arrays.asList(files));
        result.sort(Comparator.comparingLong(File::lastModified).reversed());
        return result;
    }

    public static int count() {
        return list().size();
    }

    /**
     * 最近一次崩溃的内容，没有则返回空串。
     */
    public static String latest() {
        List<File> files = list();
        return files.isEmpty() ? "" : read(files.get(0));
    }

    public static String read(File file) {
        // 用传统 IO 而不是 Files.readAllBytes：后者在 API 26 以下要靠 desugaring，
        // 崩溃日志读取路径上不值得赌这个兼容性
        try (java.io.InputStream in = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            Log.w(TAG, "read crash log failed: " + file, e);
            return "";
        }
    }

    /**
     * 把所有记录拼成一段文本，供「复制全部」用。
     */
    public static String all() {
        StringBuilder builder = new StringBuilder();
        for (File file : list()) {
            builder.append("===== ").append(file.getName()).append(" =====\n");
            builder.append(read(file)).append('\n');
        }
        return builder.toString();
    }

    public static void clear() {
        for (File file : list()) if (!file.delete()) Log.w(TAG, "delete failed: " + file);
    }

    /**
     * 超出上限时删掉最旧的几份。
     */
    private static void trim() {
        List<File> files = list();
        for (int i = MAX_FILES; i < files.size(); i++) files.get(i).delete();
    }
}
