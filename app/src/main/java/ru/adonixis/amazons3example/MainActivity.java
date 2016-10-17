package ru.adonixis.amazons3example;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final String COGNITO_POOL_ID = "YOUR_VALUE";
    private static final String BUCKET_NAME = "YOUR_VALUE";

    private static final int PICK_IMAGE_REQUEST_CODE = 1;
    private static final String TAG = MainActivity.class.getCanonicalName();

    EditText editTextUpload;
    EditText editTextDownload;
    ImageView imageViewUpload;
    ImageView imageViewDownload;
    Button buttonPickImage;
    Button buttonUpload;
    Button buttonDownload;
    Uri imageUri;
    ProgressDialog progressDialogUpload;
    ProgressDialog progressDialogDownload;

    TransferUtility transferUtility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        createTransferUtility();
    }

    private void initUI() {
        editTextUpload = (EditText) findViewById(R.id.edit_upload);
        editTextDownload = (EditText) findViewById(R.id.edit_download);

        imageViewUpload = (ImageView) findViewById(R.id.image_upload);
        imageViewDownload = (ImageView) findViewById(R.id.image_download);

        buttonPickImage = (Button) findViewById(R.id.btn_pick);
        buttonPickImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_IMAGE_REQUEST_CODE);
            }
        });

        buttonUpload = (Button) findViewById(R.id.btn_upload);
        buttonUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (imageUri != null) {
                    if (!TextUtils.isEmpty(editTextUpload.getText().toString())){
                        String objectKey = editTextUpload.getText().toString();
                        File file = null;
                        try {
                            file = createFileFromUri(imageUri, objectKey);
                            upload(file, objectKey);

                            progressDialogUpload = new ProgressDialog(MainActivity.this);
                            progressDialogUpload.setMessage("Uploading file " + file.getName());
                            progressDialogUpload.setIndeterminate(true);
                            progressDialogUpload.setCancelable(false);
                            progressDialogUpload.show();

                        } catch (IOException e) {
                            Log.e(TAG, "onClick: ", e);
                            Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Enter object key in EditText", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Pick any image to upload", Toast.LENGTH_SHORT).show();
                }
            }
        });

        buttonDownload = (Button) findViewById(R.id.btn_download);
        buttonDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TextUtils.isEmpty(editTextDownload.getText().toString())) {
                    String objectKey = editTextDownload.getText().toString();

                    progressDialogDownload = new ProgressDialog(MainActivity.this);
                    progressDialogDownload.setMessage("Downloading object key " + objectKey);
                    progressDialogDownload.setIndeterminate(true);
                    progressDialogDownload.setCancelable(false);
                    progressDialogDownload.show();

                    download(objectKey);
                } else {
                    Toast.makeText(MainActivity.this, "Enter object key in EditText", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void createTransferUtility() {
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                COGNITO_POOL_ID,
                Regions.US_EAST_1
        );
        AmazonS3Client s3Client = new AmazonS3Client(credentialsProvider);
        transferUtility = new TransferUtility(s3Client, getApplicationContext());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case PICK_IMAGE_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    imageUri = uri;
                    imageViewUpload.setImageURI(uri);
                    editTextUpload.setText(getFileNameFromUri(uri));
                    buttonUpload.setEnabled(true);
                }
        }
    }

    String getFileNameFromUri(Uri uri) {
        Cursor returnCursor = getContentResolver().query(uri, null, null, null, null);
        int nameIndex = 0;
        if (returnCursor != null) {
            nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            returnCursor.moveToFirst();
            String name = returnCursor.getString(nameIndex);
            returnCursor.close();
            return name;
        } else {
            return "";
        }
    }

    File createFileFromUri(Uri uri, String objectKey) throws IOException {
        InputStream is = getContentResolver().openInputStream(uri);
        File file = new File(getCacheDir(), objectKey);
        file.createNewFile();
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buf = new byte[2046];
        int read = -1;
        while ((read = is.read(buf)) != -1) {
            fos.write(buf, 0, read);
        }
        fos.flush();
        fos.close();
        return file;
    }

    void upload(File file, final String objectKey) {
        TransferObserver transferObserver = transferUtility.upload(
                BUCKET_NAME,
                objectKey,
                file
        );
        transferObserver.setTransferListener(new TransferListener() {

            @Override
            public void onStateChanged(int id, TransferState state) {
                Log.d(TAG, "onStateChanged: " + state);
                if (TransferState.COMPLETED.equals(state)) {
                    editTextDownload.setText(objectKey);
                    progressDialogUpload.dismiss();
                    Toast.makeText(MainActivity.this, "Image uploaded", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {}

            @Override
            public void onError(int id, Exception ex) {
                progressDialogUpload.dismiss();
                Log.e(TAG, "onError: ", ex);
                Toast.makeText(MainActivity.this, "Error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }

    void download(String objectKey) {
        final File fileDownload = new File(getCacheDir(), objectKey);

        TransferObserver transferObserver = transferUtility.download(
                BUCKET_NAME,
                objectKey,
                fileDownload
        );
        transferObserver.setTransferListener(new TransferListener(){

            @Override
            public void onStateChanged(int id, TransferState state) {
                Log.d(TAG, "onStateChanged: " + state);
                if (TransferState.COMPLETED.equals(state)) {
                    imageViewDownload.setImageBitmap(BitmapFactory.decodeFile(fileDownload.getAbsolutePath()));
                    progressDialogDownload.dismiss();
                    Toast.makeText(MainActivity.this, "Image downloaded", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {}

            @Override
            public void onError(int id, Exception ex) {
                progressDialogDownload.dismiss();
                Log.e(TAG, "onError: ", ex);
                Toast.makeText(MainActivity.this, "Error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

}
