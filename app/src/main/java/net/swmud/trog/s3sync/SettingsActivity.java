package net.swmud.trog.s3sync;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    private final SettingsActivity self = this;
    private TextView accessKeyView;
    private TextView secretKeyView;
    private TextView bucketNameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        accessKeyView = findViewById(R.id.editAccessKey);
        secretKeyView = findViewById(R.id.editSecretKey);
        bucketNameView = findViewById(R.id.editBucketName);

        final Button saveButton = findViewById(R.id.buttonSave);
        saveButton.setOnClickListener(v -> {
            MySettings s = MySettings.getInstance();
            s.updateAccessKeys(accessKeyView.getText().toString(), secretKeyView.getText().toString());
            s.updateBucketName(bucketNameView.getText().toString());
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        MySettings s = MySettings.getInstance();
        accessKeyView.setText(s.accessKey);
        secretKeyView.setText(s.secretKey);
        bucketNameView.setText(s.bucketName);
    }
}