package com.amolmhatre.amol.instagramapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;

public class MainActivity extends Activity implements ScrollViewListener
{
    public static ArrayList<Bitmap> bitmapList = new ArrayList<Bitmap>();
    private static int smallCounter = 0;
    private static String nextUrl = "";
    private static int asyncCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start initial asyncTask with most recent list of selfies
        new LoadInstagramImages(true).execute("https://api.instagram.com/v1/tags/selfie/media/recent?type=image?access_token=1450779186.1fb234f.98c98c61ea78411b845a44c6d085aa6d&client_id=6383ca016b5344b6b55ccc44bacfc3b0");
        ScrollViewExt scrollView = (ScrollViewExt) findViewById(R.id.svInstagram);
        scrollView.setScrollViewListener(this);

    }

    class LoadInstagramImages extends AsyncTask<String, Bitmap, String>
    {
        ProgressDialog pd;
        private WeakReference<ImageView> imageViewReference;
        boolean noPd = false;

        private LoadInstagramImages(boolean noPd)
        {
            // Create a new asyncTask and decide if there should be a progressDialog
            this.noPd = noPd;
        }

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();

            // show progressDialog if true
            if (noPd)
            {
                pd = new ProgressDialog(MainActivity.this);
                pd.setTitle("Downloading...");
                pd.setMessage("Please wait...");
                pd.setCancelable(false);
                pd.setIndeterminate(true);
                pd.show();
            }

            // Keeping track of how many tasks are being executed for thread management
            asyncCounter++;
            Log.d("Async Inc", "" + asyncCounter);
        }

        @Override
        protected String doInBackground(String... params)
        {
            String next_url = "";
            try
            {
                // Create URL connection to download each picture
                URL example = new URL(params[0]);
                URLConnection tc;
                tc = example.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(tc.getInputStream()));

                String line;
                while ((line = in.readLine()) != null)
                {
                    // Get JSON data from the link, extract the data
                    JSONObject ob = new JSONObject(line);

                    // Get the Data JSON array to parse for picture urls
                    JSONArray object = ob.getJSONArray("data");
                    JSONObject paginationObject = ob.getJSONObject("pagination");

                    // Get the next url for the next download
                    next_url = paginationObject.getString("next_url");
                    Log.d("Url", next_url);

                    // Parse through the array for image links to download and as they are downloaded, load them on the screen
                    for (int i = 0; i < object.length(); i++)
                    {

                        JSONObject jo = (JSONObject) object.get(i);
                        JSONObject imagesJsonObj = (JSONObject) jo.getJSONObject("images");

                        // Standard resolution
                        JSONObject stdResJsonObject = (JSONObject) imagesJsonObj.getJSONObject("standard_resolution");
                        String url = stdResJsonObject.get("url").toString();
                        Bitmap bitmap = downloadBitmap(url);
                        publishProgress(bitmap);
                        // Log.d("url", url);
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            catch (JSONException e)
            {
                e.printStackTrace();
            }
            catch (OutOfMemoryError e)
            {
                Toast.makeText(getApplicationContext(), "Out of Memory", Toast.LENGTH_SHORT).show();
            }

            return next_url;
        }

        // Method used to download files from a url in a big, small, small pattern and return them at Bitmaps
        private Bitmap downloadBitmap(String url)
        {
            final AndroidHttpClient client = AndroidHttpClient.newInstance("Android");
            final HttpGet getRequest = new HttpGet(url);
            try
            {
                HttpResponse response = client.execute(getRequest);
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK)
                {
                    Log.w("ImageDownloader", "Error " + statusCode + " while retrieving bitmap from " + url);
                    return null;
                }

                final HttpEntity entity = response.getEntity();
                if (entity != null)
                {
                    InputStream inputStream = null;
                    try
                    {
                        inputStream = entity.getContent();
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                        if (smallCounter == 0 || smallCounter == 3)
                        {
                            int width = 500;
                            int height = 500;
                            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                            smallCounter = 0;
                        }

                        else
                        {
                            int width = 320;
                            int height = 320;
                            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                        }

                        smallCounter++;
                        WeakReference<Bitmap> weakBitmap = new WeakReference<Bitmap>(bitmap);

                        return weakBitmap.get();
                    }
                    finally
                    {
                        if (inputStream != null)
                        {
                            inputStream.close();
                        }
                        entity.consumeContent();
                    }
                }
            }
            catch (Exception e)
            {
                // Could provide a more explicit error message for IOException or
                // IllegalStateException
                getRequest.abort();
                Log.w("ImageDownloader", "Error while retrieving bitmap from " + url);
            }
            finally
            {
                if (client != null)
                {
                    client.close();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(final Bitmap... bitmap)
        {
            super.onProgressUpdate(bitmap);
            TableLayout tlInstagram = (TableLayout) findViewById(R.id.tlInstagram);

            // Create Imageview for picture to be placed in and set onClickListener for it
            ImageView image = new ImageView(MainActivity.this);
            imageViewReference = new WeakReference<ImageView>(image);
            ((ImageView) imageViewReference.get()).setImageBitmap(bitmap[0]);
            ((ImageView) imageViewReference.get()).setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    // When an image is clicked, create a new dialog and put the image into it
                    final Dialog dialog = new Dialog(MainActivity.this);
                    dialog.setContentView(R.layout.image_layout);
                    dialog.setTitle("#Selfie");
                    ImageView image = (ImageView) dialog.findViewById(R.id.imDisplay);
                    image.setLayoutParams(new TableRow.LayoutParams(700, 700));
                    image.setImageBitmap(bitmap[0]);
                    Button dialogButton = (Button) dialog.findViewById(R.id.dialogButtonOK);

                    // if button is clicked, close the custom dialog
                    dialogButton.setOnClickListener(new OnClickListener()
                    {
                        @Override
                        public void onClick(View v)
                        {
                            dialog.dismiss();
                        }
                    });

                    dialog.show();
                }
            });

            // Format that ImageView
            TableRow.LayoutParams imageParams = new TableRow.LayoutParams();
            imageParams.setMargins(10, 10, 10, 10);
            imageParams.gravity = Gravity.CENTER;
            ((ImageView) imageViewReference.get()).setLayoutParams(imageParams);

            // Format the TableRow and add the ImageView to it
            TableRow tr = new TableRow(MainActivity.this);
            tr.addView(((ImageView) imageViewReference.get()));
            tr.setBackgroundColor(Color.parseColor("#000000"));
            TableRow.LayoutParams lp = new TableRow.LayoutParams(getResources().getDisplayMetrics().widthPixels, TableRow.LayoutParams.WRAP_CONTENT);
            tr.setLayoutParams(lp);

            // Add the TableRow to the TableLayout
            tlInstagram.addView(tr);

        }

        @Override
        protected void onPostExecute(String next_url)
        {
            if (noPd && pd != null)
            {
                pd.dismiss();
            }

            // Get the next url to download images from and decrement the AsyncTask counter since the task is done
            nextUrl = next_url;
            asyncCounter--;
            Log.d("Async Dec", "" + asyncCounter);
        }
    }

    @Override
    public void onScrollChanged(ScrollViewExt scrollView, int x, int y, int oldx, int oldy)
    {
        // We take the last son in the scrollview
        View view = (View) scrollView.getChildAt(scrollView.getChildCount() - 1);
        int diff = (view.getBottom() - (scrollView.getHeight() + scrollView.getScrollY()));

        // if diff is zero, then the bottom has been reached and if the counter is 0, start a new task
        if (diff == 0 && asyncCounter == 0)
        {
            new LoadInstagramImages(false).execute(nextUrl);
        }
    }
}
