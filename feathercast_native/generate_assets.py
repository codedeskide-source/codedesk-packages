"""Generate self-contained original CC0 ninja frames. Python Pillow required at build time only."""
from PIL import Image, ImageDraw
from pathlib import Path
out = Path(__file__).parent / 'app/src/main/assets/ninja'
out.mkdir(parents=True, exist_ok=True)
palettes = {
    'shadow': ('#19283d','#101c2c','#334f70','#58d5d0','#9cf7ef','#657b99'),
    'crimson': ('#2d202b','#1b141d','#513347','#ec6271','#ffc4c7','#8e6572'),
    'forest': ('#182e2a','#10211e','#35544c','#b6db76','#e0f3ad','#66887a'),
}
for name, (cloth, shade, light, accent, accent2, trim) in palettes.items():
 for blink in range(2):
  for jaw in range(6):
   im = Image.new('RGBA',(384,384),(0,0,0,0)); d = ImageDraw.Draw(im)
   # Hidden scabbard and grip
   d.line([(260,360),(296,88)], fill='#131b25',width=27)
   d.line([(262,355),(297,95)], fill=light,width=13)
   d.polygon([(109,365),(100,300),(130,268),(183,250),(231,250),(268,264),(292,298),(299,380)],fill=shade)
   d.polygon([(111,379),(121,291),(150,265),(188,290),(163,380)],fill=cloth)
   d.polygon([(291,380),(284,291),(259,265),(218,290),(242,380)],fill=cloth)
   d.polygon([(153,268),(192,287),(231,267),(222,374),(168,375)],fill=light)
   d.polygon([(153,286),(195,319),(174,366)],fill=trim)
   # Hood and mask, mask cloth jaw grows vertically without drawn mouth oval/line
   d.polygon([(111,242),(83,216),(89,132),(120,66),(163,44),(210,43),(259,62),(291,122),(291,207),(273,253),(231,295),(153,294)],fill=cloth)
   d.polygon([(92,141),(113,87),(165,60),(227,65),(274,111),(249,132),(208,109),(156,116),(112,149)],fill=light)
   d.polygon([(113,141),(143,116),(191,105),(244,118),(271,146),(257,215),(229,239),(151,239),(120,215)],fill='#dba684')
   # Mask silhouette stretches at bottom and changes NO visible mouth shape
   drop=jaw*4
   d.polygon([(104,183),(139,179),(179,188),(205,190),(244,180),(277,183),(270,233),(242,270+drop),(205,287+drop),(184,288+drop),(144,269+drop),(111,229)], fill=shade)
   d.polygon([(110,187),(146,195),(192,206+drop//4),(236,194),(270,189),(249,223),(194,244+drop//2),(135,221)],fill=cloth)
   # Headband & jewel
   d.polygon([(110,141),(124,105),(158,90),(224,90),(257,106),(272,142),(243,153),(192,149),(140,156)],fill=shade)
   d.polygon([(130,129),(149,107),(187,100),(226,108),(252,129),(227,133),(193,126),(150,134)],fill=light)
   d.rounded_rectangle((169,104,213,138),radius=7,fill='#a3bdc5')
   d.polygon([(191,110),(205,123),(191,133),(178,122)],fill=accent)
   # Eyes narrow on blink, only mask itself animates when speaking
   for x in [157,226]:
    h = 2 if blink else 11
    d.ellipse((x-19,156-h//2,x+19,156+h//2),fill='#f2e9dc')
    if not blink:
     d.ellipse((x-6,150,x+6,163),fill='#314a53')
     d.ellipse((x-3,152,x+3,161),fill='#121c26')
    d.line([(x-22,144),(x,139),(x+22,144)],fill=shade,width=5)
   # Scarf tail
   d.polygon([(258,267),(294,265),(334,296),(361,323),(329,318),(363,354),(307,326)],fill=accent)
   # Belt
   d.rounded_rectangle((155,355,239,376),radius=5,fill='#111b26',outline=trim,width=2)
   d.rounded_rectangle((184,351,210,378),radius=4,fill='#a3bdc5')
   d.polygon([(197,357),(205,364),(196,374),(187,364)],fill=accent)
   im.save(out / f'{name}-{blink}-{jaw}.png',optimize=True)
print('Created',len(list(out.glob('*.png'))),'ninja PNG frames')