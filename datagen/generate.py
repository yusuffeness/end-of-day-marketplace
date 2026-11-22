"""Generate CSV data sets for the end_of_day database.

The files are written to an output directory and loaded with LOAD DATA LOCAL INFILE.
"""

import argparse
import sys
from pathlib import Path


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--seed",
        type=int,
        default=372,
        help="random seed, so that the same seed always produces the same data (default: 372)",
    )
    parser.add_argument(
        "--orders",
        type=int,
        default=250_000,
        help="number of orders to generate (default: 250000)",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("out"),
        help="directory the CSV files are written to (default: out/)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    parse_args(argv)
    print("Data generation is not implemented yet.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
