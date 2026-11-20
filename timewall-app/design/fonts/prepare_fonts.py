"""Rebuild KitKat static assets: pip install fonttools==4.60.2, then run this file.

Original Google Fonts downloads and their documentation stay in sibling folders.
All axes are pinned (weight 400, other axes at defaults); no variable font is shipped.
"""
from pathlib import Path
import shutil
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT.parents[1] / 'src/main/assets'
FAMILIES = ['Firjar', 'Montserrat', 'Noto_Sans', 'Open_Sans', 'Raleway',
            'Roboto_Condensed', 'Roboto_Slab', 'Shantell_Sans']

def prepare():
    """Create Regular static fonts, preserving legal metadata and license notices."""
    for family in FAMILIES:
        folder = ROOT / family
        source = next(p for p in folder.iterdir() if p.name not in
                      ('OFL.txt', 'LICENSE.txt', 'README.txt'))
        font = TTFont(source)
        axes = {a.axisTag: (400 if a.axisTag == 'wght' else a.defaultValue)
                for a in font['fvar'].axes}
        static = instantiateVariableFont(font, axes, inplace=False)
        # Raleway reserves its family name: use a distinct name in the modified font.
        internal = 'TW Rale' if family == 'Raleway' else family.replace('_', ' ')
        names = {1: internal, 2: 'Regular', 3: internal + '-Regular-Static',
                 4: internal + ' Regular', 6: internal.replace(' ', '') + '-Regular',
                 16: internal, 17: 'Regular'}
        for record in static['name'].names:
            if record.nameID in names:
                record.string = names[record.nameID].encode(record.getEncoding())
        static.recalcTimestamp = False
        target = ASSETS / (family.replace('_', '') + '-Regular.ttf')
        static.save(target)
        assert 'fvar' not in TTFont(target)
        license_file = folder / ('LICENSE.txt' if family == 'Roboto_Slab' else 'OFL.txt')
        shutil.copy2(license_file, ASSETS / (family.replace('_', '') + '-' + license_file.name))
        print(target.name, axes)

if __name__ == '__main__':
    prepare()
