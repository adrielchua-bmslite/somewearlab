package com.somewearlabs.gateway;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone workspace QR scanner hosted by the gateway process.
 *
 * The retained gateway already packages Code Scanner and ZXing. Reflection keeps
 * this injected bridge buildable without copying vendor libraries into the SDK,
 * and keeps SC3 free of CameraX/ML Kit runtime dependencies.
 */
public final class WorkspaceQrScannerActivity extends Activity {
    public static final String EXTRA_INVITE_CODE = "com.sc3.somewear.sdk.INVITE_CODE";
    public static final String EXTRA_ERROR = "com.sc3.somewear.sdk.SCAN_ERROR";

    private static final int CAMERA_PERMISSION_REQUEST = 9137;
    private static final int MAX_INVITE_LENGTH = 8_192;

    private final AtomicBoolean completed = new AtomicBoolean(false);
    private TextView statusView;
    private View scannerView;
    private Object codeScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(buildContentView());
            configureScanner();
        } catch (Throwable error) {
            finishWithError(
                    "The installed Somewear gateway does not contain its QR scanner runtime ("
                            + error.getClass().getSimpleName() + ")"
            );
            return;
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] { Manifest.permission.CAMERA },
                    CAMERA_PERMISSION_REQUEST
            );
        }
    }

    private View buildContentView() throws Exception {
        Class<?> scannerViewClass = Class.forName(
                "com.budiyev.android.codescanner.CodeScannerView"
        );
        scannerView = (View) scannerViewClass.getConstructor(Context.class).newInstance(this);

        statusView = new TextView(this);
        statusView.setTextColor(Color.WHITE);
        statusView.setBackgroundColor(0x99000000);
        statusView.setTextSize(17f);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(32, 28, 32, 28);
        statusView.setText("Point the camera at a Somewear workspace QR code");

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(view -> finishCancelled());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(
                scannerView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );
        root.addView(
                statusView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP
                )
        );
        FrameLayout.LayoutParams cancelLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        cancelLayout.bottomMargin = 48;
        root.addView(cancel, cancelLayout);
        return root;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void configureScanner() throws Exception {
        Class<?> scannerClass = Class.forName("com.budiyev.android.codescanner.b");
        codeScanner = scannerClass
                .getConstructor(Context.class, scannerView.getClass())
                .newInstance(this, scannerView);

        Class<?> barcodeFormatClass = Class.forName("com.google.zxing.BarcodeFormat");
        Object qrCode = Enum.valueOf(
                (Class<? extends Enum>) barcodeFormatClass.asSubclass(Enum.class),
                "QR_CODE"
        );
        findMethod(scannerClass, "p0", 1).invoke(
                codeScanner,
                Collections.singletonList(qrCode)
        );

        // Match the retained scanner's safe autofocus + single-scan configuration.
        Class<?> autofocusClass = Class.forName("atakplugin.somewear.r6");
        findMethod(scannerClass, "j0", 1).invoke(
                codeScanner,
                autofocusClass.getField("a").get(null)
        );
        Class<?> scanModeClass = Class.forName("atakplugin.somewear.vd3");
        findMethod(scannerClass, "q0", 1).invoke(
                codeScanner,
                scanModeClass.getField("a").get(null)
        );

        Class<?> decodeCallbackClass = Class.forName("atakplugin.somewear.e70");
        Object decodeCallback = Proxy.newProxyInstance(
                decodeCallbackClass.getClassLoader(),
                new Class<?>[] { decodeCallbackClass },
                this::handleDecodeCallback
        );
        findMethod(scannerClass, "l0", 1).invoke(codeScanner, decodeCallback);

        Class<?> errorCallbackClass = Class.forName("com.budiyev.android.codescanner.f");
        Object errorCallback = Proxy.newProxyInstance(
                errorCallbackClass.getClassLoader(),
                new Class<?>[] { errorCallbackClass },
                this::handleErrorCallback
        );
        findMethod(scannerClass, "m0", 1).invoke(codeScanner, errorCallback);
    }

    private Object handleDecodeCallback(Object proxy, Method method, Object[] arguments) {
        if (isObjectMethod(proxy, method, arguments)) return objectMethod(proxy, method, arguments);
        if (!"a".equals(method.getName()) || arguments == null || arguments.length != 1) {
            return null;
        }
        try {
            String rawValue = String.valueOf(
                    arguments[0].getClass().getMethod("getText").invoke(arguments[0])
            );
            runOnUiThread(() -> handleDecodedValue(rawValue));
        } catch (Throwable error) {
            runOnUiThread(() -> showRecoverableError("Could not read that QR code"));
        }
        return null;
    }

    private Object handleErrorCallback(Object proxy, Method method, Object[] arguments) {
        if (isObjectMethod(proxy, method, arguments)) return objectMethod(proxy, method, arguments);
        if ("onError".equals(method.getName())) {
            runOnUiThread(() -> showRecoverableError(
                    "Could not start the gateway camera. Check its Camera permission."
            ));
        }
        return null;
    }

    private void handleDecodedValue(String rawValue) {
        if (completed.get()) return;
        if (!isSomewearInvite(rawValue)) {
            showRecoverableError("That QR code is not a Somewear workspace invite");
            return;
        }
        if (!completed.compareAndSet(false, true)) return;
        setResult(
                RESULT_OK,
                new Intent().putExtra(EXTRA_INVITE_CODE, rawValue)
        );
        finish();
    }

    private static boolean isSomewearInvite(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()
                || rawValue.length() > MAX_INVITE_LENGTH) {
            return false;
        }
        try {
            Uri uri = Uri.parse(rawValue.trim());
            String token = nonBlank(uri.getQueryParameter("token"));
            String meshKey = nonBlank(uri.getQueryParameter("meshKey"));
            String workspaceId = nonBlank(uri.getQueryParameter("workspaceId"));
            if ((token == null) == (meshKey == null)) return false;
            return meshKey == null || workspaceId != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String nonBlank(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void showRecoverableError(String message) {
        if (completed.get()) return;
        statusView.setText(message);
        startPreview();
    }

    private void startPreview() {
        if (completed.get() || codeScanner == null
                || checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            findMethod(codeScanner.getClass(), "t0", 0).invoke(codeScanner);
        } catch (Throwable error) {
            finishWithError("Could not start the Somewear gateway camera");
        }
    }

    private void stopPreview() {
        if (codeScanner == null) return;
        try {
            findMethod(codeScanner.getClass(), "c0", 0).invoke(codeScanner);
        } catch (Throwable ignored) {
            // Activity teardown remains best-effort.
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPreview();
    }

    @Override
    protected void onPause() {
        stopPreview();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startPreview();
        } else {
            finishWithError("Camera permission is required by the Somewear gateway QR scanner");
        }
    }

    private void finishCancelled() {
        if (!completed.compareAndSet(false, true)) return;
        setResult(RESULT_CANCELED);
        finish();
    }

    private void finishWithError(String message) {
        if (!completed.compareAndSet(false, true)) return;
        setResult(
                RESULT_CANCELED,
                new Intent().putExtra(EXTRA_ERROR, message)
        );
        finish();
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount)
            throws NoSuchMethodException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (name.equals(method.getName())
                        && method.getParameterTypes().length == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static boolean isObjectMethod(Object proxy, Method method, Object[] arguments) {
        String name = method.getName();
        return "toString".equals(name) || "hashCode".equals(name) || "equals".equals(name);
    }

    private static Object objectMethod(Object proxy, Method method, Object[] arguments) {
        if ("toString".equals(method.getName())) return "SC3WorkspaceQrCallback";
        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
        return arguments != null && arguments.length == 1 && proxy == arguments[0];
    }
}
