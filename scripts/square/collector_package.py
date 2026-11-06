#!/usr/bin/env python3
"""Repository entry point; maintained implementation also ships in the installed collector skill."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "skills/taobao-shop-updates/scripts"))
from square_package import main
if __name__ == "__main__":
    raise SystemExit(main())
