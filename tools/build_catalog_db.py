#!/usr/bin/env python3
"""
PulseIR Offline Catalog Compiler v2
Compiles three 100% open-source IR databases into one compact, deduplicated,
indexed SQLite database bundled 100% offline inside PulseIR:
  1. Flipper-IRDB Curated Main (CC0-1.0)
  2. Flipper-IRDB _Converted_/IR_Plus (CC0-1.0 repo — strong MENA/EU receiver coverage!)
  3. Official LIRC Remotes (GPLv2+ open-source — 2,800+ classic global remotes!)
"""
import hashlib
import os
import pathlib
import re
import sqlite3
import struct
import sys
from collections import Counter, defaultdict

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
FLIPPER_DIR = pathlib.Path(os.environ.get("FLIPPER_IRDB_DIR", str(REPO_ROOT.parent / "flipper_irdb")))
LIRC_DIR = pathlib.Path(os.environ.get("LIRC_REMOTES_DIR", str(REPO_ROOT.parent / "lirc_remotes" / "remotes")))
OUT_DB = REPO_ROOT / "app" / "src" / "main" / "assets" / "pulseir_catalog.db"

CATEGORY_META = {
    "tv": ("TV", "تلفزيون", 1),
    "ac": ("Air Conditioner", "تكييف", 2),
    "box": ("Set-Top Box & Receiver", "رسيفر وستالايت", 3),
    "av": ("AV Receiver & Amp", "أمبليفاير وصوتيات", 4),
    "soundbar": ("Soundbar", "ساوند بار", 5),
    "speaker": ("Speakers & Hi-Fi", "سماعات", 6),
    "projector": ("Projector", "بروجكتور", 7),
    "fan": ("Fan", "مروحة", 8),
    "led": ("LED & Lighting", "إضاءة و LED", 9),
    "streaming": ("Streaming & Media", "ميديا وبث", 10),
    "dvd": ("DVD & Blu-Ray", "دي في دي وبلوراي", 11),
    "audio": ("CD & Audio Players", "مشغلات صوت و CD", 12),
    "camera": ("Camera & DSLR", "كاميرا", 13),
    "climate": ("Heater & Air Care", "دفاية ومنقّي هواء", 14),
    "vacuum": ("Robot Vacuum", "مكنسة ذكية", 15),
    "monitor": ("Monitor & Display", "شاشة كمبيوتر", 16),
    "car": ("Car Audio", "كاسيت سيارة", 17),
    "console": ("Game Consoles", "أجهزة ألعاب", 18),
    "misc": ("Other Devices", "أجهزة أخرى", 19),
}

FLIPPER_DIR_MAP = {
    "TVs": "tv",
    "Universal_TV_Remotes": "tv",
    "ACs": "ac",
    "Cable_Boxes": "box",
    "DVB-T": "box",
    "TV_Tuner": "box",
    "Converters": "box",
    "Audio_and_Video_Receivers": "av",
    "SoundBars": "soundbar",
    "Speakers": "speaker",
    "Projectors": "projector",
    "Fans": "fan",
    "LED_Lighting": "led",
    "Streaming_Devices": "streaming",
    "Multimedia": "streaming",
    "DVD_Players": "dvd",
    "Blu-Ray": "dvd",
    "Laserdisc": "dvd",
    "VCR": "dvd",
    "CD_Players": "audio",
    "MiniDisc": "audio",
    "Cameras": "camera",
    "Heaters": "climate",
    "Air_Purifiers": "climate",
    "Humidifiers": "climate",
    "Fireplaces": "climate",
    "Vacuum_Cleaners": "vacuum",
    "Monitors": "monitor",
    "Digital_Signs": "monitor",
    "Touchscreen_Displays": "monitor",
    "Whiteboards": "monitor",
    "Picture_Frames": "monitor",
    "Car_Multimedia": "car",
    "Head_Units": "car",
    "Consoles": "console",
}

SUPPORTED_PROTOCOLS = {
    "NEC", "NECext", "NEC42", "NEC42ext",
    "Samsung32",
    "RC5", "RC5X", "RC6",
    "SIRC", "SIRC15", "SIRC20",
    "Kaseikyo", "RCA", "Pioneer",
}

# Brands overwhelmingly known for satellite / cable set-top boxes
SATELLITE_BOX_BRANDS = {
    "strong", "humax", "dreambox", "dream multimedia", "starsat", "truman",
    "eurostar", "technosat", "technisat", "echostar", "qmax", "openbox",
    "sky", "skymaster", "octagon", "kathrein", "pace", "sagem", "topfield",
    "kaon", "arion", "vuplus", "vu+", "amiko", "golden interstar", "golden interstart",
    "motorola", "arris", "scientific atlanta", "cisco", "directv", "dish",
    "freebox", "orange", "virgin", "foxtel", "astro", "tata sky", "airtel",
    "sun direct", "videocon", "bein", "osn", "dbox", "comag", "opticum",
    "xoro", "edision", "metronic", "triax", "aston", "nokia", "thomson",
    "technotrend", "starhub", "starview", "tivo", "ziggo", "kpn",
}

AC_BRANDS = {
    "gree", "midea", "carrier", "daikin", "york", "trane", "chigo", "aux",
    "kelvinator", "airwell", "electra", "fujitsu", "general", "mitsubishi",
    "haier", "hisense", "tcl", "unionaire", "fresh", "tornado", "whirlpool",
}

