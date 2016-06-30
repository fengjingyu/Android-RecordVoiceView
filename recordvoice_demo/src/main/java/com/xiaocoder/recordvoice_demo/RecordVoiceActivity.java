package com.xiaocoder.recordvoice_demo;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;


import com.xiaocoder.recordvoice.UtilFile;
import com.xiaocoder.recordvoice.XCRecordVoiceButton;

import java.io.File;

/**
 * @author xiaocoder
 * @email fengjingyu@foxmail.com
 * @description
 */
public class RecordVoiceActivity extends Activity {

    private XCRecordVoiceButton button;

    private Dialog dialog;
    private TextView dialogHint;
    private TextView dialogTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_voice);
        initWidgets();
        setListeners();
    }

    public void initWidgets() {
        button = (XCRecordVoiceButton) findViewById(R.id.id_recoderButton);
        File saveDir = UtilFile.createDirInAndroid(this, "app_recordvoice");
        button.setSaveDir(saveDir);
    }

    public void createDialog(Context context) {
        dialog = new Dialog(context, R.style.xc_s_dialog);
        dialog.setContentView(R.layout.view_record_voice_hint);
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.dimAmount = 0.0f;
        window.setAttributes(lp);
        dialogHint = (TextView) dialog.findViewById(R.id.xc_id_voice_recoder_hint_textview);
        dialogTime = (TextView) dialog.findViewById(R.id.xc_id_voice_recoder_time);
    }

    public void setListeners() {

        button.setOnButtonStatus(new XCRecordVoiceButton.OnButtonStatus() {
            @Override
            public boolean isEffectiveVoiceFileWhenTimeOut() {
                return true;
            }

            @Override
            public boolean isIntercept() {

//                finishVoicePlaying();
//
//                if (checkDue()) {
//                    shortToast("会话已结束，请重新发起会话");
//                    return true;
//                }
//
                return false;
            }

            @Override
            public void onTouchDown() {
                if (dialog == null) {
                    createDialog(RecordVoiceActivity.this);
                }
            }

            @Override
            public void onUpdateTime(int time) {
                dialogTime.setText(time + "");
            }

            @Override
            public void onStart() {
                dialogHint.setText("松开 发送");
                button.setText("松开 发送");
                dialog.show();
            }

            @Override
            public void onMoveOut() {
                dialogHint.setText("取消 发送");
                button.setText("取消 发送");
            }

            @Override
            public void onMoveIn() {
                dialogHint.setText("松开 发送");
                button.setText("松开 发送");
            }

            @Override
            public void onSuccessFile(File file, double time) {
                Toast.makeText(RecordVoiceActivity.this, time + "_" + file, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onEnd(RecoderStop stop) {
                button.setText("按住 说话");

                if (stop == RecoderStop.LESS_TIME_STOP) {
                    Toast.makeText(RecordVoiceActivity.this, "录音时间太短", Toast.LENGTH_SHORT).show();
                }

                if (dialog != null && dialog.isShowing()) {
                    dialog.cancel();
                }
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        button.onActivityPaused();
    }
}