from pathlib import Path
import struct, zlib

S = 512
px = [[(255,255,255,0) for _ in range(S)] for _ in range(S)]
for y in range(S):
    for x in range(S):
        if (x-256)**2 + (y-256)**2 <= 220**2:
            px[y][x] = (56,100,75,255)
for cx in (160,320):
    for y in range(144,177):
        for x in range(cx-16,cx+17):
            if (x-cx)**2 + (y-160)**2 <= 16**2: px[y][x] = (255,255,255,255)
for i in range(201):
    t=i/200; x=round((15+18*t)*S/48); y=round((28+20*t*(1-t))*S/48)
    for dy in range(-10,11):
        for dx in range(-10,11):
            if dx*dx+dy*dy<=100 and 0<=x+dx<S and 0<=y+dy<S: px[y+dy][x+dx]=(255,255,255,255)
target = Path(__file__).resolve().parents[1] / 'docs' / 'phonemood-play-icon-512.png'
raw=b''.join(b'\0'+bytes(v for p in row for v in p) for row in px)
def c(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
target.write_bytes(b'\x89PNG\r\n\x1a\n'+c(b'IHDR',struct.pack('>IIBBBBB',S,S,8,6,0,0,0))+c(b'IDAT',zlib.compress(raw,9))+c(b'IEND',b''))
print(target)
