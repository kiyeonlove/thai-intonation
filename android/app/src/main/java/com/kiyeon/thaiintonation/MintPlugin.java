package com.kiyeon.thaiintonation;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONObject;

/**
 * 민트 플러그인 — 웹 화면(JS)과 안드로이드를 잇는 다리
 *  - speak / stop      : 휴대폰 음성(TTS)으로 태국어·한국어 읽기
 *  - listen / stopListen : 안드로이드 음성 인식으로 태국어 듣기 (발음 채점용)
 *  - info              : 태국어 음성·음성 인식·마이크 상태 확인
 */
@CapacitorPlugin(
    name = "Mint",
    permissions = { @Permission(alias = "microphone", strings = { Manifest.permission.RECORD_AUDIO }) }
)
public class MintPlugin extends Plugin {

    private static final Locale TH = new Locale("th", "TH");
    private static final Locale KO = Locale.KOREA;

    private TextToSpeech tts;
    private int ttsStatus = 0; // 0 준비 중, 1 준비됨, -1 실패
    private SpeechRecognizer recog;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void load() {
        initTts();
    }

    private void initTts() {
        ttsStatus = 0;
        tts = new TextToSpeech(getContext(), status -> {
            ttsStatus = status == TextToSpeech.SUCCESS ? 1 : -1;
            if (ttsStatus == 1) {
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { emitTts("start", id); }
                    @Override public void onDone(String id) { emitTts("done", id); }
                    @Override public void onError(String id) { emitTts("error", id); }
                });
            }
            JSObject o = new JSObject();
            o.put("ready", ttsStatus == 1);
            notifyListeners("ttsReady", o, true);
        });
    }

    private void emitTts(String state, String id) {
        JSObject o = new JSObject();
        o.put("state", state);
        o.put("id", id);
        notifyListeners("ttsState", o);
    }

    private int langState(Locale l) {
        try {
            return tts.isLanguageAvailable(l);
        } catch (Exception e) {
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    // ---------------- 상태 확인 ----------------
    @PluginMethod
    public void info(PluginCall call) {
        JSObject o = new JSObject();
        o.put("ttsReady", ttsStatus == 1);
        o.put("ttsLoading", ttsStatus == 0);
        if (ttsStatus == 1) {
            int th = langState(TH);
            int ko = langState(KO);
            o.put("thai", th >= TextToSpeech.LANG_AVAILABLE);
            o.put("thaiMissingData", th == TextToSpeech.LANG_MISSING_DATA);
            o.put("korean", ko >= TextToSpeech.LANG_AVAILABLE);
            try {
                o.put("engine", tts.getDefaultEngine());
            } catch (Exception e) {
                o.put("engine", "");
            }
        }
        o.put("recognizer", SpeechRecognizer.isRecognitionAvailable(getContext()));
        o.put("mic", getPermissionState("microphone") == PermissionState.GRANTED);
        call.resolve(o);
    }

    // ---------------- 휴대폰 음성(TTS) ----------------
    @PluginMethod
    public void speak(PluginCall call) {
        if (ttsStatus != 1 || tts == null) {
            call.reject("tts_not_ready");
            return;
        }
        JSArray items = call.getArray("items");
        if (items == null) {
            call.reject("no_items");
            return;
        }
        try {
            tts.stop();
            int n = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                String text = it.optString("text", "");
                if (text.isEmpty()) continue;
                String lang = it.optString("lang", "th-TH");
                tts.setLanguage(lang.startsWith("ko") ? KO : TH);
                tts.setSpeechRate((float) it.optDouble("rate", 1.0));
                tts.setPitch((float) it.optDouble("pitch", 1.05));
                tts.speak(text, n == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, new Bundle(), "u" + i);
                n++;
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("speak_failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        try {
            if (tts != null) tts.stop();
        } catch (Exception e) {
            // 무시
        }
        call.resolve();
    }

    @PluginMethod
    public void reloadTts(PluginCall call) {
        try {
            if (tts != null) tts.shutdown();
        } catch (Exception e) {
            // 무시
        }
        initTts();
        call.resolve();
    }

    /** 태국어 음성 데이터 설치 화면 열기 */
    @PluginMethod
    public void installThai(PluginCall call) {
        try {
            Intent it = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(it);
            call.resolve();
        } catch (Exception e) {
            openSettings(call);
        }
    }

    /** 휴대폰의 '텍스트 음성 변환' 설정 열기 */
    @PluginMethod
    public void openTtsSettings(PluginCall call) {
        openSettings(call);
    }

    private void openSettings(PluginCall call) {
        try {
            Intent it = new Intent("com.android.settings.TTS_SETTINGS");
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(it);
            call.resolve();
        } catch (Exception e) {
            call.reject("no_settings");
        }
    }

    // ---------------- 음성 인식(발음 채점) ----------------
    @PluginMethod
    public void listen(PluginCall call) {
        if (getPermissionState("microphone") != PermissionState.GRANTED) {
            requestPermissionForAlias("microphone", call, "micCallback");
            return;
        }
        startListen(call);
    }

    @PermissionCallback
    private void micCallback(PluginCall call) {
        if (getPermissionState("microphone") == PermissionState.GRANTED) {
            startListen(call);
        } else {
            call.reject("not_granted");
        }
    }

    private void startListen(PluginCall call) {
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            call.reject("unavailable");
            return;
        }
        final String lang = call.getString("lang", "th-TH");
        main.post(() -> {
            try {
                if (tts != null) tts.stop();
                if (recog != null) {
                    recog.destroy();
                    recog = null;
                }
                recog = SpeechRecognizer.createSpeechRecognizer(getContext());
                recog.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle b) { state("listening"); }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float v) { }
                    @Override public void onBufferReceived(byte[] b) { }
                    @Override public void onEndOfSpeech() { state("finishing"); }
                    @Override public void onError(int code) {
                        JSObject o = new JSObject();
                        o.put("code", code);
                        notifyListeners("listenError", o);
                    }
                    @Override public void onResults(Bundle b) {
                        JSObject o = new JSObject();
                        o.put("texts", toArr(b));
                        notifyListeners("result", o);
                    }
                    @Override public void onPartialResults(Bundle b) {
                        JSArray a = toArr(b);
                        JSObject o = new JSObject();
                        o.put("text", a.length() > 0 ? a.optString(0, "") : "");
                        notifyListeners("partial", o);
                    }
                    @Override public void onEvent(int t, Bundle b) { }
                });
                Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
                it.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                it.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getContext().getPackageName());
                recog.startListening(it);
                call.resolve();
            } catch (Exception e) {
                call.reject("upstream_error: " + e.getMessage());
            }
        });
    }

    private void state(String s) {
        JSObject o = new JSObject();
        o.put("state", s);
        notifyListeners("listen", o);
    }

    private JSArray toArr(Bundle b) {
        JSArray a = new JSArray();
        if (b == null) return a;
        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (l != null) for (String s : l) a.put(s);
        return a;
    }

    @PluginMethod
    public void stopListen(PluginCall call) {
        main.post(() -> {
            try {
                if (recog != null) recog.stopListening();
            } catch (Exception e) {
                // 무시
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void cancelListen(PluginCall call) {
        main.post(() -> {
            try {
                if (recog != null) recog.cancel();
            } catch (Exception e) {
                // 무시
            }
            call.resolve();
        });
    }

    @Override
    protected void handleOnDestroy() {
        try {
            if (tts != null) tts.shutdown();
            if (recog != null) recog.destroy();
        } catch (Exception e) {
            // 무시
        }
    }
}
