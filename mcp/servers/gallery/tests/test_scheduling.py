import unittest
from gallery_mcp.scheduling import Album, select_album


class SchedulingTests(unittest.TestCase):
    def test_budget_accumulates_across_yields_and_waiting_does_not_spend_it(self):
        old = Album('old', 1, 0)
        new = Album('new', 2, 1)
        old.account(1.25)
        old.account(.75)
        self.assertIs(select_album([old, new], 29), new)
        self.assertIs(select_album([old, new], 30), old)
        self.assertEqual(old.elapsed, 2)
        old.account(1)
        self.assertTrue(old.high)
        old.account(1)
        self.assertFalse(old.high)
