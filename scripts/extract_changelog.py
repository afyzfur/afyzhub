#!/usr/bin/env python3
"""从 CHANGELOG.md 中提取指定版本的更新内容，用于生成发行说明。

用法：
    python3 scripts/extract_changelog.py v0.1.4-dev

在标准输出打印该版本的正文（不含版本标题本身）。
找不到对应版本时以非零状态退出，由 CI 决定如何处理。
"""
import io
import os
import re
import sys

CHANGELOG = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "CHANGELOG.md",
)


def extract(version: str, text: str) -> str:
    """取出 `## <version>` 到下一个 `## ` 之间的内容。

    版本标题允许带或不带 `v` 前缀，统一按去掉前缀后比较。
    """
    wanted = version.lstrip("v")
    # 按版本标题切块，保留标题行以便识别
    blocks = re.split(r"(?m)^(?=## )", text)
    for block in blocks:
        first_line = block.split("\n", 1)[0]
        if not first_line.startswith("## "):
            continue
        title = first_line[3:].strip().lstrip("v")
        if title != wanted:
            continue
        body = block.split("\n", 1)[1] if "\n" in block else ""
        # 去掉块尾用于分隔版本的水平线
        body = re.sub(r"\n*---\s*\n*$", "\n", body)
        return body.strip()
    return ""


def main() -> int:
    if len(sys.argv) != 2:
        print("用法：extract_changelog.py <版本号>", file=sys.stderr)
        return 2

    version = sys.argv[1]
    with io.open(CHANGELOG, encoding="utf-8") as f:
        text = f.read()

    body = extract(version, text)
    if not body:
        print(f"CHANGELOG.md 中找不到版本 {version}", file=sys.stderr)
        return 1

    print(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
