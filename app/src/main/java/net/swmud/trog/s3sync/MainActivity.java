package net.swmud.trog.s3sync;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService;
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
        MySettings.initialize(getApplicationContext().getCacheDir().getAbsolutePath(), MySettings.getAndroidId(getApplicationContext()));
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "READ_EXTERNAL_STORAGE already granted");
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
        recyclerAdapter.setDataset(new Uri[] {new Uri.Builder().appendPath("dummy1").build(), new Uri.Builder().appendPath("dummy2").build()});
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
                    e.printStackTrace();
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

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("image/") || type.startsWith("application/")) {
                handleSendImage(intent); // Handle single image being sent
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                handleSendMultipleImages(intent); // Handle multiple images being sent
            }
        }

        folderSpinner = findViewById(R.id.spinner2);
        final List<String> list = new ArrayList<>();
        list.add("/");
        foldersAdapter = new ArrayAdapter<>(self,
                android.R.layout.simple_spinner_item, list);
        foldersAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        folderSpinner.setAdapter(foldersAdapter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "READ_EXTERNAL_STORAGE granted");
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    Log.i(TAG, "READ_EXTERNAL_STORAGE rejected !!!");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    void handleSendImage(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            recyclerAdapter.setDataset(new Uri[] { imageUri } );
            recyclerAdapter.notifyDataSetChanged();
        }
    }

    void handleSendMultipleImages(Intent intent) {
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imageUris != null) {
            Uri []items = new Uri[imageUris.size()];
            int i = 0;
            for (Uri uri: imageUris) {
                items[i++] = uri;
            }
            recyclerAdapter.setDataset(items);
            recyclerAdapter.notifyDataSetChanged();
            // Update UI to reflect multiple images being shared
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
        for (Uri uri : recyclerAdapter.getDataset()) {
            String source = convertMediaUriToPath(uri);
            Path destinationPath = Paths.get(prefix, Paths.get(source).getFileName().toString());
            S3Utils.upload(getApplicationContext(), source, destinationPath.toString());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
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
}
