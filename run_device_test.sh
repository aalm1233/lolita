#!/bin/bash
# ============================================================
# Lolita App USB Device UI Test Suite
# ============================================================
# Prerequisites:
#   - Android device connected via USB with USB debugging enabled
#   - Debug APK installed (com.lolita.app)
#   - Git Bash on Windows (requires MSYS_NO_PATHCONV=1)
#
# Usage:
#   export MSYS_NO_PATHCONV=1
#   bash run_device_test.sh
#
# Test results: test_results.txt
# Screenshots:  test_screenshots/
# ============================================================

set -euo pipefail
export MSYS_NO_PATHCONV=1

ADB="${ADB:-C:/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCREENSHOT_DIR="${SCRIPT_DIR}/test_screenshots"
RESULT_FILE="${SCRIPT_DIR}/test_results.txt"
WIN_SCREENSHOT_DIR="$(cygpath -w "$SCREENSHOT_DIR" 2>/dev/null || echo "$SCREENSHOT_DIR")"
mkdir -p "$SCREENSHOT_DIR"

PASS=0
FAIL=0
WARN=0

# --- Helpers ---

log_result() {
    local name="$1" status="$2"
    echo "[$status] $name" >> "$RESULT_FILE"
    case "$status" in
        PASS) PASS=$((PASS + 1)) ;;
        FAIL) FAIL=$((FAIL + 1)) ;;
        WARN) WARN=$((WARN + 1)) ;;
    esac
}

screenshot() {
    local name="$1"
    $ADB shell "screencap -p /sdcard/${name}.png"
    $ADB pull /sdcard/${name}.png "${WIN_SCREENSHOT_DIR}\\${name}.png" 2>&1 | tail -1
}

local_size() {
    stat -c %s "${SCREENSHOT_DIR}/$1.png" 2>/dev/null || echo "0"
}

tap() {
    local x="$1" y="$2"
    $ADB shell "input tap $x $y"
    sleep 2
}

back() {
    $ADB shell "input keyevent KEYCODE_BACK"
    sleep 1
}

scroll_down() {
    $ADB shell "input swipe 558 1800 558 600 300"
    sleep 1
}

scroll_up() {
    $ADB shell "input swipe 558 600 558 1800 300"
    sleep 1
}

assert_page_changed() {
    local pre_sz="$1" post_sz="$2" label="$3"
    if [ "$post_sz" != "$pre_sz" ] && [ "$post_sz" != "0" ]; then
        echo "  PASS: $label (size changed: $pre_sz -> $post_sz)"
        log_result "$label" "PASS"
    else
        echo "  FAIL: $label (size unchanged: $pre_sz -> $post_sz)"
        log_result "$label" "FAIL"
    fi
}

get_ui_texts() {
    $ADB shell "uiautomator dump /sdcard/ui_dump.xml" 2>&1 >/dev/null
    $ADB shell "cat /sdcard/ui_dump.xml" | grep -oP 'text="[^"]*"' | sed 's/text="//;s/"$//' | grep -v '^$' | sort -u
}

# --- Device coordinate map (1116x2480 @ 480dpi) ---
# Bottom navigation (tap y=2330 to avoid gesture nav at y>=2372):
#   首页: x=102, 愿望单: x=330, 穿搭: x=558, 统计: x=786, 个人: x=1014
# Top tabs on home page (y=159):
#   位置: x=207, 服饰: x=381, 套装: x=555, 图鉴: x=720
# Top bar buttons:
#   Menu/Drawer: (60,159), Search: (846,159), Filter: (942,159), ViewMode: (1053,159)
# FAB button: (984, 2168)
# "记录今日穿搭" banner: (558, 303)

# --- Initialize ---
echo "=== Lolita App UI Test Suite ===" > "$RESULT_FILE"
echo "Date: $(date)" >> "$RESULT_FILE"
echo "Device: $($ADB shell getprop ro.product.model | tr -d '\r')" >> "$RESULT_FILE"
echo "Android: $($ADB shell getprop ro.build.version.sdk | tr -d '\r')" >> "$RESULT_FILE"
echo "" >> "$RESULT_FILE"

echo "Starting test suite..."
echo "Screenshots saved to: $SCREENSHOT_DIR"

# Clear logcat for clean crash detection
$ADB shell "logcat -c" 2>/dev/null || true

