package com.pinmi.react.printer;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.ReadableMap;
import com.pinmi.react.printer.adapter.PrinterAdapter;
import com.pinmi.react.printer.adapter.PrinterDevice;
import com.pinmi.react.printer.adapter.USBPrinterAdapter;
import com.pinmi.react.printer.adapter.USBPrinterDeviceId;

import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.net.Uri;

import java.util.List;
import android.util.Log;
import java.io.InputStream;

/**
 * Created by xiesubin on 2017/9/22.
 */

public class RNUSBPrinterModule extends ReactContextBaseJavaModule implements RNPrinterModule {

    protected ReactApplicationContext reactContext;

    protected PrinterAdapter adapter;

    public RNUSBPrinterModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @ReactMethod
    @Override
    public void init(Callback successCallback, Callback errorCallback) {
        this.adapter = USBPrinterAdapter.getInstance();
        this.adapter.init(reactContext,  successCallback, errorCallback);
    }

    @ReactMethod
    @Override
    public void closeConn()  {
        adapter.closeConnectionIfExists();
    }

    @ReactMethod
    @Override
    public void closeConnectionStampIfExists()  {
        adapter.closeConnectionStampIfExists();
    }

    @ReactMethod
    @Override
    public void getDeviceList(Callback successCallback, Callback errorCallback)  {
        List<PrinterDevice> printerDevices = adapter.getDeviceList(errorCallback);
        WritableArray pairedDeviceList = Arguments.createArray();
        if(printerDevices.size() > 0) {
            for (PrinterDevice printerDevice : printerDevices) {
                pairedDeviceList.pushMap(printerDevice.toRNWritableMap());
            }
            successCallback.invoke(pairedDeviceList);
        }else{
            errorCallback.invoke("No Device Found");
        }
    }

    @ReactMethod
    @Override
    public void printRawData(String base64Data, Callback errorCallback){
        adapter.printRawData(base64Data, errorCallback);
    }

    @ReactMethod
    @Override
    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Callback errorCallback) {
        adapter.printImageData(imageUrl, 0, 0, errorCallback);
    }

    @ReactMethod
    @Override
    public void printLabelOptions(final ReadableMap options, Callback errorCallback) {
        adapter.printLabelOptions(options, errorCallback);
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
        adapter.printQrCode(qrCode, errorCallback);
    }


    @ReactMethod
    public void connectPrinter(Integer vendorId, Integer productId, Callback successCallback, Callback errorCallback) {
        adapter.selectDevice(USBPrinterDeviceId.valueOf(vendorId, productId), successCallback, errorCallback);
    }

    @ReactMethod
    public void connectStampPrinter(Integer vendorId, Integer productId, Callback successCallback, Callback errorCallback) {
        adapter.selectStampDevice(USBPrinterDeviceId.valueOf(vendorId, productId), successCallback, errorCallback);
    }

    @Override
    public String getName() {
        return "RNUSBPrinter";
    }
}
