package net.swmud.trog.s3sync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

public class CreatePrefixActivity extends AppCompatActivity {
    private static final String TAG = CreatePrefixActivity.class.getSimpleName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_prefix);
        final CreatePrefixActivity self = this;

        final TextView prefixView = findViewById(R.id.editPrefix);
        findViewById(R.id.bCreate).setOnClickListener(v -> new Thread(() -> {
            String prefix = prefixView.getText().toString();
            while (prefix.startsWith("/")) {
                prefix = prefix.substring(1);
            }
            final String finalPrefix = prefix;
            try {
                S3Utils.createPrefix(finalPrefix);
                runOnUiThread(() -> {
                    Snackbar.make(v, "Prefix created.", Snackbar.LENGTH_LONG).show();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("prefix", finalPrefix);
                    setResult(Activity.RESULT_OK, resultIntent);
                });
            } catch (Exception e) {
                Log.d(TAG, "Cannot create prefix: " + finalPrefix, e);
                runOnUiThread(() -> Snackbar.make(v, "Error creating prefix.", Snackbar.LENGTH_LONG).show());
            }
        }).start());
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
