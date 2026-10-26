"""The texture the conduit tube renderer wraps round a run: brushed galvanized steel, streaks running
along u (the length of the tube), multiplied by the run's colour."""
import os

from gen_breaker_panel_assets import ASSETS, canvas, write_png

# Brightness per row (v, across the tube): fine streaks, none too dark, mean about 0.9.
ROWS = [0.90, 0.95, 0.86, 0.92, 0.97, 0.88, 0.84, 0.93, 0.89, 0.96, 0.87, 0.91, 0.98, 0.86, 0.92, 0.90]


def main():
    img = canvas(16, 16)
    for y, row in enumerate(ROWS):
        for x in range(16):
            # A little deterministic grain along the streak so it is not a flat band.
            grain = ((x * 7 + y * 13) % 5 - 2) * 0.008
            v = max(0, min(255, int(round(255 * (row + grain)))))
            img[y][x] = (v, v, v, 255)
    write_png(os.path.join(ASSETS, "textures", "special", "conduit_tube.png"), img)


if __name__ == "__main__":
    main()
