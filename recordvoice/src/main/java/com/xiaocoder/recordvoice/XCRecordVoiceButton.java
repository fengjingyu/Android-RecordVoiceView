package com.xiaocoder.recordvoice;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author xiaocoder
 * @email fengjingyu@foxmail.com
 * @description 可以录音的button
 */
public class XCRecordVoiceButton extends Button {

    private Executor threads = Executors.newCachedThreadPool();

    private MediaRecorder media_recorder;
    /**
     * 录音成功返回的文件存储在哪个目录里
     */
    private File save_dir;
    /**
     * 录音成功返回的文件
     */
    private File save_file;
    /**
     * 时间的压缩参数
     * （比如显示60秒，RATIO为1 实际运行的是60秒）
     * （比如显示60秒，RATIO为0.75 实际运行的是45秒）
     */
    //public static double COMPRESS_RATIO = 0.75;
    public static double COMPRESS_RATIO = 1;
    /**
     * 录音最短时间的限制，毫秒
     * 会用 录音开始时间-结束时间 与 MIN_TIME 比较，是否小于了录音时间的限制
     */
    public static int MIN_TIME = 3000;
    /**
     * 录音最长时间的限制，毫秒.
     * 会用 录音开始时间-结束时间 与 MAX_TIME 比较，是否超过了录音时间的限制
     */
    public static int MAX_TIME = 45000;
    /**
     * 每隔多少时间，time减一次，毫秒
     */
    public static final int SLEEP_TIME = (int) (1000 * COMPRESS_RATIO);
    /**
     * 从哪一个时间开始倒计时（线程里的time的初始值用的就是这个），秒。
     * <p/>
     * 有的时候可能显示的是60秒，但是真实可以录制的时间只给45秒
     */
    public static final int FAKE_TIME = (int) (MAX_TIME / COMPRESS_RATIO / 1000);
    /**
     * 记录每次录音的开始时间
     */
    private long recoder_start_time;
    /**
     * 记录每次录音的结束时间
     */
    private long recoder_end_time;
    /**
     * 每一次按下button时，默认没有移出边界
     */
    private boolean boundaryOut;
    /**
     * 防止快速点击，做了一个时间间隔的限制，及上次点击与这次点击的时间间隔必须大于 CLICK_LIMIT 毫秒，才有效
     */
    private long record_last_time;
    /**
     * 毫秒,防止快速连续点击
     */
    public static final int CLICK_LIMIT = 200;
    /**
     * 用时间间隔判断系统是否弹出权限框(之前调了200，但是小米3不行)，
     */
    public static final int SYSTME_HINT_TIME = 500;

    public interface OnButtonStatus {

        /**
         * 当录音超时的时候，且没有移除touch范围时，是否回传file
         *
         * @return true 当超时且touch在button范围内时，回传file
         * false，当超时（不论touch是否在button范围内），都不回传file
         */
        boolean isEffectiveVoiceFileWhenTimeOut();

        /**
         * 当触摸时，最先调用该方法，即onTouchEvent之后，downAction判断之前
         * <p/>
         * 是否拦截此次操作
         *
         * @return true:拦截（不可发送），false:不拦截（可发送）
         */
        boolean isIntercept();

        /**
         * downAction刚触发时(此时未做任何关于录音的代码)，立刻回调该方法
         */
        void onTouchDown();

        /**
         * 每隔SLEEP_TIME秒，回调该方法一次，time初始值是FAKE_TIME
         */
        void onUpdateTime(int time);

        /**
         * 开始倒计时。建议：show你的dialog，可以给“松开发送”的提示
         */
        void onStart();

        /**
         * 移出button范围，但没有up,这里建议：可以给“松开取消发送”的提示
         */
        void onMoveOut();

        /**
         * 移出button范围，但没有up，这时又移入button，没有up，这里建议：可以给“松开发送”的提示
         */
        void onMoveIn();

        /**
         * 录音成功时回调，传回文件 和 时间（不包括录音时间太少的文件）
         */
        void onSuccessFile(File file, double time);

        /**
         * 这里建议：close你的dialog
         */
        void onEnd(RecoderStop stop);

        enum RecoderStop {
            FORCE_STOP,
            CANCLE_STOP,
            LESS_TIME_STOP,
            OUT_TIME_STOP,
            EXCEPTION_STOP,
            NORMAL_STOP
        }
    }

