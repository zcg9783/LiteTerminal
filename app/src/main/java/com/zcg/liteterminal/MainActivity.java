package com.zcg.liteterminal;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {

    private EditText mInput;
    private TextView mOutput;
    private ScrollView mScroll;
    private Process mShell;
    private OutputStreamWriter mWriter;
    private final AtomicBoolean mIsRoot = new AtomicBoolean(false);
    private final AtomicReference<String> mCurrentDir = new AtomicReference<>("~");
    private final int STORAGE_PERMISSION_CODE = 100;
    private static final String CURL_URL = "https://vip.123pan.cn/1814215835/watchapp/curl";
    private static final String CURL_PATH = "curl";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        mInput = findViewById(R.id.inputEditText);
        mOutput = findViewById(R.id.outputTextView);
        mScroll = findViewById(R.id.scrollView);

        checkStoragePermission();
        initShellSession();
        setupInput();
        new Handler().postDelayed(this::focusInput, 300);
    }

    private void initShellSession() {
        try {
            mShell = Runtime.getRuntime().exec("sh");
            mWriter = new OutputStreamWriter(mShell.getOutputStream());

            new Thread(() -> readStream(mShell.getInputStream(), false)).start();
            new Thread(() -> readStream(mShell.getErrorStream(), true)).start();

            sendCommand("id -u", false);
            sendCommand("pwd", false);

        } catch (IOException e) {
            showError("Shell启动失败: " + e.getMessage());
        }
    }

    private void readStream(InputStream input, boolean isError) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processOutputLine(line, isError);
                updatePrompt();
                focusInput();
            }
        } catch (IOException ignored) {}
    }

    private void processOutputLine(String line, boolean isError) {
        if (line.equals("0")) {
            mIsRoot.set(true);
        } else if (line.startsWith("/")) {
            mCurrentDir.set(shortenPath(line));
        }
        if (isError) {
            showError(line);
        } else {
            showOutput(line);
        }
    }

    private String shortenPath(String path) {
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[parts.length-1] : "~";
    }

    private void setupInput() {
        findViewById(R.id.executeButton).setOnClickListener(v -> {
            String cmd = mInput.getText().toString().trim();
            if (cmd.isEmpty()) return;

            if (cmd.equals("down_curl")) {
                new Thread(this::downloadCurl).start();
            } else {
                showFormattedPrompt(cmd);
                sendCommand(cmd, true);
                handleSpecialCommand(cmd);
            }

            mInput.setText("");
            focusInput();
        });
    }

    private void handleSpecialCommand(String cmd) {
        if (cmd.startsWith("cd ")) {
            sendCommand("pwd", false);
        }
    }

    private void downloadCurl() {
        try {
            File curlFile = new File(getFilesDir(), CURL_PATH);
            
            HttpURLConnection conn = (HttpURLConnection) new URL(CURL_URL).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = new FileOutputStream(curlFile)) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            Runtime.getRuntime().exec("chmod 755 " + curlFile.getAbsolutePath()).waitFor();
            
            String exportPath = "export PATH=$PATH:" + curlFile.getParent();
            sendCommand(exportPath, false);
            
            showSuccess("curl组件下载成功！");
        } catch (Exception e) {
            showError("下载失败: " + e.getMessage());
        }
    }

    private void sendCommand(String cmd, boolean showPrompt) {
        try {
            mWriter.write(cmd + "\n");
            mWriter.flush();
            if (showPrompt) updatePrompt();
        } catch (IOException e) {
            showError("命令发送失败");
        }
    }

    private void showFormattedPrompt(String cmd) {
        SpannableString ss = new SpannableString(getPrompt() + cmd + "\n");
        ss.setSpan(new ForegroundColorSpan(Color.parseColor("#2196F3")), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        appendText(ss);
    }

    private void showOutput(String text) {
        SpannableString ss = new SpannableString(text + "\n");
        appendText(ss);
    }

    private void showSuccess(String text) {
        SpannableString ss = new SpannableString(text + "\n");
        ss.setSpan(new ForegroundColorSpan(Color.parseColor("#4CAF50")), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        appendText(ss);
    }

    private void showError(String text) {
        SpannableString ss = new SpannableString(text + "\n");
        ss.setSpan(new ForegroundColorSpan(Color.parseColor("#F44336")), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        appendText(ss);
    }

    private void appendText(SpannableString text) {
        runOnUiThread(() -> {
            mOutput.append(text);
            mScroll.post(() -> mScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void updatePrompt() {
        runOnUiThread(() -> mInput.setHint(getPrompt()));
    }

    private String getPrompt() {
        return mCurrentDir.get() + (mIsRoot.get() ? " # " : " $ ");
    }

    private void focusInput() {
        runOnUiThread(() -> {
            if (!mInput.isFocused()) {
                mInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(mInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showPermissionDialog();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
            }
        }
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle("需要文件权限")
            .setMessage("请允许访问所有文件以正常使用")
            .setPositiveButton("去设置", (d, w) -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage("必须授予存储权限才能继续使用")
                    .setPositiveButton("重试", (d, w) -> checkStoragePermission())
                    .setNegativeButton("退出", (d, w) -> finish())
                    .show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mWriter != null) mWriter.close();
            if (mShell != null) mShell.destroy();
        } catch (IOException ignored) {}
    }
}