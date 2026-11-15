"""Builds docs/images/features.gif, the picture at the top of the README.

The frames are the screenshots the app's own gallery test takes, each with a
caption added on top. Run this again after the screens change:

1. Install the app and its tests on an emulator, then take the screenshots:
     ./gradlew :app:installDebug :app:installDebugAndroidTest
     adb shell am instrument -w -e class com.example.fit.ui.AppGalleryTest \
         app.overload.tracker.test/androidx.test.runner.AndroidJUnitRunner
2. Copy them to a folder on the computer:
     adb pull /sdcard/Android/data/app.overload.tracker/files/screenshots shots
3. Build the GIF (needs Python with Pillow):
     python tools/readme-gif/make_gif.py shots docs/images/features.gif

The captions are in FRAMES below. The fonts are the Segoe UI files that ship
with Windows; change FONT_BOLD and FONT_REGULAR on another system.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

if len(sys.argv) != 3:
    sys.exit("usage: python make_gif.py <screenshots folder> <output.gif>")
shots_dir, out_path = sys.argv[1], sys.argv[2]

FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT_REGULAR = "C:/Windows/Fonts/segoeui.ttf"

WIDTH = 432            # the 1080 px wide screenshots at 40%
SCREEN_H = 960         # 2400 px at 40%
BAND_H = 100           # caption strip above the screenshot
FRAME_MS = 2600        # how long each screen stays up
MARGIN = 20

WHITE = (255, 255, 255)
SOFT = (222, 226, 250)

# (screenshot, headline, one line under it)
FRAMES = [
    ("03-today-in-progress.png", "Log sets with one tap",
     "Tick a set when it is done. New records show at once."),
    ("15-today-finished.png", "Finish and see what you beat",
     "Total load, sets done and every new record."),
    ("04-routines.png", "Plan your week",
     "Give each day a routine or a rest day."),
    ("06-exercise-picker.png", "154 exercises with pictures",
     "Search, or filter by muscle group."),
    ("09-exercise-detail.png", "Track every exercise",
     "Max weight, best set and a chart of each session."),
    ("11-progress-weekly.png", "See your week at a glance",
     "Total load and cardio minutes, week by week."),
    ("13-history.png", "Every workout is saved",
     "Open any day to look back or correct it."),
    ("07-profile.png", "Your data stays on your phone",
     "No account. Export a backup file whenever you like."),
]

title_font = ImageFont.truetype(FONT_BOLD, 25)
sub_font = ImageFont.truetype(FONT_REGULAR, 15)


def load(name):
    return Image.open(os.path.join(shots_dir, name)).convert("RGB")


# The caption strip takes the app's own colour: the filled "Finish workout" button.
BAND = load("03-today-in-progress.png").getpixel((800, 2037))


def draw_dots(draw, index):
    """A row of dots, top right, showing which screen this is."""
    gap, radius, y = 14, 3, 22
    first_x = WIDTH - MARGIN - (len(FRAMES) - 1) * gap
    dim = tuple(int(w * 0.55 + b * 0.45) for w, b in zip(WHITE, BAND))
    for i in range(len(FRAMES)):
        x = first_x + i * gap
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=WHITE if i == index else dim)


def build(index, name, title, sub):
    for text, font in ((title, title_font), (sub, sub_font)):
        if font.getlength(text) > WIDTH - 2 * MARGIN:
            sys.exit(f"caption is too wide for the picture: {text}")
    frame = Image.new("RGB", (WIDTH, BAND_H + SCREEN_H), BAND)
    frame.paste(load(name).resize((WIDTH, SCREEN_H), Image.LANCZOS), (0, BAND_H))
    draw = ImageDraw.Draw(frame)
    draw.text((MARGIN, 12), "MuscleDot", font=sub_font, fill=SOFT)
    draw.text((MARGIN, 33), title, font=title_font, fill=WHITE)
    draw.text((MARGIN, 68), sub, font=sub_font, fill=SOFT)
    draw_dots(draw, index)
    return frame


frames = [build(i, *frame) for i, frame in enumerate(FRAMES)]

# Each frame gets its own 256 colours so the photos and charts stay clean.
paletted = [
    f.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
    for f in frames
]
os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
paletted[0].save(out_path, save_all=True, append_images=paletted[1:], duration=FRAME_MS,
                 loop=0, optimize=False, disposal=1)
print(f"{out_path}: {len(frames)} frames, {os.path.getsize(out_path) // 1024} KB")