AV_BRANDS = {
    "denon", "onkyo", "marantz", "yamaha", "harman kardon", "technics",
    "nad", "rotel", "arcam", "bose", "sonos", "klipsch", "polk", "jbl",
    "creative", "logitech", "edifier", "teufel", "sherwood", "kenwood",
    "aiwa", "teac", "nakamichi", "cambridge audio", "pioneer", "jvc",
}

PROJECTOR_BRANDS = {"epson", "benq", "optoma", "viewsonic", "infocus", "acer", "nec", "barco", "christie", "casio"}
CAMERA_BRANDS = {"canon", "nikon", "olympus", "pentax", "minolta", "fujifilm", "leica"}
STREAMING_BRANDS = {"roku", "apple", "nvidia", "amazon", "firetv", "xiaomi", "wd", "dune", "popcorn", "kodi", "minix", "mecool"}
CAR_BRANDS = {"alpine", "clarion", "blaupunkt", "jvc car", "kenwood car"}

def infer_category_from_text(brand: str, model: str, comments: str = "", button_names=None) -> str:
    b = brand.lower().strip()
    text = f"{brand} {model} {comments}".lower()

    # Explicit keywords in model/comments win first
    if re.search(r"\b(dvb|dvb-t|dvb-s|dvb-c|sat|satellite|set[\s-]?top|stb|cable[\s-]?box|receiver|decoder|srt\d+|pvr|zapbox|freeview|sky\s?box|dish|humax|dreambox|starsat|truman|qmax|eurostar|technosat)\b", text):
        # Watch out for AV receiver vs satellite receiver
        if re.search(r"\b(av\s?receiver|stereo|amplifier|amp|home\s?theater|surround|hifi|hi-fi)\b", text):
            return "av"
        return "box"
    if re.search(r"\b(air\s?cond|aircon|a/c|hvac|split\s?ac|\bac\b)\b", text):
        return "ac"
    if re.search(r"\b(projector|beamer)\b", text):
        return "projector"
    if re.search(r"\b(soundbar|sound\s?bar)\b", text):
        return "soundbar"
    if re.search(r"\b(blu-?ray|dvd|vcr|laserdisc|bd-?player)\b", text):
        return "dvd"
    if re.search(r"\b(tv|television|bravia|viera|regza|aquos|trinitron|lcd|oled|plasma|smart\s?tv)\b", text):
        return "tv"
    if re.search(r"\b(av\s?receiver|amplifier|stereo|tuner|hi-?fi|home\s?cinema|home\s?theater|minidisc|cd\s?player)\b", text):
        return "av"
    if re.search(r"\b(fan|ventilator|tower\s?fan)\b", text):
        return "fan"
    if re.search(r"\b(led|rgb|lamp|bulb|light|strip)\b", text):
        return "led"
    if re.search(r"\b(camera|dslr|eos|alpha|shutter)\b", text):
        return "camera"
    if re.search(r"\b(car|autoradio|head\s?unit|car\s?stereo)\b", text):
        return "car"
    if re.search(r"\b(heater|purifier|humidifier|fireplace)\b", text):
        return "climate"
    if re.search(r"\b(vacuum|roomba|roborock)\b", text):
        return "vacuum"
    if re.search(r"\b(roku|apple\s?tv|fire\s?tv|chromecast|shield|media\s?player|boxee|kodi|xtreamer)\b", text):
        return "streaming"
    if re.search(r"\b(monitor)\b", text):
        return "monitor"

    # Brand heuristics
    if b in SATELLITE_BOX_BRANDS:
        return "box"
    if b in CAMERA_BRANDS:
        return "camera"
    if b in STREAMING_BRANDS:
        return "streaming"
    if b in CAR_BRANDS:
        return "car"
    if b in AV_BRANDS:
        return "av"
    if b in PROJECTOR_BRANDS:
        return "projector"

    # Button heuristics
    if button_names:
        bset = {n.lower() for n in button_names}
        if any(k in bset for k in ("cool", "heat", "temp+", "swing", "dry")):
            return "ac"
        if any(k in bset for k in ("epg", "tv/radio", "sat", "fav", "dtv/av", "tv/sat")):
            return "box"
        if any(k in bset for k in ("ch+", "ch-", "channel_up", "key_channelup", "ch_next")):
            return "tv"
        if any(k in bset for k in ("eject", "play", "pause", "stop", "rew", "ff")):
            return "dvd"

    if b in AC_BRANDS:
        return "ac"
    return "tv" if b in {"samsung", "lg", "sony", "philips", "panasonic", "toshiba", "sharp", "sanyo", "hitachi", "grundig", "loewe", "vestel", "beko", "hisense", "tcl", "vizio", "insignia", "rca", "magnavox", "akai", "alba", "bush", "funai", "orion", "daewoo", "goldstar", "medion", "telefunken", "thomson", "nordmende", "saba", "metz", "finlux", "schaub lorenz", "changhong", "skyworth", "konka", "element", "westinghouse", "emerson", "sceptre"} else "misc"

def parse_le_hex(hex_str: str) -> int:
    parts = hex_str.strip().split()
    val = 0
    for i, b in enumerate(parts[:4]):
        val |= (int(b, 16) & 0xFF) << (8 * i)
    if val >= 0x80000000:
        val -= 0x100000000
    return val

def reverse_byte(b: int) -> int:
    b &= 0xFF
    out = 0
    for i in range(8):
        if (b >> i) & 1:
            out |= 1 << (7 - i)
    return out

def pack_pattern(timings: list[int]) -> bytes:
    out = bytearray()
    for t in timings:
        v = max(1, int(t))
        if v < 0xFFFF:
            out.extend(struct.pack("<H", v))
        else:
            out.extend(struct.pack("<HI", 0xFFFF, v))
    return bytes(out)

