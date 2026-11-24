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


def test_community_browser_supports_exports_and_has_visible_back_button():
    source=(JAVA/'CommunityActivity.java').read_text()
    assert 'header.setMinimumHeight(dp(64))' in source
    assert 'root.requestApplyInsets()' in source
    assert 'blob".equals(scheme)' in source
    assert 'startActivity(new Intent(Intent.ACTION_VIEW,u))' in source

def test_android_update_banner_and_diagnostics():
    diag=(ASSETS/'diagnostics.js').read_text()
    assert 'update-banner' in diag
    assert 'GlyphForge_Diagnostics.json' in diag
    assert 'Nothing SDK загружен' in diag
    assert "versionName '0.4.5'" in (ROOT/'app/build.gradle').read_text()


def test_release_signed_update_path():
    assert 'REQUEST_INSTALL_PACKAGES' not in (ROOT/'app/src/main/AndroidManifest.xml').read_text()
    assert 'installUpdate(' not in (JAVA/'MainActivity.java').read_text()
    wf=(ROOT/'.github/workflows/release-apk.yml').read_text()
    assert 'apksigner' in wf and 'gh release create' in wf and 'workflow_dispatch:' in wf
    assert 'secrets.GF_KEYSTORE_B64' in wf

def test_security_guardrails():
    import xml.etree.ElementTree as ET
    root=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
    ns='{http://schemas.android.com/apk/res/android}'
    perms={x.get(ns+'name') for x in root.findall('uses-permission')}
    assert not perms & {'android.permission.REQUEST_INSTALL_PACKAGES','android.permission.INSTALL_PACKAGES','android.permission.READ_CONTACTS','android.permission.READ_SMS','android.permission.READ_CALL_LOG'}
    assert root.find('application').get(ns+'usesCleartextTraffic')=='false'
    main=(JAVA/'MainActivity.java').read_text()
    assert 'settings.setAllowFileAccess(false)' in main and 'settings.setBlockNetworkLoads(true)' in main
    assert 'Content-Security-Policy' in (ASSETS/'index.html').read_text()
    assert not (JAVA/'UpdateInstaller.java').exists() and not (JAVA/'UpdateFileProvider.java').exists()
    assert 'if(!needed||n.extras==null)' in (JAVA/'GlyphNotificationListener.java').read_text()
    for path in ['build-apk.yml','release-apk.yml']:
        assert 'GlyphMatrix-Developer-Kit/999b1143a6bff0dd79db11d21b92c15c56a346b1/' in (ROOT/'.github/workflows'/path).read_text()
