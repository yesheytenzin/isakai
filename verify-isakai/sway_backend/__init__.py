"""Isolated-sway acceptance flows for the three pinned apps.

Backend: sway_backend.compositor (private sway + vinput + grim + wl-paste).
Each flow launches the real pinned APK worktree, drives the real window,
and asserts on app-visible state plus app-recorded persistence.
"""
from .compositor import SwayError  # noqa: F401
from . import flows_opencalc as opencalc  # noqa: F401
from . import flows_calculator as calculator  # noqa: F401
from . import flows_notes as notes  # noqa: F401
