package top.bogey.touch_tool.ui.setting;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import top.bogey.touch_tool.MainApplication;
import top.bogey.touch_tool.R;
import top.bogey.touch_tool.bean.save.setting.SettingSaver;
import top.bogey.touch_tool.databinding.ViewSettingBinding;
import top.bogey.touch_tool.service.MainAccessibilityService;
import top.bogey.touch_tool.service.TaskInfoSummary;
import top.bogey.touch_tool.service.notification.NotificationService;
import top.bogey.touch_tool.service.super_user.ISuperUser;
import top.bogey.touch_tool.service.super_user.SuperUser;
import top.bogey.touch_tool.service.super_user.root.RootSuperUser;
import top.bogey.touch_tool.service.super_user.shizuku.ShizukuSuperUser;
import top.bogey.touch_tool.ui.MainActivity;
import top.bogey.touch_tool.ui.blueprint.picker.ColorPickerPreview;
import top.bogey.touch_tool.ui.tool.task_manager.ExportTaskDialog;
import top.bogey.touch_tool.utils.AppUtil;
import top.bogey.touch_tool.utils.HidClient;
import top.bogey.touch_tool.utils.HidOtaClient;

public class SettingView extends Fragment {
    private ViewSettingBinding binding;

    private final MenuProvider menuProvider = new MenuProvider() {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_setting, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.autoGiveCapturePermission) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.setting_auto_give_capture_permission)
                        .setMessage(R.string.setting_auto_give_capture_permission_des);
                String cmd = String.format("appops set %s PROJECT_MEDIA allow", requireActivity().getPackageName());
                if (SuperUser.getInstance().isValid()) {
                    builder.setPositiveButton(R.string.setting_execute_shell, (dialog, which) -> SuperUser.getInstance().runCommand(cmd))
                            .setNegativeButton(R.string.setting_copy_shell, (dialog, which) -> AppUtil.copyToClipboard(requireContext(), cmd))
                            .setNeutralButton(R.string.cancel, null)
                            .show();
                } else {
                    builder.setPositiveButton(R.string.setting_copy_shell, (dialog, which) -> AppUtil.copyToClipboard(requireContext(), cmd))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }
                return true;
            } else if (menuItem.getItemId() == R.id.helpDoc) {
                AppUtil.gotoUrl(requireContext(), getString(R.string.setting_help_doc_url));
            } else if (menuItem.getItemId() == R.id.helpVideo) {
                AppUtil.gotoUrl(requireContext(), getString(R.string.setting_help_video_url));
            }
            return false;
        }
    };

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getParentFragmentManager().beginTransaction().detach(this).commitAllowingStateLoss();
        getParentFragmentManager().beginTransaction().attach(this).commitAllowingStateLoss();
    }

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        MainActivity activity = (MainActivity) requireActivity();

        binding = ViewSettingBinding.inflate(inflater, container, false);

        binding.toolBar.addMenuProvider(menuProvider, getViewLifecycleOwner());

        // 功能启用
        binding.enableSwitch.setOnSwitchClickListener(v -> {
            MainAccessibilityService service = MainApplication.getInstance().getService();
            boolean enable = false;
            if (binding.enableSwitch.isChecked()) {
                if (AppUtil.isAccessibilityServiceEnabled(activity)) {
                    if (service != null) {
                        enable = true;
                        service.setEnabled(true);
                    } else {
                        binding.enableSwitch.setChecked(false);
                    }
                } else {
                    if (SettingSaver.SHOW_APP_SERVICE_ENABLE_TIPS.get()) {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } else {
                        AppUtil.showDialog(activity, getString(R.string.app_setting_enable_tips, getString(R.string.app_name)), result -> {
                            if (result) {
                                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                            }
                        });
                        SettingSaver.SHOW_APP_SERVICE_ENABLE_TIPS.set(true);
                    }
                    binding.enableSwitch.setChecked(false);
                }
            } else {
                if (service != null) {
                    service.setEnabled(false);
                }
            }
            SettingSaver.APP_SERVICE.set(enable);
        });

        MainAccessibilityService serv = MainApplication.getInstance().getService();
        binding.enableSwitch.setChecked(serv != null && serv.isEnabled());

        // 重启服务
        binding.reloadService.setOnClickListener(v -> {
            binding.reloadService.setEnabled(false);

            if (activity.stopAccessibilityServiceBySecurePermission()) {
                binding.getRoot().postDelayed(() -> {
                    SettingSaver.APP_SERVICE.set(true);
                    activity.restartAccessibilityServiceBySecurePermission();
                    Toast.makeText(activity, R.string.app_setting_reload_success, Toast.LENGTH_SHORT).show();
                    try {
                        binding.enableSwitch.setChecked(true);
                        binding.reloadService.setEnabled(true);
                    } catch (Exception ignored) {
                    }
                }, 1000);
            } else {
                Toast.makeText(activity, R.string.app_setting_reload_error, Toast.LENGTH_SHORT).show();
                binding.reloadService.setEnabled(true);
            }
        });
        refreshReloadService();

        // 自动重启服务
        binding.autoReloadService.setOnClickListener(v -> {

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.app_setting_auto_reload_title)
                    .setMessage(R.string.app_setting_auto_reload_message);
            String cmd = String.format("pm grant %s %s", requireActivity().getPackageName(), Manifest.permission.WRITE_SECURE_SETTINGS);
            if (SuperUser.getInstance().isValid()) {
                builder.setPositiveButton(R.string.setting_execute_shell, (dialog, which) -> {
                            SuperUser.getInstance().runCommand(cmd);
                            binding.getRoot().postDelayed(() -> {
                                refreshReloadService();
                                refreshAutoReloadService();
                            }, 500);
                        })
                        .setNegativeButton(R.string.setting_copy_shell, (dialog, which) -> AppUtil.copyToClipboard(requireContext(), cmd))
                        .setNeutralButton(R.string.cancel, null)
                        .show();
            } else {
                builder.setPositiveButton(R.string.setting_copy_shell, (dialog, which) -> AppUtil.copyToClipboard(requireContext(), cmd))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        refreshAutoReloadService();

        // 隐藏后台
        binding.hideBackSwitch.setOnSwitchClickListener(v -> SettingSaver.APP_HIDE_ACTIVITY_BACKGROUND.set(activity, binding.hideBackSwitch.isChecked()));
        binding.hideBackSwitch.setChecked(SettingSaver.APP_HIDE_ACTIVITY_BACKGROUND.get());

        // 忽略电量限制
        binding.ignoreBatterySwitch.setOnSwitchClickListener(v -> {
            if (binding.ignoreBatterySwitch.isChecked()) {
                if (!AppUtil.isIgnoredBattery(activity)) {
                    AppUtil.gotoIgnoreBattery(activity);
                    binding.ignoreBatterySwitch.setChecked(false);
                }
            } else {
                if (AppUtil.isIgnoredBattery(activity)) {
                    AppUtil.gotoAppDetailView(activity);
                    binding.ignoreBatterySwitch.setChecked(true);
                }
            }
        });
        binding.ignoreBatterySwitch.setChecked(AppUtil.isIgnoredBattery(activity));

        // 前台服务
        binding.forgeServiceSwitch.setOnSwitchClickListener(v -> SettingSaver.APP_KEEP_ALIVE_SERVICE.set(activity, binding.forgeServiceSwitch.isChecked()));
        binding.forgeServiceSwitch.setChecked(SettingSaver.APP_KEEP_ALIVE_SERVICE.get());

        // 自动备份
        binding.autoBackupSelect.setOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                View view = group.findViewById(checkedId);
                int index = group.indexOfChild(view);
                SettingSaver.TASK_AUTO_BACKUP.set(index);
                MainAccessibilityService service = MainApplication.getInstance().getService();
                if (service != null && service.isEnabled()) {
                    service.addAlarm();
                }
            }
        });
        binding.autoBackupSelect.checkIndex(SettingSaver.TASK_AUTO_BACKUP.get());
        binding.autoBackupSelect.setOnClickListener(v -> {
            ExportTaskDialog.autoBackup(activity);
            Toast.makeText(activity, R.string.app_setting_auto_backup_tips, Toast.LENGTH_SHORT).show();
        });

        // 清理缓存
        binding.cleanCacheButton.setOnClickListener(v -> {
            String[] dirs = new String[]{AppUtil.LOG_DIR_NAME, AppUtil.TASK_DIR_NAME, AppUtil.DOCUMENT_DIR_NAME};
            String[] dirNames = getResources().getStringArray(R.array.cache_dir_name);

            File[] files = new File[dirs.length];
            boolean[] isChecked = new boolean[dirs.length];
            for (int i = 0; i < dirs.length; i++) {
                String dir = dirs[i];
                File file = new File(activity.getCacheDir(), dir);
                files[i] = file;
                String sizeString = AppUtil.getFileSizeString(file);
                dirNames[i] += " (" + sizeString + ")";
                isChecked[i] = file.exists();
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.app_setting_clean_cache)
                    .setPositiveButton(R.string.enter, (dialog, which) -> {
                        boolean flag = true;
                        for (int i = 0; i < isChecked.length; i++) {
                            boolean checked = isChecked[i];
                            if (checked) {
                                AppUtil.deleteFile(files[i]);
                            } else {
                                flag = false;
                            }
                        }
                        if (flag) AppUtil.deleteFile(activity.getCacheDir());
                        binding.cleanCacheButton.setDescription(getString(R.string.app_setting_clean_cache_desc, AppUtil.getCacheDirsSizeString(activity)));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setMultiChoiceItems(dirNames, isChecked, (dialog, which, checked) -> isChecked[which] = checked)
                    .show();
        });
        binding.cleanCacheButton.setDescription(getString(R.string.app_setting_clean_cache_desc, AppUtil.getCacheDirsSizeString(activity)));

        // 超级用户
        binding.superUserSelect.setOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                View view = group.findViewById(checkedId);
                int index = group.indexOfChild(view);
                SettingSaver.PERMISSION_SUPER_USER.set(index);

                // 尝试超级用户
                ISuperUser instance = SuperUser.getInstance();
                if (instance != null) {
                    instance.init(result -> {
                        if (!result) {
                            SettingSaver.PERMISSION_SUPER_USER.set(0);
                            binding.superUserSelect.checkIndex(0);

                            if (instance instanceof ShizukuSuperUser) Toast.makeText(activity, R.string.permission_setting_super_user_no_shizuku, Toast.LENGTH_SHORT).show();
                            else if (instance instanceof RootSuperUser) Toast.makeText(activity, R.string.permission_setting_super_user_no_root, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                refreshNotificationCmd();
                refreshAutoReloadService();
            }
        });
        binding.superUserSelect.checkIndex(SettingSaver.PERMISSION_SUPER_USER.get());

        // 通知来源
        binding.notificationTypeSelect.checkIndex(SettingSaver.PERMISSION_NOTIFICATION.get());
        binding.notificationTypeSelect.setOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                View view = group.findViewById(checkedId);
                int index = group.indexOfChild(view);
                if (index == 0) {
                    SettingSaver.PERMISSION_NOTIFICATION.set(0);
                    activity.stopService(new Intent(activity, NotificationService.class));
                } else {
                    if (NotificationService.isEnabled()) {
                        SettingSaver.PERMISSION_NOTIFICATION.set(1);
                    } else {
                        NotificationService.requestConnect(activity);
                        SettingSaver.PERMISSION_NOTIFICATION.set(0);
                        binding.notificationTypeSelect.checkIndex(0);
                    }
                }
                refreshNotificationCmd();
            }
        });

        binding.notificationTypeCmd.setOnClickListener(v -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.permission_setting_notification_type_sensitive_dialog_title)
                    .setMessage(R.string.permission_setting_notification_type_sensitive_dialog_message);
            String cmd = String.format("appops set %s RECEIVE_SENSITIVE_NOTIFICATIONS allow", requireActivity().getPackageName());
            if (SuperUser.getInstance().isValid()) {
                builder.setPositiveButton(R.string.setting_execute_shell, (dialog, which) -> SuperUser.getInstance().runCommand(cmd))
                        .setNegativeButton(R.string.setting_copy_shell, (dialog, which) -> AppUtil.copyToClipboard(requireContext(), cmd))
                        .setNeutralButton(R.string.cancel, null)
                        .show();
            } else {
                builder.setPositiveButton(R.string.setting_copy_shell, (dialog, which) -> AppUtil.copyToClipboard(requireContext(), cmd))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        refreshNotificationCmd();

        // 精确定时
        AlarmManager alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
        binding.alarmSwitch.setOnSwitchClickListener(v -> {
            if (binding.alarmSwitch.isChecked()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        SettingSaver.PERMISSION_EXACT_ALARM.set(true);
                    } else {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        startActivity(intent);
                        binding.alarmSwitch.setChecked(false);
                    }
                } else {
                    binding.alarmSwitch.setChecked(true);
                }
            } else {
                SettingSaver.PERMISSION_EXACT_ALARM.set(false);
            }
            if (binding.alarmSwitch.isChecked()) {
                MainAccessibilityService service = MainApplication.getInstance().getService();
                if (service != null && service.isEnabled()) {
                    service.resetAllAlarm();
                }
            }
        });
        binding.alarmSwitch.setChecked(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() && SettingSaver.PERMISSION_EXACT_ALARM.get());
        binding.alarmSwitch.setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? View.VISIBLE : View.GONE);

        // 蓝牙监听
        binding.bluetoothSwitch.setOnSwitchClickListener(v -> {
            if (binding.bluetoothSwitch.isChecked()) {
                binding.bluetoothSwitch.setChecked(false);
                activity.launcherBluetooth((code, data) -> {
                    boolean enable = code == Activity.RESULT_OK;
                    SettingSaver.PERMISSION_BLUETOOTH.set(enable);
                    binding.bluetoothSwitch.setChecked(enable);
                });
            } else {
                SettingSaver.PERMISSION_BLUETOOTH.set(false);
            }
        });
        binding.bluetoothSwitch.setChecked(SettingSaver.PERMISSION_BLUETOOTH.get());

        // 所有文件访问
        binding.storageSwitch.setOnSwitchClickListener(v -> {
            if (binding.storageSwitch.isChecked()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    // 去系统授权页，返回时在 onResume 里刷新状态
                    binding.storageSwitch.setChecked(false);
                    AppUtil.startStoragePermissionActivity(activity);
                } else {
                    SettingSaver.PERMISSION_STORAGE.set(true);
                }
            } else {
                // 关闭授权也只能去系统设置页操作
                binding.storageSwitch.setChecked(true);
                AppUtil.startStoragePermissionActivity(activity);
            }
        });
        boolean storageGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Environment.isExternalStorageManager() : SettingSaver.PERMISSION_STORAGE.get();
        binding.storageSwitch.setChecked(storageGranted);

        // 云端 OCR 密钥
        binding.cloudOcrApiKey.setOnClickListener(v -> AppUtil.showEditDialog(requireContext(), R.string.setting_cloud_ocr_key, SettingSaver.BAIDU_OCR_API_KEY.get(), result -> {
            SettingSaver.BAIDU_OCR_API_KEY.set(result == null ? "" : result.trim());
            binding.cloudOcrApiKey.setDescription(maskKey(SettingSaver.BAIDU_OCR_API_KEY.get()));
        }));
        binding.cloudOcrApiKey.setDescription(maskKey(SettingSaver.BAIDU_OCR_API_KEY.get()));

        binding.cloudOcrSecretKey.setOnClickListener(v -> AppUtil.showEditDialog(requireContext(), R.string.setting_cloud_ocr_secret, SettingSaver.BAIDU_OCR_SECRET_KEY.get(), result -> {
            SettingSaver.BAIDU_OCR_SECRET_KEY.set(result == null ? "" : result.trim());
            binding.cloudOcrSecretKey.setDescription(maskKey(SettingSaver.BAIDU_OCR_SECRET_KEY.get()));
        }));

        binding.cloudOcrSecretKey.setDescription(maskKey(SettingSaver.BAIDU_OCR_SECRET_KEY.get()));

        // HID 设备（ESP32）：IP 与端口，供 HID 中继、点击图片等动作共用
        binding.hidDevice.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_hid_device)
                .setItems(new CharSequence[]{
                        getString(R.string.setting_hid_manual),
                        getString(R.string.setting_hid_discover),
                        getString(R.string.setting_hid_test),
                        getString(R.string.setting_hid_check),
                        getString(R.string.setting_hid_ota)}, (dialog, which) -> {
                    if (which == 0) {
                        editHidDevice();
                    } else if (which == 1) {
                        discoverHidDevice();
                    } else if (which == 2) {
                        testHidDevice();
                    } else if (which == 3) {
                        refreshHidDevice();
                    } else {
                        startHidOta();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
        refreshHidDevice();

        // HID 蓝牙直连（固件 v1.2.3+）：指令走 BLE GATT，不依赖 WiFi
        binding.hidBleMode.setOnSwitchClickListener(v -> {
            if (binding.hidBleMode.isChecked()) {
                binding.hidBleMode.setChecked(false);
                activity.launcherBluetooth((code, data) -> {
                    boolean enable = code == Activity.RESULT_OK;
                    SettingSaver.HID_USE_BLE.set(enable);
                    binding.hidBleMode.setChecked(enable);
                    refreshHidBle();
                    if (enable) {
                        Toast.makeText(requireContext(), "蓝牙直连已开启，动作将优先走蓝牙发送", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                SettingSaver.HID_USE_BLE.set(false);
                top.bogey.touch_tool.utils.BleHidClient.disconnect();
                refreshHidBle();
            }
        });
        binding.hidBleMode.setChecked(SettingSaver.HID_USE_BLE.get());

        binding.hidBleDevice.setOnClickListener(v -> pickBleDevice());
        refreshHidBle();

        // 定位
        binding.locationSwitch.setOnSwitchClickListener(v -> {
            if (binding.locationSwitch.isChecked()) {
                binding.locationSwitch.setChecked(false);
                activity.launcherFineLocation((code, data) -> {
                    boolean enable = code == Activity.RESULT_OK;
                    enable &= activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    SettingSaver.PERMISSION_LOCATION.set(enable);
                    binding.locationSwitch.setChecked(enable);
                    if (enable) {
                        TaskInfoSummary.getInstance().setWifiInfo(AppUtil.getWifiInfo(activity));
                    }
                });
            } else {
                SettingSaver.PERMISSION_LOCATION.set(false);
            }
        });
        binding.locationSwitch.setChecked(SettingSaver.PERMISSION_LOCATION.get());


        // 手势轨迹
        binding.showTouchSwitch.setOnSwitchClickListener(v -> SettingSaver.TASK_GESTURE_TRACE.set(binding.showTouchSwitch.isChecked()));
        binding.showTouchSwitch.setChecked(SettingSaver.TASK_GESTURE_TRACE.get());

        // 标记目标区域
        binding.showTargetAreaSwitch.setOnSwitchClickListener(v -> SettingSaver.TASK_TARGET_MARK.set(binding.showTargetAreaSwitch.isChecked()));
        binding.showTargetAreaSwitch.setChecked(SettingSaver.TASK_TARGET_MARK.get());

        // 任务提示
        binding.taskTipsSwitch.setOnSwitchClickListener(v -> SettingSaver.TASK_RUNNING_TIPS.set(binding.taskTipsSwitch.isChecked()));
        binding.taskTipsSwitch.setChecked(SettingSaver.TASK_RUNNING_TIPS.get());

        // 详细日志
        binding.detailLogSwitch.setOnSwitchClickListener(v -> SettingSaver.TASK_DETAIL_LOG.set(binding.detailLogSwitch.isChecked()));
        binding.detailLogSwitch.setChecked(SettingSaver.TASK_DETAIL_LOG.get());

        // 日志重置
        binding.resetLogSwitch.setOnSwitchClickListener(v -> SettingSaver.TASK_RESET_DETAIL_LOG.set(binding.resetLogSwitch.isChecked()));
        binding.resetLogSwitch.setChecked(SettingSaver.TASK_RESET_DETAIL_LOG.get());

        // 音量键退出
        binding.volumeButtonExitSwitch.setOnSwitchClickListener(v -> SettingSaver.TASK_VOLUME_KEY_STOP.set(binding.volumeButtonExitSwitch.isChecked()));
        binding.volumeButtonExitSwitch.setChecked(SettingSaver.TASK_VOLUME_KEY_STOP.get());


        // 卡片默认展开状态
        binding.cardTypeSelect.setOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                View view = group.findViewById(checkedId);
                int index = group.indexOfChild(view);
                SettingSaver.BLUEPRINT_CARD_EXPAND_STATE.set(index);
            }
        });
        binding.cardTypeSelect.checkIndex(SettingSaver.BLUEPRINT_CARD_EXPAND_STATE.get());

        // 卡片整理时的间隔
        binding.arrangeCardOffset.setSliderOnChangeListener((slider, value, fromUser) -> SettingSaver.BLUEPRINT_CARD_ARRANGE_PADDING.set((int) value));
        binding.arrangeCardOffset.setValue(SettingSaver.BLUEPRINT_CARD_ARRANGE_PADDING.get());

        // 连环添加动作
        binding.chainAddSwitch.setOnSwitchClickListener(v -> SettingSaver.BLUEPRINT_CHAIN_ADD.set(binding.chainAddSwitch.isChecked()));
        binding.chainAddSwitch.setChecked(SettingSaver.BLUEPRINT_CHAIN_ADD.get());


        // 手动执行悬浮窗
        binding.manualPlaySetting.setOnClickListener(v -> {
            NavController controller = Navigation.findNavController(MainApplication.getInstance().getActivity(), R.id.conView);
            controller.navigate(SettingViewDirections.actionSettingToSettingPlayView());
        });

        // 小窗优化
        binding.supportFreeFormSwitch.setOnSwitchClickListener(v -> SettingSaver.FREE_FORM_OPTIMIZE.set(binding.supportFreeFormSwitch.isChecked()));
        binding.supportFreeFormSwitch.setChecked(SettingSaver.FREE_FORM_OPTIMIZE.get());

        // 暗色模式
        binding.themeSelect.setOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                View view = group.findViewById(checkedId);
                int index = group.indexOfChild(view);
                SettingSaver.NIGHT_MODE.set(index);
            }
        });
        binding.themeSelect.checkIndex(SettingSaver.NIGHT_MODE.get());

        // 动态颜色
        binding.dynamicColorSwitch.setOnSwitchClickListener(v -> SettingSaver.DYNAMIC_COLOR.set(activity, binding.dynamicColorSwitch.isChecked()));
        binding.dynamicColorSwitch.setOnClickListener(v -> ColorPickerPreview.showPicker(result -> SettingSaver.DYNAMIC_COLOR_OPTIONS.set(activity, result.getColor()), SettingSaver.DYNAMIC_COLOR_OPTIONS.get()));
        binding.dynamicColorSwitch.setChecked(SettingSaver.DYNAMIC_COLOR.get());

        PackageManager packageManager = activity.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(activity.getPackageName(), 0);
            binding.versionText.setText(packageInfo.versionName + "(" + packageInfo.versionCode + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return binding.getRoot();
    }

    private void refreshHidBle() {
        String mac = SettingSaver.HID_BLE_MAC.get();
        if (mac == null || mac.trim().isEmpty()) {
            binding.hidBleDevice.setDescription("未选择（请先在系统蓝牙里配对「鼠标键盘」）");
        } else {
            binding.hidBleDevice.setDescription(mac + (SettingSaver.HID_USE_BLE.get() ? "  ● 蓝牙直连中" : "  ○ 已选择"));
        }
    }

    // 从已配对蓝牙设备中选择 ESP32 HID 设备（需先在系统蓝牙里完成配对）
    private void pickBleDevice() {
        MainActivity activity = (MainActivity) requireActivity();
        activity.launcherBluetooth((code, data) -> {
            if (code != Activity.RESULT_OK || !isAdded()) return;
            android.bluetooth.BluetoothManager bm = (android.bluetooth.BluetoothManager)
                    requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
            if (bm == null || bm.getAdapter() == null) {
                Toast.makeText(requireContext(), "蓝牙不可用", Toast.LENGTH_SHORT).show();
                return;
            }
            java.util.Set<android.bluetooth.BluetoothDevice> bonded;
            try {
                bonded = bm.getAdapter().getBondedDevices();
            } catch (SecurityException e) {
                return;
            }
            if (bonded == null || bonded.isEmpty()) {
                Toast.makeText(requireContext(), "没有已配对设备，请先在系统蓝牙里配对", Toast.LENGTH_LONG).show();
                return;
            }
            java.util.List<android.bluetooth.BluetoothDevice> list = new java.util.ArrayList<>(bonded);
            CharSequence[] names = new CharSequence[list.size()];
            for (int i = 0; i < list.size(); i++) {
                android.bluetooth.BluetoothDevice d = list.get(i);
                String name;
                try {
                    name = d.getName();
                } catch (SecurityException e) {
                    name = null;
                }
                names[i] = (name == null || name.isEmpty() ? "未命名设备" : name) + "  (" + d.getAddress() + ")";
            }
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.setting_hid_ble_device)
                    .setItems(names, (dialog, which) -> {
                        SettingSaver.HID_BLE_MAC.set(list.get(which).getAddress());
                        refreshHidBle();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private String maskKey(String key) {
        if (key == null || key.isEmpty()) return "未配置";
        if (key.length() <= 6) return "已配置";
        return key.substring(0, 4) + "****";
    }

    @SuppressLint("SetTextI18n")
    private void refreshHidDevice() {
        String ip = SettingSaver.HID_DEVICE_IP.get();
        int port = SettingSaver.HID_DEVICE_PORT.get();
        if (ip == null || ip.isEmpty()) {
            binding.hidDevice.setDescription("未配置（点击输入 IP 与端口）");
            return;
        }
        binding.hidDevice.setDescription(ip + ":" + port + "  ● 检测中…");
        // 异步探活（走 TCP，UDP 探活不可靠），结果回填状态点、延迟与固件版本
        new Thread(() -> {
            long start = System.currentTimeMillis();
            String ping = HidClient.httpPing(ip, 80, 1500);
            if (ping == null) HidClient.markOffline();
            else HidClient.markOnline();
            long cost = System.currentTimeMillis() - start;
            String desc;
            if (ping == null) {
                desc = ip + ":" + port + "  ● 不可达";
            } else {
                // 新固件返回 "pong 1.1.0"，老固件只能退回根页面判断
                String version = ping.startsWith("pong") ? ping.substring(Math.min(4, ping.length())).trim() : "";
                desc = ip + ":" + port + "  ● 在线 " + cost + "ms" + (version.isEmpty() ? "" : "  v" + version);
            }
            String finalDesc = desc;
            requireActivity().runOnUiThread(() -> {
                if (isAdded()) binding.hidDevice.setDescription(finalDesc);
            });
        }, "hid-status").start();
    }

    // 手动输入 IP 与端口
    private void editHidDevice() {
        String currentIp = SettingSaver.HID_DEVICE_IP.get();
        AppUtil.showEditDialog(requireContext(), R.string.setting_hid_device, currentIp, result -> {
            String ip = result == null ? "" : result.trim();
            SettingSaver.HID_DEVICE_IP.set(ip);
            int currentPort = SettingSaver.HID_DEVICE_PORT.get();
            AppUtil.showEditDialog(requireContext(), R.string.hid_relay_port, String.valueOf(currentPort), portResult -> {
                try {
                    int port = Integer.parseInt(portResult == null ? "" : portResult.trim());
                    SettingSaver.HID_DEVICE_PORT.set(Math.max(1, Math.min(65535, port)));
                } catch (NumberFormatException ignored) {
                }
                refreshHidDevice();
            });
            refreshHidDevice();
        });
    }

    // 自动发现：扫描局域网找 ESP32 配置页
    private void discoverHidDevice() {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(requireContext());
        progress.setMessage("正在扫描局域网（约3秒）…");
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            java.util.List<String> found = HidClient.discoverDevices();
            requireActivity().runOnUiThread(() -> {
                progress.dismiss();
                if (found.isEmpty()) {
                    Toast.makeText(requireContext(), "未发现 ESP32 设备，请确认手机与设备连同一个WiFi", Toast.LENGTH_LONG).show();
                    return;
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.setting_hid_discover)
                        .setItems(found.toArray(new CharSequence[0]), (dialog, which) -> {
                            SettingSaver.HID_DEVICE_IP.set(found.get(which));
                            refreshHidDevice();
                            Toast.makeText(requireContext(), "已选择 " + found.get(which), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        }, "hid-discover").start();
    }

    // 测试连接：访问设备配置页，顺带取固件信息
    private void testHidDevice() {
        String ip = SettingSaver.HID_DEVICE_IP.get();
        if (ip == null || ip.isEmpty()) {
            Toast.makeText(requireContext(), "请先配置设备IP", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), "正在测试 " + ip + " …", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String body = HidClient.httpGet(ip, 80, 3000);
            requireActivity().runOnUiThread(() -> {
                if (body == null) {
                    Toast.makeText(requireContext(), "连接失败：设备不可达（检查IP与WiFi）", Toast.LENGTH_LONG).show();
                } else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("连接成功 " + ip + ":" + SettingSaver.HID_DEVICE_PORT.get())
                            .setMessage(body)
                            .setPositiveButton(R.string.enter, null)
                            .show();
                }
            });
        }, "hid-test").start();
    }

    // ---------------- HID 固件 OTA 升级 ----------------
    // 流程：读 /info 确认设备可升级 → 选本地 .bin → 分块上传到 /update → 设备写另一分区并重启
    private void startHidOta() {
        String ip = SettingSaver.HID_DEVICE_IP.get();
        if (ip == null || ip.isEmpty()) {
            Toast.makeText(requireContext(), "请先配置设备IP", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), "正在读取设备信息…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String info = HidOtaClient.fetchInfo(ip, 80, 3000);
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (info == null) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("无法连接设备")
                            .setMessage("读取 http://" + ip + ":80/info 失败。\n\n"
                                    + "请确认：\n1. 手机与设备在同一 WiFi\n2. 设备已刷入支持 OTA 的固件（v1.1.0+）\n3. IP 地址正确")
                            .setPositiveButton(R.string.enter, null)
                            .show();
                    return;
                }
                showOtaDialog(ip, info);
            });
        }, "hid-ota-info").start();
    }

    private void showOtaDialog(String ip, String info) {
        // 从 /info 的 JSON 里粗略取版本与芯片，取不到就原样展示
        String version = "-";
        String chip = "-";
        try {
            org.json.JSONObject json = new org.json.JSONObject(info);
            version = json.optString("fw", "-");
            chip = json.optString("chip", "-");
        } catch (Exception ignored) {
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("固件升级 " + ip)
                .setMessage("当前固件：" + version + "\n芯片：" + chip + "\n\n"
                        + "选择编译好的 firmware.bin 上传。上传期间不要断电，"
                        + "完成后设备会自动重启，原有 WiFi 配置和蓝牙配对保留。")
                .setPositiveButton("选择 .bin 文件", (d, w) -> {
                    MainActivity activity = MainApplication.getInstance().getActivity();
                    if (activity == null) return;
                    activity.launcherOpenDocument((code, intent) -> {
                        if (code != Activity.RESULT_OK || intent == null || intent.getData() == null) return;
                        android.net.Uri uri = intent.getData();
                        String name = queryDisplayName(uri);
                        confirmOta(ip, uri, name);
                    }, "application/octet-stream");
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String queryDisplayName(android.net.Uri uri) {
        android.database.Cursor cursor = null;
        try {
            cursor = requireContext().getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "firmware.bin";
    }

    private void confirmOta(String ip, android.net.Uri uri, String fileName) {
        if (fileName == null || !fileName.toLowerCase().endsWith(".bin")) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("文件类型不对")
                    .setMessage("请选择 PlatformIO 编译产出的 firmware.bin。\n\n"
                            + "注意：不要选 autojs-hid-esp32c3.bin 这种整片镜像，"
                            + "OTA 只能刷应用分区，整片镜像会导致升级后无法启动。")
                    .setPositiveButton(R.string.enter, null)
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认升级")
                .setMessage("即将把 " + fileName + " 写入设备 " + ip + "\n\n"
                        + "⚠️ 升级过程中不要断电或断网。若固件与芯片型号不匹配，设备会启动失败，需要用数据线重刷。")
                .setPositiveButton("开始升级", (d, w) -> doOtaUpload(ip, uri, fileName))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doOtaUpload(String ip, android.net.Uri uri, String fileName) {
        android.app.ProgressDialog progress = new android.app.ProgressDialog(requireContext());
        progress.setTitle("正在升级固件");
        progress.setMessage("准备中…");
        progress.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        HidOtaClient.uploadFirmware(ip, 80, uri, fileName, new HidOtaClient.Progress() {
            @Override
            public void onProgress(int percent, long sent, long total) {
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    progress.setProgress(Math.max(0, percent));
                    progress.setMessage("已上传 " + (sent / 1024) + " KB / " + (total / 1024) + " KB");
                });
            }

            @Override
            public void onResult(boolean ok, String message) {
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    progress.dismiss();
                    if (!ok) {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("升级失败")
                                .setMessage(message + "\n\n"
                                        + "设备原固件未被破坏，可直接重试。\n"
                                        + "常见原因：\n"
                                        + "· 选的不是 firmware.bin（整片镜像 / 老版本固件）\n"
                                        + "· 上传中途断网或锁屏导致进程被挂起\n"
                                        + "· 固件不是本工程编译的，缺少 /update 接口")
                                .setPositiveButton(R.string.enter, null)
                                .show();
                        return;
                    }
                    // 成功也再确认一次：等设备重启完成后读 /ping 看版本号是否真的变了
                    verifyOta(ip, message);
                });
            }
        });
    }

    // 升级后的自检：轮询 /ping，最多等 30 秒，读到版本号才算真成功
    private void verifyOta(String ip, String successMessage) {
        android.app.ProgressDialog wait = new android.app.ProgressDialog(requireContext());
        wait.setMessage("设备重启中，正在确认版本…");
        wait.setCancelable(false);
        wait.show();
        new Thread(() -> {
            String version = null;
            long deadline = System.currentTimeMillis() + 30000L;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                String ping = HidClient.httpPing(ip, 80, 1500);
                if (ping != null && ping.startsWith("pong")) {
                    version = ping.substring(4).trim();
                    break;
                }
            }
            String finalVersion = version;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                wait.dismiss();
                HidClient.markOffline();  // 清缓存，强制下次重新探活
                refreshHidDevice();
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(finalVersion != null ? "升级成功" : "已上传，待确认")
                        .setMessage(finalVersion != null
                                ? successMessage + "\n\n设备当前固件：v" + finalVersion + "\n"
                                  + "现在可以使用 HID 按键、键盘输入、拖拽/长按/连点了。"
                                : "固件已写入，但 30 秒内没读到设备新版本号。\n\n"
                                  + "可能还在重启，稍后点「检测连接状态」看版本号即可。\n"
                                  + "若版本号长期不变，说明上传的文件不是本工程编译的 firmware.bin。")
                        .setPositiveButton(R.string.enter, null)
                        .show();
            });
        }, "hid-ota-verify").start();
    }

    private void refreshNotificationCmd() {
        int notificationType = SettingSaver.PERMISSION_NOTIFICATION.get();
        boolean version = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
        binding.notificationTypeCmd.setVisibility(notificationType == 1 && version ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从系统授权页返回时刷新所有文件访问开关状态
        if (binding.storageSwitch != null) {
            boolean granted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Environment.isExternalStorageManager() : SettingSaver.PERMISSION_STORAGE.get();
            SettingSaver.PERMISSION_STORAGE.set(granted);
            binding.storageSwitch.setChecked(granted);
        }
    }

    private void refreshReloadService() {
        boolean granted = requireActivity().checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
        binding.reloadService.setVisibility(granted ? View.VISIBLE : View.GONE);
    }

    private void refreshAutoReloadService() {
        boolean granted = requireActivity().checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
        binding.autoReloadService.setVisibility(granted ? View.GONE : View.VISIBLE);
    }
}
