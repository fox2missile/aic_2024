import requests
import os
from pathlib import Path


def main() -> None:
    maps_dir = 'maps'

    list_response = requests.get("https://www.coliseum.ai/api/tournaments/aic2024/maps")
    list_response.raise_for_status()

    for obj in list_response.json():
        if not obj["visible"]:
            continue

        map_name = obj["name"]
        print(f"Downloading {map_name}")

        map_response = requests.get(f"https://www.coliseum.ai/api/tournaments/aic2024/maps/{map_name.lower()}/file")
        map_response.raise_for_status()

        map_file = os.path.join(maps_dir, f'{map_name}.txt')
        with open(map_file, 'wb') as file:
            file.write(map_response.content)


if __name__ == "__main__":
    main()
