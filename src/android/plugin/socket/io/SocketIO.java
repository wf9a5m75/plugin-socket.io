package plugin.socket.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Ack;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;


public class SocketIO extends CordovaPlugin {
  private Socket socket;
  private Map<Integer, Emitter.Listener> LISTENERS = new HashMap<Integer, Emitter.Listener>();
  
  public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    
    try {
      Method method = this.getClass().getDeclaredMethod(action, JSONArray.class, CallbackContext.class);
      method.setAccessible(true);
      method.invoke(this, args, callbackContext);
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      callbackContext.error(e.getMessage());
      return false;
    }
    
  }
  
  @SuppressWarnings("unused")
  private void connect(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String uri;
    try {
      uri = args.getString(0);
      Log.d("CordovaLog", "--uri: " + uri);
      socket = IO.socket(uri);
      
      socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
        
        @Override
        public void call(Object... args) {
          Log.d("CordovaLog", "--connect: success");
          callbackContext.success();
        }
      });
      socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
        
        @Override
        public void call(Object... args) {
          Log.d("CordovaLog", "--connect: error");
          callbackContext.error("" + args[0]);
        }
      });
      socket.connect();
    } catch (Exception e) {
      e.printStackTrace();
      callbackContext.error(e.getMessage());
    }
  }
  @SuppressWarnings("unused")
  private void on(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    String event = args.getString(0);
    final Boolean needKeepCallback = args.getBoolean(1);
    
    Emitter.Listener listener = new Emitter.Listener() {
      
      @Override
      public void call(Object... args) {
        PluginResult pluginResult = makePluginResult(PluginResult.Status.OK, args);
        pluginResult.setKeepCallback(needKeepCallback);
        callbackContext.sendPluginResult(pluginResult);
      }
    };

    int hashCode = listener.hashCode();
    Log.d("CordovaLog", "callbackId=" + callbackContext.getCallbackId() + ",needKeep=" + needKeepCallback);

    this.socket.on(event, listener);
    
    PluginResult pluginResult;
    if (needKeepCallback) {
      pluginResult = new PluginResult(PluginResult.Status.OK, hashCode);
      pluginResult.setKeepCallback(true);
      LISTENERS.put(hashCode, listener);
      callbackContext.sendPluginResult(pluginResult);
    }
  }

  @SuppressWarnings("unused")
  private void off(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if (args.length() == 0) {
      this.socket.off();
      callbackContext.success();
      return;
    }

    String eventName = args.getString(0);
    if (args.length() == 1) {
      this.socket.off(eventName);
      callbackContext.success();
      return;
    }
    
    int hashCode = args.getInt(1);
    if (LISTENERS.containsKey(hashCode)) {
      Emitter.Listener listener = LISTENERS.remove(hashCode);
      this.socket.off(eventName, listener);
    }
  }
  
  @SuppressWarnings("unused")
  private void emit(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    // Get emit parameters
    boolean needCallback = args.getBoolean(0);
    String event = args.getString(1);
    
    Object[] params = new Object[args.length() - 2];
    for (int i = 2; i < args.length(); i++) {
      params[i - 2] = args.get(i);
    }
    
    Class<Socket> socketClass = (Class<Socket>) socket.getClass();
    try {
      Method emit = null;
      if (needCallback) {
        emit = socketClass.getMethod("emit", String.class, Object[].class, Ack.class);
        emit.invoke(socket, event, params, new Ack() {
          @Override
          public void call(Object... args) {
            callbackContext.sendPluginResult(makePluginResult(PluginResult.Status.OK, args));
          }
        });
      } else {
        emit = socketClass.getMethod("emit", String.class, Object[].class);
        emit.invoke(socket, event, params);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
    
  private PluginResult makePluginResult(PluginResult.Status status, Object... args) {
    if (args.length == 0) {
      return new PluginResult(status);
    }
    if (args.length == 1) {
      Object args0 = args[0];
      if (args0 instanceof JSONObject) {
        return new PluginResult(status, (JSONObject) args0);
      }
      if (args0 instanceof JSONArray) {
        return new PluginResult(status, (JSONArray) args0);
      }
      if (args0 instanceof Integer) {
        return new PluginResult(status, (Integer) args0);
      }
      if (args0 instanceof String) {
        return new PluginResult(status, (String) args0);
      }
      try {
        return new PluginResult(status, toByteArray(args0));
      } catch (IOException e) {
        return new PluginResult(status, ((String) args0).getBytes());
      }
    }
    
    JSONArray result = new JSONArray();
    for (int i = 0; i < args.length; i++) {
      result.put(args[i]);
    }
    return new PluginResult(status, result);
  
  }
  
  //toByteArray and toObject are taken from: http://tinyurl.com/69h8l7x
  public static byte[] toByteArray(Object obj) throws IOException {
      byte[] bytes = null;
      ByteArrayOutputStream bos = null;
      ObjectOutputStream oos = null;
      try {
          bos = new ByteArrayOutputStream();
          oos = new ObjectOutputStream(bos);
          oos.writeObject(obj);
          oos.flush();
          bytes = bos.toByteArray();
      } finally {
          if (oos != null) {
              oos.close();
          }
          if (bos != null) {
              bos.close();
          }
      }
      return bytes;
  }
  

  @SuppressWarnings("unused")
  private void getWebPfromBase64EncodedImage(final JSONArray args, final CallbackContext callbackContext) {
    cordova.getThreadPool().execute(new Runnable(){

      @SuppressLint("NewApi")
      @Override
      public void run() {
        String base64EncodedImage = null;
        try {
          base64EncodedImage = args.getString(0);
        } catch (JSONException e1) {
          e1.printStackTrace();
        }
        if (base64EncodedImage == null) {
          callbackContext.error("Cannot get image data");
          return;
        }
        
        String[] tmp = base64EncodedImage.split(",");
        byte[] byteArray= Base64.decode(tmp[1], Base64.DEFAULT);
        
        Bitmap bitmap= null;
        try {
          bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        } catch (Exception e) {
          e.printStackTrace();
        }
        if (bitmap == null) {
          callbackContext.error("Cannot get image");
          return;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.WEBP, 75, out);
        byte[] webpByteArray = out.toByteArray();
        callbackContext.success(webpByteArray);
        //String result = "data:image/webp;base64," + Base64.encodeToString(webpByteArray, Base64.NO_WRAP);
        //callbackContext.success(result);
/*
        int bytes = smallBitmap.getByteCount();
        ByteBuffer buffer = ByteBuffer.allocate(bytes);
        smallBitmap.copyPixelsToBuffer(buffer);
        byte[] pixels = buffer.array();
        int height = smallBitmap.getHeight();
        int width = smallBitmap.getWidth();
        int stride = bytes / height;
        int quality = 98;
        byte[] webpByteArray = libwebp.WebPEncodeRGBA(pixels, width, height, stride, quality);
        String result = "data:image/webp;base64," + Base64.encodeToString(webpByteArray, Base64.NO_WRAP);
        callbackContext.success(result);

        smallBitmap.recycle();
        bitmap.recycle();
        smallBitmap = null;
        bitmap = null;
        */
      }
      
    });
    
  }
}
