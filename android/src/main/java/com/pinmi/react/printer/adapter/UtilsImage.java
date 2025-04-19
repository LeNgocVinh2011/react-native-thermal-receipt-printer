package com.pinmi.react.printer.adapter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

public class UtilsImage {
    public static Bitmap resizeTheImageForPrinting(Bitmap original, int targetWidth, int imageHeight) {
        float aspectRatio = (float) original.getHeight() / original.getWidth();
        int targetHeight = (int) (targetWidth * aspectRatio);

        Bitmap scaled = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true);
        Bitmap bw = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bw);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        canvas.drawBitmap(scaled, 0, 0, paint);

        for (int y = 0; y < bw.getHeight(); y++) {
            for (int x = 0; x < bw.getWidth(); x++) {
                int color = bw.getPixel(x, y);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int gray = (r + g + b) / 3;
                bw.setPixel(x, y, gray < 160 ? Color.BLACK : Color.WHITE);
            }
        }

        return bw;
    }

    public static boolean shouldPrintColor(int col) {
        return col == Color.BLACK;
    }

    public static byte[] recollectSlice(int y, int x, int[][] img) {
        byte[] slices = new byte[]{0, 0, 0};
        for (int yy = y, i = 0; yy < y + 24 && i < 3; yy += 8, i++) {
            byte slice = 0;
            for (int b = 0; b < 8; b++) {
                int yyy = yy + b;
                if (yyy >= img.length) {
                    continue;
                }
                int col = img[yyy][x];
                boolean v = shouldPrintColor(col);
                slice |= (byte) ((v ? 1 : 0) << (7 - b));
            }
            slices[i] = slice;
        }
        return slices;
    }

    public static int[][] getPixelsSlow(Bitmap image2, int imageWidth, int imageHeight) {

        Bitmap image = resizeTheImageForPrinting(image2, imageWidth, imageHeight);

        int width = image.getWidth();
        int height = image.getHeight();
        int[][] pixels = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = image.getPixel(x, y);

                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int gray = (r + g + b) / 3;

                // Threshold
                int bw = (gray < 160) ? Color.BLACK : Color.WHITE;
                pixels[y][x] = bw;
            }
        }

        return pixels;
    }
}
