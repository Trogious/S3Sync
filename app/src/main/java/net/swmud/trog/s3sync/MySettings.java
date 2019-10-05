package net.swmud.trog.s3sync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class MySettings {
    private static final String TAG = MySettings.class.getSimpleName();
    private static MySettings instance;

    private static final String SETTINGS_FILE = "s3syncsettings";
    private static final String ACCESS_KEY_FIELD = "access_key";
    private static final String SECRET_KEY_FIELD = "secret_key";
    private static final String BUCKET_NAME_FIELD = "bucket_name";
    private static final String REGION_FIELD = "region";
    private static final String LAST_PREFIX_KEY_FIELD = "last_prefix";

    public String accessKey;
    public String secretKey;
    public String bucketName;
    public String region;
    public String lastSelectedPrefix;

    private String settingsDir;
    private String encryptionKey;

    public static MySettings getInstance() {
        if (instance == null) {
            instance = new MySettings();
        };
        return instance;
    }

    public static void initialize(String settingsDir, String encryptionKey) {
        MySettings s = getInstance();
        s.settingsDir = settingsDir;
        s.encryptionKey = encryptionKey;
        s.load();
    }

    @SuppressLint("HardwareIds")
    public static String getAndroidId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private String[] encryptAll(String ...values) {
        String[] encrypted = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                try {
                    encrypted[i] = Crypto.encrypt(encryptionKey, values[i]);
                } catch (InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException | UnsupportedEncodingException | BadPaddingException | IllegalBlockSizeException e) {
                    Log.d(TAG, Log.getStackTraceString(e));
                }
            }
        }

        return encrypted;
    }

    public void save() {
        Path path = Paths.get(settingsDir, SETTINGS_FILE);
        String[] encrypted = encryptAll(accessKey, secretKey, bucketName, region);

        JSONObject json = new JSONObject();
        try {
            json.put(ACCESS_KEY_FIELD, encrypted[0]);
            json.put(SECRET_KEY_FIELD, encrypted[1]);
            json.put(BUCKET_NAME_FIELD, encrypted[2]);
            json.put(REGION_FIELD, encrypted[3]);
            json.put(LAST_PREFIX_KEY_FIELD, lastSelectedPrefix);
        } catch (JSONException e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }
        String payload = json.toString();
        try {
            Files.write(path, payload.getBytes());
        } catch (IOException e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }
    }

    public void load() {
        try {
            String jsonStr = new String(Files.readAllBytes(Paths.get(settingsDir, SETTINGS_FILE)));
            JSONObject json = new JSONObject(jsonStr);
            accessKey = Crypto.decrypt(encryptionKey, json.getString(ACCESS_KEY_FIELD));
            secretKey = Crypto.decrypt(encryptionKey, json.getString(SECRET_KEY_FIELD));
            bucketName = Crypto.decrypt(encryptionKey, json.getString(BUCKET_NAME_FIELD));
            region = Crypto.decrypt(encryptionKey, json.getString(REGION_FIELD));
            lastSelectedPrefix = json.optString(LAST_PREFIX_KEY_FIELD, null);
        } catch (IOException | JSONException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }
    }

    public List<String> getAccessKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(accessKey);
        keys.add(secretKey);
        return keys;
    }

    public void updateAccessKeys(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        save();
    }

    public void updateBucketName(String bucketName) {
        this.bucketName = bucketName;
        save();
    }

    public void updateRegion(String region) {
        this.region = region;
        save();
    }

    public void updateLastSelectedPrefix(String lastSelectecPrefix) {
        this.lastSelectedPrefix = lastSelectecPrefix;
        save();
    }
}