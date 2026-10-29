package top.bogey.touch_tool.ui.blueprint.card;

import java.util.HashMap;
import java.util.Map;

import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.action.Action;
import top.bogey.touch_tool.bean.action.ActionType;

/**
 * 动作使用说明：按动作类型给出具体说明，未收录的按分类给通用说明。
 */
public final class ActionHelp {
    private final static Map<ActionType, Integer> SPECIFIC = new HashMap<>();

    static {
        SPECIFIC.put(ActionType.IF_LOGIC, R.string.help_if_logic);
        SPECIFIC.put(ActionType.ELSE_IF_LOGIC, R.string.help_else_if_logic);
        SPECIFIC.put(ActionType.WAIT_IF_LOGIC, R.string.help_wait_if_logic);
        SPECIFIC.put(ActionType.SWITCH_LOGIC, R.string.help_switch_logic);
        SPECIFIC.put(ActionType.CHOICE_LOGIC, R.string.help_choice_logic);
        SPECIFIC.put(ActionType.FOR_LOGIC, R.string.help_for_logic);
        SPECIFIC.put(ActionType.WHILE_LOGIC, R.string.help_while_logic);
        SPECIFIC.put(ActionType.SEQUENCE_LOGIC, R.string.help_sequence_logic);
        SPECIFIC.put(ActionType.RANDOM_LOGIC, R.string.help_random_logic);
        SPECIFIC.put(ActionType.PARALLEL_LOGIC, R.string.parallel_action_help);
        SPECIFIC.put(ActionType.TRY_CATCH_LOGIC, R.string.help_try_catch_logic);
        SPECIFIC.put(ActionType.DELAY, R.string.help_delay);
        SPECIFIC.put(ActionType.FIND_NODE, R.string.help_find_node);
        SPECIFIC.put(ActionType.GET_VARIABLE, R.string.help_get_variable);
        SPECIFIC.put(ActionType.SET_VARIABLE, R.string.help_set_variable);
        SPECIFIC.put(ActionType.GET_OR_SET_VARIABLE, R.string.help_get_variable);
        SPECIFIC.put(ActionType.EXECUTE_TASK, R.string.help_execute_task);
        SPECIFIC.put(ActionType.STOP_TASK, R.string.help_stop_task);
        SPECIFIC.put(ActionType.NODE_TOUCH, R.string.help_node_touch);
        SPECIFIC.put(ActionType.EDITTEXT_INPUT, R.string.help_edittext_input);
        SPECIFIC.put(ActionType.EDITTEXT_PASTE, R.string.help_edittext_input);
        SPECIFIC.put(ActionType.IS_NODE_EXIST, R.string.help_is_node_exist);
        SPECIFIC.put(ActionType.PICK_NODE, R.string.help_pick_node);
        SPECIFIC.put(ActionType.TOUCH, R.string.help_touch);
        SPECIFIC.put(ActionType.CONTINUOUS_TAP, R.string.help_continuous_tap);
        SPECIFIC.put(ActionType.MULTI_FINGER_SWIPE, R.string.help_multi_finger_swipe);
        SPECIFIC.put(ActionType.FIND_IMAGE, R.string.help_find_image);
        SPECIFIC.put(ActionType.FIND_IMAGES, R.string.help_find_image);
        SPECIFIC.put(ActionType.FIND_COLORS, R.string.help_find_colors);
        SPECIFIC.put(ActionType.TOUCH_IMAGE, R.string.help_touch_image);
        SPECIFIC.put(ActionType.TOUCH_IMAGE_ALL, R.string.help_touch_image);
        SPECIFIC.put(ActionType.GET_IMAGE, R.string.help_get_image);
        SPECIFIC.put(ActionType.GET_OCR_TEXT, R.string.help_get_ocr_text);
        SPECIFIC.put(ActionType.FIND_OCR_TEXT, R.string.help_find_ocr_text);
        SPECIFIC.put(ActionType.IS_OCR_TEXT_EXIST, R.string.help_find_ocr_text);
        SPECIFIC.put(ActionType.LIST_FOREACH, R.string.help_list_foreach);
        SPECIFIC.put(ActionType.MAP_FOREACH, R.string.help_map_foreach);
        SPECIFIC.put(ActionType.OPEN_APP, R.string.help_open_app);
        SPECIFIC.put(ActionType.CLOSE_APP, R.string.help_close_app);
        SPECIFIC.put(ActionType.RESTART_APP, R.string.help_restart_app);
        SPECIFIC.put(ActionType.OPEN_URI_SCHEME, R.string.help_open_uri_scheme);
        SPECIFIC.put(ActionType.OPEN_SHORTCUT, R.string.help_open_shortcut);
        SPECIFIC.put(ActionType.GET_CURRENT_APPLICATION, R.string.help_get_current_app);
        SPECIFIC.put(ActionType.CHECK_IN_APPLICATION, R.string.help_check_in_app);
        SPECIFIC.put(ActionType.WAIT_IN_APPLICATION, R.string.help_wait_in_app);
        SPECIFIC.put(ActionType.STRING_TO_APP, R.string.help_string_to_app);
        SPECIFIC.put(ActionType.WECHAT_MINI_PROGRAM, R.string.help_wechat_mini_program);
        SPECIFIC.put(ActionType.LIST_DIR, R.string.help_list_dir);
        SPECIFIC.put(ActionType.READ_FILE, R.string.help_read_file);
        SPECIFIC.put(ActionType.READ_FILE_LINES, R.string.help_read_file_lines);
        SPECIFIC.put(ActionType.WRITE_FILE, R.string.help_write_file);
        SPECIFIC.put(ActionType.APPEND_LINE, R.string.help_append_line);
        SPECIFIC.put(ActionType.SEARCH_FILE_CONTENT, R.string.help_search_file_content);
        SPECIFIC.put(ActionType.GET_FILE_INFO, R.string.help_get_file_info);
        SPECIFIC.put(ActionType.CREATE_DIR, R.string.help_create_dir);
        SPECIFIC.put(ActionType.CREATE_FILE, R.string.help_create_file);
        SPECIFIC.put(ActionType.DELETE_FILE, R.string.help_delete_file);
        SPECIFIC.put(ActionType.COPY_FILE, R.string.help_copy_file);
        SPECIFIC.put(ActionType.MOVE_FILE, R.string.help_move_file);
        SPECIFIC.put(ActionType.RENAME_FILE, R.string.help_rename_file);
        SPECIFIC.put(ActionType.ZIP_CREATE, R.string.help_zip_create);
        SPECIFIC.put(ActionType.ZIP_EXTRACT, R.string.help_zip_extract);
        SPECIFIC.put(ActionType.READ_IMAGE_FILE, R.string.help_read_image_file);
        SPECIFIC.put(ActionType.WRITE_IMAGE_FILE, R.string.help_write_image_file);
        SPECIFIC.put(ActionType.EXCEL_READ, R.string.help_excel_read);
        SPECIFIC.put(ActionType.EXCEL_WRITE, R.string.help_excel_write);
        SPECIFIC.put(ActionType.SQL_EXECUTE, R.string.help_sql_execute);
        SPECIFIC.put(ActionType.LIST_MAKE, R.string.help_list_make);
        SPECIFIC.put(ActionType.LIST_APPEND, R.string.help_list_append);
        SPECIFIC.put(ActionType.LIST_ADD, R.string.help_list_add);
        SPECIFIC.put(ActionType.LIST_GET, R.string.help_list_get);
        SPECIFIC.put(ActionType.LIST_SET, R.string.help_list_set);
        SPECIFIC.put(ActionType.LIST_REMOVE, R.string.help_list_remove);
        SPECIFIC.put(ActionType.LIST_REMOVE_OBJECT, R.string.help_list_remove_object);
        SPECIFIC.put(ActionType.LIST_CONTAIN, R.string.help_list_contain);
        SPECIFIC.put(ActionType.LIST_INDEX_OF, R.string.help_list_index_of);
        SPECIFIC.put(ActionType.LIST_IS_EMPTY, R.string.help_list_is_empty);
        SPECIFIC.put(ActionType.LIST_SIZE, R.string.help_list_size);
        SPECIFIC.put(ActionType.LIST_REVERSE, R.string.help_list_reverse);
        SPECIFIC.put(ActionType.LIST_SORT, R.string.help_list_sort);
        SPECIFIC.put(ActionType.LIST_SUBLIST, R.string.help_list_sublist);
        SPECIFIC.put(ActionType.LIST_CLEAR, R.string.help_list_clear);
        SPECIFIC.put(ActionType.LIST_CHOICE, R.string.help_list_choice);
        SPECIFIC.put(ActionType.MAP_MAKE, R.string.help_map_make);
        SPECIFIC.put(ActionType.MAP_APPEND, R.string.help_map_append);
        SPECIFIC.put(ActionType.MAP_SET, R.string.help_map_set);
        SPECIFIC.put(ActionType.MAP_GET, R.string.help_map_get);
        SPECIFIC.put(ActionType.MAP_CONTAIN_KEY, R.string.help_map_contain_key);
        SPECIFIC.put(ActionType.MAP_CONTAIN_VALUE, R.string.help_map_contain_value);
        SPECIFIC.put(ActionType.MAP_KEYS, R.string.help_map_get_keys);
        SPECIFIC.put(ActionType.MAP_VALUES, R.string.help_map_get_values);
        SPECIFIC.put(ActionType.MAP_REMOVE, R.string.help_map_remove);
        SPECIFIC.put(ActionType.MAP_IS_EMPTY, R.string.help_map_is_empty);
        SPECIFIC.put(ActionType.MAP_CLEAR, R.string.help_map_clear);
        SPECIFIC.put(ActionType.MAP_SIZE, R.string.help_map_size);
    }