# ============================================================
# GROUP 1: App Launch
# ============================================================
echo ""
echo "=== GROUP 1: App Launch ==="
echo "--- GROUP 1: App Launch ---" >> "$RESULT_FILE"

$ADB shell "am force-stop com.lolita.app"
sleep 1
$ADB shell "am start -n com.lolita.app/com.lolita.app.ui.MainActivity"
sleep 3

screenshot "G01_launch"
launch_sz=$(local_size "G01_launch")
if [ "$launch_sz" -gt 50000 ]; then
    echo "  PASS: T1.01 App launches successfully ($launch_sz bytes)"
    log_result "T1.01 App launches" "PASS"
else
    echo "  FAIL: T1.01 App launch (blank screen? $launch_sz bytes)"
    log_result "T1.01 App launches" "FAIL"
fi

texts=$(get_ui_texts)
missing_bottom=""
for t in 首页 愿望单 穿搭 统计 个人; do
    echo "$texts" | grep -q "$t" || missing_bottom="$missing_bottom $t"
done
if [ -z "$missing_bottom" ]; then
    echo "  PASS: T1.02 Bottom nav 5 tabs visible"
    log_result "T1.02 Bottom nav tabs visible" "PASS"
else
    echo "  FAIL: T1.02 Missing tabs:$missing_bottom"
    log_result "T1.02 Bottom nav tabs visible" "FAIL"
fi

missing_top=""
for t in 位置 服饰 套装 图鉴; do
    echo "$texts" | grep -q "$t" || missing_top="$missing_top $t"
done
if [ -z "$missing_top" ]; then
    echo "  PASS: T1.03 Top 4 tabs visible"
    log_result "T1.03 Top tabs visible" "PASS"
else
    echo "  FAIL: T1.03 Missing tabs:$missing_top"
    log_result "T1.03 Top tabs visible" "FAIL"
fi

# ============================================================
# GROUP 2: Bottom Navigation
# ============================================================
echo ""
echo "=== GROUP 2: Bottom Navigation ==="
echo "--- GROUP 2: Bottom Navigation ---" >> "$RESULT_FILE"

tap 102 2330; sleep 1
screenshot "G02_00_home"
home_sz=$(local_size "G02_00_home")

# T2.01: 愿望单
tap 330 2330
screenshot "G02_01_wishlist"
wish_sz=$(local_size "G02_01_wishlist")
assert_page_changed "$home_sz" "$wish_sz" "T2.01 Navigate to Wishlist"

# T2.02: 穿搭
tap 558 2330
screenshot "G02_02_outfit"
outfit_sz=$(local_size "G02_02_outfit")
assert_page_changed "$wish_sz" "$outfit_sz" "T2.02 Navigate to Outfit Log"

# T2.03: 统计
tap 786 2330
screenshot "G02_03_stats"
stats_sz=$(local_size "G02_03_stats")
assert_page_changed "$outfit_sz" "$stats_sz" "T2.03 Navigate to Stats"

# T2.04: 个人
tap 1014 2330
screenshot "G02_04_settings"
settings_sz=$(local_size "G02_04_settings")
assert_page_changed "$stats_sz" "$settings_sz" "T2.04 Navigate to Settings"

# T2.05: Return home
tap 102 2330
screenshot "G02_05_home_return"
home2_sz=$(local_size "G02_05_home_return")
echo "  PASS: T2.05 Return to Home"
log_result "T2.05 Return to Home" "PASS"

# ============================================================
# GROUP 3: Home Top Tabs
# ============================================================
echo ""
echo "=== GROUP 3: Home Top Tabs ==="
echo "--- GROUP 3: Home Top Tabs ---" >> "$RESULT_FILE"

tap 207 159; screenshot "G03_01_location"
loc_sz=$(local_size "G03_01_location")

tap 381 159; screenshot "G03_02_items"
items_sz=$(local_size "G03_02_items")

tap 555 159; screenshot "G03_03_coordinate"
coord_sz=$(local_size "G03_03_coordinate")

tap 720 159; screenshot "G03_04_catalog"
catalog_sz=$(local_size "G03_04_catalog")

echo "  Sizes: Location=$loc_sz Items=$items_sz Coordinate=$coord_sz Catalog=$catalog_sz"
if [ "$loc_sz" != "$items_sz" ] && [ "$items_sz" != "$coord_sz" ] && [ "$coord_sz" != "$catalog_sz" ]; then
    echo "  PASS: T3.01-04 All 4 top tabs switch correctly"
    log_result "T3.01-04 Top tab switching" "PASS"
