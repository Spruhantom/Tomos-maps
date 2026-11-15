import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BOUNDARIES = ROOT / "data" / "boundaries"


def read_poly(path):
    lines = [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    rings = []
    index = 1
    while index < len(lines) and lines[index] != "END":
        ring_id = lines[index]
        index += 1
        points = []
        while index < len(lines) and lines[index] != "END":
            parts = lines[index].split()
            if len(parts) < 2:
                raise ValueError(f"Invalid coordinate in {path}")
            points.append((float(parts[0]), float(parts[1])))
            index += 1
        if index >= len(lines) or len(points) < 4:
            raise ValueError(f"Invalid ring {ring_id} in {path}")
        index += 1
        rings.append((ring_id, points))
    if not rings:
        raise ValueError(f"No polygon rings in {path}")
    return rings


def point_in_ring(point, ring):
    x, y = point
    inside = False
    points = ring
    previous = points[-1]
    for current in points:
        x1, y1 = previous
        x2, y2 = current
        if (y1 > y) != (y2 > y):
            crossing = (x2 - x1) * (y - y1) / (y2 - y1) + x1
            if x < crossing:
                inside = not inside
        previous = current
    return inside


def point_in_poly(point, rings):
    inside = False
    for ring_id, coordinates in rings:
        hit = point_in_ring(point, coordinates)
        if ring_id.startswith("!"):
            if hit:
                return False
        elif hit:
            inside = True
    return inside


class RegionalBoundaryTests(unittest.TestCase):
    def test_greater_london_contains_central_london(self):
        rings = read_poly(BOUNDARIES / "greater-london.poly")
        self.assertTrue(point_in_poly((-0.1276, 51.5072), rings))

    def test_south_east_contains_storrington_west_sussex(self):
        rings = read_poly(BOUNDARIES / "south-east-england.poly")
        # Storrington village centre, approximate WGS84 coordinate.
        self.assertTrue(point_in_poly((-0.454, 50.93), rings))

    def test_south_east_boundary_has_all_nine_county_components(self):
        rings = read_poly(BOUNDARIES / "south-east-england.poly")
        self.assertEqual(len(rings), 9)


if __name__ == "__main__":
    unittest.main()
