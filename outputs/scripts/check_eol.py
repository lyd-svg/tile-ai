# -*- coding: utf-8 -*-
"""检查待纳入版本控制的文件的行尾风格"""
import sys, io, glob

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

files = sorted(glob.glob('*.md') + glob.glob('*.yaml') + ['.gitignore']
               + glob.glob('outputs/scripts/*') + glob.glob('outputs/tools/*'))
crlf_files, lf_files = [], []
for f in files:
    b = open(f, 'rb').read()
    crlf = b.count(b'\r\n')
    lf = b.count(b'\n') - crlf
    kind = 'CRLF' if crlf > lf else 'LF'
    print(f'{f:<44} CRLF={crlf:<6} LF={lf:<6} -> {kind}')
    (crlf_files if kind == 'CRLF' else lf_files).append(f)

print()
print(f'CRLF 文件: {len(crlf_files)}')
print(f'LF   文件: {len(lf_files)}')
