"""GlyphForge app browser smoke and regression tests. Run: pytest -q tests_smoke.py."""
from pathlib import Path
import re
import json
import shutil
import pytest
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / 'app/src/main/assets'
SCRIPTS = ('format.js','kitten-sample.js','gif.js','gifexport.js','app.js','music.js')

@pytest.fixture(scope='session')
def browser():
    with sync_playwright() as p:
        b = p.chromium.launch(headless=True, executable_path=shutil.which('chromium'), args=['--no-sandbox'])
        yield b
        b.close()

@pytest.fixture
def page(browser):
    pg = browser.new_page(viewport={'width':390, 'height':844}, accept_downloads=True)
    errors=[]
    pg.on('pageerror',lambda e:errors.append(str(e)))
    html = (ASSETS/'index.html').read_text(encoding='utf-8')
    html = re.sub(r'<script src="[^"]+"></script>', '', html)
    pg.set_content(html)
    # The test harness uses an about:blank origin; provide origin-like localStorage.
    pg.evaluate('''() => {const memory = new Map(); Object.defineProperty(window, 'localStorage', {value: {getItem:k=>memory.has(k)?memory.get(k):null,setItem:(k,v)=>memory.set(k,String(v)),removeItem:k=>memory.delete(k),clear:()=>memory.clear()},configurable:true});}''')
    for src in SCRIPTS:
        pg.add_script_tag(content=(ASSETS/src).read_text(encoding='utf-8'))
    yield pg
    assert not errors, errors
    pg.close()


def test_loaded_and_matrix(page):
    assert page.title().startswith('GlyphForge 0.3')
    assert page.evaluate('GlyphForgeTest.maskCount()')==137
    assert len(page.evaluate('GlyphForgeTest.getState().designs'))>=3
    assert page.locator('#device-status').inner_text().startswith('Браузер')

@pytest.mark.parametrize('tab', ['studio','import','library','rules','scenes','music','ringtone'])
def test_navigation(page,tab):
    page.locator(f'.nav[data-tab="{tab}"]').click()
    assert page.locator(f'#tab-{tab}').evaluate('(el)=>el.classList.contains("current")')

@pytest.mark.parametrize('width,height', [(390,844),(360,760),(1280,900)])
def test_responsive(page,width,height):
    page.set_viewport_size({'width':width,'height':height})
    page.locator('.nav[data-tab="music"]').click()
    assert page.locator('#music-preview').count()==1
    assert page.locator('#music-presets').count()==1


def test_edit_save_and_frames(page):
    page.locator('#project-name').fill('Автотест: пиксель')
    box=page.locator('#grid').bounding_box()
    page.locator('#grid').click(position={'x':box['width']/2,'y':box['height']/2})
    assert int(page.locator('#lit-count').inner_text())>=1
    page.locator('#add-frame').click()
    assert page.locator('#frame-total').inner_text()=='2'
    page.locator('#duration').fill('300')
    page.locator('#duplicate-frame').click()
    assert page.locator('#frame-total').inner_text()=='3'
    page.locator('#delete-frame').click()
    assert page.locator('#frame-total').inner_text()=='2'
    page.locator('#save-design').click()
    ds=page.evaluate('GlyphForgeTest.getState().designs')
    assert any(x['title']=='Автотест: пиксель' and len(x['frames'])==2 for x in ds)

@pytest.mark.parametrize('gif', ['Running-Kitten.gif','Running-Kitten (1).gif'])
def test_gif_import_both_sizes(page,gif):
    page.locator('.nav[data-tab="import"]').click()
    page.locator('#file-media').set_input_files(ROOT/'samples'/gif)
    expect(page.locator('#frame-total')).to_have_text('7',timeout=7000)
    page.locator('#save-design').click()
    d=page.evaluate('GlyphForgeTest.getState().designs[0]')
    assert len(d['frames'])==7
    assert all(len(f['pixels'])==169 for f in d['frames'])
    assert all(sum(bool(x) for x in f['pixels'])>0 for f in d['frames'])


def test_gif_variants_same_pixels(page):
    for gif in ['Running-Kitten.gif','Running-Kitten (1).gif']:
        page.locator('.nav[data-tab="import"]').click()
        page.locator('#file-media').set_input_files(ROOT/'samples'/gif)
        expect(page.locator('#frame-total')).to_have_text('7',timeout=7000)
        page.locator('#save-design').click()
    d=page.evaluate('GlyphForgeTest.getState().designs.slice(0,2)')
    assert len(d[0]['frames'])==len(d[1]['frames'])==7
    assert [[v>0 for v in f['pixels']] for f in d[0]['frames']]==[[v>0 for v in f['pixels']] for f in d[1]['frames']]


def test_import_museum_json_author(page):
    frame=[0]*137
    frame[60]=255
    obj={'v':4,'title':'Авторский кот','frames':[{'d':120,'p':frame}],'meta':{'author':'TestArtist','postId':321}}
    page.locator('.nav[data-tab="library"]').click()
    page.locator('#file-json').set_input_files({'name':'museum.json','mimeType':'application/json','buffer':json.dumps(obj).encode()})
    expect(page.locator('#library-grid')).to_contain_text('TestArtist',timeout=7000)
    assert any(d['meta']['author']=='TestArtist' for d in page.evaluate('GlyphForgeTest.getState().designs') if d.get('meta'))


def test_community_import_gforge(page):
    page.locator('.nav[data-tab="library"]').click()
    page.locator('#file-json').set_input_files(ROOT/'samples'/'Running-Kitten.gforge')
    ds=page.evaluate('GlyphForgeTest.getState().designs')
    assert len(ds)>=4
    assert len(ds[0]['frames'])==7