LIRC_KEY_FRIENDLY = {
    "KEY_POWER": "Power",
    "KEY_POWER2": "Power",
    "KEY_SLEEP": "Sleep",
    "KEY_MUTE": "Mute",
    "KEY_VOLUMEUP": "Vol+",
    "KEY_VOLUMEDOWN": "Vol-",
    "KEY_CHANNELUP": "Ch+",
    "KEY_CHANNELDOWN": "Ch-",
    "KEY_UP": "Up",
    "KEY_DOWN": "Down",
    "KEY_LEFT": "Left",
    "KEY_RIGHT": "Right",
    "KEY_OK": "OK",
    "KEY_ENTER": "OK",
    "KEY_SELECT": "OK",
    "KEY_BACK": "Back",
    "KEY_EXIT": "Exit",
    "KEY_MENU": "Menu",
    "KEY_HOME": "Home",
    "KEY_INFO": "Info",
    "KEY_EPG": "Guide",
    "KEY_GUIDE": "Guide",
    "KEY_LAST": "Last",
    "KEY_PREVIOUS": "Prev",
    "KEY_NEXT": "Next",
    "KEY_PLAY": "Play",
    "KEY_PAUSE": "Pause",
    "KEY_PLAYPAUSE": "Play/Pause",
    "KEY_STOP": "Stop",
    "KEY_REWIND": "Rew",
    "KEY_FASTFORWARD": "FF",
    "KEY_RECORD": "Rec",
    "KEY_EJECTCD": "Eject",
    "KEY_OPEN": "Eject",
    "KEY_RED": "Red",
    "KEY_GREEN": "Green",
    "KEY_YELLOW": "Yellow",
    "KEY_BLUE": "Blue",
    "KEY_SUBTITLE": "Subtitle",
    "KEY_AUDIO": "Audio",
    "KEY_TEXT": "Text",
    "KEY_FAVORITES": "Fav",
    "KEY_TV": "TV",
    "KEY_RADIO": "Radio",
    "KEY_TV2": "TV/Radio",
    "KEY_SAT": "SAT",
    "KEY_AUX": "Input",
    "KEY_VIDEO": "Input",
    "KEY_CYCLEWINDOWS": "Input",
    "KEY_MODE": "Mode",
    "KEY_LIST": "List",
    "KEY_SETUP": "Setup",
    "KEY_OPTION": "Options",
    "KEY_ZOOM": "Zoom",
    "KEY_ANGLE": "Angle",
    "KEY_PAGEUP": "Page+",
    "KEY_PAGEDOWN": "Page-",
}

def prettify_lirc_key(raw_key: str) -> str:
    up = raw_key.strip().upper()
    if up in LIRC_KEY_FRIENDLY:
        return LIRC_KEY_FRIENDLY[up]
    m = re.fullmatch(r"(?:KEY_|BTN_)?(?:NUMERIC_)?([0-9])", up)
    if m:
        return m.group(1)
    cleaned = re.sub(r"^(?:KEY_|BTN_)", "", raw_key.strip(), flags=re.I)
    cleaned = cleaned.replace("_", " ").strip()
    if cleaned.isupper() and len(cleaned) > 3:
        cleaned = cleaned.title()
    return cleaned or raw_key