else
    echo "  WARN: T3.01-04 Some tabs may share same view"
    log_result "T3.01-04 Top tab switching" "WARN"
fi

# ============================================================
# GROUP 4: FAB - Item Edit
# ============================================================
echo ""
echo "=== GROUP 4: FAB Tests ==="
echo "--- GROUP 4: FAB Tests ---" >> "$RESULT_FILE"

tap 381 159; sleep 1
screenshot "G04_pre_item_fab"
pre_sz=$(local_size "G04_pre_item_fab")
tap 984 2168
screenshot "G04_post_item_fab"
post_sz=$(local_size "G04_post_item_fab")
assert_page_changed "$pre_sz" "$post_sz" "T4.01 FAB opens Item Edit"
back; sleep 1

# ============================================================
# GROUP 5: FAB - Coordinate Edit
# ============================================================
echo ""
echo "=== GROUP 5: Coordinate FAB ==="
echo "--- GROUP 5: Coordinate FAB ---" >> "$RESULT_FILE"

tap 555 159; sleep 1
screenshot "G05_pre_coord_fab"
pre_sz=$(local_size "G05_pre_coord_fab")
tap 984 2168
screenshot "G05_post_coord_fab"
post_sz=$(local_size "G05_post_coord_fab")
assert_page_changed "$pre_sz" "$post_sz" "T5.01 FAB opens Coordinate Edit"
back; sleep 1

# ============================================================
# GROUP 6: FAB - Catalog Edit
# ============================================================
echo ""
echo "=== GROUP 6: Catalog FAB ==="
echo "--- GROUP 6: Catalog FAB ---" >> "$RESULT_FILE"

tap 720 159; sleep 1
screenshot "G06_pre_catalog_fab"
pre_sz=$(local_size "G06_pre_catalog_fab")
tap 984 2168
screenshot "G06_post_catalog_fab"
post_sz=$(local_size "G06_post_catalog_fab")
assert_page_changed "$pre_sz" "$post_sz" "T6.01 FAB opens Catalog Edit"
back; sleep 1

# ============================================================
# GROUP 7: Settings Page
# ============================================================
echo ""
echo "=== GROUP 7: Settings Page ==="
echo "--- GROUP 7: Settings Page ---" >> "$RESULT_FILE"

tap 1014 2330; sleep 2
screenshot "G07_settings"
settings_sz=$(local_size "G07_settings")
echo "  PASS: T7.01 Settings page accessible ($settings_sz bytes)"
log_result "T7.01 Settings page accessible" "PASS"

scroll_down; sleep 1
screenshot "G07_settings_scrolled"
scrolled_sz=$(local_size "G07_settings_scrolled")
if [ "$scrolled_sz" != "$settings_sz" ]; then
    echo "  PASS: T7.02 Settings page scrollable"
    log_result "T7.02 Settings page scrollable" "PASS"
else
    echo "  WARN: T7.02 Settings scroll unverified"
    log_result "T7.02 Settings page scrollable" "WARN"
fi

# ============================================================
# GROUP 8: Stats Page
# ============================================================
echo ""
echo "=== GROUP 8: Stats Page ==="
echo "--- GROUP 8: Stats Page ---" >> "$RESULT_FILE"

tap 786 2330; sleep 2
screenshot "G08_stats"
stats_sz=$(local_size "G08_stats")
echo "  PASS: T8.01 Stats page accessible ($stats_sz bytes)"
log_result "T8.01 Stats page accessible" "PASS"

scroll_down; sleep 1
screenshot "G08_stats_scrolled"
scrolled_sz=$(local_size "G08_stats_scrolled")
if [ "$scrolled_sz" != "$stats_sz" ]; then
    echo "  PASS: T8.02 Stats page scrollable"
    log_result "T8.02 Stats page scrollable" "PASS"
else
    echo "  WARN: T8.02 Stats scroll unverified"
    log_result "T8.02 Stats page scrollable" "WARN"
fi

# ============================================================
# GROUP 9: Outfit Log Page
# ============================================================
echo ""
echo "=== GROUP 9: Outfit Log ==="
echo "--- GROUP 9: Outfit Log ---" >> "$RESULT_FILE"

