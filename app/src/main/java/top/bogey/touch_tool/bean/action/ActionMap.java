package top.bogey.touch_tool.bean.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;

public class ActionMap {

    public static List<ActionType> getTypes(ActionGroupType groupType) {
        ArrayList<ActionType> list = new ArrayList<>();
        switch (groupType) {
            case START -> list.addAll(Arrays.asList(
                    ActionType.MANUAL_START,
                    ActionType.APPLICATION_START,
                    ActionType.APPLICATION_QUIT_START,
                    ActionType.TIME_START,
                    ActionType.NOTIFICATION_START,
                    ActionType.NETWORK_START,
                    ActionType.BATTERY_START,
                    ActionType.SCREEN_START,
                    ActionType.BLUETOOTH_START,
                    ActionType.RECEIVED_SHARE_START,
                    ActionType.BROADCAST_START,
                    ActionType.OUT_CALL_START,
                    ActionType.FILE_START,
                    ActionType.QUICK_TILE_START,
                    ActionType.GEOFENCE_START,
                    ActionType.NODE_APPEAR_START,
                    ActionType.NOTIFICATION_BUTTON_START,
                    ActionType.CUSTOM_START,
                    ActionType.CUSTOM_END
            ));

            case LOGIC -> list.addAll(Arrays.asList(
                    ActionType.IF_LOGIC,
                    ActionType.WAIT_IF_LOGIC,
                    ActionType.FOR_LOGIC,
                    ActionType.WHILE_LOGIC,
                    ActionType.RANDOM_LOGIC,
                    ActionType.SEQUENCE_LOGIC,
                    ActionType.PARALLEL_LOGIC,
                    ActionType.SWITCH_LOGIC,
                    ActionType.CHOICE_LOGIC
            ));

            case NORMAL -> list.addAll(Arrays.asList(
                    ActionType.DELAY,

                    ActionType.LOG,
                    ActionType.STICK,
                    ActionType.CLOSE_STICK,
                    ActionType.CLOSE_ALL_STICK,

                    ActionType.INPUT_CONFIG,
                    ActionType.STOP_TASK,
                    ActionType.SWITCH_TASK,
                    ActionType.IS_TASK_RUNNING,
                    ActionType.GET_ALL_RUNNING_TASK,
                    ActionType.SHOW_TEXT
            ));

            case CLICK -> list.addAll(Arrays.asList(
                    ActionType.TOUCH,
                    ActionType.TOUCH_POINT,
                    ActionType.NODE_TOUCH,
                    ActionType.TOUCH_IMAGE,
                    ActionType.TOUCH_IMAGE_ALL,
                    ActionType.DRAG_IMAGE,
                    ActionType.TOUCH_COLOR
            ));

            case APP -> list.addAll(Arrays.asList(
                    ActionType.OPEN_APP,
                    ActionType.OPEN_URI_SCHEME,
                    ActionType.WECHAT_MINI_PROGRAM,
                    ActionType.CLOSE_APP,
                    ActionType.RESTART_APP,
                    ActionType.OPEN_SHORTCUT,
                    ActionType.GET_CURRENT_APPLICATION,
                    ActionType.CHECK_IN_APPLICATION,
                    ActionType.STRING_TO_APP,
                    ActionType.WAIT_IN_APPLICATION
            ));

            case SYSTEM -> list.addAll(Arrays.asList(
                    ActionType.SHELL,
                    ActionType.SHARE_TO,
                    ActionType.SYSTEM_KEY,

                    ActionType.PLAY_RINGTONE,
                    ActionType.STOP_RINGTONE,
                    ActionType.TEXT_TO_SPEECH,
                    ActionType.VIBRATE,
                    ActionType.SEND_NOTIFICATION,
                    ActionType.NOTIFICATION_READ,
                    ActionType.NOTIFICATION_READ_ALL,
                    ActionType.NOTIFICATION_REMOVE,
                    ActionType.SEND_BROADCAST,
                    ActionType.SEND_TOAST,

                    ActionType.WRITE_TO_CLIPBOARD,
                    ActionType.READ_FROM_CLIPBOARD,

                    ActionType.SWITCH_SCREEN,
                    ActionType.GET_SCREEN_STATUS,

                    ActionType.SWITCH_CAPTURE,
                    ActionType.CHECK_CAPTURE_READY,

                    ActionType.GET_BATTERY_STATUS,

                    ActionType.GET_NETWORK_STATUS,
                    ActionType.GET_WIFI_INFO,

                    ActionType.GET_BLUETOOTH_DEVICES,

                    ActionType.GET_CURRENT_DATE,
                    ActionType.GET_CURRENT_TIME,

                    ActionType.GET_VOLUME,
                    ActionType.SET_VOLUME
            ));

            case FILE -> list.addAll(Arrays.asList(
                    ActionType.LIST_DIR,
                    ActionType.READ_FILE,
                    ActionType.WRITE_FILE,
                    ActionType.DELETE_FILE,
                    ActionType.COPY_FILE,
                    ActionType.MOVE_FILE,
                    ActionType.GET_FILE_INFO,
                    ActionType.CREATE_DIR,
                    ActionType.CREATE_FILE,
                    ActionType.RENAME_FILE,
                    ActionType.ZIP_CREATE,
                    ActionType.ZIP_EXTRACT,
                    ActionType.READ_FILE_LINES,
                    ActionType.APPEND_LINE,
                    ActionType.SEARCH_FILE_CONTENT,
                    ActionType.WRITE_IMAGE_FILE,
                    ActionType.READ_IMAGE_FILE,
                    ActionType.EXCEL_READ,
                    ActionType.EXCEL_WRITE,
                    ActionType.SQL_EXECUTE
            ));

            case NUMBER -> list.addAll(Arrays.asList(
                    ActionType.NUMBER_ADD,
                    ActionType.NUMBER_SUB,
                    ActionType.NUMBER_MUL,
                    ActionType.NUMBER_DIV,
                    ActionType.NUMBER_MOD,
                    ActionType.NUMBER_EQUAL,
                    ActionType.NUMBER_LESS,
                    ActionType.NUMBER_GREATER,
                    ActionType.NUMBER_RANDOM,
                    ActionType.NUMBER_TO_INT,
                    ActionType.NUMBER_ABS,

                    ActionType.CHECK_NUMBER_IN_VALUE_AREA,
                    ActionType.NUMBER_TO_VALUE_AREA,

                    ActionType.MATH_EXPRESSION
            ));

            case STRING -> list.addAll(Arrays.asList(
                    ActionType.STRING_TO_NUMBER,
                    ActionType.STRING_APPEND,
                    ActionType.STRING_SUBSTRING,
                    ActionType.STRING_REGEX,
                    ActionType.STRING_SPLIT,
                    ActionType.STRING_EQUAL,
                    ActionType.STRING_REPLACE,
                    ActionType.STRING_ENCODE,
                    ActionType.STRING_DECODE,
                    ActionType.STRING_ENCRYPT,
                    ActionType.GET_OCR_TEXT,
                    ActionType.CLOUD_OCR_TEXT,
                    ActionType.FIND_OCR_TEXT,
                    ActionType.IS_OCR_TEXT_EXIST,
                    ActionType.PARSE_JSON,
                    ActionType.STRING_FROM_OBJECT,
                    ActionType.STRING_TO_SINGLE_SELECT,
                    ActionType.STRING_CONTAIN,
                    ActionType.STRING_JOIN,
                    ActionType.STRING_REGEX_EXTRACT,
                    ActionType.STRING_LENGTH,
                    ActionType.STRING_CUT
            ));

             case BOOLEAN -> list.addAll(Arrays.asList(
                     ActionType.BOOLEAN_OR,
                     ActionType.BOOLEAN_AND,
                     ActionType.BOOLEAN_OR_SHORT,
                     ActionType.BOOLEAN_AND_SHORT,
                     ActionType.BOOLEAN_NOT
             ));

            case NODE -> list.addAll(Arrays.asList(
                    ActionType.FIND_NODE,
                    ActionType.IS_NODE_EXIST,
                    ActionType.GET_NODES_IN_AREA,
                    ActionType.GET_NODE_INFO,
                    ActionType.GET_NODE_CHILDREN,
                    ActionType.GET_NODE_PARENT,
                    ActionType.GET_WINDOWS,
                    ActionType.CHECK_NODE_VALID,

                    ActionType.NODE_TOUCH,
                    ActionType.EDITTEXT_INPUT,
                    ActionType.PICK_NODE,

                    ActionType.NODE_SCROLL,
                    ActionType.SCROLL_UNTIL_NODE,
                    ActionType.WAIT_NODE,
                    ActionType.NODE_EXPAND,
                    ActionType.NODE_TOUCH_ALL,
                    ActionType.NODE_SET_PROGRESS
            ));

            case IMAGE -> list.addAll(Arrays.asList(
                    ActionType.GET_IMAGE,
                    ActionType.LOAD_IMAGE,
                    ActionType.CROP_IMAGE,
                    ActionType.RESIZE_IMAGE,
                    ActionType.IMAGE_PREPROCESS,
                    ActionType.SAVE_IMAGE,
                    ActionType.FIND_IMAGE,
                    ActionType.FIND_IMAGES,
                    ActionType.IS_IMAGE_EXIST,
                    ActionType.TOUCH_IMAGE,
                    ActionType.TOUCH_IMAGE_ALL,
                    ActionType.DRAG_IMAGE,
                    ActionType.CREATE_QRCODE,
                    ActionType.PARSE_QRCODE,
                    ActionType.YOLO_DETECT,
                    ActionType.GET_COLOR,
                    ActionType.FIND_COLORS,
                    ActionType.IS_COLOR_EXIST,
                    ActionType.COLOR_EQUAL,
                    ActionType.TOUCH_COLOR
            ));

            case AREA -> list.addAll(Arrays.asList(
                    ActionType.AREA_TO_INT,
                    ActionType.AREA_FROM_INT,
                    ActionType.AREA_OFFSET,
                    ActionType.CHECK_AREA_CONTAIN_POS,
                    ActionType.CHECK_AREA_RELATION,
                    ActionType.GET_AREA_INTERSECTION,
                    ActionType.GET_AREA_CENTER,
                    ActionType.GET_AREA_RANDOM,
                    ActionType.PICK_AREA,
                    ActionType.MARK_AREA
            ));

            case POINT -> list.addAll(Arrays.asList(
                    ActionType.POINT_FROM_INT,
                    ActionType.POINT_TO_INT,
                    ActionType.POINT_OFFSET,
                    ActionType.POINT_TO_TOUCH,
                    ActionType.POINTS_TO_TOUCH,
                    ActionType.TOUCH,
                    ActionType.TOUCH_POINT,
                    ActionType.MULTI_FINGER_SWIPE,
                    ActionType.CONTINUOUS_TAP
            ));

            case LIST -> list.addAll(Arrays.asList(
                    ActionType.LIST_MAKE,
                    ActionType.LIST_FOREACH,
                    ActionType.LIST_ADD,
                    ActionType.LIST_GET,
                    ActionType.LIST_CHOICE,
                    ActionType.LIST_SET,
                    ActionType.LIST_SIZE,
                    ActionType.LIST_IS_EMPTY,
                    ActionType.LIST_CONTAIN,
                    ActionType.LIST_REMOVE,
                    ActionType.LIST_REMOVE_OBJECT,
                    ActionType.LIST_APPEND,
                    ActionType.LIST_CLEAR,
                    ActionType.LIST_INDEX_OF,
                    ActionType.LIST_SUBLIST,
                    ActionType.LIST_SORT,
                    ActionType.LIST_REVERSE
            ));

            case MAP -> list.addAll(Arrays.asList(
                    ActionType.MAP_MAKE,
                    ActionType.MAP_FOREACH,
                    ActionType.MAP_GET,
                    ActionType.MAP_SIZE,
                    ActionType.MAP_IS_EMPTY,
                    ActionType.MAP_CONTAIN_KEY,
                    ActionType.MAP_CONTAIN_VALUE,
                    ActionType.MAP_SET,
                    ActionType.MAP_REMOVE,
                    ActionType.MAP_APPEND,
                    ActionType.MAP_CLEAR,
                    ActionType.MAP_KEYS,
                    ActionType.MAP_VALUES
            ));

            case API -> list.addAll(Arrays.asList(
                    ActionType.HTTP_REQUEST,
                    ActionType.JSON_GET_VALUE,
                    ActionType.JSON_ARRAY,
                    ActionType.JS_CODE
            ));
        }
        return list;
    }

    public enum ActionGroupType {
        START, LOGIC, NORMAL, CLICK, APP, SYSTEM, FILE, NUMBER, STRING, BOOLEAN, NODE, IMAGE, AREA, POINT, LIST, MAP, API;

        public String getName() {
            String[] strings = MainApplication.getInstance().getResources().getStringArray(R.array.action_group);
            return strings[ordinal()];
        }
    }
}
