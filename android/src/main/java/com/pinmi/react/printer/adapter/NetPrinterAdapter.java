package com.pinmi.react.printer.adapter;

import static com.pinmi.react.printer.adapter.UtilsImage.getPixelsSlow;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import com.facebook.common.internal.ImmutableMap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.pinmi.react.printer.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import android.graphics.BitmapFactory;

import androidx.annotation.RequiresApi;

/**
 * Created by xiesubin on 2017/9/22.
 */

public class NetPrinterAdapter implements PrinterAdapter {
    private static NetPrinterAdapter mInstance;
    private ReactApplicationContext mContext;
    private String LOG_TAG = "RNNetPrinter";
    private NetPrinterDevice mNetDevice;
    private NetPrinterDevice mStampNetDevice;

    // {TODO- support other ports later}
    // private int[] PRINTER_ON_PORTS = {515, 3396, 9100, 9303};

    private int[] PRINTER_ON_PORTS = { 9100 };
    private static final String EVENT_SCANNER_RESOLVED = "scannerResolved";
    private static final String EVENT_SCANNER_RUNNING = "scannerRunning";

    private final static char ESC_CHAR = 0x1B;
    private static byte[] SELECT_BIT_IMAGE_MODE = { 0x1B, 0x2A, 33 };
    private final static byte[] SET_LINE_SPACE_24 = new byte[] { ESC_CHAR, 0x33, 24 };
    private final static byte[] SET_LINE_SPACE_32 = new byte[] { ESC_CHAR, 0x33, 32 };
    private final static byte[] LINE_FEED = new byte[] { 0x0A };
    private static byte[] CENTER_ALIGN = { 0x1B, 0X61, 0X31 };

    private Socket mSocket;
    private  Socket mStampSocket;

    private boolean isRunning = false;

    private NetPrinterAdapter() {

    }

    public static NetPrinterAdapter getInstance() {
        if (mInstance == null) {
            mInstance = new NetPrinterAdapter();

        }
        return mInstance;
    }

    @Override
    public void init(ReactApplicationContext reactContext, Callback successCallback, Callback errorCallback) {
        this.mContext = reactContext;
        successCallback.invoke();
    }

    @Override
    public List<PrinterDevice> getDeviceList(Callback errorCallback) {
        // errorCallback.invoke("do not need to invoke get device list for net
        // printer");
        // Use emitter instancee get devicelist to non block main thread
        this.scan();
        List<PrinterDevice> printerDevices = new ArrayList<>();
        return printerDevices;
    }