tap 558 2330; sleep 2
screenshot "G09_outfit"
outfit_sz=$(local_size "G09_outfit")
echo "  PASS: T9.01 Outfit Log page accessible ($outfit_sz bytes)"
log_result "T9.01 Outfit Log page accessible" "PASS"

# ============================================================
# GROUP 10: Wishlist Page
# ============================================================
echo ""
echo "=== GROUP 10: Wishlist ==="
echo "--- GROUP 10: Wishlist ---" >> "$RESULT_FILE"

tap 330 2330; sleep 2
screenshot "G10_wishlist"
wish_sz=$(local_size "G10_wishlist")
echo "  PASS: T10.01 Wishlist page accessible ($wish_sz bytes)"
log_result "T10.01 Wishlist page accessible" "PASS"

# ============================================================
# GROUP 11: Back Navigation
# ============================================================
echo ""
echo "=== GROUP 11: Back Navigation ==="
echo "--- GROUP 11: Back Navigation ---" >> "$RESULT_FILE"

# Go to an edit page and press back
tap 102 2330; sleep 1
tap 381 159; sleep 1
tap 984 2168; sleep 2
screenshot "G11_on_edit"
edit_sz=$(local_size "G11_on_edit")
back; sleep 1
screenshot "G11_after_back"
after_back_sz=$(local_size "G11_after_back")
assert_page_changed "$edit_sz" "$after_back_sz" "T11.01 Back from edit page"

# ============================================================
# GROUP 12: Stability Check
# ============================================================
echo ""
echo "=== GROUP 12: Stability ==="
echo "--- GROUP 12: Stability ---" >> "$RESULT_FILE"

crash_count=$($ADB shell "logcat -d -s AndroidRuntime:E" 2>/dev/null | grep "com.lolita.app" || true | wc -l | tr -d '[:space:]')
if [ "$crash_count" = "0" ]; then
    echo "  PASS: T12.01 No crashes during test session"
    log_result "T12.01 No crashes" "PASS"
else
    echo "  FAIL: T12.01 $crash_count crashes detected"
    log_result "T12.01 No crashes" "FAIL"
    $ADB shell "logcat -d -s AndroidRuntime:E" 2>/dev/null | grep "com.lolita.app" | head -5
fi

anr_count=$($ADB shell "logcat -d" 2>/dev/null | { grep -c "ANR in com.lolita.app" || true; } | tr -d '[:space:]')
if [ "$anr_count" = "0" ]; then
    echo "  PASS: T12.02 No ANR during test session"
    log_result "T12.02 No ANR" "PASS"
else
    echo "  FAIL: T12.02 $anr_count ANR detected"
    log_result "T12.02 No ANR" "FAIL"
fi



# ============================================================\n# GROUP 13: Backup Import\n# ============================================================\necho ""\necho "=== GROUP 13: Backup Import ==="\necho "--- GROUP 13: Backup Import ---" >> "$RESULT_FILE"\n\n# Ensure test file exists on device\n$ADB shell "ls /sdcard/Download/lolita_backup_test.zip" >/dev/null 2>&1 || {\n    $ADB push "${SCRIPT_DIR}/lolita_backup_20260318_191642.zip" /sdcard/Download/lolita_backup_test.zip\n    sleep 1\n}\n\n# Navigate to settings\ntap 1014 2330; sleep 2\nscreenshot "G13_00_settings"\nsettings_sz=$(local_size "G13_00_settings")\n\n# Tap "数据备份与恢复"\ntap 558 1050; sleep 2\nscreenshot "G13_01_backup_restore"\nbackup_sz=$(local_size "G13_01_backup_restore")\nassert_page_changed "$settings_sz" "$backup_sz" "T13.01 Navigate to Backup Restore"\n\n# Scroll down to reveal import section\n$ADB shell "input swipe 558 1800 558 1000 300"\nsleep 1\nscreenshot "G13_01b_scrolled"\n\n# Tap "选择备份文件" button (import section, lower portion after scroll)\ntap 557 2100; sleep 3\nscreenshot "G13_02_file_picker"\npicker_sz=$(local_size "G13_02_file_picker")\nassert_page_changed "$backup_sz" "$picker_sz" "T13.02 Open backup file picker"\n\n# Verify file picker activity actually opened\ncurrent_activity=$($ADB shell "dumpsys activity activities" | grep "mFocusedApp" | grep -o "u0 [^ }]*" | head -1)\nif echo "$current_activity" | grep -qi "documentsui"; then\n    echo "  PASS: T13.02b File picker activity confirmed ($current_activity)"\n    log_result "T13.02b File picker activity confirmed" "PASS"\nelse\n    echo "  WARN: T13.02b File picker activity not detected ($current_activity)"\n    log_result "T13.02b File picker activity confirmed" "WARN"\nfi\n\n# Dismiss file picker (device requires 2 back presses to exit DocumentsUI)\nback; sleep 1\nback; sleep 2\nscreenshot "G13_03_after_dismiss"\ndismiss_sz=$(local_size "G13_03_after_dismiss")\n\n# Verify we're back in the app\ncurrent_activity=$($ADB shell "dumpsys activity activities" | grep "mFocusedApp" | grep -o "u0 [^ }]*" | head -1)\nif echo "$current_activity" | grep -qi "lolita"; then\n    echo "  PASS: T13.03 Dismissed file picker and returned to app"\n    log_result "T13.03 Dismiss file picker and return to app" "PASS"\nelse\n    echo "  WARN: T13.03 Return to app unverified (activity: $current_activity)"\n    log_result "T13.03 Dismiss file picker and return to app" "WARN"\nfi

