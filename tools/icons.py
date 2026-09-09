"""Fetch Material Symbols Outlined SVGs and convert them to Android vector drawables.

Material Symbols ship as a single path on a "0 -960 960 960" viewBox, so the
conversion is a viewport of 960x960 plus a translate of the y origin.
"""
import io, os, re, sys, urllib.request

NAMES = [
    "account_circle", "add", "arrow_back", "arrow_drop_down", "call_split",
    "check_circle", "chevron_right", "close", "code", "content_paste",
    "bolt", "check", "delete", "directions_bus", "dns", "edit", "error", "event", "event_repeat",
    "group_add", "groups", "handshake", "info", "ios_share", "link",
    "logout", "more_vert", "notes", "qr_code_2",
    "receipt_long", "repeat", "restaurant", "schedule",
    "open_in_new", "person_add", "qr_code_scanner", "search", "settings",
    "swap_horiz", "query_stats",
    "person_alert",
    "schedule_send",
    "fullscreen", "flash_on", "flash_off", "photo_camera",
    "favorite", "bug_report",
]

# One per Spliit category, mirroring the web client's own choices in
# src/app/groups/[groupId]/expenses/category-icon.tsx. Kept distinct where
# upstream repeats an icon, so a category is recognisable in the feed.
NAMES += """
account_balance add_circle arrow_forward attractions balance build casino
chair checkroom child_care cloud_off history
cleaning_services directions_bike directions_bus directions_car electric_bolt
fitness_center flight handyman home hotel lightbulb local_bar
local_fire_department local_gas_station local_parking local_taxi movie
music_note paid payments pets power receipt_long redeem restaurant router
savings school shield shopping_cart stethoscope train volunteer_activism
water_drop wine_bar
""".split()

URL = ("https://fonts.gstatic.com/s/i/short-term/release/"
       "materialsymbolsoutlined/{name}/default/24px.svg")

NAMES = sorted(set(NAMES))

OUT = "app/src/main/res/drawable"

TEMPLATE = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Outlined, Apache-2.0. Do not hand-edit; see tools/icons.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
    <group android:translateY="960">
        <path
            android:fillColor="@android:color/white"
            android:pathData="{path}" />
    </group>
</vector>
'''

os.makedirs(OUT, exist_ok=True)
for name in NAMES:
    svg = urllib.request.urlopen(URL.format(name=name), timeout=30).read().decode("utf-8")
    paths = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
    if not paths:
        sys.exit("no path in %s: %s" % (name, svg[:200]))
    if 'viewBox="0 -960 960 960"' not in svg:
        sys.exit("unexpected viewBox in %s: %s" % (name, svg[:200]))
    # Several symbols are drawn as more than one subpath; join them, which is
    # what the SVG renderer does anyway with a single fill rule.
    data = " ".join(p.strip() for p in paths)
    out = os.path.join(OUT, "ic_%s.xml" % name)
    io.open(out, "w", encoding="utf-8", newline="\n").write(TEMPLATE.format(path=data))
    print("%-22s %5d chars" % (name, len(data)))
print("wrote %d icons to %s" % (len(NAMES), OUT))
