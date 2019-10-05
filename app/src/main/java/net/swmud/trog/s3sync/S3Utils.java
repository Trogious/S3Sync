package net.swmud.trog.s3sync;

import android.content.Context;
import android.util.Log;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class S3Utils {
    private static final String TAG = S3Utils.class.getSimpleName();
    private static String bucketName;
    private static String regionName;
    private static String accessKey;
    private static String secretKey;
    private static AmazonS3Client client;
    private static AWSConfiguration awsConfig;

    public static void setBucketName(String bucketName) {
        S3Utils.bucketName = bucketName;
    }

    public static void setRegionName(String regionName) {
        S3Utils.regionName = regionName;
    }

    public static void setAccessKey(String accessKey) {
        S3Utils.accessKey = accessKey;
    }

    public static void setSecretKey(String secretKey) {
        S3Utils.secretKey = secretKey;
    }

    public static AmazonS3Client getClient() {
        return new AmazonS3Client(new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() {
                return accessKey;
            }

            @Override
            public String getAWSSecretKey() {
                return secretKey;
            }
        }, Region.getRegion(regionName));
    }

    private static AWSConfiguration buildAwsConfig() {
        AWSConfiguration awsConfiguration = null;
        JSONObject cfg = new JSONObject();
        try {
            cfg.put("Bucket", bucketName);
            cfg.put("Region", regionName);
            JSONObject defaultCfg = new JSONObject();
            defaultCfg.put("Default", cfg);
            JSONObject config = new JSONObject();
            config.put("S3TransferUtility", defaultCfg);
            awsConfiguration = new AWSConfiguration(config);
        } catch (JSONException e) {
            Log.d(TAG, Log.getStackTraceString(e));
        }

        return awsConfiguration;
    }

    public static void initialize() {
        MySettings s = MySettings.getInstance();
        bucketName = s.bucketName;
        regionName = s.region;
        accessKey = s.accessKey;
        secretKey = s.secretKey;
        client = getClient();
        awsConfig = buildAwsConfig();
    }

    public static List<String> listObjects() {
        final List<String> objects = new ArrayList<>();
        if (bucketName != null) {
            Thread t = new Thread(() -> {
                try {
                    List<S3ObjectSummary> summaries = client.listObjects(bucketName).getObjectSummaries();
                    summaries.sort((o1, o2) -> o1.getLastModified().compareTo(o2.getLastModified()));
                    summaries.forEach(s3ObjectSummary -> objects.add(s3ObjectSummary.getKey()));
                } catch (Exception e) {
                    Log.d(TAG, Log.getStackTraceString(e));
                }
            });
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                Log.d(TAG, Log.getStackTraceString(e));
            }
        }
        return objects;
    }

    public static SortedSet<String> getFolders() {
        SortedSet<String> folders = new TreeSet<>();
        listObjects().forEach(s -> {
            if (s.trim().endsWith("/")) {
                folders.add(s);
            } else if (s.contains("/")) {
                Path p = Paths.get(s);
                int count = p.getNameCount();
                Iterator<Path> it = p.iterator();
                Path outPath = Paths.get("");
                while (it.hasNext()) {
                    p = it.next();
                    if (--count > 0) {
                        outPath = Paths.get(outPath.toString(), p.toString());
                        folders.add(outPath.toString() + "/");
                    }
                }
            }
        });
        return folders;
    }

    public static void upload(Context context, String source, String destinationPath, TransferListener listener) {
        AmazonS3Client cl = S3Utils.getClient();
        TransferUtility transferUtility =
                TransferUtility.builder()
                        .context(context)
                        .awsConfiguration(awsConfig)
                        .s3Client(cl)
                        .build();

        Log.d(TAG, source + " -> " + destinationPath);
        if (destinationPath.startsWith("/")) {
            destinationPath = destinationPath.substring(1);
        }
        TransferObserver uploadObserver = transferUtility.upload(bucketName, destinationPath, new File(source));

        // Attach a listener to the observer to get state update and progress notifications
        uploadObserver.setTransferListener(listener);

        // If you prefer to poll for the data, instead of attaching a
        // listener, check for the state and progress in the observer.
        if (TransferState.COMPLETED == uploadObserver.getState()) {
            // Handle a completed upload.
        }

        Log.d(TAG, "Bytes Transferred: " + uploadObserver.getBytesTransferred());
        Log.d(TAG, "Bytes Total: " + uploadObserver.getBytesTotal());
    }

    public static void createPrefix(String fullPathName) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(0);
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, fullPathName + "/",
                new ByteArrayInputStream(new byte[0]), metadata);
        client.putObject(putObjectRequest);
    }
}