    private Handler handler = new Handler();

    private OnButtonStatus listener;

    public void setOnButtonStatus(OnButtonStatus listener) {
        this.listener = listener;
    }

    public XCRecordVoiceButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        timeRunnable = new TimeRunnable();
    }

    public XCRecordVoiceButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        timeRunnable = new TimeRunnable();
    }

    public XCRecordVoiceButton(Context context) {
        super(context);
        timeRunnable = new TimeRunnable();
    }

    public class TimeRunnable implements Runnable {

        public void update(int time) {
            this.time = time;
        }

        int time;

        @Override
        public void run() {
            if (listener != null) {
                listener.onUpdateTime(time);
            }
        }
    }

    TimeRunnable timeRunnable;

    /**
     * 子线程中运行的time--代码
     */
    public class UpdateRunnable implements Runnable {

        public boolean isQuitNow = false;

        @Override
        public void run() {
            int i = FAKE_TIME;
            while (!isQuitNow && i >= 0) {
                try {
                    if (i <= 0) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                toStop();
                            }
                        });
                    }
                    timeRunnable.update(i--);
                    handler.post(timeRunnable);
                    Thread.sleep(SLEEP_TIME);
                } catch (Exception e) {
                    e.printStackTrace();
                    isQuitNow = true;
                }
            }
        }
    }

    private UpdateRunnable current_time_runnable;

    private void startTimeRunnable() {
        threads.execute(current_time_runnable = new UpdateRunnable());
    }

    private void endTimeRunnable() {
        if (current_time_runnable != null) {
            current_time_runnable.isQuitNow = true;
        }
    }

    /**
     * 录音是否正在进行（dialog是否关闭）
     */
    private boolean isRecording;

    private void end(OnButtonStatus.RecoderStop stop) {
        endTimeRunnable();//该方法放前面
        if (listener != null) {
            listener.onEnd(stop);
        }
        isRecording = false;
    }

    public boolean isRecording() {
        return isRecording;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        // 防止连续快速的点击
        if (System.currentTimeMillis() - record_last_time < CLICK_LIMIT) {
            record_last_time = System.currentTimeMillis();
            return false;
        }

        if (listener != null) {
            if (listener.isIntercept()) {
                return false;
            }
        }

        final int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:

                log("down");

                if (listener != null) {
                    listener.onTouchDown();
                }

                // 确保停止了
                stop(OnButtonStatus.RecoderStop.FORCE_STOP);

                if (start()) {
                    return false;
                }

                break;
            case MotionEvent.ACTION_MOVE:
                if (isOutSide(event.getX(), event.getY())) {
                    if (!boundaryOut) {
                        boundaryOut = true;
                        if (listener != null) {
                            listener.onMoveOut();
                        }
                    }
                } else {
                    if (boundaryOut) {
                        boundaryOut = false;
                        if (listener != null) {
                            listener.onMoveIn();
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                log("up");
                toStop();
                break;
            default:
                return true;
        }
        return true;
    }

    private boolean start() {
        startTimeRunnable();

        if (listener != null) {
            listener.onStart();
        }

        isRecording = true;

        long startTemp = System.currentTimeMillis();

        try {
            recording();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "麦克风正在被其他应用占用，请关闭后重试", Toast.LENGTH_LONG).show();
        }

        long endTemp = System.currentTimeMillis();

        if (endTemp - startTemp > SYSTME_HINT_TIME) {
            // 弹出系统权限窗口
            try {
                // 取消录音
                stop(OnButtonStatus.RecoderStop.CANCLE_STOP);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void toStop() {
        if (boundaryOut) {
            // touch出界了
            log("出界了， 删除文件");
            stop(OnButtonStatus.RecoderStop.CANCLE_STOP);
        } else {
            log("未出界");
            // 记录录制的结束时间
            recoder_end_time = System.currentTimeMillis();
            // 真实时间
            float voiceTime = recoder_end_time - recoder_start_time;
            if (voiceTime < MIN_TIME) {
                stop(OnButtonStatus.RecoderStop.LESS_TIME_STOP);
            } else if (voiceTime + 100 > MAX_TIME) {
                stop(OnButtonStatus.RecoderStop.OUT_TIME_STOP);
            } else {
                stop(OnButtonStatus.RecoderStop.NORMAL_STOP);
            }
        }
        record_last_time = System.currentTimeMillis();
    }

    /**
     * 是否touch出了控件的边界
     */
    private boolean isOutSide(float x, float y) {
        if (x > getWidth() || x < 0 || y < 0 || y > getHeight()) {
            return true;
        }
        return false;
    }

    public void setSaveDir(File save_dir) {
        this.save_dir = save_dir;
    }

    public File getSaveDir() {
        if (save_dir == null || !save_dir.exists()) {
            save_dir = UtilFile.createDirInAndroid(getContext(), "appTempVoiceDir123");
        }
        return save_dir;
    }

    private void recording() {
        try {
            File dir = getSaveDir();
            if (dir == null || !dir.exists()) {
                return;
            }
            save_file = new File(dir, "voice_" + System.currentTimeMillis());
            if (!save_file.exists()) {
                save_file.createNewFile();
            }
            media_recorder = getPreparedRecorder(save_file);
            recoder_start_time = System.currentTimeMillis();
            media_recorder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onActivityPaused() {
        stop(OnButtonStatus.RecoderStop.FORCE_STOP);
    }

    public void onActivityDestroy() {
        stop(OnButtonStatus.RecoderStop.FORCE_STOP);
    }

    private MediaRecorder getPreparedRecorder(File file) {
        MediaRecorder recorder = new MediaRecorder();
        // 设置为初始状态
        recorder.reset();
        // 设置声音来源 , 需要加权限
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        // 指定音频文件输出的格式
        recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
        // 指定音频的编码格式
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        // recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        // 指定文件输出路径
        recorder.setOutputFile(file.getAbsolutePath());

        try {
            // 开始缓冲
            recorder.prepare();
        } catch (IOException e) {
            log(this + "--prepare() failed");
        }
        return recorder;
    }

    public void log(String msg) {
        Log.i(this.getClass().getSimpleName(), msg);
    }

    public void stop(OnButtonStatus.RecoderStop stopType) {
        // 重置
        boundaryOut = false;

        if (media_recorder != null) {
            // 停止并释放资源
            try {
                // 点击录制后快速松开，会报异常
                media_recorder.stop();
            } catch (Exception e) {
                e.printStackTrace();
                log("停止时抛异常了（如时间太短）");
                stopType = OnButtonStatus.RecoderStop.EXCEPTION_STOP;
            } finally {
                media_recorder.release();
                media_recorder = null;
            }

            if (stopType == OnButtonStatus.RecoderStop.EXCEPTION_STOP) {
                deleteFile("exception");
            } else if (stopType == OnButtonStatus.RecoderStop.FORCE_STOP) {
                // 确保停止，不可删除文件

            } else if (stopType == OnButtonStatus.RecoderStop.CANCLE_STOP) {
                // 触摸up时出界了 或者 录音权限时，会删除当前录音的文件
                deleteFile("cancle");
            } else if (stopType == OnButtonStatus.RecoderStop.LESS_TIME_STOP) {
                log("时间太短 ，删除文件");
                deleteFile("less time");
            } else if (stopType == OnButtonStatus.RecoderStop.OUT_TIME_STOP) {
                if (listener != null && listener.isEffectiveVoiceFileWhenTimeOut()) {
                    successFile();
                } else {
                    deleteFile("out time");
                }
            } else if (stopType == OnButtonStatus.RecoderStop.NORMAL_STOP) {
                successFile();
            }
        }

        final OnButtonStatus.RecoderStop type = stopType;

        if (stopType == OnButtonStatus.RecoderStop.LESS_TIME_STOP || stopType == OnButtonStatus.RecoderStop.EXCEPTION_STOP) {
            // 让dialog多停留下
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    end(type);
                }
            }, 500);
        } else {
            end(type);
        }
    }

    private void successFile() {
        log("准备上传文件" + (recoder_end_time - recoder_start_time));
        if (listener != null) {
            listener.onSuccessFile(save_file, (recoder_end_time - recoder_start_time) / COMPRESS_RATIO);
            save_file = null;
        }
    }

    public void deleteFile(String debug) {
        if (save_file != null && save_file.exists()) {
            save_file.delete();
            log("delete---" + debug + save_file.getAbsolutePath());
            save_file = null;
        }
    }

}