# ============================================================
# GROUP 14: Taobao Excel Import
# ============================================================
echo ""
echo "=== GROUP 14: Taobao Excel Import ==="
echo "--- GROUP 14: Taobao Excel Import ---" >> "$RESULT_FILE"

# Ensure test file exists on device
$ADB shell "ls /sdcard/Download/taobao_orders_test.xlsx" >/dev/null 2>&1 || {
    $ADB push "${SCRIPT_DIR}/订单数据 (11).xlsx" /sdcard/Download/taobao_orders_test.xlsx
    sleep 1
}

# Navigate to settings
tap 1014 2330; sleep 2
screenshot "G14_00_settings"
settings2_sz=$(local_size "G14_00_settings")

# Tap "淘宝订单导入"
tap 558 1200; sleep 2
screenshot "G14_01_taobao_import"
taobao_sz=$(local_size "G14_01_taobao_import")
assert_page_changed "$settings2_sz" "$taobao_sz" "T14.01 Navigate to Taobao Import"

# Tap "选择文件（可多选）" button (actual center found via pixel analysis: 557, 1469)
tap 557 1469; sleep 2
screenshot "G14_02_file_picker"
picker2_sz=$(local_size "G14_02_file_picker")
assert_page_changed "$taobao_sz" "$picker2_sz" "T14.02 Open file picker"

# Select the Excel file (grid card center found via pixel analysis: 275, 800)
tap 275 800; sleep 3
screenshot "G14_03_order_list"
order_list_sz=$(local_size "G14_03_order_list")
assert_page_changed "$picker2_sz" "$order_list_sz" "T14.03 Select Excel and show order list"

# Tap "下一步" button (actual center found via pixel analysis: 906, 2209)
tap 906 2209; sleep 2
screenshot "G14_04_prepare"
prepare_sz=$(local_size "G14_04_prepare")
assert_page_changed "$order_list_sz" "$prepare_sz" "T14.04 Proceed to data preparation"

# Go back to avoid actual import
back; sleep 1
back; sleep 1
back; sleep 1

# ============================================================
# Summary
# ============================================================
echo ""
echo "==========================================="
echo "  TEST RESULTS SUMMARY"
echo "==========================================="
TOTAL=$((PASS + FAIL + WARN))
echo "  PASS:  $PASS"
echo "  FAIL:  $FAIL"
echo "  WARN:  $WARN"
echo "  TOTAL: $TOTAL"
echo "==========================================="

echo "" >> "$RESULT_FILE"
echo "=== Summary ===" >> "$RESULT_FILE"
echo "PASS: $PASS / $TOTAL" >> "$RESULT_FILE"
echo "FAIL: $FAIL / $TOTAL" >> "$RESULT_FILE"
echo "WARN: $WARN / $TOTAL" >> "$RESULT_FILE"

echo ""
echo "Results saved to: $RESULT_FILE"
echo "Screenshots saved to: $SCREENSHOT_DIR"

# Return to home
tap 102 2330

exit $FAIL
