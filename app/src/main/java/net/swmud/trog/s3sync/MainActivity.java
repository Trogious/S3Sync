package net.swmud.trog.s3sync;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private final MainActivity self = this;
    private RecyclerAdapter recyclerAdapter;
    private Intent transferService;
    private ArrayAdapter<String> foldersAdapter;
    private Spinner folderSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MySettings.initialize(getApplicationContext().getDataDir().getAbsolutePath(), MySettings.getAndroidId(getApplicationContext()));
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_EXTERNAL_STORAGE already granted");
                Snackbar.make(view, "Uploading", Snackbar.LENGTH_LONG).show();
                String prefix = (String)folderSpinner.getSelectedItem();
                uploadAll(prefix);
            } else {
                Snackbar.make(view, "No Permission, requesting. Accept and re-try.", Snackbar.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        1);
            }
        });

        RecyclerView recyclerView = findViewById(R.id.my_recycler_view);
        recyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerAdapter = new RecyclerAdapter(this::convertMediaUriToPath);
        recyclerView.setAdapter(recyclerAdapter);
        recyclerAdapter.setDataset(RecyclerAdapter.getDummyDataSet());
        recyclerAdapter.notifyDataSetChanged();

        getApplicationContext().startService(transferService = new Intent(getApplicationContext(), TransferService.class));

        // Initialize the AWSMobileClient if not initialized
        AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {
            @Override
            public void onResult(UserStateDetails userStateDetails) {
                Log.d(TAG, "AWSMobileClient initialized. User State is " + userStateDetails.getUserState());
                final MySettings s = MySettings.getInstance();
                try {
                    List<String> accessKeys = s.getAccessKeys();
                    if (accessKeys.get(0) == null || accessKeys.get(1) == null) {
                        return;
                    }
                    S3Utils.initialize(AWSMobileClient.getInstance().getConfiguration(), accessKeys);
                } catch (JSONException e) {
                    Log.e(TAG, "JSON error.", e);
                }
                final SortedSet<String> folders = S3Utils.getFolders();
                runOnUiThread(() -> {
                    folderSpinner.setOnItemSelectedListener(null);
                    foldersAdapter.clear();
                    foldersAdapter.add("/");
                    foldersAdapter.addAll(folders);
                    foldersAdapter.notifyDataSetChanged();

                    if (s.lastSelectecPrefix != null) {
                        int found = foldersAdapter.getPosition(s.lastSelectecPrefix);
                        if (found >= 0 && found < foldersAdapter.getCount()) {
                            folderSpinner.setSelection(found);
                        }
                    }
                    folderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            s.updateLastSelectecPrefix(foldersAdapter.getItem(position));
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {

                        }
                    });
                });
                folders.forEach(f -> Log.d(TAG, f));
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Initialization error.", e);
            }
        });

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (type != null && (type.startsWith("image/") || type.startsWith("application/"))) {
            if (Intent.ACTION_SEND.equals(action)) {
                handleSendImage(intent);
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                handleSendMultipleImages(intent);
            }
        }

        folderSpinner = findViewById(R.id.spinner2);
        final List<String> list = new ArrayList<>();
        list.add("/");
        foldersAdapter = new ArrayAdapter<>(self, android.R.layout.simple_spinner_item, list);
        foldersAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        folderSpinner.setAdapter(foldersAdapter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "READ_EXTERNAL_STORAGE granted");
                } else {
                    Log.d(TAG, "READ_EXTERNAL_STORAGE rejected !!!");
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            foldersAdapter.add(data.getStringExtra("prefix"));
            foldersAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_create_prefix) {
            Intent intent = new Intent(this, CreatePrefixActivity.class);
            startActivityForResult(intent, 1);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        getApplicationContext().stopService(transferService);
        Process.killProcess(Process.myPid());
        super.onDestroy();
    }


    void handleSendImage(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            recyclerAdapter.setDataset(new RecyclerAdapter.DataItem[] { new RecyclerAdapter.DataItem(imageUri)} );
            recyclerAdapter.notifyDataSetChanged();
        }
    }

    void handleSendMultipleImages(Intent intent) {
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imageUris != null) {
            RecyclerAdapter.DataItem[]items = new RecyclerAdapter.DataItem[imageUris.size()];
            int i = 0;
            for (Uri uri: imageUris) {
                items[i++] = new RecyclerAdapter.DataItem(uri);
            }
            recyclerAdapter.setDataset(items);
            recyclerAdapter.notifyDataSetChanged();
        }
    }

    public String convertMediaUriToPath(Uri uri) {
        String [] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, proj,  null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();
            return path;
        }
        return uri.getPath();
    }

    private void uploadAll(String prefix) {
        for (final RecyclerAdapter.DataItem item : recyclerAdapter.getDataset()) {
            String source = convertMediaUriToPath(item.uri);
            Path destinationPath = Paths.get(prefix, Paths.get(source).getFileName().toString());
            if (item.progressBar != null) {
                item.progressBar.setProgress(0);
            }
            S3Utils.upload(getApplicationContext(), source, destinationPath.toString(), new TransferListener() {
                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        if (item.progressBar != null) {
                            item.progressBar.setProgress(100);
                            item.progressBar.setProgressTintList(ColorStateList.valueOf(Color.GREEN));
                        }
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int)percentDonef;

                    if (item.progressBar != null) {
                        item.progressBar.setProgress(percentDone);
                    }
                    Log.d(TAG, "ID:" + id + " bytesCurrent: " + bytesCurrent
                            + " bytesTotal: " + bytesTotal + " " + percentDone + "%");
                }

                @Override
                public void onError(int id, Exception ex) {
                    if (item.progressBar != null) {
                        item.progressBar.setProgressTintList(ColorStateList.valueOf(Color.RED));
                    }
                }
            });
        }
    }
}