    private void scan() {
        if (isRunning)
            return;
        new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void run() {
                try {
                    isRunning = true;
                    emitEvent(EVENT_SCANNER_RUNNING, isRunning);

                    WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    String ipAddress = ipToString(wifiManager.getConnectionInfo().getIpAddress());
                    WritableArray array = Arguments.createArray();

                    String prefix = ipAddress.substring(0, ipAddress.lastIndexOf('.') + 1);
                    int suffix = Integer
                            .parseInt(ipAddress.substring(ipAddress.lastIndexOf('.') + 1, ipAddress.length()));

                    for (int i = 0; i <= 255; i++) {
                        if (i == suffix)
                            continue;
                        ArrayList<Integer> ports = getAvailablePorts(prefix + i);
                        if (!ports.isEmpty()) {
                            WritableMap payload = Arguments.createMap();

                            payload.putString("host", prefix + i);
                            payload.putInt("port", 9100);

                            array.pushMap(payload);
                        }
                    }

                    emitEvent(EVENT_SCANNER_RESOLVED, array);

                } catch (NullPointerException ex) {
                    Log.i(LOG_TAG, "No connection");
                } finally {
                    isRunning = false;
                    emitEvent(EVENT_SCANNER_RUNNING, isRunning);
                }
            }
        }).start();
    }

    private void emitEvent(String eventName, Object data) {
        if (mContext != null) {
            mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, data);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private ArrayList<Integer> getAvailablePorts(String address) {
        ArrayList<Integer> ports = new ArrayList<>();
        for (int port : PRINTER_ON_PORTS) {
            if (crunchifyAddressReachable(address, port))
                ports.add(port);
        }
        return ports;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private static boolean crunchifyAddressReachable(String address, int port) {
        try {

            try (Socket crunchifySocket = new Socket()) {
                // Connects this socket to the server with a specified timeout value.
                crunchifySocket.connect(new InetSocketAddress(address, port), 100);
            }
            // Return true if connection successful
            return true;
        } catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    private String ipToString(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }

    @Override
    public void selectDevice(PrinterDeviceId printerDeviceId, Callback sucessCallback, Callback errorCallback) {
        NetPrinterDeviceId netPrinterDeviceId = (NetPrinterDeviceId) printerDeviceId;

        if (this.mSocket != null && !this.mSocket.isClosed()
                && mNetDevice.getPrinterDeviceId().equals(netPrinterDeviceId)) {
            Log.i(LOG_TAG, "already selected device, do not need repeat to connect");
            sucessCallback.invoke(this.mNetDevice.toRNWritableMap());
            return;
        }

        try {
            Socket socket = new Socket(netPrinterDeviceId.getHost(), netPrinterDeviceId.getPort());
            if (socket.isConnected()) {
                closeConnectionIfExists();
                this.mSocket = socket;
                this.mNetDevice = new NetPrinterDevice(netPrinterDeviceId.getHost(), netPrinterDeviceId.getPort());
                sucessCallback.invoke(this.mNetDevice.toRNWritableMap());
            } else {
                errorCallback.invoke("unable to build connection with host: " + netPrinterDeviceId.getHost()
                        + ", port: " + netPrinterDeviceId.getPort());
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            errorCallback.invoke("failed to connect printer: " + e.getMessage());
        }
    }

    @Override
    public void selectStampDevice(PrinterDeviceId printerDeviceId, Callback sucessCallback, Callback errorCallback) {
        NetPrinterDeviceId netPrinterDeviceId = (NetPrinterDeviceId) printerDeviceId;

        if (this.mStampSocket != null && !this.mStampSocket.isClosed()
                && mStampNetDevice.getPrinterDeviceId().equals(netPrinterDeviceId)) {
            Log.i(LOG_TAG, "already selected device, do not need repeat to connect");
            sucessCallback.invoke(this.mStampNetDevice.toRNWritableMap());
            return;
        }

        try {
            Socket socket = new Socket(netPrinterDeviceId.getHost(), netPrinterDeviceId.getPort());
            if (socket.isConnected()) {
                closeConnectionStampIfExists();
                this.mStampSocket = socket;
                this.mStampNetDevice = new NetPrinterDevice(netPrinterDeviceId.getHost(), netPrinterDeviceId.getPort());
                sucessCallback.invoke(this.mStampNetDevice.toRNWritableMap());
            } else {
                errorCallback.invoke("unable to build connection with host: " + netPrinterDeviceId.getHost()
                        + ", port: " + netPrinterDeviceId.getPort());
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            errorCallback.invoke("failed to connect printer: " + e.getMessage());
        }
    }

    @Override
    public void closeConnectionIfExists() {
        if (this.mSocket != null) {
            if (!this.mSocket.isClosed()) {
                try {
                    this.mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            this.mSocket = null;

        }
    }

    @Override
    public void closeConnectionStampIfExists() {
        if (this.mStampSocket != null) {
            if (!this.mStampSocket.isClosed()) {
                try {
                    this.mStampSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            this.mStampSocket = null;
        }
    }

    @Override
    public void printRawData(String rawBase64Data, Callback errorCallback) {
        if (this.mSocket == null) {
            errorCallback.invoke("bluetooth connection is not built, may be you forgot to connectPrinter");
            return;
        }
        final String rawData = rawBase64Data;
        final Socket socket = this.mSocket;
        Log.v(LOG_TAG, "start to print raw data " + rawBase64Data);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] bytes = Base64.decode(rawData, Base64.DEFAULT);
                    OutputStream printerOutputStream = socket.getOutputStream();
                    printerOutputStream.write(bytes, 0, bytes.length);
                    printerOutputStream.flush();
                    closeConnectionIfExists();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "failed to print data" + rawData);
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void printLabelOptions(final ReadableMap options, Callback errorCallback) {
        if (this.mStampSocket == null) {
            errorCallback.invoke("network connection is not built, may be you forgot to connectPrinter");
            return;
        }
        final Socket socket = this.mStampSocket;
        Log.v(LOG_TAG, "start to print label ");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int width = options.getInt("width");
                    int height = options.getInt("height");
                    int gap = options.hasKey("gap") ? options.getInt("gap") : 0;

                    ReadableArray texts = options.hasKey("text")? options.getArray("text"):null;
                    ReadableArray qrCodes = options.hasKey("qrcode")? options.getArray("qrcode"):null;
                    ReadableArray barCodes = options.hasKey("barcode")? options.getArray("barcode"):null;
                    ReadableArray images = options.hasKey("image")? options.getArray("image"):null;

                    TscCommand tsc = new TscCommand();
                    tsc.addSize(width,height);
                    tsc.addGap(gap);
                    tsc.addCls();

                    // Add text to label
                    for (int i = 0;texts!=null&& i < texts.size(); i++) {
                        ReadableMap text = texts.getMap(i);
                        String t = text.getString("text");
                        int x = text.getInt("x");
                        int y = text.getInt("y");
                        TscCommand.FONTTYPE fonttype = findFontType(text.getString("fonttype"));
                        TscCommand.ROTATION rotation = findRotation(text.getInt("rotation"));
                        TscCommand.FONTMUL xscal = findFontMul(text.getInt("xscal"));
                        TscCommand.FONTMUL yscal = findFontMul(text.getInt("xscal"));
                        boolean bold = text.hasKey("bold") && text.getBoolean("bold");

                        try {
                            byte[] temp = t.getBytes("UTF-8");
                            String temStr = new String(temp, "UTF-8");
                            t = new String(temStr.getBytes("GB2312"), "GB2312");
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Text error");
                            return;
                        }

                        tsc.addText(x, y, fonttype, rotation, xscal, yscal, t);

                        if(bold){
                            tsc.addText(x+1, y, fonttype, rotation, xscal, yscal, t);
                            tsc.addText(x, y+1, fonttype, rotation, xscal, yscal, t);
                        }
                    }

                    if(images != null){
                        for (int i = 0; i < images.size(); i++) {
                            ReadableMap img = images.getMap(i);
                            int x = img.getInt("x");
                            int y = img.getInt("y");
                            int imgWidth = img.getInt("width");
                            TscCommand.BITMAP_MODE mode = findBitmapMode(img.getInt("mode"));
                            String image  = img.getString("image");
                            byte[] decoded = Base64.decode(image, Base64.DEFAULT);
                            Bitmap b = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                            tsc.addBitmap(x,y, mode, imgWidth,b);
                        }
                    }

                    tsc.addPrint(1, 1);
                    Vector<Byte> bytes = tsc.getCommand();
                    byte[] tosend = new byte[bytes.size()];
                    for(int i=0;i<bytes.size();i++){
                        tosend[i]= bytes.get(i);
                    }

                    OutputStream printerOutputStream = socket.getOutputStream();
                    printerOutputStream.write(tosend);
                    printerOutputStream.flush();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "failed to print label");
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            myBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);

            return myBitmap;
        } catch (IOException e) {
            // Log exception
            return null;
        }
    }

    @Override
    public void printImageData(final String imageUrl, int imageWidth, int imageHeight, Callback errorCallback) {
        final Bitmap bitmapImage = getBitmapFromURL(imageUrl);

        if (bitmapImage == null) {
            errorCallback.invoke("image not found");
            return;
        }

        if (this.mSocket == null) {
            errorCallback.invoke("Net connection is not built, may be you forgot to connectPrinter");
            return;
        }

        final Socket socket = this.mSocket;
        try {
            int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

            OutputStream printerOutputStream = socket.getOutputStream();

            printerOutputStream.write(SET_LINE_SPACE_24);
            printerOutputStream.write(CENTER_ALIGN);

            for (int y = 0; y < pixels.length; y += 24) {
                // Like I said before, when done sending data,
                // the printer will resume to normal text printing
                printerOutputStream.write(SELECT_BIT_IMAGE_MODE);
                // Set nL and nH based on the width of the image
                printerOutputStream.write(new byte[]{(byte) (0x00ff & pixels[y].length)
                        , (byte) ((0xff00 & pixels[y].length) >> 8)});
                for (int x = 0; x < pixels[y].length; x++) {
                    // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                    printerOutputStream.write(recollectSlice(y, x, pixels));
                }

                // Do a line feed, if not the printing will resume on the same line
                printerOutputStream.write(LINE_FEED);
            }
            printerOutputStream.write(SET_LINE_SPACE_32);
            printerOutputStream.write(LINE_FEED);

            printerOutputStream.flush();
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to print data");
            e.printStackTrace();
        }
    }

    @Override
    public void printImageBase64(final Bitmap bitmapImage, int imageWidth, int imageHeight, Callback errorCallback) {
        if (bitmapImage == null) {
            errorCallback.invoke("image not found");
            return;
        }

        if (this.mSocket == null) {
            errorCallback.invoke("Net connection is not built, may be you forgot to connectPrinter");
            return;
        }

        final Socket socket = this.mSocket;

        try {
            int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

            OutputStream printerOutputStream = socket.getOutputStream();

            printerOutputStream.write(SET_LINE_SPACE_24);
            printerOutputStream.write(CENTER_ALIGN);

            for (int y = 0; y < pixels.length; y += 24) {
                // Like I said before, when done sending data,
                // the printer will resume to normal text printing
                printerOutputStream.write(SELECT_BIT_IMAGE_MODE);
                // Set nL and nH based on the width of the image
                printerOutputStream.write(new byte[]{(byte) (0x00ff & pixels[y].length)
                        , (byte) ((0xff00 & pixels[y].length) >> 8)});
                for (int x = 0; x < pixels[y].length; x++) {
                    // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                    printerOutputStream.write(recollectSlice(y, x, pixels));
                }

                // Do a line feed, if not the printing will resume on the same line
                printerOutputStream.write(LINE_FEED);
            }
            printerOutputStream.write(SET_LINE_SPACE_32);
            printerOutputStream.write(LINE_FEED);

            printerOutputStream.flush();
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to print data");
            e.printStackTrace();
        }
    }

    @Override
    public void printQrCode(String qrCode, Callback errorCallback) {
        final Bitmap bitmapImage = TextToQrImageEncode(qrCode);

        if (bitmapImage == null) {
            errorCallback.invoke("image not found");
            return;
        }

        if (this.mSocket == null) {
            errorCallback.invoke("bluetooth connection is not built, may be you forgot to connectPrinter");
            return;
        }

        final Socket socket = this.mSocket;

        try {
            int[][] pixels = getPixelsSlow(bitmapImage, 0, 0);

            OutputStream printerOutputStream = socket.getOutputStream();

            printerOutputStream.write(SET_LINE_SPACE_24);
            printerOutputStream.write(CENTER_ALIGN);

            for (int y = 0; y < pixels.length; y += 24) {
                // Like I said before, when done sending data,
                // the printer will resume to normal text printing
                printerOutputStream.write(SELECT_BIT_IMAGE_MODE);
                // Set nL and nH based on the width of the image
                printerOutputStream.write(
                        new byte[] { (byte) (0x00ff & pixels[y].length), (byte) ((0xff00 & pixels[y].length) >> 8) });
                for (int x = 0; x < pixels[y].length; x++) {
                    // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                    printerOutputStream.write(recollectSlice(y, x, pixels));
                }

                // Do a line feed, if not the printing will resume on the same line
                printerOutputStream.write(LINE_FEED);
            }
            printerOutputStream.write(SET_LINE_SPACE_32);
            printerOutputStream.write(LINE_FEED);

            printerOutputStream.flush();
        } catch (IOException e) {
            Log.e(LOG_TAG, "failed to print data");
            e.printStackTrace();
        }
    }

    private Bitmap TextToQrImageEncode(String Value) {

        com.google.zxing.Writer writer = new QRCodeWriter();

        BitMatrix bitMatrix = null;
        try {
            bitMatrix = writer.encode(Value, com.google.zxing.BarcodeFormat.QR_CODE, 250, 250,
                    ImmutableMap.of(EncodeHintType.MARGIN, 1));
            int width = 250;
            int height = 250;
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    bmp.setPixel(i, j, bitMatrix.get(i, j) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (WriterException e) {
            // Log.e("QR ERROR", ""+e);

        }

        return null;
    }

    private byte[] recollectSlice(int y, int x, int[][] img) {
        byte[] slices = new byte[] { 0, 0, 0 };
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

    private boolean shouldPrintColor(int col) {
        final int threshold = 127;
        int a, r, g, b, luminance;
        a = (col >> 24) & 0xff;
        if (a != 0xff) {// Ignore transparencies
            return false;
        }
        r = (col >> 16) & 0xff;
        g = (col >> 8) & 0xff;
        b = col & 0xff;

        luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);

        return luminance < threshold;
    }

    public static Bitmap resizeTheImageForPrinting(Bitmap image) {
        // making logo size 150 or less pixels
        int width = image.getWidth();
        int height = image.getHeight();
        if (width > 200 || height > 200) {
            if (width > height) {
                float decreaseSizeBy = (200.0f / width);
                return getBitmapResized(image, decreaseSizeBy);
            } else {
                float decreaseSizeBy = (200.0f / height);
                return getBitmapResized(image, decreaseSizeBy);
            }
        }
        return image;
    }

    public static int getRGB(Bitmap bmpOriginal, int col, int row) {
        // get one pixel color
        int pixel = bmpOriginal.getPixel(col, row);
        // retrieve color of all channels
        int R = Color.red(pixel);
        int G = Color.green(pixel);
        int B = Color.blue(pixel);
        return Color.rgb(R, G, B);
    }

    public static Bitmap getBitmapResized(Bitmap image, float decreaseSizeBy) {
        Bitmap resized = Bitmap.createScaledBitmap(image, (int) (image.getWidth() * decreaseSizeBy),
                (int) (image.getHeight() * decreaseSizeBy), true);
        return resized;
    }

    private TscCommand.BARCODETYPE findBarcodeType(String type) {
        TscCommand.BARCODETYPE barcodeType = TscCommand.BARCODETYPE.CODE128;
        for (TscCommand.BARCODETYPE t : TscCommand.BARCODETYPE.values()) {
            if ((""+t.getValue()).equalsIgnoreCase(type)) {
                barcodeType = t;
                break;
            }
        }
        return barcodeType;
    }

    private TscCommand.READABLE findReadable(int readable) {
        TscCommand.READABLE ea = TscCommand.READABLE.EANBLE;
        if (TscCommand.READABLE.DISABLE.getValue() == readable) {
            ea = TscCommand.READABLE.DISABLE;
        }
        return ea;
    }

    private TscCommand.FONTMUL findFontMul(int scan) {
        TscCommand.FONTMUL mul = TscCommand.FONTMUL.MUL_1;
        for (TscCommand.FONTMUL m : TscCommand.FONTMUL.values()) {
            if (m.getValue() == scan) {
                mul = m;
                break;
            }
        }
        return mul;
    }

    private TscCommand.ROTATION findRotation(int rotation) {
        TscCommand.ROTATION rt = TscCommand.ROTATION.ROTATION_0;
        for (TscCommand.ROTATION r : TscCommand.ROTATION.values()) {
            if (r.getValue() == rotation) {
                rt = r;
                break;
            }
        }
        return rt;
    }

    private TscCommand.FONTTYPE findFontType(String fonttype) {
        TscCommand.FONTTYPE ft = TscCommand.FONTTYPE.FONT_CHINESE;
        for (TscCommand.FONTTYPE f : TscCommand.FONTTYPE.values()) {
            if ((""+f.getValue()).equalsIgnoreCase(fonttype)) {
                ft = f;
                break;
            }
        }
        return ft;
    }

    private TscCommand.BITMAP_MODE findBitmapMode(int mode){
        TscCommand.BITMAP_MODE bm = TscCommand.BITMAP_MODE.OVERWRITE;
        for (TscCommand.BITMAP_MODE m : TscCommand.BITMAP_MODE.values()) {
            if (m.getValue() == mode) {
                bm = m;
                break;
            }
        }
        return bm;
    }
}
