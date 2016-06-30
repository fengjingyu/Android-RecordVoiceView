package com.xiaocoder.recordvoice;

import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * @author xiaocoder on 2016/6/29 21:17
 * @email fengjingyu@foxmail.com
 * @description
 */
public class UtilFile {

    public static boolean isSDcardExist() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    public static File createDirInAndroid(Context context, String dirName) {
        try {
            if (isSDcardExist()) {
                return createDirInSDCard(dirName);
            } else {
                return createDirInside(context, dirName);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File createDirInSDCard(String dirName) {
        File dir = null;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {

            if (dirName == null || dirName.trim().length() == 0) {
                return Environment.getExternalStorageDirectory();
            }
            String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + dirName;
            dir = new File(dirPath);

            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
        return dir;
    }

    public static File createDirInside(Context context, String dirName) {
        File dir = null;
        if (dirName == null || dirName.trim().length() == 0) {
            return context.getCacheDir();
        }
        String dirPath = context.getCacheDir() + File.separator + dirName;
        dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
