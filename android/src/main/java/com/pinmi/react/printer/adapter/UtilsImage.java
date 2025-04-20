package com.pinmi.react.printer.adapter;

import android.graphics.Bitmap;
import android.graphics.Color;

public class UtilsImage {
    public static Bitmap resizeTheImageForPrinting(Bitmap original, int targetWidth, int imageHeight) {
        float aspectRatio = (float) original.getHeight() / original.getWidth();
        int targetHeight = (int) (targetWidth * aspectRatio);

        Bitmap resized = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true);
        Bitmap bw = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int color = resized.getPixel(x, y);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                int gray = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                int threshold = 180;
                int newColor = gray < threshold ? Color.BLACK : Color.WHITE;

                bw.setPixel(x, y, newColor);
            }
        }

        return bw;
    }

    public static int[][] getPixelsSlow(Bitmap image2, int imageWidth, int imageHeight) {

        Bitmap image = resizeTheImageForPrinting(image2, imageWidth, imageHeight);

        int width = image.getWidth();
        int height = image.getHeight();
        int[][] pixels = new int[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y][x] = image.getPixel(x, y);
            }
        }

        return pixels;
    }
}