def infer_role(name: str, category_id: str) -> str:
    s = re.sub(r"[^a-z0-9+_-]", "", name.lower().replace(" ", "_"))
    s_clean = re.sub(r"[_-]+", "_", s).strip("_")

    if s_clean in ("power", "pwr", "pow", "on_off", "power_toggle", "standby", "pwr_toggle", "toggle", "key_power", "power2"):
        return "POWER"
    if s_clean in ("power_on", "pwr_on", "on", "turn_on", "ac_on"):
        return "POWER_ON"
    if s_clean in ("power_off", "pwr_off", "off", "turn_off", "ac_off", "shutdown"):
        return "POWER_OFF"
    if s_clean in ("mute", "audio_mute", "vol_mute", "unmute", "key_mute"):
        return "MUTE"
    if s_clean in ("vol_up", "vol+", "volume_up", "volume+", "vol_+", "v+", "v_up", "volup", "volumeup"):
        return "VOL_UP"
    if s_clean in ("vol_dn", "vol_down", "vol-", "volume_down", "volume-", "vol_-", "v-", "v_dn", "voldn", "voldown", "volumedown"):
        return "VOL_DOWN"
    if s_clean in ("ch_next", "ch_up", "ch+", "channel_up", "channel+", "prog_up", "p+", "chup", "ch_+", "channelup"):
        return "CH_UP"
    if s_clean in ("ch_prev", "ch_dn", "ch_down", "ch-", "channel_down", "channel-", "prog_dn", "prog_down", "p-", "chdn", "chdown", "ch_-", "channeldown"):
        return "CH_DOWN"
    if s_clean in ("up", "dir_up", "nav_up", "arrow_up", "cursor_up"):
        return "UP"
    if s_clean in ("down", "dn", "dir_down", "nav_down", "arrow_down", "cursor_down"):
        return "DOWN"
    if s_clean in ("left", "dir_left", "nav_left", "arrow_left", "cursor_left"):
        return "LEFT"
    if s_clean in ("right", "dir_right", "nav_right", "arrow_right", "cursor_right"):
        return "RIGHT"
    if s_clean in ("ok", "enter", "select", "center", "confirm"):
        return "OK"
    if s_clean in ("back", "return", "ret", "prev_menu"):
        return "BACK"
    if s_clean in ("home", "smart", "smart_hub", "android_home"):
        return "HOME"
    if s_clean in ("menu", "settings", "setup", "options", "tools"):
        return "MENU"
    if s_clean in ("exit", "quit", "cancel"):
        return "EXIT"
    if s_clean in ("info", "display", "disp", "status"):
        return "INFO"
    if s_clean in ("source", "input", "av", "tv_av", "hdmi", "av_tv", "aux", "dtv_av"):
        return "INPUT"
    if s_clean in ("guide", "epg", "tv_guide"):
        return "GUIDE"
    if s_clean in ("last", "prev_ch", "recall", "last_ch", "pre_ch"):
        return "LAST_CH"

    m_num = re.fullmatch(r"(?:num_|key_|btn_|digit_)?([0-9])", s_clean)
    if m_num:
        return f"NUM_{m_num.group(1)}"

    if s_clean in ("play", "play_pause", "playpause"):
        return "PLAY"
    if s_clean in ("pause",):
        return "PAUSE"
    if s_clean in ("stop",):
        return "STOP"
    if s_clean in ("rew", "rewind", "fast_back", "fb", "backward", "rev"):
        return "REW"
    if s_clean in ("ff", "fast_forward", "fwd", "forward", "ffwd", "fastforward"):
        return "FF"
    if s_clean in ("prev", "previous", "skip_back", "track_prev", "skip-"):
        return "PREV"
    if s_clean in ("next", "skip_forward", "track_next", "skip+"):
        return "NEXT"
    if s_clean in ("rec", "record"):
        return "REC"
    if s_clean in ("eject", "open", "open_close", "tray"):
        return "EJECT"
    if s_clean in ("red", "r", "color_red"):
        return "COLOR_RED"
    if s_clean in ("green", "g", "color_green"):
        return "COLOR_GREEN"
    if s_clean in ("yellow", "y", "color_yellow"):
        return "COLOR_YELLOW"
    if s_clean in ("blue", "b", "color_blue"):
        return "COLOR_BLUE"
    if s_clean in ("white", "w", "color_white"):
        return "COLOR_WHITE"
    if s_clean in ("bright+", "bright_up", "brightness+", "brightness_up", "dimmer+", "dim_up"):
        return "BRIGHT_UP"
    if s_clean in ("bright-", "bright_dn", "bright_down", "brightness-", "brightness_down", "dimmer-", "dim_down"):
        return "BRIGHT_DOWN"
    if s_clean in ("temp+", "temp_up", "temperature+", "temp_inc", "warmer"):
        return "TEMP_UP"
    if s_clean in ("temp-", "temp_dn", "temp_down", "temperature-", "temp_dec", "cooler"):
        return "TEMP_DOWN"
    if s_clean in ("mode", "ac_mode"):
        return "MODE"
    if s_clean in ("fan", "fan_speed", "speed", "wind"):
        return "FAN_SPEED"
    if s_clean in ("swing", "osc", "oscillate", "oscillation", "vane"):
        return "SWING"
    if s_clean in ("timer", "sleep", "delay"):
        return "TIMER"
    if s_clean in ("cool", "mode_cool"):
        return "AC_COOL"
    if s_clean in ("heat", "mode_heat"):
        return "AC_HEAT"
    if s_clean in ("dry", "mode_dry"):
        return "AC_DRY"
    if s_clean in ("auto", "mode_auto"):
        return "AC_AUTO"
    if s_clean in ("shutter", "shoot", "capture", "s", "trigger"):
        return "SHUTTER"
    if s_clean in ("shutter_2s", "2s", "delay_2s", "timer_2s"):
        return "SHUTTER_2S"
    if category_id == "ac" and ("power" in s_clean or s_clean.startswith("pwr")):
        return "POWER"
    return "OTHER"

def parse_ir_file(path: pathlib.Path):
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return [], ""

    comments = []
    signals = []
    cur = {}

    def flush():
        nonlocal cur
        if "name" in cur and "type" in cur:
            bname = cur["name"].strip()
            if not bname or bname.lower() == "unknown":
                cur = {}
                return
            t = cur["type"]
            if t == "parsed":
                proto = cur.get("protocol", "")
                addr_s = cur.get("address", "")
                cmd_s = cur.get("command", "")
                if proto in SUPPORTED_PROTOCOLS and addr_s and cmd_s:
                    try:
                        signals.append({
                            "name": bname,
                            "type": 0,
                            "protocol": proto,
                            "address": parse_le_hex(addr_s),
                            "command": parse_le_hex(cmd_s),
                        })
                    except ValueError:
                        pass
            elif t == "raw":
                freq_s = cur.get("frequency", "38000")
                data_s = cur.get("data", "")
                if data_s:
                    try:
                        freq = int(float(freq_s))
                        if freq < 15000 or freq > 80000:
                            freq = 38000
                        vals = [int(x) for x in data_s.split() if x.isdigit() and int(x) > 0]
                        if len(vals) >= 4:
                            # Trim excessive repeat tail on huge raw captures (>350 pulses) only if clean
                            signals.append({
                                "name": bname,
                                "type": 1,
                                "frequency": freq,
                                "timings": vals[:400],
                            })
                    except ValueError:
                        pass
        cur = {}

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith("#"):
            if len(comments) < 8:
                comments.append(line.lstrip("#").strip())
            flush()
            continue
        if ":" in line:
            k, v = line.split(":", 1)
            k = k.strip().lower()
            v = v.strip()
            if k == "name":
                flush()
                cur["name"] = v
            elif k in ("type", "protocol", "address", "command", "frequency", "data"):
                cur[k] = v
    flush()
    return signals, " ".join(comments)

