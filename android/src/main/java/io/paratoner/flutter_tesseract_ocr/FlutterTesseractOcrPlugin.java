package io.paratoner.flutter_tesseract_ocr;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class FlutterTesseractOcrPlugin implements FlutterPlugin, MethodCallHandler {
  TessBaseAPI api = null;
  HandlerThread handlerThread;
  Handler handler;
  Handler mainHandler = new Handler(Looper.getMainLooper());

  private MethodChannel channel;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    handlerThread = new HandlerThread("flutter_tesseract_ocr");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
    handler.post(() -> api = new TessBaseAPI());

    BinaryMessenger messenger = flutterPluginBinding.getBinaryMessenger();
    channel = new MethodChannel(messenger, "flutter_tesseract_ocr");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    channel = null;

    handler.post(() -> {
      api.recycle();
      api = null;
    });
    handler = null;
    handlerThread.quitSafely();
    handlerThread = null;
  }

  @Override
  public void onMethodCall(final MethodCall call, final Result result) {
    switch (call.method) {
      case "extractText":
        _handleExtract(call, result, false);
        break;
      case "extractHocr":
        _handleExtract(call, result, true);
        break;
      default:
        result.notImplemented();
    }
  }

  private void _handleExtract(MethodCall call, Result result, boolean isExtractHOCR) {
    final String tessDataPath = call.argument("tessData");
    final String imagePath = call.argument("imagePath");
    final Map<String, String> args = call.argument("args");

    Integer oemArg = call.argument("oem");
    final int oem = oemArg == null ? TessBaseAPI.OEM_DEFAULT : oemArg;

    String languageArg = call.argument("language");
    final String language = languageArg == null ? "eng" : languageArg;

    Integer psmArg = call.argument("psm");
    if (args != null) {
      // Compatible with older versions
      String psmString = args.remove("psm");
      if (psmString != null) {
        psmArg = Integer.parseInt(psmString);
      }
    }
    final int psm = psmArg == null ? TessBaseAPI.PageSegMode.PSM_AUTO_OSD : psmArg;

    handler.post(() -> {
      try {
        if (!api.init(tessDataPath, language, oem)) {
          mainHandler.post(() -> result.error("init-failed", "TessBaseAPI.init() return false", null));
        }
        api.setPageSegMode(psm);

        if (args != null) {
          for (Map.Entry<String, String> entry : args.entrySet()) {
            api.setVariable(entry.getKey(), entry.getValue());
          }
        }

        api.setImage(new File(imagePath));
        final String text = isExtractHOCR ? api.getHOCRText(0) : api.getUTF8Text();

        mainHandler.post(() -> result.success(text));

      } catch (RuntimeException e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        mainHandler.post(() -> result.error("error", e.getMessage(), writer.toString()));
      }
    });
  }
}
