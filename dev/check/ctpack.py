# Writes the connected-texture test pack for scene `ctlook` (addons ct-fabric, ct-forge-vanilla): python3 dev/check/ctpack.py <profile>/resourcepacks/sbct-test.zip
import zipfile, io, sys
from PIL import Image, ImageDraw
def png(im):
    b = io.BytesIO(); im.save(b, 'PNG'); return b.getvalue()
def tile(color, edge=None):
    im = Image.new('RGBA', (16, 16), color)
    if edge:
        ImageDraw.Draw(im).rectangle([0, 0, 15, 15], outline=edge)
    return im
z = zipfile.ZipFile(sys.argv[1], 'w')
z.writestr('pack.mcmeta', '{"pack":{"pack_format":15,"supported_formats":[15,999],"min_format":15,"max_format":999,"description":"sbct test"}}')
base = 'assets/minecraft/optifine/ctm/sbct/'
z.writestr(base + 'stone/stone.properties', 'method=ctm\nmatchBlocks=stone\ntiles=0-46\n')
for i in range(47):
    z.writestr(base + f'stone/{i}.png', png(tile((200, 40 + i * 4, 40, 255), (255, 255, 255, 255))))
z.writestr(base + 'ov/ov.properties', 'method=overlay\nmatchBlocks=dirt\nconnectBlocks=deepslate\ntiles=0-16\n')
for i in range(17):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ImageDraw.Draw(im).rectangle([0, 0, 15, 5], fill=(60, 120, 255, 255))
    ImageDraw.Draw(im).rectangle([0, 0, 5, 15], fill=(60, 120, 255, 255))
    z.writestr(base + f'ov/{i}.png', png(im))