def parse_lirc_conf(path: pathlib.Path):
    """Parse an official LIRC .lircd.conf file into one or more (remote_name, comments, signals) entries."""
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return []

    file_comments = []
    for line in text.splitlines()[:25]:
        s = line.strip()
        if s.startswith("#"):
            file_comments.append(s.lstrip("#").strip())
    comment_str = " ".join(file_comments)

    results = []
    # Split into `begin remote ... end remote` blocks
    blocks = re.findall(r"begin\s+remote(.*?)end\s+remote", text, flags=re.S | re.I)
    for blk in blocks:
        cfg = {
            "name": path.stem,
            "bits": 0,
            "flags": "",
            "freq": 38000,
            "header": None,
            "one": None,
            "zero": None,
            "ptrail": 0,
            "plead": 0,
            "pre_data_bits": 0,
            "pre_data": 0,
            "post_data_bits": 0,
            "post_data": 0,
            "pre": None,
            "post": None,
        }
        # Strip inline comments only outside codes parsing carefully
        in_codes = False
        in_raw = False
        codes = []
        raw_codes = []
        cur_raw_name = None
        cur_raw_vals = []

        for raw_line in blk.splitlines():
            line = raw_line.split("#", 1)[0].strip()
            if not line:
                continue
            low = line.lower()
            if re.match(r"begin\s+codes\b", low):
                in_codes = True
                continue
            if re.match(r"end\s+codes\b", low):
                in_codes = False
                continue
            if re.match(r"begin\s+raw_codes\b", low):
                in_raw = True
                continue
            if re.match(r"end\s+raw_codes\b", low):
                if cur_raw_name and len(cur_raw_vals) >= 4:
                    raw_codes.append((cur_raw_name, cur_raw_vals))
                cur_raw_name = None
                cur_raw_vals = []
                in_raw = False
                continue

            if in_codes:
                parts = line.split()
                if len(parts) >= 2:
                    kname = parts[0]
                    try:
                        cval = int(parts[1], 0)
                        codes.append((kname, cval))
                    except ValueError:
                        pass
                continue

            if in_raw:
                if low.startswith("name "):
                    if cur_raw_name and len(cur_raw_vals) >= 4:
                        raw_codes.append((cur_raw_name, cur_raw_vals))
                    cur_raw_name = line.split(None, 1)[1].strip()
                    cur_raw_vals = []
                else:
                    for tok in line.split():
                        if tok.isdigit() and int(tok) > 0:
                            cur_raw_vals.append(int(tok))
                continue

            parts = line.split()
            if len(parts) < 2:
                continue
            k = parts[0].lower()
            try:
                if k == "name":
                    cfg["name"] = parts[1]
                elif k == "bits":
                    cfg["bits"] = int(parts[1], 0)
                elif k == "flags":
                    cfg["flags"] = parts[1].upper()
                elif k == "frequency":
                    f = int(parts[1], 0)
                    if 15000 <= f <= 80000:
                        cfg["freq"] = f
                elif k == "header" and len(parts) >= 3:
                    cfg["header"] = (int(parts[1]), int(parts[2]))
                elif k == "one" and len(parts) >= 3:
                    cfg["one"] = (int(parts[1]), int(parts[2]))
                elif k == "zero" and len(parts) >= 3:
                    cfg["zero"] = (int(parts[1]), int(parts[2]))
                elif k == "ptrail":
                    cfg["ptrail"] = int(parts[1])
                elif k == "plead":
                    cfg["plead"] = int(parts[1])
                elif k == "pre_data_bits":
                    cfg["pre_data_bits"] = int(parts[1], 0)
                elif k == "pre_data":
                    cfg["pre_data"] = int(parts[1], 0)
                elif k == "post_data_bits":
                    cfg["post_data_bits"] = int(parts[1], 0)
                elif k == "post_data":
                    cfg["post_data"] = int(parts[1], 0)
                elif k == "pre" and len(parts) >= 3:
                    cfg["pre"] = (int(parts[1]), int(parts[2]))
                elif k == "post" and len(parts) >= 3:
                    cfg["post"] = (int(parts[1]), int(parts[2]))
            except ValueError:
                pass

        signals = []
        flags = cfg["flags"]

        # Raw codes block
        for rname, rvals in raw_codes:
            if len(rvals) >= 4:
                signals.append({
                    "name": prettify_lirc_key(rname),
                    "type": 1,
                    "frequency": cfg["freq"],
                    "timings": rvals[:400],
                })

        # Structured SPACE_ENC or PULSE_ENC codes
        if codes and cfg["one"] and cfg["zero"] and ("SPACE_ENC" in flags or "PULSE_ENC" in flags):
            hdr = cfg["header"]
            one_m, one_s = cfg["one"]
            zero_m, zero_s = cfg["zero"]
            total_bits = cfg["pre_data_bits"] + cfg["bits"] + cfg["post_data_bits"]

            # Fast path: detect exact standard 32-bit NEC / NECext and store as tiny Parsed signal!
            is_nec_32 = (
                "SPACE_ENC" in flags
                and hdr is not None
                and 8000 <= hdr[0] <= 9800
                and 3800 <= hdr[1] <= 5000
                and 350 <= one_m <= 800 and 1300 <= one_s <= 2000
                and 350 <= zero_m <= 800 and 300 <= zero_s <= 800
                and total_bits == 32
                and cfg["pre"] is None and cfg["post"] is None
            )
            # Fast path: detect Samsung32 (4500/4500 header, 32 bits)
            is_sam_32 = (
                "SPACE_ENC" in flags
                and hdr is not None
                and 4000 <= hdr[0] <= 5100
                and 4000 <= hdr[1] <= 5100
                and 350 <= one_m <= 800 and 1300 <= one_s <= 2000
                and 350 <= zero_m <= 800 and 300 <= zero_s <= 800
                and total_bits == 32
                and cfg["pre"] is None and cfg["post"] is None
            )

            for kname, cval in codes:
                pname = prettify_lirc_key(kname)
                if is_nec_32:
                    full32 = (cfg["pre_data"] << (cfg["bits"] + cfg["post_data_bits"])) | (cval << cfg["post_data_bits"]) | cfg["post_data"]
                    b0 = reverse_byte((full32 >> 24) & 0xFF)
                    b1 = reverse_byte((full32 >> 16) & 0xFF)
                    b2 = reverse_byte((full32 >> 8) & 0xFF)
                    b3 = reverse_byte(full32 & 0xFF)
                    if (b0 ^ b1) == 0xFF and (b2 ^ b3) == 0xFF:
                        signals.append({"name": pname, "type": 0, "protocol": "NEC", "address": b0, "command": b2})
                    else:
                        addr16 = b0 | (b1 << 8)
                        cmd16 = b2 | (b3 << 8)
                        signals.append({"name": pname, "type": 0, "protocol": "NECext", "address": addr16, "command": cmd16})
                elif is_sam_32:
                    full32 = (cfg["pre_data"] << (cfg["bits"] + cfg["post_data_bits"])) | (cval << cfg["post_data_bits"]) | cfg["post_data"]
                    b0 = reverse_byte((full32 >> 24) & 0xFF)
                    b1 = reverse_byte((full32 >> 16) & 0xFF)
                    b2 = reverse_byte((full32 >> 8) & 0xFF)
                    b3 = reverse_byte(full32 & 0xFF)
                    if b0 == b1 and (b2 ^ b3) == 0xFF:
                        signals.append({"name": pname, "type": 0, "protocol": "Samsung32", "address": b0, "command": b2})
                    else:
                        # Emit exact raw pulse train
                        timings = _encode_lirc_pulse_space(cfg, cval)
                        if len(timings) >= 6:
                            signals.append({"name": pname, "type": 1, "frequency": cfg["freq"], "timings": timings})
                else:
                    if 8 <= total_bits <= 64:
                        timings = _encode_lirc_pulse_space(cfg, cval)
                        if len(timings) >= 6:
                            signals.append({"name": pname, "type": 1, "frequency": cfg["freq"], "timings": timings})

        # Structured RC5
        elif codes and "RC5" in flags and "RC6" not in flags:
            for kname, cval in codes:
                pname = prettify_lirc_key(kname)
                full = (cfg["pre_data"] << cfg["bits"]) | cval
                addr = (full >> 6) & 0x1F
                cmd = full & 0x3F
                signals.append({"name": pname, "type": 0, "protocol": "RC5", "address": addr, "command": cmd})

        if signals:
            results.append((cfg["name"], comment_str, signals))

    return results

