package com.braille.tesseract.sandarbh.braille;

import android.app.AlertDialog;
import android.app.FragmentBreadCrumbs;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.service.chooser.ChooserTarget;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import com.google.common.io.ByteStreams;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import static android.util.Log.e;
import static android.util.Log.println;
import static android.util.Log.v;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DEBUG",TTS_FLAG = "TTS";
    private Button capture,pick;
    private ImageView image;
    public Bitmap bitmap;
    public int height,width,FOCUS_COUNTER = 0,TTS_RESULT = TextToSpeech.ERROR;
    public final int IMAGE_CAPTURED = 1,IMAGE_SELECTED = 2,CONFIG_BITMAP = 3,ACTION_OCR = 4,ON = 1,OFF = 0;
    private int[][] bitmapArray;
    int[][] focusArray = new int[8][8];
    private Double sum[],focus[];
    private Double final_focus = 0.0;
    private final int PERMISSION_GRANTED = 1;
    public TextView result;
    public String extract = "";
    public String DATA_PATH,IMAGE_PATH,IMAGE_NAME;
    public File image_file;
    public Toolbar toolbar;
    public AlertDialog.Builder alert_build;
    public AlertDialog dialog;
    public ProgressDialog progressDialog;
    private Mat imageMat;
    private TextToSpeech ttsObject;
    private BaseLoaderCallback mLoaderCallback;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        capture = (Button) findViewById(R.id.capture);
        pick = (Button)findViewById(R.id.pick);
        image = (ImageView) findViewById(R.id.image);
        result = (TextView) findViewById(R.id.textView);
        toolbar = (Toolbar)findViewById(R.id.bar);

        setSupportActionBar(toolbar);
        toolbar.setTitle("Braille");
        toolbar.setLogo(R.mipmap.app_icon);
        image.setBackgroundColor(Color.TRANSPARENT);

        requestStoragePermission();
        InitOpenCV();
        initTTS();

    }

    public void initTTS(){
        ttsObject = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {

                if (status== TextToSpeech.SUCCESS)
                    TTS_RESULT = ttsObject.setLanguage(Locale.US);

                else if (status == TextToSpeech.LANG_MISSING_DATA)
                    e(TAG,"Language Data Missing!");
                else if (status == TextToSpeech.LANG_NOT_SUPPORTED)
                    e(TAG,"language not Supported!");
                else
                    e(TAG,"Unexpected Error!");

            }
        });
    }

    public void InitOpenCV(){
        mLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                switch (status) {
                    case LoaderCallbackInterface.SUCCESS:
                    {
                        Log.i(TAG, "OpenCV loaded successfully");
                        imageMat=new Mat();
                    } break;
                    default:
                    {
                        super.onManagerConnected(status);
                    } break;
                }
            }
        };

        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void requestStoragePermission(){
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED){
            //Log.v(TAG,"Requesting");

            ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},PERMISSION_GRANTED);
        }
        else
            Start();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode){

            case PERMISSION_GRANTED : if (grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Start();
            }
            else
                Toast.makeText(this,"Restart app and grant permission to use the app!",Toast.LENGTH_LONG).show();
                break;
        }
    }

    public void Start(){

        if (!createdTesseractFiles())
            Toast.makeText(this,"Unexpected Error occured! Please restart the app!",Toast.LENGTH_SHORT).show();
        else {
            Capture();
            selectFilefromDevice();
        }
    }

    private class BackgroundWorker extends AsyncTask<Integer,String,Integer> {

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        protected void onPreExecute() {
            switchLoadingDialog(ON);
        }

        @Override
        protected Integer doInBackground(Integer... RequestCode) {

            if (RequestCode[0] == CONFIG_BITMAP){
                publishProgress("Configuring Bitmap...");
                configBitmap();

                publishProgress("Processing Bitmap...");
                BitmaptoGrayScale();

                publishProgress("Preparing Bitmap...");
                makeBitmap();

                publishProgress("Update");
                publishProgress("Checking Focus...");
                checkFocus();

                e(TAG,"GLV");
                GrayLevelVariance();

                return RequestCode[0];
            }

            else if (RequestCode[0] == ACTION_OCR){
                Log.e(TAG,"OCR in Process!");
                publishProgress("OCR in Process.Please wait...");

                TessBaseAPI tessBaseAPI = new TessBaseAPI();
                tessBaseAPI.init(DATA_PATH,"eng");
                tessBaseAPI.setImage(bitmap);
                extract = tessBaseAPI.getUTF8Text();
                extract = extract.replaceAll("[^a-zA-Z0-9]+", " ");
                extract = extract.trim();

                tessBaseAPI.end();
                publishProgress("Done!");
                e(TAG,"Done!");
            }
            return RequestCode[0];
        }

        @Override
        protected void onProgressUpdate(String... values) {
            dialog.setMessage(values[0]);
            if (values[0].equals("Update"))
                image.setImageBitmap(bitmap);

            e(TAG,values[0]);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        protected void onPostExecute(Integer code) {
            if (code == CONFIG_BITMAP) {
                switchLoadingDialog(OFF);
                image.setImageBitmap(bitmap);

                if (final_focus<8) {
                    Toast.makeText(MainActivity.this, "Image out of Focus!", Toast.LENGTH_LONG).show();
                    showOutOfFocuswarning();
                    e(TAG, "Out of Focus");
                }
                else
                    startOcr();
            }

            else {
                result.setText(extract);
                switchLoadingDialog(OFF);

                TexttoSpeech();
            }
        }
    }

    public void selectFilefromDevice(){

        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent filePicker = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(filePicker,IMAGE_SELECTED);
            }
        });

    }

    public boolean createdTesseractFiles(){
        byte[] bytes;
        InputStream istream = getResources().openRawResource(R.raw.eng);
        try {
            DATA_PATH = getFilesDir().toString();
            e(TAG,DATA_PATH);

            File data_folder = new File(DATA_PATH,"tessdata");

            if (!data_folder.exists())
                data_folder.mkdir();

            File tess_data = new File(DATA_PATH+"/tessdata/","eng.traineddata");

            FileOutputStream ostream = new FileOutputStream(tess_data);

            bytes = ByteStreams.toByteArray(istream);
            ostream.write(bytes);
            ostream.close();

            e(TAG,DATA_PATH);
            e(TAG,tess_data.getAbsolutePath());


        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public File createImageFile(){

        IMAGE_NAME = "Braille.jpg";
        IMAGE_PATH = Environment.getExternalStorageDirectory().getPath();
        File folder = new File(IMAGE_PATH,"Tesseract");
        if (!folder.exists())
           folder.mkdir();

        if (!folder.exists() && !folder.mkdir()){
            Toast.makeText(this,"Unable to create target Directory!",Toast.LENGTH_SHORT).show();
            e(TAG,"Create Directory Failed!");
        }
        else
           IMAGE_PATH =  IMAGE_PATH.concat("/Tesseract/Braille.jpg");

        return (new File(IMAGE_PATH));
    }

    public void Capture() {

        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                e(TAG,"Capturing!");

                image_file = createImageFile();
                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    camera.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image_file));
                }
                else
                {
                    Uri imageUri = FileProvider.getUriForFile(MainActivity.this,
                            getApplicationContext().getPackageName()+".provider",image_file);
                    camera.putExtra(MediaStore.EXTRA_OUTPUT,imageUri);
                }
                startActivityForResult(camera, IMAGE_CAPTURED);
            }
        });

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode){
            case IMAGE_CAPTURED : if (resultCode == RESULT_OK) {

                e(TAG,"Configuring Bitmap!");
                new BackgroundWorker().execute(CONFIG_BITMAP);
            }
            break;

            case IMAGE_SELECTED : if (resultCode == RESULT_OK){

                Uri image_uri = data.getData();
                String projection[] = {MediaStore.Images.Media.DATA};

                Cursor cursor = getContentResolver().query(image_uri,projection,null,null,null);
                assert cursor != null;
                cursor.moveToFirst();

                int index = cursor.getColumnIndex(projection[0]);
                IMAGE_PATH = cursor.getString(index);
                cursor.close();
                IMAGE_NAME = Uri.parse(IMAGE_PATH).getLastPathSegment();

                e(TAG,IMAGE_PATH+" "+IMAGE_NAME);
                e(TAG,"Configuring Bitmap!");

                new BackgroundWorker().execute(CONFIG_BITMAP);

            }
            break;
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void switchLoadingDialog(int Switch){

        if (Switch == ON) {
            alert_build = new AlertDialog.Builder(this);
            alert_build.setTitle("Loading...");
            alert_build.setView(R.layout.sample_loading__dialog);
            alert_build.setCancelable(false);
            alert_build.setMessage("Please wait a while....");
            dialog = alert_build.create();
            dialog.show();
        }
        else
            dialog.cancel();

    }

    public void showOutOfFocuswarning(){
        alert_build = new AlertDialog.Builder(MainActivity.this);

        alert_build.setTitle("Warning!")
                .setMessage("Your image is Out of Focus. For better results, choose another image or re-capture!")
                .setCancelable(false)
                .setPositiveButton("Proceed Anyway", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startOcr();
                    }
                })
                .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                });
        AlertDialog warning = alert_build.create();
        warning.show();

    }

    public int makeBitmap(){
        int i,j;
        height = bitmap.getHeight();
        width = bitmap.getWidth();
        int[] bitmapPixels = new int[width * height];

        e(TAG,"Height : "+height+" Width :  "+width);

        if (height>1000 || width>1000){
            bitmap = resizeBitmap(bitmap,1000,1000);
        }
        height = bitmap.getHeight();
        width = bitmap.getWidth();
        e(TAG,"Scaled Height : "+height+" Scaled Width :  "+width);

        bitmap.getPixels(bitmapPixels,0,width,0,0,width,height);
        bitmapArray = new int[height][width];

        for (i = 0; i < width * height; i++) {
            bitmapArray[i / width][i % width] = Color.red(bitmapPixels[i]);
        }

        bitmapPixels = null;
        System.gc();

        for (i=0;i<8;i++){
            for (j=0;j<8;j++){
                if ((i>=2 && i<=5) && (j>=2 && j<=5)){
                    focusArray[i][j] = 3;
                }
                else
                    focusArray[i][j] = -1;
            }
        }

        e(TAG,"Bitmap Array Done");

        /*
        isImageFocused(height/2,3*height/4,0,width/2,4);
        isImageFocused(height/2,3*height/4,width/2,width,5);
        isImageFocused(3*height/4,height,0,width/2,6);
        isImageFocused(3*height/4,height,width/2,width,7);
*/

        return 0;
    }

    public void checkFocus(){
        sum = new Double[8];
        focus = new Double[8];

        FOCUS_COUNTER = 0;
        final_focus = 0.0;
        isImageFocused(0,height/2,0,width/2,0);
        isImageFocused(0,height/2,width/2,width,1);
        isImageFocused(height/2,height,0,width/2,2);
        isImageFocused(height/2,height,width/2,width,3);

        waitForResult();
    }

    public void isImageFocused(final int h_begin, final int h_end, final int w_begin, final int w_end, final int flag) {

        class Focus extends AsyncTask<Void, Integer, Double> {

            @Override
            protected void onPreExecute() {
            }

            @Override
            protected Double doInBackground(Void... none) {

                int i, j, m, n;

                final String TAG = "DEBUG";
                e(TAG, "Checking Focus");
                //Double sum;
                focus[flag] = 0.0;

                for (i = h_begin; i < h_end - 8; i++) {
                    for (j = w_begin; j < w_end - 8; j++) {

                        sum[flag] = 0.0;
                        for (m = 0; m < 8; m++) {
                            for (n = 0; n < 8; n++) {
                                sum[flag] += focusArray[n][m] * bitmapArray[i + n][j + m];
                            }
                        }
                        focus[flag] += (Math.pow(sum[flag], 2));

                    }
                }
                return focus[flag];
            }

            @Override
            protected void onPostExecute(Double focus) {

                final_focus += focus*Math.pow(10,-10);
                FOCUS_COUNTER++;
                e(TAG, "Focus : " + final_focus + " "+FOCUS_COUNTER);

            }
        }

        new Focus().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void waitForResult(){

        while (FOCUS_COUNTER != 4) {
            synchronized (this) {
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        bitmapArray = null;
        System.gc();

    }

    public int configBitmap() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;

        e(TAG,"Configuring Bitmap!");
        //dialog.setMessage("Configuring Bitmap...");

        bitmap = BitmapFactory.decodeFile(IMAGE_PATH,options);

        try {
            ExifInterface exif = new ExifInterface(IMAGE_PATH);
            int exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            int rotate = 0;
            e(TAG,"Rotation : "+exifOrientation);

            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 0;
                    break;
            }

            if (rotate != 0) {
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();

                // Setting pre rotate
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);

                // Rotating Bitmap
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
            }

            // Convert to ARGB_8888, required by tess
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            //BitmaptoGrayScale();

        } catch (IOException e) {
            e(TAG, "Couldn't correct orientation: " + e.toString());
        }
        return 0;
    }

    public int BitmaptoGrayScale(){

        e(TAG,"Converting to GrayScale!");

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(filter);
        canvas.drawBitmap(bitmap,0,0,paint);

        //image.setImageBitmap(bitmap);

        return 0;

    }

    private static Bitmap resizeBitmap(Bitmap image, int maxWidth, int maxHeight) {

        e(TAG,"Resizing!");
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > ratioBitmap) {
                finalWidth = (int) ((float)maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float)maxWidth / ratioBitmap);
            }
            image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
            return image;
        } else {
            return image;
        }
    }

    public void GrayLevelVariance(){

        Utils.bitmapToMat(bitmap, imageMat);
        Imgproc.cvtColor(imageMat, imageMat, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(imageMat, imageMat, new Size(3, 3), 0);
        //Imgproc.adaptiveThreshold(imageMat, imageMat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 5, 4);
        //Imgproc.medianBlur(imageMat, imageMat, 3);
        Imgproc.threshold(imageMat, imageMat, 0, 255, Imgproc.THRESH_OTSU);
        Utils.matToBitmap(imageMat,bitmap);

        //image.setImageBitmap(bitmap);
    }

    public void startOcr(){

        Toast.makeText(this,"OCR in process",Toast.LENGTH_SHORT).show();
        new BackgroundWorker().execute(ACTION_OCR);
    }

    public void TexttoSpeech(){

        e(TAG,"TTS!");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (TTS_RESULT == TextToSpeech.SUCCESS){
                e(TAG,"Speaking...");
            }
            ttsObject.speak(extract,TextToSpeech.QUEUE_FLUSH,null,TTS_FLAG);
        }
        else
            ttsObject.speak(extract,TextToSpeech.QUEUE_FLUSH,null);

        if (!ttsObject.isSpeaking())
            ttsObject.shutdown();

    }

}
