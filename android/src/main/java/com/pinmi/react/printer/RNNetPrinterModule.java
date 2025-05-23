package com.pinmi.react.printer;

import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.pinmi.react.printer.adapter.BLEPrinterDeviceId;
import com.pinmi.react.printer.adapter.NetPrinterAdapter;
import com.pinmi.react.printer.adapter.NetPrinterDeviceId;
import com.pinmi.react.printer.adapter.PrinterAdapter;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;

import java.util.List;
import java.io.InputStream;
/**
 * Created by xiesubin on 2017/9/22.
 */

public class RNNetPrinterModule extends ReactContextBaseJavaModule implements RNPrinterModule {

    private PrinterAdapter adapter;
    private ReactApplicationContext reactContext;

    public RNNetPrinterModule(ReactApplicationContext reactContext){
        super(reactContext);
        this.reactContext = reactContext;
    }

    @ReactMethod
    @Override
    public void init(Callback successCallback, Callback errorCallback) {
        this.adapter = NetPrinterAdapter.getInstance();
        this.adapter.init(reactContext,  successCallback, errorCallback);
    }

    @ReactMethod
    @Override
    public void closeConn() {
        this.adapter = NetPrinterAdapter.getInstance();
        this.adapter.closeConnectionIfExists();
    }

    @ReactMethod
    @Override
    public void closeConnectionStampIfExists() {
        this.adapter = NetPrinterAdapter.getInstance();
        this.adapter.closeConnectionStampIfExists();
    }

    @ReactMethod
    @Override
    public void getDeviceList(Callback successCallback, Callback errorCallback) {
        try {
            this.adapter.getDeviceList(errorCallback);
            successCallback.invoke();
        } catch (Exception ex) {
            errorCallback.invoke(ex.getMessage());
        }
        // this.adapter.getDeviceList(errorCallback);
    }

    @ReactMethod
    public void connectPrinter(String host, Integer port, Callback successCallback, Callback errorCallback) {
        adapter.selectDevice(NetPrinterDeviceId.valueOf(host, port), successCallback, errorCallback);
    }

    @ReactMethod
    public void connectStampPrinter(String host, Integer port, Callback successCallback, Callback errorCallback) {
        adapter.selectStampDevice(NetPrinterDeviceId.valueOf(host, port), successCallback, errorCallback);
    }

    @ReactMethod
    @Override
    public void printRawData(String base64Data, Callback errorCallback) {
        adapter.printRawData(base64Data, errorCallback);
    }

    @ReactMethod
    @Override
    public void printLabelOptions(final ReadableMap options, Callback errorCallback) {
        adapter.printLabelOptions(options, errorCallback);
    }

    @ReactMethod
    @Override
    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Callback errorCallback) {
        Log.v("imageUrl", imageUrl);
        adapter.printImageData(imageUrl, imageWidth, imageHeight, errorCallback);
    }

    @ReactMethod
    @Override
    public void printImageBase64(String base64, int imageWidth, int imageHeight, Callback errorCallback) {
        try {
            Uri uri = Uri.parse(base64);
            InputStream inputStream = getReactApplicationContext().getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                adapter.printImageBase64(bitmap, imageWidth, imageHeight, errorCallback);
                inputStream.close();
            }
        } catch (Exception e) {
            Log.e("printBase64", "Convert bitmap error");
        }
    }

    @ReactMethod
    @Override
    public void printQrCode(String qrCode, Callback errorCallback) {
        Log.v("qrCode", qrCode);
        adapter.printQrCode(qrCode, errorCallback);
    }



    @Override
    public String getName() {
        return "RNNetPrinter";
    }
}
