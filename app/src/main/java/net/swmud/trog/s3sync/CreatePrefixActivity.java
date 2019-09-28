package net.swmud.trog.s3sync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

public class CreatePrefixActivity extends AppCompatActivity {
    /*
    private final Consumer<String> adapter;
    public CreatePrefixActivity(Consumer<String> adapter) {
        this.adapter = adapter;
    }
    */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_prefix);

        final TextView prefixView = findViewById(R.id.editPrefix);
        findViewById(R.id.bCreate).setOnClickListener(v -> new Thread(() -> {
            try {
                String prefix = prefixView.getText().toString();
                while (prefix.startsWith("/")) {
                    prefix = prefix.substring(1);
                }
                S3Utils.createPrefix(prefix);
                final String finalPrefix = prefix;
                runOnUiThread(() -> {
                    Snackbar.make(v, "Prefix created.", Snackbar.LENGTH_LONG).show();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("prefix", finalPrefix);
                    setResult(Activity.RESULT_OK, resultIntent);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Snackbar.make(v, "Error creating prefix.", Snackbar.LENGTH_LONG).show());
            }
        }).start());
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
