"""Regression tests for Android UX, diagnostics and update affordances."""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parent
ASSETS=ROOT/"app/src/main/assets"

JAVA=ROOT/'app/src/main/java/com/example/glyphforge'

def test_android_community_header_respects_safe_area():
    source=(JAVA/'CommunityActivity.java').read_text()
    assert 'setOnApplyWindowInsetsListener' in source
    assert 'systemBars()' in source
    assert 'view.setPadding(bars.left,bars.top' in source


def test_community_blob_download_is_supported_without_external_js_bridge():
    source=(JAVA/'CommunityActivity.java').read_text()
    assert 'blob'.join(['','']) in source
    assert 'onJsPrompt' in source
    assert 'browser.addJavascriptInterface(' not in source


def test_update_checker_is_opt_in_for_installation():
    source=(JAVA/'UpdateChecker.java').read_text()
    assert 'api.github.com/repos/CRILIK/GlyphForge-0.3V/releases/latest' in source
    assert 'ACTION_VIEW' in (JAVA/'MainActivity.java').read_text()
    assert 'INSTALL_PACKAGES' not in source
