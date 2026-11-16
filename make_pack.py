from PIL import Image, ImageDraw
from pathlib import Path
import json, zipfile
root=Path(__file__).parent/'resourcepack'
colors=[('#24103e','#9d4dff','#ead7ff'),('#102d54','#3d9bff','#d8f3ff'),('#4b101d','#ff355e','#ffd2dc'),('#183c4a','#4fe5dc','#e2ffff'),('#6a2a0a','#ffb52e','#fff1b8'),('#24134e','#a56cff','#eee1ff'),('#173a55','#70e5ff','#e3fbff'),('#4b1010','#ff4b28','#ffe0c7'),('#18365a','#8bdcff','#e5f7ff'),('#3a3210','#f2d34b','#fff6bf')]
# pixel art icons, drawn on 32x32 canvas with crisp 1px pixels
for i,name in enumerate(['void_scythe','thunder_blade','blood_dagger','guardian_shield','phoenix_feather','rift_crystal','time_hourglass','berserker_heart','frost_rune','summoning_bell']):
    dark,glow,light=colors[i]
    im=Image.new('RGBA',(32,32),(0,0,0,0)); d=ImageDraw.Draw(im)
    # outer rune sparkles
    for x,y in [(4,5),(26,4),(5,25),(27,25),(15,2),(2,16),(29,15)]: d.point((x,y),fill=light)
    if i==0: # curved scythe blade + shaft
        d.line([(9,28),(13,22),(17,16),(21,11),(25,7)],fill=dark,width=5)
        d.line([(9,28),(13,22),(17,16),(21,11),(25,7)],fill=glow,width=2)
        d.line([(23,9),(25,4),(29,3),(28,8),(24,13),(19,15),(15,14),(12,11),(15,10),(19,12),(23,9)],fill=glow,width=3)
        d.line([(24,8),(27,5),(26,9),(22,12),(18,13)],fill=light,width=1)
        d.rectangle((7,26,11,29),fill=light)
    elif i==1:
        d.line([(16,3),(16,25)],fill=glow,width=4); d.polygon([(16,1),(11,8),(16,6),(21,8)],fill=light); d.polygon([(16,22),(11,28),(16,26),(21,28)],fill=glow); d.line([(8,16),(24,16)],fill=light,width=2)
    elif i==2:
        d.polygon([(16,2),(22,13),(18,27),(14,27),(10,13)],fill=glow); d.line([(16,4),(16,25)],fill=light,width=1); d.rectangle((13,25,19,29),fill=dark)
    elif i==3:
        d.polygon([(16,2),(27,7),(25,21),(16,29),(7,21),(5,7)],fill=glow); d.polygon([(16,6),(23,10),(21,20),(16,25),(11,20),(9,10)],fill=dark); d.line([(16,9),(16,23)],fill=light,width=2)
    elif i==4:
        d.polygon([(16,2),(20,11),(28,6),(24,18),(16,28),(8,18),(4,6),(12,11)],fill=glow); d.line([(16,7),(16,25)],fill=light,width=2)
    elif i==5:
        d.polygon([(16,2),(26,13),(16,29),(6,13)],fill=glow); d.polygon([(16,6),(22,13),(16,24),(10,13)],fill=dark); d.line([(16,5),(16,25)],fill=light,width=1)
    elif i==6:
        d.polygon([(8,3),(24,3),(19,13),(19,19),(24,29),(8,29),(13,19),(13,13)],fill=glow); d.line([(13,13),(19,19),(13,19),(19,13)],fill=light,width=2); d.rectangle((7,2,25,4),fill=light); d.rectangle((7,28,25,30),fill=light)
    elif i==7:
        d.polygon([(16,29),(4,15),(5,8),(11,5),(16,10),(21,5),(27,8),(28,15)],fill=glow); d.line([(16,11),(16,25)],fill=light,width=2)
    elif i==8:
        d.polygon([(16,2),(20,11),(29,16),(20,21),(16,30),(12,21),(3,16),(12,11)],fill=glow); d.line([(16,5),(16,27)],fill=light,width=2); d.line([(5,16),(27,16)],fill=light,width=1)
    else:
        d.arc((6,3,26,25),180,360,fill=glow,width=4); d.rectangle((8,14,24,27),fill=glow); d.rectangle((11,17,21,24),fill=dark); d.line([(16,17),(16,24)],fill=light,width=2); d.rectangle((13,27,19,30),fill=light)
    # subtle dark outline and bright highlights
    im=im.resize((128,128),Image.Resampling.NEAREST)
    im.save(root/f'assets/ultimateartifacts/textures/item/{name}.png')
    model={'parent':'minecraft:item/generated','textures':{'layer0':f'ultimateartifacts:item/{name}'}}
    (root/f'assets/ultimateartifacts/models/item/{name}.json').write_text(json.dumps(model,ensure_ascii=False,indent=2))
    definition={'type':'minecraft:model','model':f'ultimateartifacts:item/{name}'}
    (root/f'assets/ultimateartifacts/items/{name}.json').write_text(json.dumps(definition,ensure_ascii=False,indent=2))
(root/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':75,'description':'UltimateArtifacts • Paper 1.21.11'}},ensure_ascii=False,indent=2))
(root/'README.txt').write_text('UltimateArtifacts resource pack for Minecraft Java 1.21.11.\nZip the CONTENTS of this folder, not the folder itself.\nInstall in resourcepacks or host it and configure server.properties.\n')
zip_path=Path(__file__).parent/'UltimateArtifacts-ResourcePack-1.21.11.zip'
with zipfile.ZipFile(zip_path,'w',zipfile.ZIP_DEFLATED) as z:
    for p in root.rglob('*'):
        if p.is_file(): z.write(p,p.relative_to(root))
print(zip_path)