    private final static Map<String, Integer> CATEGORY = new HashMap<>();

    static {
        CATEGORY.put("start", R.string.help_cat_start);
        CATEGORY.put("logic", R.string.help_cat_logic);
        CATEGORY.put("task", R.string.help_cat_task);
        CATEGORY.put("variable", R.string.help_cat_variable);
        CATEGORY.put("normal", R.string.help_cat_normal);
        CATEGORY.put("node", R.string.help_cat_node);
        CATEGORY.put("point", R.string.help_cat_point);
        CATEGORY.put("area", R.string.help_cat_area);
        CATEGORY.put("image", R.string.help_cat_image);
        CATEGORY.put("string", R.string.help_cat_string);
        CATEGORY.put("number", R.string.help_cat_number);
        CATEGORY.put("bool", R.string.help_cat_bool);
        CATEGORY.put("list", R.string.help_cat_list);
        CATEGORY.put("map", R.string.help_cat_map);
        CATEGORY.put("file", R.string.help_cat_file);
        CATEGORY.put("app", R.string.help_cat_app);
        CATEGORY.put("system", R.string.help_cat_system);
        CATEGORY.put("api", R.string.help_cat_api);
    }

    private ActionHelp() {
    }

    public static int getHelpRes(Action action) {
        Integer specific = SPECIFIC.get(action.getType());
        if (specific != null) return specific;

        Package pkg = action.getClass().getPackage();
        if (pkg != null) {
            String name = pkg.getName();
            for (Map.Entry<String, Integer> entry : CATEGORY.entrySet()) {
                if (name.endsWith(".action." + entry.getKey())) return entry.getValue();
            }
        }
        return R.string.help_generic;
    }
}
