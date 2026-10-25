package top.bogey.touch_tool.bean.save.model;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.tencent.mmkv.MMKV;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import top.bogey.touch_tool.utils.GsonUtil;

public class ModelSaver {
    private static ModelSaver instance;

    public static ModelSaver getInstance() {
        synchronized (ModelSaver.class) {
            if (instance == null) {
                instance = new ModelSaver();
            }
        }
        return instance;
    }

    private static final String MODEL_DB = "MODEL_DB";
    private static final String METADATA = "metadata.json";

    private final MMKV mmkv = MMKV.mmkvWithID(MODEL_DB, MMKV.SINGLE_PROCESS_MODE);
    private final Map<String, LiteRTModel> modelMap = new LinkedHashMap<>();
    private final Set<OnModelChangedListener> listeners = ConcurrentHashMap.newKeySet();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnModelChangedListener {
        void onModelChanged();
    }

    public void addListener(OnModelChangedListener listener) {
        listeners.add(listener);
    }

    public void removeListener(OnModelChangedListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        mainHandler.post(() -> listeners.forEach(l -> {
            try {
                l.onModelChanged();
            } catch (Exception ignored) {
            }
        }));
    }

    private ModelSaver() {
        String[] keys = mmkv.allKeys();
        if (keys != null) {
            for (String key : keys) {
                LiteRTModel liteRTModel = GsonUtil.getAsObject(mmkv.decodeString(key), LiteRTModel.class, null);
                if (liteRTModel != null) modelMap.put(liteRTModel.getId(), liteRTModel);
            }
        }
    }

    public boolean importModel(Context context, Uri uri) {
        return importModelCore(context, uri);
    }

    // OCR 模型列表为空时自动导入内置的 PP-OCRv5 中文模型（含旧版本内置模型自动升级）
    // 返回是否执行了导入
    public boolean ensureBuiltinModel(Context context) {
        LiteRTModel existing = modelMap.get("builtin-ppocrv5-zh");
        boolean needUpgrade = false;
        if (existing != null) {
            // 已存在：内置模型版本低于当前打包版本则升级重导
            if (existing instanceof OcrModel ocrModel && ocrModel.getBuiltinVersion() >= 2) return false;
            needUpgrade = true;
        } else {
            if (!getModelList(LiteRTModel.ModelType.OCR).isEmpty()) return false;
        }
        try {
            if (needUpgrade) removeModel(context, "builtin-ppocrv5-zh");
            boolean result = importModel(context, context.getAssets().open("models/ppocr_v5_zh.zip"));
            if (result) notifyListeners();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean importModel(Context context, java.io.InputStream inputStream) {
        // 流落临时文件，转 Uri 走统一导入
        java.io.File temp = new java.io.File(context.getCacheDir(), "import_model.zip");
        try {
            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(temp);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        boolean result = importModelCore(context, Uri.fromFile(temp));
        temp.delete();
        return result;
    }

    private boolean importModelCore(Context context, Uri uri) {
        LiteRTModel liteRTModel = null;
        try (ZipInputStream zipInputStream = new ZipInputStream(context.getContentResolver().openInputStream(uri))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                String name = zipEntry.getName();
                if (METADATA.equals(name)) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zipInputStream));

                    StringBuilder builder = new StringBuilder();
                    char[] buffer = new char[1024];
                    int length;
                    while ((length = reader.read(buffer)) != -1) {
                        builder.append(new String(buffer, 0, length));
                    }
                    String json = builder.toString();
                    liteRTModel = GsonUtil.getAsObject(json, LiteRTModel.class, null);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (liteRTModel == null) return false;
        boolean imported = liteRTModel.importModel(context, uri);
        if (imported) {
            modelMap.put(liteRTModel.getId(), liteRTModel);
            mmkv.encode(liteRTModel.getId(), GsonUtil.toJson(liteRTModel));
            notifyListeners();
        }
        return imported;
    }

    public void removeModel(Context context, String id) {
        LiteRTModel liteRTModel = modelMap.remove(id);
        if (liteRTModel != null) {
            liteRTModel.removeModel(context);
        }
        mmkv.removeValueForKey(id);
        notifyListeners();
    }

    public List<LiteRTModel> getModelList(LiteRTModel.ModelType type) {
        List<LiteRTModel> list = new ArrayList<>();
        modelMap.values().forEach(modelInfo -> {
            if (modelInfo.getType() == type) {
                list.add(modelInfo);
            }
        });
        list.sort(Comparator.comparingLong(LiteRTModel::getTime));
        return list;
    }

    public List<LiteRTModel> getModelList() {
        List<LiteRTModel> list = new ArrayList<>(modelMap.values());
        list.sort(Comparator.comparingLong(LiteRTModel::getTime));
        return list;
    }

    public LiteRTModel getModel(String id) {
        return modelMap.get(id);
    }
}
