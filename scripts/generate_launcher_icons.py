#!/usr/bin/env python3
"""从启动图标的 SVG 前后景生成共享 Android 矢量资源。"""

from pathlib import Path
import xml.etree.ElementTree as ET


ANDROID_NS = "http://schemas.android.com/apk/res/android"
AAPT_NS = "http://schemas.android.com/aapt"
SVG_NS = "http://www.w3.org/2000/svg"
VIEWPORT_SIZE = 108

ET.register_namespace("android", ANDROID_NS)
ET.register_namespace("aapt", AAPT_NS)


def android(name: str) -> str:
    return f"{{{ANDROID_NS}}}{name}"


def vector() -> ET.Element:
    return ET.Element("vector", {
        android("width"): "108dp",
        android("height"): "108dp",
        android("viewportWidth"): str(VIEWPORT_SIZE),
        android("viewportHeight"): str(VIEWPORT_SIZE),
    })


def write_vector(target: Path, element: ET.Element) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(element, space="    ")
    target.write_bytes(ET.tostring(element, encoding="utf-8", xml_declaration=True) + b"\n")


def foreground(source: Path) -> ET.Element:
    root = ET.parse(source).getroot()
    output = vector()
    for shape in root:
        name = shape.tag.removeprefix(f"{{{SVG_NS}}}")
        match name:
            case "circle":
                x, y, radius = (float(shape.attrib[key]) for key in ("cx", "cy", "r"))
                path = (
                    f"M{x + radius:g},{y:g}"
                    f"a{radius:g},{radius:g} 0,1 0 {-2 * radius:g},0"
                    f"a{radius:g},{radius:g} 0,1 0 {2 * radius:g},0Z"
                )
            case "path":
                path = shape.attrib["d"]
            case _:
                raise ValueError(f"不支持的前景元素：{name}")
        attributes = {
            android("pathData"): path,
            android("fillColor"): shape.attrib["fill"],
        }
        if "fill-opacity" in shape.attrib:
            attributes[android("fillAlpha")] = shape.attrib["fill-opacity"]
        ET.SubElement(output, "path", attributes)
    return output


def rgb(value: str) -> tuple[int, int, int]:
    return tuple(int(value[index:index + 2], 16) for index in (1, 3, 5))


def interpolate(start: tuple[int, ...], end: tuple[int, ...], progress: float) -> str:
    channels = (round(first + (last - first) * progress) for first, last in zip(start, end))
    return "#" + "".join(f"{channel:02X}" for channel in channels)


def background(source: Path) -> ET.Element:
    root = ET.parse(source).getroot()
    namespace = {"svg": SVG_NS}
    edges = {}
    for name in ("left", "right"):
        gradient = root.find(f"svg:defs/svg:linearGradient[@id='{name}']", namespace)
        if gradient is None:
            raise ValueError(f"背景缺少 {name} 渐变")
        stops = gradient.findall("svg:stop", namespace)
        edges[name] = tuple(rgb(stop.attrib["stop-color"]) for stop in stops)
    output = vector()
    # 色带重叠覆盖抗锯齿边界，缩小后仍保持不透明的双线性渐变。
    for y in range(VIEWPORT_SIZE):
        progress = (y + 0.5) / VIEWPORT_SIZE
        path = ET.SubElement(output, "path", {
            android("pathData"): f"M0,{max(0, y - 1)}H108V{min(VIEWPORT_SIZE, y + 2)}H0Z",
        })
        color = ET.SubElement(path, f"{{{AAPT_NS}}}attr", {"name": "android:fillColor"})
        ET.SubElement(color, "gradient", {
            android("type"): "linear",
            android("startX"): "0",
            android("startY"): "0",
            android("endX"): "108",
            android("endY"): "0",
            android("startColor"): interpolate(*edges["left"], progress),
            android("endColor"): interpolate(*edges["right"], progress),
        })
    return output


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    sources = root / "app/src/main/icons"
    resources = root / "core/ui/src/main/res/drawable"
    write_vector(resources / "ic_launcher_foreground.xml", foreground(sources / "ic_launcher_foreground.svg"))
    write_vector(resources / "ic_launcher_background.xml", background(sources / "ic_launcher_background.svg"))
    print("已生成启动图标前后景：core/ui/src/main/res/drawable")


if __name__ == "__main__":
    main()