def test_scene_and_rule(page):
    page.locator('.nav[data-tab="scenes"]').click()
    page.locator('#scene-name').fill('Ночной режим QA')
    page.locator('#add-step').click()
    page.locator('#save-scene').click()
    s=page.evaluate('GlyphForgeTest.getState().scenes')
    assert len(s)==1
    assert len(s[0]['steps'])==2
    page.locator('.nav[data-tab="rules"]').click()
    page.locator('#rule-event').select_option('notification')
    page.locator('#rule-target').select_option('s:'+s[0]['id'])
    page.locator('#rule-package').fill('org.telegram.messenger')
    page.locator('#rule-keyword').fill('срочно')
    page.locator('#add-rule').click()
    r=page.evaluate('GlyphForgeTest.getState().rules')
    assert len(r)==1 and r[0]['app']=='org.telegram.messenger' and r[0]['keyword']=='срочно'
    assert r[0]['target'].startswith('s:')
    assert len(page.evaluate('GlyphForgeTest.buildSceneFrames(GlyphForgeTest.getState().scenes[0])'))>=2


def test_music_templates_and_demo(page):
    page.locator('.nav[data-tab="music"]').click()
    assert len(page.evaluate('GlyphForgeMusicTest.templates'))>=8
    page.locator('#music-demo').click()
    page.wait_for_timeout(360)
    assert 'ДЕМО' in page.locator('#music-preview-state').inner_text()
    assert page.locator('#music-meter-fill').evaluate('el=>el.style.width')!='0%'
    page.locator('#music-preview-stop').click()


def test_music_save_search_import(page):
    page.locator('.nav[data-tab="music"]').click()
    page.locator('#music-title').fill('Мой бас тест')
    page.locator('#music-mode').select_option('art')
    page.locator('#music-save').click()
    assert len(page.evaluate('GlyphForgeTest.getState().musicProfiles'))==1
    page.locator('#music-search').fill('Мой бас тест')
    assert page.locator('#music-presets').inner_text().count('Мой бас тест')>=1
    example={'kind':'glyphforge-music-profile','version':1,'profile':{'title':'Импортированный бит','mode':'wave','sensitivity':2.2}}
    page.locator('#music-import-file').set_input_files({'name':'new.gforgebeat','mimeType':'application/json','buffer':json.dumps(example).encode()})
    assert len(page.evaluate('GlyphForgeTest.getState().musicProfiles'))==2


def test_export_png_and_gif(page):
    with page.expect_download() as d:
        page.locator('#export-gif').click()
    download=d.value
    assert download.suggested_filename.endswith('.gif')
    assert Path(download.path()).stat().st_size>20
    with page.expect_download() as p:
        page.locator('#export-png').click()
    assert p.value.suggested_filename.endswith('.png')


def test_local_state_restore(page):
    page.locator('#project-name').fill('Сохранённый объект')
    page.locator('#save-design').click()
    storage=page.evaluate("localStorage.getItem('glyphforge-state-v1')")
    assert 'Сохранённый объект' in storage
    page.evaluate('localStorage.removeItem("nothing")')


def test_invalid_library_import_no_crash(page):
    page.locator('#file-json').set_input_files({'name':'bad.json','mimeType':'application/json','buffer':b'{invalid'})
    assert 'Ошибка импорта' in page.locator('#toast').inner_text()


def test_music_render_changes_based_on_signal(page):
    page.locator('.nav[data-tab="music"]').click()
    page.locator('#music-mode').select_option('bars')
    off=page.evaluate('''() => {
      GlyphForgeMusicTest.render({rms:0,bands:Array(8).fill(0)},0,0);
      return document.getElementById('music-preview').toDataURL()
    }''')
    on=page.evaluate('''() => {
      GlyphForgeMusicTest.render({rms:0.8,bands:Array(8).fill(0.7)},0.9,1);
      return document.getElementById('music-preview').toDataURL()
    }''')
    assert off!=on

def test_video_import_local_mp4(page):
    page.locator('.nav[data-tab="import"]').click()
    page.locator('#file-media').set_input_files(ROOT/'tests'/'testsrc_2s.mp4')
    expect(page.locator('#frame-total')).to_have_text('13',timeout=12000)
    assert 'Готово: 13 кадров' in page.locator('#import-result').inner_text()
    page.locator('#save-design').click()
    assert len(page.evaluate('GlyphForgeTest.getState().designs[0].frames'))==13


def test_toggle_rule_and_delete(page):
    page.locator('.nav[data-tab="rules"]').click()
    page.locator('#add-rule').click()
    assert page.evaluate('GlyphForgeTest.getState().rules[0].enabled') is True
    page.locator('.rule-item button.pill').click()
    assert page.evaluate('GlyphForgeTest.getState().rules[0].enabled') is False
    page.locator('.rule-item button.danger').click()
    assert len(page.evaluate('GlyphForgeTest.getState().rules'))==0


def test_library_export_and_reimport(page):
    page.locator('.nav[data-tab="library"]').click()
    before=len(page.evaluate('GlyphForgeTest.getState().designs'))
    with page.expect_download() as exp:
        page.locator('#export-all').click()
    item=exp.value
    assert item.suggested_filename=='glyphforge-library.gforge'
    page.locator('#file-json').set_input_files({'name':'library.gforge','mimeType':'application/json','buffer':Path(item.path()).read_bytes()})
    expect(page.locator('#library-grid .design-card')).to_have_count(before*2,timeout=7000)


def test_json_format_roundtrip(page):
    good=page.evaluate('''() => {
      const original=GlyphForgeTest.getState().designs[0];
      const exported=GlyphFormat.exportMuseum(original);
      const decoded=GlyphFormat.normalizeDesign(exported);
      return JSON.stringify(decoded.frames.map(x=>x.pixels)) === JSON.stringify(original.frames.map(x=>x.pixels));
    }''')
    assert good
