package com.kiyeon.thaiintonation;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 민트 플러그인: 휴대폰 음성(TTS) + 음성 인식(발음 채점)
        registerPlugin(MintPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