def _encode_lirc_pulse_space(cfg, code_val: int) -> list[int]:
    out = []
    if cfg["header"] and cfg["header"][0] > 0 and cfg["header"][1] > 0:
        out.extend([cfg["header"][0], cfg["header"][1]])
    if cfg["plead"] > 0:
        out.append(cfg["plead"])
    one_m, one_s = cfg["one"]
    zero_m, zero_s = cfg["zero"]
    if one_m <= 0 or one_s <= 0 or zero_m <= 0 or zero_s <= 0:
        return []

    def emit_bits(val: int, nbits: int):
        for i in range(nbits - 1, -1, -1):
            if (val >> i) & 1:
                out.extend([one_m, one_s])
            else:
                out.extend([zero_m, zero_s])

    if cfg["pre_data_bits"] > 0:
        emit_bits(cfg["pre_data"], cfg["pre_data_bits"])
    if cfg["pre"] and cfg["pre"][0] > 0 and cfg["pre"][1] > 0:
        out.extend([cfg["pre"][0], cfg["pre"][1]])
    if cfg["bits"] > 0:
        emit_bits(code_val, cfg["bits"])
    if cfg["post"] and cfg["post"][0] > 0 and cfg["post"][1] > 0:
        out.extend([cfg["post"][0], cfg["post"][1]])
    if cfg["post_data_bits"] > 0:
        emit_bits(cfg["post_data"], cfg["post_data_bits"])
    if cfg["ptrail"] > 0:
        out.append(cfg["ptrail"])
    return out

def clean_brand_name(raw_brand: str) -> str:
    b = raw_brand.replace("_", " ").strip()
    b = re.sub(r"\s+", " ", b)
    if not b:
        return "Generic"
    # Title-case pure lowercase folder names from LIRC (e.g. "strong" -> "Strong", "lg" -> "LG")
    if b.islower():
        if len(b) <= 3 and b in {"lg", "jvc", "nec", "rca", "nad", "jbl", "wd", "sky", "kpn", "osn"}:
            return b.upper()
        return b.title()
    return b

def clean_model_name(stem: str, brand: str) -> str:
    m = stem.replace(".lircd", "").replace("_", " ").strip()
    m = re.sub(r"\s+", " ", m)
    if brand.lower() != "generic" and m.lower().startswith(brand.lower() + " "):
        trimmed = m[len(brand):].strip(" -_")
        if trimmed:
            m = trimmed
    return m or stem

