/**
 * Copyright (c) 2015 CommonsWare, LLC
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.commonsware.cwac.cam2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.util.Log;
import android.util.TimingLogger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;


public class JPEGSquareResizeWriter extends JPEGWriter {

    private final int widthHeight;

    public JPEGSquareResizeWriter(Context ctxt, int widthHeight, int quality) {
        super(ctxt, quality);
        this.widthHeight = widthHeight;
    }

    @Override
    public void process(PictureTransaction xact, ImageContext imageContext) {
        Log.i("JPEGSquareResizeWriter", "process");
        TimingLogger logger = new TimingLogger("ProcessImage", "Process");
        logger.addSplit("Start");
        Uri output=xact.getProperties().getParcelable(PROP_OUTPUT);
        boolean updateMediaStore=xact
                .getProperties()
                .getBoolean(PROP_UPDATE_MEDIA_STORE, false);
        byte[] src=imageContext.getJpeg();
        if (output!=null) {
            try {
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(src, 0, src.length, opts);
                if (opts.outWidth == -1 || opts.outHeight == -1) {
                    throw new RuntimeException("Invalid width or height: width=" + opts.outWidth + " height=" + opts.outHeight);
                }
                final int outWidth = opts.outWidth;
                final int outHeight = opts.outHeight;
                opts.inSampleSize = Math.min(outWidth / widthHeight, outHeight / widthHeight);
                opts.inJustDecodeBounds = false;
                logger.addSplit("About to do resize");
                final Bitmap bitmap = BitmapFactory.decodeByteArray(src, 0, src.length, opts);
                logger.addSplit("basic resize done");
                final int squareDimension = Math.min(bitmap.getWidth(), bitmap.getHeight());
                final Bitmap squareBitmap = ThumbnailUtils.extractThumbnail(bitmap, squareDimension, squareDimension, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
                logger.addSplit("square crop done");
                final Bitmap resultBitmap = Bitmap.createScaledBitmap(squareBitmap, widthHeight, widthHeight,true);
                logger.addSplit("full scale done");
                ByteArrayOutputStream baos=new ByteArrayOutputStream();
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, this.quality, baos);
                resultBitmap.recycle();
                squareBitmap.recycle();
                byte[] result = baos.toByteArray();

                if (output.getScheme().equals("file")) {
                    String path=output.getPath();
                    File f=new File(path);

                    f.getParentFile().mkdirs();

                    FileOutputStream fos=new FileOutputStream(f);

                    fos.write(result);
                    fos.flush();
                    fos.getFD().sync();
                    fos.close();

                    if (updateMediaStore) {
                        MediaScannerConnection.scanFile(imageContext.getContext(),
                                new String[]{path}, new String[]{"image/jpeg"},
                                null);
                    }
                }
                else {
                    OutputStream out=getContext().getContentResolver().openOutputStream(output);

                    out.write(result);
                    out.flush();
                    out.close();
                }
            }
            catch (Exception e) {
                // throw new UnsupportedOperationException("Exception when trying to write JPEG", e);
                AbstractCameraActivity.BUS.post(new CameraEngine.DeepImpactEvent(e));
            }
        }
        logger.addSplit("saved file done");
        logger.dumpToLog();
    }
}
