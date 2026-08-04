from __future__ import annotations

from pathlib import Path

from .adb_client import AdbClient
from .evidence import sha256_file, write_json
from .models import Observation
from .ui_selectors import parse_ui_nodes, visible_texts


def capture_observation(
    client: AdbClient,
    output_directory: str | Path,
    *,
    prefix: str = "observation",
) -> Observation:
    output = Path(output_directory).resolve()
    output.mkdir(parents=True, exist_ok=True)

    screenshot_path = output / f"{prefix}.png"
    xml_path = output / f"{prefix}.xml"
    json_path = output / f"{prefix}.json"

    screenshot_path.write_bytes(client.screenshot_png())
    xml_text = client.dump_ui_xml()
    xml_path.write_text(xml_text, encoding="utf-8")

    nodes = parse_ui_nodes(xml_text)
    observation = Observation(
        serial=client.serial or "",
        foreground=client.foreground_info(),
        screen=client.screen_info(),
        screenshot_path=str(screenshot_path),
        screenshot_sha256=sha256_file(screenshot_path),
        xml_path=str(xml_path),
        node_count=len(nodes),
        visible_texts=visible_texts(nodes),
        json_path=str(json_path),
    )
    write_json(json_path, observation.to_dict())
    return observation
