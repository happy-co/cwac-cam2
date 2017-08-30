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
import android.graphics.Matrix;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.util.Log;
import android.util.TimingLogger;

import com.android.mms.exif.ExifInterface;

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
        boolean normalizeOrientation = !xact
                .getProperties()
                .getBoolean(PROP_SKIP_ORIENTATION_NORMALIZATION, false);
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
                Matrix matrix = new Matrix();
                final float sx = widthHeight  / (float)squareBitmap.getWidth();
                final float sy = widthHeight / (float)squareBitmap.getHeight();
                matrix.setScale(sx, sy);
                final Bitmap scaledRotatedBitmap;
                ByteArrayOutputStream baos=new ByteArrayOutputStream();
                int orientation=imageContext.getOrientation();
                if (normalizeOrientation && needsNormalization(orientation)) {
                    matrix.setRotate(degreesForRotation(orientation));
                    scaledRotatedBitmap = Bitmap.createBitmap(squareBitmap, 0, 0, squareBitmap.getWidth(), squareBitmap.getHeight(), matrix, true);
                    logger.addSplit("full scale and rotate done");
                    final ExifInterface exif = imageContext.getExifInterface();
                    exif.setTagValue(ExifInterface.TAG_ORIENTATION, 1);
                    exif.removeCompressedThumbnail();
                    logger.addSplit("full scale done");
                    exif.writeExif(scaledRotatedBitmap, baos, quality);
                } else {
                    scaledRotatedBitmap = Bitmap.createBitmap(squareBitmap, 0, 0, squareBitmap.getWidth(), squareBitmap.getHeight(), matrix, true);
                    logger.addSplit("full scale and rotate done");
                    scaledRotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                }
                logger.addSplit("wrote exif data");
                scaledRotatedBitmap.recycle();
                squareBitmap.recycle();
                byte[] result = baos.toByteArray();
                imageContext.setJpeg(result);
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

    private boolean needsNormalization(int orientation) {
        return(orientation==8 || orientation==3 || orientation==6);
    }

    static private int degreesForRotation(int orientation) {
        int result;

        switch (orientation) {
            case 8:
                result=270;
                break;

            case 3:
                result=180;
                break;

            default:
                result=90;
        }

        return(result);
    }
}
