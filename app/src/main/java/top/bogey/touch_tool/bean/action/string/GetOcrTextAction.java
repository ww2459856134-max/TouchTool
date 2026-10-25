package top.bogey.touch_tool.bean.action.string;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.ActionCheckResult;
import top.bogey.touch_tool.bean.action.ActionType;
import top.bogey.touch_tool.bean.action.parent.ExecuteOrCalculateAction;
import top.bogey.touch_tool.bean.action.parent.SyncAction;
import top.bogey.touch_tool.bean.pin.Pin;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_list.PinList;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinInteger;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_number.PinNumber;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinArea;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_scale_able.PinImage;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinSingleSelect;
import top.bogey.touch_tool.bean.pin.pin_objects.pin_string.PinString;
import top.bogey.touch_tool.bean.save.model.LiteRTModel;
import top.bogey.touch_tool.bean.save.model.ModelResult;
import top.bogey.touch_tool.bean.save.model.ModelSaver;
import top.bogey.touch_tool.bean.task.Task;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskRunnable;

public class GetOcrTextAction extends ExecuteOrCalculateAction implements SyncAction {
    private final transient Pin sourcePin = new Pin(new PinImage(), R.string.pin_image);
    private final transient Pin similarPin = new Pin(new PinInteger(60), R.string.get_ocr_text_action_similar);
    private final transient Pin typePin = new Pin(new PinSingleSelect(), R.string.get_ocr_text_action_type, false, false, true);
    private final transient Pin textPin = new Pin(new PinString(), R.string.get_ocr_text_action_text, true);
    private final transient Pin textArrayPin = new Pin(new PinList(new PinString()), true);
    private final transient Pin areaArrayPin = new Pin(new PinList(new PinArea()), true);
    private final transient Pin confPin = new Pin(new PinList(new PinInteger()), R.string.get_ocr_text_action_similar, true);
    // 失败原因：图片为空 / 模型缺失 / 未识别到文字。新针脚追加在末尾，旧任务按位置同步不受影响
    private final transient Pin errorPin = new Pin(new PinString(), R.string.cloud_ocr_error, true);

    public GetOcrTextAction() {
        super(ActionType.GET_OCR_TEXT);
        addPins(sourcePin, similarPin, typePin, textPin, textArrayPin, areaArrayPin, confPin, errorPin);
    }

    public GetOcrTextAction(JsonObject jsonObject) {
        super(jsonObject);
        reAddPins(sourcePin, similarPin, typePin, textPin, textArrayPin, areaArrayPin, confPin, errorPin);
    }

    @Override
    protected void doAction(TaskRunnable runnable, Pin pin) {
        sync(runnable.getTask());
        PinImage source = getPinValue(runnable, sourcePin);
        PinNumber<?> similar = getPinValue(runnable, similarPin);
        PinSingleSelect type = getPinValue(runnable, typePin);

        errorPin.setValue(new PinString(""));
        Bitmap bitmap = source.getImage();
        if (bitmap == null) {
            errorPin.setValue(new PinString("图片为空"));
            return;
        }

        List<LiteRTModel> models = ModelSaver.getInstance().getModelList(LiteRTModel.ModelType.OCR);
        if (models.size() <= type.getIndex()) {
            errorPin.setValue(new PinString("未找到可用的OCR模型"));
            return;
        }

        MainAccessibilityService service = MainApplication.getInstance().getService();
        LiteRTModel model = models.get(type.getIndex());
        List<ModelResult> results = model.execute(service, bitmap, similar.floatValue() / 100f);
        if (results.isEmpty()) {
            errorPin.setValue(new PinString("画面中未识别到文字"));
            return;
        }

        PinList textArray = textArrayPin.getValue(PinList.class);
        PinList areaArray = areaArrayPin.getValue(PinList.class);
        PinList confArray = confPin.getValue(PinList.class);

        StringBuilder builder = new StringBuilder();
        for (ModelResult result : results) {
            if (result.getValue() * 100 < similar.intValue()) continue;
            builder.append(result.getText()).append("\n");
            textArray.add(new PinString(result.getText()));
            RectF area = result.getArea();
            Rect rect = new Rect((int) area.left, (int) area.top, (int) area.right, (int) area.bottom);
            areaArray.add(new PinArea(rect));
            confArray.add(new PinInteger((int) (result.getValue() * 100)));
        }
        textPin.getValue(PinString.class).setValue(builder.toString().trim());
    }

    @Override
    public void check(ActionCheckResult result, Task task) {
        super.check(result, task);
        List<LiteRTModel> models = ModelSaver.getInstance().getModelList(LiteRTModel.ModelType.OCR);
        if (models.isEmpty()) {
            result.addResult(ActionCheckResult.ResultType.ERROR, R.string.check_need_ocr_module_error);
        }
    }

    @Override
    public void sync(Task context) {
        List<LiteRTModel> models = ModelSaver.getInstance().getModelList(LiteRTModel.ModelType.OCR);
        if (models.isEmpty()) return;

        List<String> modelNames = new ArrayList<>();
        for (LiteRTModel model : models) {
            modelNames.add(model.getName());
        }
        typePin.getValue(PinSingleSelect.class).setOptions(modelNames);
        typePin.setValue(context, typePin.getValue());
    }
}