def main():
    if not FLIPPER_DIR.exists():
        print(f"ERROR: Flipper-IRDB not found: {FLIPPER_DIR}", file=sys.stderr)
        sys.exit(1)

    OUT_DB.parent.mkdir(parents=True, exist_ok=True)
    if OUT_DB.exists():
        OUT_DB.unlink()

    conn = sqlite3.connect(OUT_DB)
    cur = conn.cursor()
    cur.executescript("""
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA temp_store = MEMORY;

        CREATE TABLE meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );

        CREATE TABLE categories (
            id TEXT PRIMARY KEY,
            display_en TEXT NOT NULL,
            display_ar TEXT NOT NULL,
            sort_order INTEGER NOT NULL,
            remote_count INTEGER NOT NULL,
            brand_count INTEGER NOT NULL
        );

        CREATE TABLE remotes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            category_id TEXT NOT NULL,
            brand TEXT NOT NULL,
            brand_norm TEXT NOT NULL,
            model TEXT NOT NULL,
            model_norm TEXT NOT NULL,
            button_count INTEGER NOT NULL,
            has_power INTEGER NOT NULL,
            power_fingerprint TEXT
        );

        CREATE TABLE raw_blobs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            frequency INTEGER NOT NULL,
            pattern_blob BLOB NOT NULL
        );

        CREATE TABLE buttons (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            remote_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            role TEXT NOT NULL,
            sort_order INTEGER NOT NULL,
            signal_type INTEGER NOT NULL,
            protocol TEXT,
            address INTEGER,
            command INTEGER,
            raw_blob_id INTEGER
        );
    """)

    raw_cache = {}
    cat_remotes = Counter()
    cat_brands = defaultdict(set)
    brand_display = {}
    seen_remotes = set()  # (cat_id, brand_norm, model_norm)

    total_remotes = 0
    total_buttons = 0
    source_counts = Counter()

    def insert_remote(cat_id: str, raw_brand: str, raw_model: str, signals: list[dict], source_tag: str):
        nonlocal total_remotes, total_buttons
        if not signals:
            return
        brand = clean_brand_name(raw_brand)
        brand_norm = brand.lower()
        key_b = (cat_id, brand_norm)
        if key_b not in brand_display:
            brand_display[key_b] = brand
        else:
            brand = brand_display[key_b]

        model = clean_model_name(raw_model, brand)
        model_norm = model.lower()
        rkey = (cat_id, brand_norm, model_norm)
        if rkey in seen_remotes:
            return
        seen_remotes.add(rkey)

        seen_btns = set()
        clean_signals = []
        power_fp = None
        power_fallback_fp = None

        for sig in signals:
            bname = sig["name"].strip()
            if not bname:
                continue
            role = infer_role(bname, cat_id)
            dedup_k = (bname.lower(), sig["type"], sig.get("protocol"), sig.get("address"), sig.get("command"))
            if dedup_k in seen_btns:
                continue
            seen_btns.add(dedup_k)

            if sig["type"] == 0:
                fp = f"P:{sig['protocol']}:{sig['address']}:{sig['command']}"
                raw_id = None
            else:
                blob = pack_pattern(sig["timings"])
                ck = (sig["frequency"], blob)
                raw_id = raw_cache.get(ck)
                if raw_id is None:
                    cur.execute("INSERT INTO raw_blobs(frequency, pattern_blob) VALUES (?, ?)", ck)
                    raw_id = cur.lastrowid
                    raw_cache[ck] = raw_id
                fp = f"R:{sig['frequency']}:{hashlib.md5(blob).hexdigest()[:12]}"

            if role == "POWER" and power_fp is None:
                power_fp = fp
            elif role in ("POWER_ON", "POWER_OFF") and power_fallback_fp is None:
                power_fallback_fp = fp

            clean_signals.append((bname, role, sig, raw_id))

        if not clean_signals:
            return

        final_power_fp = power_fp or power_fallback_fp
        has_power = 1 if final_power_fp else 0

        cur.execute(
            """INSERT INTO remotes(category_id, brand, brand_norm, model, model_norm,
               button_count, has_power, power_fingerprint)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (cat_id, brand, brand_norm, model, model_norm, len(clean_signals), has_power, final_power_fp),
        )
        remote_id = cur.lastrowid

        btn_rows = []
        for idx, (bname, role, sig, raw_id) in enumerate(clean_signals):
            if sig["type"] == 0:
                btn_rows.append((remote_id, bname, role, idx, 0, sig["protocol"], sig["address"], sig["command"], None))
            else:
                btn_rows.append((remote_id, bname, role, idx, 1, None, None, None, raw_id))

        cur.executemany(
            """INSERT INTO buttons(remote_id, name, role, sort_order, signal_type,
               protocol, address, command, raw_blob_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            btn_rows,
        )

        cat_remotes[cat_id] += 1
        cat_brands[cat_id].add(brand_norm)
        total_remotes += 1
        total_buttons += len(clean_signals)
        source_counts[source_tag] += 1

    # --- Source 1: Flipper-IRDB Curated Main (CC0-1.0) ---
    for dp, dn, fn in os.walk(FLIPPER_DIR):
        dn[:] = sorted([d for d in dn if not d.startswith(".") and not d.startswith("_")])
        for fname in sorted(fn):
            if not fname.lower().endswith(".ir") or "UNIVERSAL_POWER_OFF" in fname.upper():
                continue
            fpath = pathlib.Path(dp) / fname
            rel = fpath.relative_to(FLIPPER_DIR).parts
            cat_id = FLIPPER_DIR_MAP.get(rel[0], "misc")
            signals, _ = parse_ir_file(fpath)
            if not signals:
                continue
            if len(rel) >= 3:
                raw_brand = rel[1]
            else:
                parts_name = fpath.stem.split("_")
                raw_brand = parts_name[0] if len(parts_name) > 1 else "Generic"
            model = fpath.stem
            if len(rel) > 3:
                sub = " ".join(p.replace("_", " ") for p in rel[2:-1])
                if sub.lower() not in model.lower():
                    model = f"{sub} - {model}"
            insert_remote(cat_id, raw_brand, model, signals, "flipper_main")

    # --- Source 2: Flipper-IRDB _Converted_/IR_Plus (CC0-1.0 repo — Strong, Qmax, Truman, Starsat, etc.) ---
    irplus_dir = FLIPPER_DIR / "_Converted_" / "IR_Plus"
    if irplus_dir.exists():
        for dp, dn, fn in os.walk(irplus_dir):
            dn.sort()
            for fname in sorted(fn):
                if not fname.lower().endswith(".ir"):
                    continue
                fpath = pathlib.Path(dp) / fname
                rel = fpath.relative_to(irplus_dir).parts
                raw_brand = rel[1] if len(rel) >= 3 else (rel[0] if len(rel) >= 2 else "Generic")
                signals, comments = parse_ir_file(fpath)
                if not signals:
                    continue
                cat_id = infer_category_from_text(raw_brand, fpath.stem, comments, [s["name"] for s in signals])
                insert_remote(cat_id, raw_brand, fpath.stem, signals, "flipper_irplus")

    # --- Source 3: Official LIRC Remotes (Open-Source GPL) ---
    if LIRC_DIR.exists():
        for dp, dn, fn in os.walk(LIRC_DIR):
            dn.sort()
            for fname in sorted(fn):
                if not fname.lower().endswith(".conf"):
                    continue
                fpath = pathlib.Path(dp) / fname
                rel = fpath.relative_to(LIRC_DIR).parts
                raw_brand = rel[0] if len(rel) >= 2 else "Generic"
                parsed_entries = parse_lirc_conf(fpath)
                for rname, comments, signals in parsed_entries:
                    model = fpath.stem
                    if len(parsed_entries) > 1:
                        model = f"{fpath.stem} ({rname})"
                    cat_id = infer_category_from_text(raw_brand, f"{model} {rname}", comments, [s["name"] for s in signals])
                    insert_remote(cat_id, raw_brand, model, signals, "lirc")

    for cat_id, count in cat_remotes.items():
        en_name, ar_name, cat_order = CATEGORY_META[cat_id]
        cur.execute(
            "INSERT INTO categories(id, display_en, display_ar, sort_order, remote_count, brand_count) VALUES (?, ?, ?, ?, ?, ?)",
            (cat_id, en_name, ar_name, cat_order, count, len(cat_brands[cat_id])),
        )

    cur.executemany(
        "INSERT INTO meta(key, value) VALUES (?, ?)",
        [
            ("schema_version", "2"),
            ("source", "Flipper-IRDB (CC0-1.0) + Official LIRC Remotes (GPL)"),
            ("total_remotes", str(total_remotes)),
            ("total_buttons", str(total_buttons)),
            ("total_raw_blobs", str(len(raw_cache))),
        ],
    )

    cur.executescript("""
        CREATE INDEX idx_remotes_cat_brand ON remotes(category_id, brand_norm);
        CREATE INDEX idx_remotes_cat_power ON remotes(category_id, has_power, power_fingerprint);
        CREATE INDEX idx_remotes_search ON remotes(brand_norm, model_norm);
        CREATE INDEX idx_buttons_remote ON buttons(remote_id, sort_order);
        CREATE INDEX idx_buttons_role ON buttons(remote_id, role);
        ANALYZE;
        VACUUM;
    """)
    conn.commit()
    conn.close()

    db_mb = OUT_DB.stat().st_size / (1024 * 1024)
    print("SUCCESS! Mega Offline Catalog v2 Built:")
    print(f"  Sources:    {dict(source_counts)}")
    print(f"  Categories: {len(cat_remotes)}")
    print(f"  Remotes:    {total_remotes}")
    print(f"  Buttons:    {total_buttons}")
    print(f"  Unique Raw: {len(raw_cache)}")
    print(f"  SQLite DB:  {db_mb:.2f} MB (uncompressed; APK ZIP shrinks ~65%)")
    # Print box & Strong stats!
    c2 = sqlite3.connect(OUT_DB)
    box_r, box_b = c2.execute("SELECT remote_count, brand_count FROM categories WHERE id='box'").fetchone()
    box_scan = c2.execute("SELECT COUNT(DISTINCT power_fingerprint) FROM remotes WHERE category_id='box' AND has_power=1").fetchone()[0]
    strong_rows = c2.execute("SELECT category_id, brand, model, button_count FROM remotes WHERE brand_norm LIKE '%strong%'").fetchall()
    print(f"  BOX / SAT:  {box_r} remotes, {box_b} brands, {box_scan} unique scan Power codes")
    print(f"  STRONG ({len(strong_rows)} remotes):")
    for sr in strong_rows:
        print(f"    [{sr[0]}] {sr[1]} - {sr[2]} ({sr[3]} btns)")
    c2.close()

if __name__ == "__main__":
    main()
